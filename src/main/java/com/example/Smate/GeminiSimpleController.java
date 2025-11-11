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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;


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
            @RequestParam("question") String question,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot,
            HttpSession session
    ) {

        // --- (A) 페르소나 캐시 저장 (기존과 동일) ---
        log.info("[CACHE-SET] Key='{}', Value='{}'", sessionId, domain);
        session.setAttribute("selectedPersona", domain);
        personaCacheService.setPersona(sessionId, domain);
        // ----------------------------------------------


        // ⭐️ [추가] 스크린샷이 제대로 수신되었는지 로그 확인
        if (screenshot != null && !screenshot.isEmpty()) {
            log.info("[Chat] 스크린샷 수신 성공! 파일명: {}, 크기: {} bytes",
                    screenshot.getOriginalFilename(), screenshot.getSize());
        }


        // --- ⭐️ 3단계 우선순위 로직 ---

        // 1) ⭐️ (1순위) "켜줘" 로직 (텍스트 전용)
        String executionResponse = geminiService.handleExecutionRequest(question, computerId);
        if (executionResponse != null) {
            log.info("앱 실행 감지: {}", executionResponse);
            // ⭐️ "CHAT" 타입으로 응답 (기본 생성자)
            ChatResponseDto dto = new ChatResponseDto(executionResponse, new TaskDto(null, null));
            return Mono.just(ResponseEntity.ok(dto));
        }

        // 2) ⭐️ (2순위) "알람/일정" 로직 (텍스트 전용)
        TaskDto task = geminiService.extractTaskFromMessage(question);

        // 3) ⭐️ (3순위) "일반 대화" (스크린샷 유무에 따라 분기)
        Mono<String> aiMono;

        if (screenshot != null && !screenshot.isEmpty()) {
            // 3-1) [신규] 스크린샷이 있으면 '비전(Vision)' 메서드 호출
            log.info("[Chat] 비전(멀티모달) API 호출");
            try {
                byte[] imageBytes = screenshot.getBytes();
                // ⭐️ [SYSTEM_SCREENSHOT]이든, 사용자 질문이든 일단 'question'을 그대로 넘김
                aiMono = geminiService.callGeminiWithVision(sessionId, domain, question, imageBytes);
            } catch (IOException e) {
                log.error("스크린샷 바이트 변환 실패", e);
                aiMono = Mono.just("앗! 스크린샷을 읽다가 오류가 났어. 😢");
            }
        } else {
            // 3-2) [기존] 스크린샷이 없으면 '텍스트' 메서드 호출
            log.info("[Chat] 일반(텍스트) API 호출");
            aiMono = geminiService.callGemini(sessionId, domain, question);
        }

        // 4) ⭐️ (공통) 알람 결과와 AI 응답 결합
        return aiMono.map(aiReply -> {
            if (task.getTime() != null && task.getText() != null) {
                log.info("알람 추출 감지 (대화 중): {}", task.getText());
            }

            // ⭐️⭐️⭐️ [핵심 수정] ⭐️⭐️⭐️
            // ----------------------------------------------
            // 'question'이 [SYSTEM_SCREENSHOT]이었는지 확인하여 DTO의 'type'을 결정
            String responseType = "[SYSTEM_SCREENSHOT]".equals(question) ? "SYSTEM_REMARK" : "CHAT";

            ChatResponseDto dto = new ChatResponseDto(aiReply, task, responseType);
            // ----------------------------------------------

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
        // ... (이하 로직 동일) ...
        String selectedPersona = (String) session.getAttribute("selectedPersona");
        if (selectedPersona != null) {
            return ResponseEntity.ok(selectedPersona);
        } else {
            return ResponseEntity.status(404).body("No character selected in this session.");
        }
    }
}