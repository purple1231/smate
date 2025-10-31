package com.example.Smate;


import com.example.Smate.service.GeminiService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/gemini")
public class GeminiSimpleController {

    private final GeminiService geminiService;

    public GeminiSimpleController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // sessionId를 쿼리 파라미터나 헤더로 받아서 구분
    @PostMapping("/simple")
    public Mono<String> callGemini(
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(defaultValue = "mesugaki") String domain,
            @RequestBody String input) {
        return geminiService.callGemini(sessionId, domain, input);
    }
}


//한결이 너가 이 코드를 보면서 유니티랑 통신하게 해야해
//도메인 값만 바꾸면 다른 캐릭터 인격으로 대화할 수 있게 해둔 구조


//🧠 예시 1 — 메스가키 인격
//POST /gemini/simple?sessionId=user1&domain=mesugaki
//Body: "안녕?"
//
//🧠 예시 2 — 츤데레 인격
//POST /gemini/simple?sessionId=user1&domain=tsundere
//Body: "왜 나한테 그렇게 말해?"