package com.example.Smate;

import com.example.Smate.domain.Recommendation;
import com.example.Smate.repo.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationRepository recommendationRepository;

    @GetMapping("/recommendation")
    @Transactional // ✨ 중요: 조회와 삭제가 한 트랜잭션으로 묶여야 함
    public ResponseEntity<Recommendation> getRecommendation(@RequestParam String computerId) {
        // 1. 5분 이내의 가장 최근 추천을 1개 찾습니다.
        Optional<Recommendation> latestRecommendation = recommendationRepository
                .findTopByComputerIdAndTimestampAfterOrderByIdDesc(computerId, LocalDateTime.now().minusMinutes(5));

        // 2. 추천이 있다면,
        if (latestRecommendation.isPresent()) {
            Recommendation recommendation = latestRecommendation.get();
            // 3. 해당 컴퓨터의 모든 추천 기록을 삭제합니다. (다음에 또 새로운 추천을 받기 위해)
            //recommendationRepository.deleteAllByComputerId(computerId);
            // 4. 찾은 추천을 반환합니다.
            return ResponseEntity.ok(recommendation);
        } else {
            // 추천이 없다면 404 Not Found 응답을 보냅니다.
            return ResponseEntity.notFound().build();
        }
    }
}