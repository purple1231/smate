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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Base64; // ⭐️ [추가] Base64 임포트
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


    /**
     * ⭐️ [수정됨] 멀티모달(텍스트 + 이미지) API 호출
     * - 'input' 값에 따라 프롬프트를 분기합니다.
     */
    public Mono<String> callGeminiWithVision(String sessionId, String domain, String input, byte[] imageBytes) {
        Persona persona = PersonaRepository.getPersona(domain);
        Deque<String> history = sessionMemory.computeIfAbsent(sessionId, k -> new LinkedList<>());

        String textPrompt; // ⭐️ 프롬프트를 담을 변수

        // ⭐️ [핵심] 'input' 값에 따라 프롬프트 분기
        if ("[SYSTEM_SCREENSHOT]".equals(input)) {
            // 1. (신규) 시스템 스크린샷용 "재밌는 말" 프롬프트
            // log.info("[Vision] 시스템 스크린샷용 '재밌는 말' 프롬프트 사용"); // (로그 추가)
            textPrompt = """
                    %s
                    [규칙]
                    1. 방금 사용자의 데스크탑 화면 스크린샷을 받았어. (사용자는 이걸 몰라)
                    2. 넌 이 화면을 보고 사용자의 현재 상황에 대해 '재치있거나', '재미있는' 한 마디를 해야 해.
                    3.  화면에 '커비'나 '시호' 같은 애니메이션 캐릭터(아바타)가 보일 수 있는데, 그건 '너' 자신이야.
                       **이 캐릭터에 대해서는 절대 언급하거나 반응하지 마.**
                       오직 사용자가 '무엇을 하고 있는지'(예: 코딩, 웹 서핑, 바탕화면)에 대해서만 집중해서 말해.
                    4. 너의 페르소나를 완벽하게 유지하면서, 너무 길지 않게 한두 문장으로 말해줘.
                    5. ⭐️ [중요] 이 응답은 사용자가 직접 물어본 것이 아니므로, 절대 대화 이력(History)에 저장하면 안 돼.
                    
                    [예시: (페르소나: 츤데레)]
                    (화면: 코딩 중) -> "흥... 또 에러난 거야? 맨날 그것도 못하고."
                    (화면: 유튜브 시청) -> "쯧... 한가하게 놀고 있네. 뭐, 잠깐 쉬는 것도 나쁘진 않지만."
                    (화면: 바탕화면) -> "왜 아무것도 안 해? 혹시... 내 생각이라도 하는 거야? ...바보."
                    
                    [실제 응답]
                    """.formatted(persona.getDescription());

        } else {
            // 2. (기존) 사용자 질문용 "여기서..." 프롬프트
            // log.info("[Vision] 사용자 질문용 '여기서' 프롬프트 사용"); // (로그 추가)

            // 1. 대화 이력 (History) 빌드
            StringBuilder context = new StringBuilder();
            for (String h : history) {
                context.append(h).append("\n");
            }

            textPrompt = """
                    %s
                    [규칙]
                    1. 너는 사용자의 데스크탑 화면을 함께 보고 있어.
                    2. 사용자가 "여기서" 라고 말하면 함께 전송된 스크린샷을 의미하는 거야.
                    3. 스크린샷을 보고 사용자의 질문에 대답해.
                    4.  화면에 '커비'나 '시호' 같은 애니메이션 캐릭터(아바타)가 보일 수 있는데, 그건 '너' 자신이야.
                       **이 캐릭터에 대해서는 절대 언급하거나 반응하지 마.**
                       오직 사용자가 '무엇을 하고 있는지'(예: 코딩, 웹 서핑, 바탕화면)에 대해서만 집중해서 말해.
                    [이전 대화]
                    %s
                    [사용자 질문]
                    %s
                    """.formatted(persona.getDescription(), context, input);
        }

        // 3. 이미지 Base64 인코딩 (공통)
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        // 4. 멀티모달 요청 본문(Body) 생성 (공통)
        List<VisionPart> parts = new ArrayList<>();
        parts.add(new VisionPart(textPrompt)); // ⭐️ 분기된 textPrompt 사용
        parts.add(new VisionPart(new InlineData("image/png", imageBase64)));

        VisionRequest body = new VisionRequest(List.of(new VisionContent(parts)));

        // 5. API 호출 (공통)
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
                    // ⭐️ [수정] 시스템 스크린샷이 아닐 때만 대화 이력에 저장
                    if (!"[SYSTEM_SCREENSHOT]".equals(input)) {
                        history.addLast("Q: " + input + "\nA: " + reply);
                        if (history.size() > 10) history.removeFirst();
                    }
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




    // --- ⭐️ [신규] 멀티모달 요청을 위한 DTO 클래스들 ---

    private static class VisionRequest {
        @JsonProperty("contents")
        public List<VisionContent> contents;

        public VisionRequest(List<VisionContent> contents) {
            this.contents = contents;
        }
    }

    private static class VisionContent {
        @JsonProperty("parts")
        public List<VisionPart> parts;

        public VisionContent(List<VisionPart> parts) {
            this.parts = parts;
        }
    }

    private static class VisionPart {
        @JsonProperty("text")
        public String text;

        @JsonProperty("inlineData")
        public InlineData inlineData;

        // 텍스트 파트용 생성자
        public VisionPart(String text) {
            this.text = text;
            this.inlineData = null;
        }

        // 이미지 파트용 생성자
        public VisionPart(InlineData inlineData) {
            this.text = null;
            this.inlineData = inlineData;
        }
    }

    private static class InlineData {
        @JsonProperty("mimeType")
        public String mimeType;

        @JsonProperty("data")
        public String data;

        public InlineData(String mimeType, String data) {
            this.mimeType = mimeType;
            this.data = data;
        }
    }
    // ------------------------------------------------


}