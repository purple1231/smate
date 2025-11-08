package com.example.Smate.service;

import com.example.Smate.domain.Persona;
import com.example.Smate.domain.PersonaRepository;
import com.example.Smate.dto.TaskDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_URL =
            "/v1beta/models/gemini-pro:generateContent";

    // ✅ 세션별 대화 저장
    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();

    public GeminiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    // ✅ 기존 채팅 호출
    public Mono<String> callGemini(String sessionId, String domain, String input) {
        Persona persona = PersonaRepository.getPersona(domain);

        // 대화 기록 불러오기
        Deque<String> history = sessionMemory.computeIfAbsent(sessionId, k -> new LinkedList<>());
        StringBuilder context = new StringBuilder();
        for (String h : history) context.append(h).append("\n");

        // 프롬프트 구성
        String prompt = """
                %s

                [이전 대화]
                %s

                [사용자 입력]
                %s
                """.formatted(persona.getDescription(), context, input);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(GEMINI_URL)
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    history.addLast("Q: " + input + "\nA: " + response);
                    if (history.size() > 10) history.removeFirst();
                });
    }

    // ✅ 알람 Task만 JSON으로 추출하는 전용 메서드
    public TaskDto extractTaskFromMessage(String userMessage) {
        String prompt = """
        너는 '알람 일정 추출기'야.
        아래 문장에서 알람 또는 일정 요청이 있는지 찾아서
        JSON 형식으로 정확히 출력해.

        {
          "time": "2025-11-08 15:00",
          "text": "도서관 가기"
        }

        ▷ 규칙:
        - 날짜 없으면 오늘 날짜로 처리 (yyyy-MM-dd HH:mm)
        - 할 일(text)은 자연스럽고 짧게 (예: 도서관 가기)
        - 알람 요청이 없으면 {"time": null, "text": null}
        - 설명하지 말고 JSON만 출력해

        문장: "%s"
        """.formatted(userMessage);

        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        try {
            String rawResponse = webClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Gemini JSON 결과에서 text만 추출
            String jsonText = extractFirstText(rawResponse);

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonText, TaskDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ✅ Gemini 응답 JSON에서 candidates[0].content.parts[0].text 값을 추출
    private String extractFirstText(String geminiRawJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(geminiRawJson);

            return root.get("candidates").get(0)
                    .get("content").get("parts").get(0)
                    .get("text").asText();
        } catch (Exception e) {
            System.out.println("❌ [extractFirstText] JSON 파싱 실패: " + e.getMessage());
            return null; // 실패 시 null 반환
        }
    }

}
