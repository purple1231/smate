package com.example.Smate;

import com.example.Smate.dto.ChatResponseDto;
import com.example.Smate.dto.TaskDto;
import com.example.Smate.service.GeminiService;
import com.example.Smate.service.PersonaCacheService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/gemini")
public class GeminiSimpleController {

    private final GeminiService geminiService;
    private final PersonaCacheService personaCacheService;

    public GeminiSimpleController(GeminiService geminiService, PersonaCacheService personaCacheService) {
        this.geminiService = geminiService;
        this.personaCacheService = personaCacheService;
    }

    /**
     * ⭐️ [핵심 수정] 챗봇의 모든 요청을 3단계 '우선순위'로 처리
     * 1. (1순위) "켜줘" -> 앱 실행
     * 2. (2순위) "알람/일정" -> 알람 추출
     * 3. (3순위) 그 외 -> 일반 대화
     */
    @PostMapping("/simple")
    public Mono<ResponseEntity<ChatResponseDto>> chat(
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(defaultValue = "yandere") String domain,
            @RequestParam String computerId, // ⭐️ [필수] 프론트에서 computerId를 받아야 함
            @RequestBody String userMessage,
            HttpSession session
    ) {

        // --- (A) 페르소나 캐시 저장 (기존과 동일) ---
        log.info("[CACHE-SET] Key='{}', Value='{}'", sessionId, domain);
        session.setAttribute("selectedPersona", domain);
        personaCacheService.setPersona(sessionId, domain);
        // ----------------------------------------------


        // --- (B) ⭐️ [수정] 3단계 우선순위 로직 ---

        // 1) ⭐️ (1순위) "켜줘"가 있는지 확인 (앱 실행)
        // GeminiService는 "켜줘"가 있을 때만 응답(성공/실패)을 반환하고, 없으면 null을 반환합니다.
        String executionResponse = geminiService.handleExecutionRequest(userMessage, computerId);

        if (executionResponse != null) {
            // "켜줘" 명령이 감지됨! (성공이든 실패든)
            // 즉시 앱 실행 결과만 반환하고, 알람/잡담 로직은 무시합니다.
            log.info("앱 실행 감지: {}", executionResponse);
            ChatResponseDto dto = new ChatResponseDto(executionResponse, new TaskDto(null, null));
            return Mono.just(ResponseEntity.ok(dto));
        }

        // 2) ⭐️ (2순위) "켜줘"가 없었을 때만, "알람/일정" 확인
        TaskDto task = geminiService.extractTaskFromMessage(userMessage);

        // 3) ⭐️ (3순위) "켜줘"가 없었을 때만, "일반 대화" 실행
        Mono<String> aiMono = geminiService.callGemini(sessionId, domain, userMessage);

        // 3-1) (알람 O, 일반대화 O)
        if (task.getTime() != null && task.getText() != null) {
            log.info("알람 추출 감지: {}", task.getText());
            return aiMono.map(aiReply -> {
                // 알람과 AI 응답을 둘 다 반환
                ChatResponseDto dto = new ChatResponseDto(aiReply, task);
                return ResponseEntity.ok(dto);
            });
        }

        // 3-2) (알람 X, 일반대화 O)
        log.info("일반 대화 처리");
        return aiMono.map(aiReply -> {
            ChatResponseDto dto = new ChatResponseDto(aiReply, task); // task는 (null, null)
            return ResponseEntity.ok(dto);
        });
        // ----------------------------------------------
    }


    /**
     * [신규 API] 현재 세션에 저장된 캐릭터 정보를 확인하는 API
     * (기존과 동일)
     */
    @GetMapping("/character")
    public ResponseEntity<String> getCurrentCharacter(HttpSession session) {
        String selectedPersona = (String) session.getAttribute("selectedPersona");
        if (selectedPersona != null) {
            return ResponseEntity.ok(selectedPersona);
        } else {
            return ResponseEntity.status(404).body("No character selected in this session.");
        }
    }
}