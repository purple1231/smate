package com.example.Smate.service;

// ⭐️ [추가] 필요한 import
import com.example.Smate.log.ActivityLog;
import com.example.Smate.repo.ActivityLogRepository;
import com.example.Smate.domain.Persona;
import com.example.Smate.domain.PersonaRepository;
import com.example.Smate.dto.TaskDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired; // ⭐️ [추가]
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors; // ⭐️ [추가]
import java.util.ArrayList; // ⭐️ [추가]

@Service
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActivityLogRepository activityLogRepository;

    @Value("${gemini.api.key}")
    private String apiKey;
    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();
    private static final String GEMINI_PATH = "/v1beta/models/gemini-flash-latest:generateContent";
    private static final String PYTHON_CLIENT_URL = "http://localhost:5001/execute";

    private static final Map<String, String> APP_NAME_MAPPING = Map.of(
            "spotify", "Spotify.exe",
            "discord", "Discord.exe",
            "code", "Code.exe"
    );

    private static class AppListDto {
        public List<String> apps;
    }

    @Autowired
    public GeminiService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
        this.webClient = WebClient.create("https://generativelanguage.googleapis.com");
    }

    // ... (callGemini 메서드는 기존과 동일) ...
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

    // ... (extractTaskFromMessage 메서드는 기존과 동일) ...
    public TaskDto extractTaskFromMessage(String userMessage) {
        // (기존 코드와 동일)
        if (userMessage == null ||
                (!userMessage.contains("알람") &&
                        !userMessage.contains("일정") &&
                        !userMessage.contains("리마인드")))
        {
            return new TaskDto(null, null);
        }
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
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
                today,
                LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                today,
                userMessage
        );

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
            jsonText = jsonText.replace("```json", "").replace("```", "").trim();
            return objectMapper.readValue(jsonText, TaskDto.class);
        } catch (Exception e) {
            return new TaskDto(null, null);
        }
    }

    // ... (handleExecutionRequest 메서드는 기존과 동일) ...
    public String handleExecutionRequest(String userMessage, String computerId) {
        List<String> appNames = extractExecutionTarget(userMessage);
        if (appNames == null || appNames.isEmpty()) {
            return null;
        }
        List<String> executedApps = new ArrayList<>();
        List<String> failedApps = new ArrayList<>();
        for (String appName : appNames) {
            String processName = APP_NAME_MAPPING.get(appName);
            if (processName == null) {
                System.err.println("Gemini가 매핑에 없는 앱을 반환: " + appName);
                failedApps.add(appName + "(매핑 없음)");
                continue;
            }
            Optional<ActivityLog> logEntry = activityLogRepository
                    .findTopByComputerIdAndProcessNameAndLogTypeOrderByIdDesc(
                            computerId,
                            processName,
                            ActivityLog.LogType.START // ⭐️ [수정] LogType.START를 명시
                    );
            if (logEntry.isEmpty()) {
                System.err.println(computerId + " 컴퓨터의 " + processName + " 경로를 DB에서 찾을 수 없습니다.");
                failedApps.add(appName + "(경로 없음)");
                continue;
            }
            String processPath = logEntry.get().getProcessPath();
            if (processPath == null || processPath.isBlank() || processPath.equalsIgnoreCase("NULL")) {
                System.err.println(processName + "의 경로가 DB에 NULL로 저장되어 있습니다.");
                failedApps.add(appName + "(경로 NULL)");
                continue;
            }
            sendExecutionCommandToPython(processPath);
            executedApps.add(appName);
        }
        if (executedApps.isEmpty()) {
            String failedReason = failedApps.stream().collect(Collectors.joining(", "));
            return "앗, " + failedReason + " 문제로 실행에 실패했어요. 😢";
        }
        String successResponse = String.join(", ", executedApps) + "을(를) 실행할게요! 🚀";
        if (!failedApps.isEmpty()) {
            String failedReason = failedApps.stream().collect(Collectors.joining(", "));
            successResponse += " (하지만 " + failedReason + "는 실패했어요.)";
        }
        return successResponse;
    }

    /**
     * 2. (Gemini 호출) 실행할 앱 이름 "목록"을 추출하는 헬퍼 메서드
     */
    private List<String> extractExecutionTarget(String userMessage) {

        // ⭐️⭐️⭐️ [핵심 수정] ⭐️⭐️⭐️
        // "켜줘" 라는 단어가 없으면 앱 실행 API를 아예 호출하지 않습니다.
        if (userMessage == null || !userMessage.contains("켜줘"))
        {
            return null;
        }
        // ⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️

        // "켜줘"가 있을 때만 Gemini에게 물어봅니다.
        String prompt = """
                너의 유일한 임무는 사용자 문장에서 실행하려는 앱 키워드를 JSON 배열(List)로 추출하는 것이다.
                대상 앱: [spotify, discord, code]
                [규칙]
                1. '스포티파이 켜줘', '노래 켜줘' -> "spotify"
                2. '디코 켜줘', '디스코드 켜줘' -> "discord"
                3. 'vscode 켜줘', '코드 켜줘' -> "code"
                4. ⭐️ [중요] "노래 들으면서 코딩하게 켜줘" 처럼 여러 개가 감지되면 ["spotify", "code"] 처럼 배열에 모두 담아.
                5. 만약 문장이 키워드를 포함해도, 실제 '요청'이 아니라면 (예: "스포티파이 좋아?"), {"apps": null} 또는 {"apps": []}를 반환해.
                6. ⭐️ [절대 규칙] 설명, 사과, 인사 등 어떠한 텍스트도 JSON 객체 외에 절대 출력하지 마. 오직 JSON 코드만 응답해.
                [예시 1]
                문장: "스포티파이 켜줘"
                응답: {"apps": ["spotify"]}
                [예시 2]
                문장: "노래랑 같이 코딩하게 켜줘"
                응답: {"apps": ["spotify", "code"]}
                [예시 3]
                문장: "디스코드 실행했어?"
                응답: {"apps": null}
                [실제 추출]
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
                return null;
            }
            jsonText = jsonText.replace("```json", "").replace("```", "").trim();
            AppListDto dto = objectMapper.readValue(jsonText, AppListDto.class);
            return dto.apps;
        } catch (Exception e) {
            System.err.println("앱 실행 추출 Gemini API 오류: " + e.getMessage());
            return null;
        }
    }

    // ... (sendExecutionCommandToPython 메서드는 기존과 동일) ...
    private void sendExecutionCommandToPython(String path) {
        Map<String, Object> body = Map.of("command", path);
        webClient.post()
                .uri(PYTHON_CLIENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        response -> System.out.println("✅ [Python] 실행 요청 성공: " + response),
                        error -> System.err.println("❌ [Python] 실행 요청 실패: " + error.getMessage())
                );
    }

    // ... (extractFirstText 메서드는 기존과 동일) ...
    private String extractFirstText(String geminiRaw) {
        try {
            JsonNode root = objectMapper.readTree(geminiRaw);
            return root.get("candidates").get(0)
                    .get("content").get("parts").get(0)
                    .get("text").asText();
        } catch (Exception e) {
            return null;
        }
    }
}