package com.example.Smate.service;


import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자의 computerId와 마지막으로 선택한 페르소나를 매핑하여 저장하는 메모리 캐시입니다.
 * LogService가 이 캐시를 참조하여 올바른 말투를 사용합니다.
 */
@Service
public class PersonaCacheService {

    // computerId -> personaName (예: "my-pc-001" -> "mesugaki")
    // ConcurrentHashMap: 여러 스레드에서 동시에 접근해도 안전합니다.
    private static final Map<String, String> personaCache = new ConcurrentHashMap<>();

    /**
     * 캐시에 페르소나를 저장합니다.
     */
    public void setPersona(String computerId, String personaName) {
        if (computerId == null || personaName == null) return;
        personaCache.put(computerId, personaName);
        System.out.println(computerId);
        System.out.println(personaName);
    }

    /**
     * 캐시에서 페르소나를 조회합니다.
     * @param computerId
     * @return 저장된 페르소나 이름. (만약 저장된 값이 없으면 기본값 "kirby" 반환)
     */
    public String getPersona(String computerId) {
        if (computerId == null) return "kirby"; // 기본값
        // 저장된 값이 없으면 "kirby"를 반환합니다.
        return personaCache.getOrDefault(computerId, "kirby");
    }
}