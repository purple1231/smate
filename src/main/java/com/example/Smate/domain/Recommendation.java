package com.example.Smate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String computerId;

    @Column(nullable = false)
    private String recommendedApp;

    @Column(nullable = false)
    private String reasonApp; // 어떤 앱을 사용했기 때문에 추천하는지 (원본 앱)

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "recommended_app_path", columnDefinition = "TEXT")
    private String recommendedAppPath;

    @Column(name = "message", columnDefinition = "TEXT") // 👈 [추가] 멘트 필드
    private String message;

    // ✨ [수정] 생성자 오버로딩 (message 필드 포함)
    public Recommendation(String computerId, String recommendedApp, String reasonApp, String recommendedAppPath, String message) {
        this.computerId = computerId;
        this.recommendedApp = recommendedApp;
        this.reasonApp = reasonApp;
        this.recommendedAppPath = recommendedAppPath;
        this.message = message; // 👈 [추가] 멘트 설정
        this.timestamp = LocalDateTime.now();
    }
}