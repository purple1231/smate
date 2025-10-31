package com.example.Smate.service;


import com.example.Smate.domain.Persona;
import com.example.Smate.domain.PersonaRepository;
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

    private final Map<String, Deque<String>> sessionMemory = new HashMap<>();

    public GeminiService() {
        this.webClient = WebClient.create("https://generativelanguage.googleapis.com");
    }

    public Mono<String> callGemini(String sessionId, String domain, String input) {
        // 1️⃣ 인격 불러오기
        Persona persona = PersonaRepository.getPersona(domain);

        // 2️⃣ 이전 대화 불러오기
        Deque<String> history = sessionMemory.computeIfAbsent(sessionId, k -> new LinkedList<>());

        // 3️⃣ 과거 대화 내용을 합쳐 prompt 구성
        StringBuilder context = new StringBuilder();
        for (String h : history) {
            context.append(h).append("\n");
        }

        String prompt = """
                %s

                이전 대화 맥락:
                %s

                새로운 입력:
                %s
                """.formatted(persona.getDescription(), context, input);

        // 4️⃣ 요청 본문 생성
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        // 5️⃣ Gemini API 호출
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/gemini-flash-lite-latest:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    history.addLast("Q: " + input + "\nA: " + response);
                    if (history.size() > 10) {
                        history.removeFirst();
                    }
                });
    }
}
