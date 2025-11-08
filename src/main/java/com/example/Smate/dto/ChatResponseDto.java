// ChatResponseDto.java
package com.example.Smate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    private String reply;   // ✅ AI가 사용자에게 말한 대답
    private TaskDto task;   // ✅ 알람이 감지되면 Task 정보, 없으면 null
}
