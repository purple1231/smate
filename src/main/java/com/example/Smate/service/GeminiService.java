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

import java.util.*;

@Service
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    // 세션별 대화 기억
    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();

    // 모델 경로 상수화
    private static final String GEMINI_PATH =
            "/v1beta/models/gemini-flash-latest:generateContent";

    // JSON 파싱을 위한 ObjectMapper 추가
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService() {
        this.webClient = WebClient.create("https://generativelanguage.googleapis.com");
    }

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
                .map(this::extractFirstText) // ✨ 응답에서 텍스트만 추출
                .doOnNext(reply -> {
                    history.addLast("Q: " + input + "\nA: " + reply);
                    if (history.size() > 10) history.removeFirst();
                });
    }


    /**
     * [신규] 알람/일정 추출 메서드
     */
    public TaskDto extractTaskFromMessage(String userMessage) {
        // 모델한테 "JSON만 줘"라고 시키는 프롬프트
        String prompt = """
                너는 '알람 일정 추출기'다.
                사용자가 쓴 한국어 문장 안에 알람/일정/리마인드 요청이 있거나 문장이 있으면,
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
            // 똑같은 방식으로 API 호출 (단, 동기식 .block() 사용)
            String raw = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(GEMINI_PATH)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 동기로 결과 받기

            // Gemini 전체 응답에서 text 부분(JSON)만 뽑기
            String jsonText = extractFirstText(raw);

            if (jsonText == null || jsonText.isBlank()) {
                return new TaskDto(null, null);
            }

            // 그 text가 우리가 시킨 JSON이므로 그대로 파싱
            return objectMapper.readValue(jsonText, TaskDto.class);

        } catch (Exception e) {
            // 실패하면 Unity 쪽에 null/null 보내도록
            return new TaskDto(null, null);
        }
    }

    /**
     * [신규] Gemini 응답(JSON)에서 실제 텍스트만 뽑는 보조 메서드
     */
    private String extractFirstText(String geminiRaw) {
        try {
            JsonNode root = objectMapper.readTree(geminiRaw);
            return root.get("candidates").get(0)
                    .get("content").get("parts").get(0)
                    .get("text").asText();
        } catch (Exception e) {
            return null; // 파싱 실패 시
        }
    }
}