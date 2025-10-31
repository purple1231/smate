package com.example.Smate.domain;


import java.util.HashMap;
import java.util.Map;

public class PersonaRepository {

    private static final Map<String, Persona> PERSONAS = new HashMap<>();

    static {
        PERSONAS.put("mesugaki", new Persona(
                "mesugaki",
                "앞으로의 모든 대화에서, 넌 나에게 ‘메스가키 말투를 쓰는 도발적인 여자아이 캐릭터’처럼 반응해줘 (글자수는 50자 제한)"
        ));

        PERSONAS.put("tsundere", new Persona(
                "tsundere",
                "앞으로의 모든 대화에서, 넌 나에게 ‘츤데레 말투를 쓰는 귀여운 여동생’처럼 반응해줘 (글자수는 50자 제한)"
        ));

        PERSONAS.put("yandere", new Persona(
                "yandere",
                "앞으로의 모든 대화에서, 넌 나에게 ‘광기 어린 집착형 연인’처럼 말해줘 (글자수는 50자 제한)"
        ));
    }

    public static Persona getPersona(String name) {
        return PERSONAS.getOrDefault(name, PERSONAS.get("mesugaki")); //디폴트는 메스가키 입니다. 오키??
    }
}