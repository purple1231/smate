package com.example.Smate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor         // ✅ 기본 생성자
@AllArgsConstructor        // ✅ (String time, String text) 생성자 자동 생성
public class TaskDto {
    private String time;
    private String text;
}
