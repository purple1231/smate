package com.example.Smate.log;


import com.example.Smate.dto.LogRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor // JPA는 기본 생성자가 꼭 필요합니다.
@Table(name = "activity_log") // 실제 DB 테이블 이름
public class ActivityLog {
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

    @Column(name = "computer_id") // ✨ DB의 computer_id 컬럼과 연결
    private String computerId;

    // DTO 객체를 받아서 Entity 객체로 변환해주는 생성자
    public ActivityLog(LogRequestDto requestDto) {
        this.username = requestDto.getUsername();
        this.processName = requestDto.getProcessName();
        this.processPath = requestDto.getProcessPath();
        this.logTimestamp = requestDto.getLogTimestamp();
        this.computerId = requestDto.getComputerId();
    }
}