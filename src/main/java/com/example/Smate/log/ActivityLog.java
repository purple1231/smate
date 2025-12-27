package com.example.Smate.log;


import com.example.Smate.dto.LogRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "activity_log") // 실제 DB 테이블 이름
public class ActivityLog {

    public enum LogType {
        START, STOP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String processName;

    @Column(columnDefinition = "TEXT")
    private String processPath;

    @Column(nullable = false)
    private LocalDateTime logTimestamp;

    @Column(name = "computer_id") // DB의 computer_id 컬럼과 연결
    private String computerId;

    // ✨ [추가] 로그 타입 컬럼
    @Enumerated(EnumType.STRING) // DB에는 "START", "STOP" 문자열로 저장
    @Column(name = "log_type")
    private LogType logType;



    // DTO 객체를 받아서 Entity 객체로 변환해주는 생성자
    public ActivityLog(LogRequestDto requestDto) {
        this.username = requestDto.getUsername();
        this.processName = requestDto.getProcessName();
        this.processPath = requestDto.getProcessPath();
        this.logTimestamp = requestDto.getLogTimestamp();
        this.computerId = requestDto.getComputerId();

        // DTO의 문자열을 Enum으로 변환
        if ("STOP".equalsIgnoreCase(requestDto.getLogType())) {
            this.logType = LogType.STOP;
        } else {
            this.logType = LogType.START; // 기본값은 START
        }


    }
}