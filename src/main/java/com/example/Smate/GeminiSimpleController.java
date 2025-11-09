package com.example.Smate;

import com.example.Smate.dto.ChatResponseDto;
import com.example.Smate.dto.TaskDto;
import com.example.Smate.service.GeminiService;
import com.example.Smate.service.PersonaCacheService; // ✨ 1. 기존 캐시 서비스 Import
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j; // ✨ 2. Slf4j Import
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j // ✨ 3. @Slf4j 어노테이션 추가
@RestController
@RequestMapping("/gemini")
public class GeminiSimpleController {

    private final GeminiService geminiService;
    private final PersonaCacheService personaCacheService; // ✨ 4. 기존 캐시 서비스 의존성 추가

    // ✨ 5. 생성자 수정
    public GeminiSimpleController(GeminiService geminiService, PersonaCacheService personaCacheService) {
        this.geminiService = geminiService;
        this.personaCacheService = personaCacheService;
    }

    /**
     * Gemini API를 호출하고, 알람을 추출하며, 사용된 캐릭터(domain)를 세션과 캐시에 저장합니다.
     */
    @PostMapping("/simple")
    public Mono<ResponseEntity<ChatResponseDto>> chat( // ✨ 6. 반환 타입 변경 (친구 코드 적용)
                                                       @RequestParam(defaultValue = "default") String sessionId,
                                                       @RequestParam(defaultValue = "yandere") String domain,
                                                       @RequestBody String userMessage, // ✨ 7. 변수명 변경 (input -> userMessage)
                                                       HttpSession session
    ) {

        // --- (A) 김진근로직임!!!!! (로그 및 캐시 저장) ---
        log.info("[CACHE-SET] Key='{}', Value='{}'", sessionId, domain);
        session.setAttribute("selectedPersona", domain);
        personaCacheService.setPersona(sessionId, domain);
        // ----------------------------------------------


        // --- (B) 한결이로직임! (AI 응답 + 알람 추출) ---

        // 1) AI 대화 응답 받기 (비동기)
        Mono<String> aiMono = geminiService.callGemini(sessionId, domain, userMessage);

        // 2) 알람/일정 추출하기 (동기)
        TaskDto task = geminiService.extractTaskFromMessage(userMessage);

        // 3) (A)와 (B)를 합쳐서 ChatResponseDto로 반환
        return aiMono.map(aiReply -> {
            ChatResponseDto dto = new ChatResponseDto(aiReply, task);
            return ResponseEntity.ok(dto);
        });
        // ----------------------------------------------
    }

    /**
     * [신규 API] 현재 세션에 저장된 캐릭터 정보를 확인하는 API
     * (이 코드는 두 분 다 동일합니다)
     */
    @GetMapping("/character")
    public ResponseEntity<String> getCurrentCharacter(HttpSession session) {
        // 세션에서 "selectedPersona" 값을 가져옵니다.
        String selectedPersona = (String) session.getAttribute("selectedPersona");

        if (selectedPersona != null) {
            // 세션에 캐릭터 정보가 있으면 해당 값을 반환 (HTTP 200 OK)
            return ResponseEntity.ok(selectedPersona);
        } else {
            // 세션에 정보가 없으면, 아직 선택되지 않았다는 의미의 메시지를 반환 (HTTP 404 Not Found)
            return ResponseEntity.status(404).body("No character selected in this session.");
        }
    }
}