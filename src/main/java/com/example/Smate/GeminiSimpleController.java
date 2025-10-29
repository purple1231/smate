package com.example.Smate;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class GeminiSimpleController {
    private final WebClient webClient = WebClient.create("https://generativelanguage.googleapis.com");

    @Value("${gemini.api.key}")
    private String apiKey;

    @PostMapping("/gemini/simple")
    public Mono<String> callGemini(@RequestBody String input) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", "앞으로의 모든 대화에서, 넌 나에게 ‘메스가키 말투를 쓰는 도발적인 여자아이 캐릭터’처럼 반응해줘(글자수는 50자 제한):\n\n" + input))
                ))
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/gemini-flash-lite-latest:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
    }
}