package com.example.Smate;

import com.example.Smate.dto.ChatResponseDto;
import com.example.Smate.dto.TaskDto;
import com.example.Smate.service.GeminiService;
import jakarta.servlet.http.HttpSession; // HttpSession import 추가!
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/gemini")
public class GeminiSimpleController {

    private final GeminiService geminiService;

    public GeminiSimpleController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Gemini API를 호출하고, 사용된 캐릭터(domain)를 세션에 저장합니다.
     */
    @PostMapping("/simple")
    public Mono<String> callGemini(
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(defaultValue = "yandere") String domain,
            @RequestBody String input,
            HttpSession session) { // ✨ 1. 메소드 파라미터로 HttpSession 추가

        // ✨ 2. 사용자가 선택한 캐릭터(domain)를 세션에 "selectedPersona" 라는 이름으로 저장
        session.setAttribute("selectedPersona", domain);

        // 기존 로직은 그대로 실행
        return geminiService.callGemini(sessionId, domain, input);
    }

    /**
     * ✨ 3. [신규 API] 현재 세션에 저장된 캐릭터 정보를 확인하는 API
     * 다른 서비스(로그 분석 등)에서 이 API를 호출하여 현재 캐릭터를 알아낼 수 있습니다.
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

    @PostMapping("/gemini/simple")
    public ResponseEntity<ChatResponseDto> chat(@RequestParam String sessionId,
                                                @RequestParam String domain,
                                                @RequestBody String userMessage) {
        // 1️⃣ 원래 AI 대답 가져오기
        String aiReply = geminiService.callGemini(sessionId, domain, userMessage).block();

        // 2️⃣ 알람 있는지 분석해서 TaskDto 생성
        TaskDto task = geminiService.extractTaskFromMessage(userMessage);

        // 3️⃣ 최종 응답 JSON 구성
        ChatResponseDto response =  new ChatResponseDto(aiReply, task);

        return ResponseEntity.ok(response);
    }

}

//한결이 너가 이 코드를 보면서 유니티랑 통신하게 해야해
//도메인 값만 바꾸면 다른 캐릭터 인격으로 대화할 수 있게 해둔 구조
//모르겠다면 지피티에 requestparam이 뭔지 알려달라 해라


//🧠 예시 1 — 메스가키 인격
//POST /gemini/simple?sessionId=user1&domain=mesugaki
//Body: "안녕?"
//
//🧠 예시 2 — 츤데레 인격
//POST /gemini/simple?sessionId=user1&domain=tsundere
//Body: "왜 나한테 그렇게 말해?"