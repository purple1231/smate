package com.example.Smate.domain;


public class Persona {
    private final String name;          // 인격 이름
    private final String description;   // 인격 설명 (프롬프트용 문장)

    public Persona(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}