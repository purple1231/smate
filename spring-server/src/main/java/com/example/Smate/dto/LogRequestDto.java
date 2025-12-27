package com.example.Smate.dto;



import lombok.Data;
import java.time.LocalDateTime;

@Data // Getter, Setter, toString 등을 자동으로 만들어wna.
public class LogRequestDto {
    private String username;
    private String processName;
    private String processPath;
    private LocalDateTime logTimestamp;
    private String computerId;
    private String logType;
}