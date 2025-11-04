package com.example.Smate.repo;

import com.example.Smate.domain.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    // 특정 컴퓨터 ID와 특정 시간 범위 내에서 가장 최신 추천 1개를 찾음
    Optional<Recommendation> findTopByComputerIdAndTimestampAfterOrderByIdDesc(String computerId, LocalDateTime timestamp);

    // 특정 컴퓨터 ID의 모든 추천 내역을 삭제
    void deleteAllByComputerId(String computerId);
}