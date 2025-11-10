package com.example.Smate.service;

import com.example.Smate.domain.Persona;
import com.example.Smate.domain.PersonaRepository;
import com.example.Smate.dto.ChatResponseDto;
import com.example.Smate.dto.TaskDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Base64;

@Service
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    // 세션별 간단 메모리
    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();

    private static final String GEMINI_PATH =
            "/v1beta/models/gemini-flash-latest:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService() {
        this.webClient = WebClient.create("https://generativelanguage.googleapis.com");
    }

    // ====== 텍스트만 ======
    public Mono<String> callGemini(String sessionId, String domain, String input) {
        Persona persona = PersonaRepository.getPersona(domain);
        Deque<String> history = sessionMemory.computeIfAbsent(sessionId, k -> new LinkedList<>());

        StringBuilder context = new StringBuilder();
        for (String h : history) {
            context.append(h).append("\n");
        }

        String prompt = """
            %s

            이전 대화:
            %s

            사용자:
            %s
            """.formatted(persona.getDescription(), context, input);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(GEMINI_PATH)
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::extractFirstText)
                .doOnNext(reply -> {
                    history.addLast("Q: " + input + "\nA: " + reply);
                    if (history.size() > 10) history.removeFirst();
                });
    }

    public Mono<ChatResponseDto> callGeminiWithImage(String sessionId,
                                                     String domain,
                                                     String userMessage,
                                                     MultipartFile screenshot) {
        Persona persona = PersonaRepository.getPersona(domain);
        Deque<String> history = sessionMemory.computeIfAbsent(sessionId, k -> new LinkedList<>());
        StringBuilder context = new StringBuilder();
        for (String h : history) {
            context.append(h).append("\n");
        }

        return Mono.fromCallable(() -> Base64.getEncoder().encodeToString(screenshot.getBytes()))
                .flatMap(base64Image -> {
                    // ① 여기서 네가 쓰는 모델 이름을 확인
                    // 지금 properties에는 flash-lite 넣어놨는데, 서비스에서는 1.5-flash를 호출하고 있었음
                    Map<String, Object> body = Map.of(
                            "contents", List.of(Map.of(
                                    "parts", List.of(
                                            Map.of("text",
                                                    """
                                                    %s
                                                    이전 대화:
                                                    %s
                                                    지금부터 사용자가 보낸 스크린샷 화면을 보고 문제를 설명하고,
                                                    요청한 내용이 무엇인지 한국어로 설명하라.
                                                    """.formatted(persona.getDescription(), context)
                                            ),
                                            Map.of(
                                                    "inlineData", Map.of(
                                                            "mimeType", "image/png",
                                                            "data", base64Image
                                                    )
                                            ),
                                            Map.of("text", "사용자 추가 설명: " + userMessage)
                                    )
                            ))
                    );

                    return webClient.post()
                            .uri(uriBuilder -> uriBuilder
                                    // 🔴 여기 모델 이름을 너 설정이랑 맞춰보자
                                    // .path("/v1beta/models/gemini-1.5-flash:generateContent")
                                    .path("/v1beta/models/gemini-flash-lite-latest:generateContent")
                                    .queryParam("key", apiKey)
                                    .build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            // ② 실제 원본 응답을 로그로 찍는다
                            .doOnNext(raw -> System.out.println("[GEMINI RAW IMAGE RESP] " + raw));
                })
                .map(raw -> {
                    String replyText = extractFirstText(raw);
                    TaskDto task = extractTaskFromMessage(userMessage);

                    history.addLast("Q(이미지): " + userMessage + "\nA: " + replyText);
                    if (history.size() > 10) history.removeFirst();

                    return new ChatResponseDto(replyText, task);
                })
                // ③ 여기서 실제 에러를 찍어본다
                .onErrorResume(e -> {
                    e.printStackTrace(); // 콘솔에 실제 이유 표시
                    return Mono.just(new ChatResponseDto("이미지 분석 중 오류가 발생했습니다.", new TaskDto(null, null)));
                });
    }



    // ====== 알람용 ======
    public TaskDto extractTaskFromMessage(String userMessage) {
        String prompt = """
                너는 '알람 일정 추출기'다.
                사용자가 쓴 한국어 문장 안에 알람/일정/리마인드 요청이 있으면
                아래 JSON 형식으로만 출력해.

                {
                  "time": "2025-11-08 15:00",
                  "text": "도서관 가기"
                }

                규칙:
                - text 는 자연스럽고 짧게.
                - 만약 알람 요청이 전혀 없으면 {"time": null, "text": null} 만 출력.
                - 설명, 말풍선, 해설 절대 쓰지 마. JSON 문자열만 보내.

                문장: "%s"
                """.formatted(userMessage);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        try {
            String raw = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(GEMINI_PATH)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String jsonText = extractFirstText(raw);

            if (jsonText == null || jsonText.isBlank()) {
                return new TaskDto(null, null);
            }

            return objectMapper.readValue(jsonText, TaskDto.class);

        } catch (Exception e) {
            return new TaskDto(null, null);
        }
    }

    // ====== 공통 파서 ======
    private String extractFirstText(String geminiRaw) {
        try {
            JsonNode root = objectMapper.readTree(geminiRaw);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                System.out.println("[GEMINI PARSE] no candidates: " + geminiRaw);
                return "이미지에서 설명할 수 있는 텍스트를 찾지 못했습니다.";
            }
            JsonNode first = candidates.get(0);
            JsonNode parts = first.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                System.out.println("[GEMINI PARSE] no parts: " + geminiRaw);
                return "이미지에서 설명할 수 있는 텍스트를 찾지 못했습니다.";
            }
            return parts.get(0).path("text").asText();
        } catch (Exception e) {
            e.printStackTrace();
            return "이미지 응답 파싱 중 오류가 발생했습니다.";
        }
    }

}
