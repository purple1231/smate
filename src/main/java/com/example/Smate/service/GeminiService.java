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

import java.time.LocalDate; // ⭐️ [추가] 오늘 날짜를 위해
import java.time.format.DateTimeFormatter; // ⭐️ [추가] 날짜 포맷을 위해
import java.util.*;

@Service
public class GeminiService {
    // ... (apiKey, webClient, sessionMemory 등 다른 변수들은 그대로) ...
    private final WebClient webClient;
    @Value("${gemini.api.key}")
    private String apiKey;
    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();
    private static final String GEMINI_PATH = "/v1beta/models/gemini-flash-latest:generateContent";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService() {
        this.webClient = WebClient.create("https://generativelanguage.googleapis.com");
    }

    // ... (callGemini 메서드는 그대로) ...
    public Mono<String> callGemini(String sessionId, String domain, String input) {
        // (기존 코드와 동일)
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


    /**
     * [수정됨] 알람/일정 추출 메서드 (프롬프트 강화 버전)
     */
    public TaskDto extractTaskFromMessage(String userMessage) {

        // 1. (기존) Java에서 키워드 검사 (효율화!)
        if (userMessage == null ||
                (!userMessage.contains("알람") &&
                        !userMessage.contains("일정") &&
                        !userMessage.contains("리마인드")))
        {
            return new TaskDto(null, null); // ⭐️ API 호출 안 함
        }

        // ---
        // ✨ [수정] 2. "오늘 날짜"를 프롬프트에 포함시켜 정확도 높이기
        // ---
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE); // "2025-11-11" (오늘 날짜)

        // ---
        // ✨ [수정] 3. 프롬프트를 "추출"에만 집중하도록 변경
        // ---
        String prompt = """
                너의 유일한 임무는 사용자 문장에서 '시간'과 '할 일'을 JSON 객체로 추출하는 것이다.
                
                [오늘 날짜: %s]
                
                [규칙]
                1. '내일 오후 3시', '10분 뒤' 같은 상대적 시간도 [오늘 날짜]를 기준으로 'YYYY-MM-DD HH:MM' 형식으로 계산해. (시간은 24시 표기법)
                2. 'text'는 할 일을 짧고 명확하게 요약해.
                3. 만약 문장이 '알람' 등의 키워드를 포함해도, 실제 '요청'이 아니라 단순한 잡담이나 질문이라면 (예: "알람 시끄러워", "내일 일정 있어?"), {"time": null, "text": null} 을 반환해.
                4. ⭐️ [절대 규칙] 설명, 사과, 인사 등 어떠한 텍스트도 JSON 객체 외에 절대 출력하지 마. 오직 JSON 코드만 응답해.
                
                [예시 1]
                문장: "내일 오후 3시에 도서관 가라고 알려줘"
                응답: {"time": "%s 15:00", "text": "도서관 가기"}
                
                [예시 2]
                문장: "오늘 7시 반에 저녁 약속 리마인드 해줘"
                응답: {"time": "%s 19:30", "text": "저녁 약속"}
                
                [예시 3]
                문장: "알람 맞췄어?"
                응답: {"time": null, "text": null}
                
                [실제 추출]
                문장: "%s"
                """.formatted(
                today, // [오늘 날짜] (첫 번째 %s)
                LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE), // [예시 1]의 '내일' 날짜 (두 번째 %s)
                today, // [예시 2]의 '오늘' 날짜 (세 번째 %s)
                userMessage // [실제 추출] 문장 (네 번째 %s)
        );

        // (API 호출 로직은 기존과 동일)
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

            // ⭐️ "```json\n ... \n```" 같은 마크다운 제거
            jsonText = jsonText.replace("```json", "").replace("```", "").trim();

            return objectMapper.readValue(jsonText, TaskDto.class);

        } catch (Exception e) {
            return new TaskDto(null, null);
        }
    }

    /**
     * Gemini 응답(JSON)에서 실제 텍스트만 뽑는 보조 메서드
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