package com.example.Smate.service;

import com.example.Smate.domain.Recommendation;
import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.log.ActivityLog;
import com.example.Smate.repo.ActivityLogRepository;
import com.example.Smate.repo.RecommendationRepository; // ✨ 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final ActivityLogRepository activityLogRepository;
    private final RecommendationRepository recommendationRepository; // ✨ Repository 주입

    @Transactional
    public void saveLog(LogRequestDto requestDto) {
        ActivityLog activityLog = new ActivityLog(requestDto);
        activityLogRepository.save(activityLog);

        // 비동기로 추천 로직 호출
        recommendRelatedApp(requestDto);
    }

    /**
     * 사용자의 과거 활동 기반으로 연관 앱을 추천하고 DB에 저장하는 메소드
     */
    @Async
    @Transactional
    public void recommendRelatedApp(LogRequestDto currentLog) { // ✨ 반환 타입 변경 없음, 내부 로직만 변경
        log.info("▶️ [추천 로직 시작] '{}' 앱에 대한 추천을 시작합니다.", currentLog.getProcessName());

        LocalDateTime yesterdayStart = currentLog.getLogTimestamp().minusDays(1).with(LocalTime.MIN);
        LocalDateTime yesterdayEnd = currentLog.getLogTimestamp().minusDays(1).with(LocalTime.MAX);

        List<ActivityLog> yesterdayLogs = activityLogRepository
                .findByProcessNameAndComputerIdAndLogTimestampBetween(
                        currentLog.getProcessName(),
                        currentLog.getComputerId(),
                        yesterdayStart,
                        yesterdayEnd
                );

        if (yesterdayLogs.isEmpty()) {
            log.info("🤷 [추천 로직] 어제 '{}' 앱을 사용한 기록이 없어 추천을 종료합니다.", currentLog.getProcessName());
            return;
        }

        log.info("📊 [추천 로직] 어제 '{}' 앱을 총 {}번 사용하셨네요.", currentLog.getProcessName(), yesterdayLogs.size());

        List<String> relatedApps = yesterdayLogs.stream()
                .flatMap(log -> {
                    LocalDateTime searchStart = log.getLogTimestamp().minusMinutes(15);
                    LocalDateTime searchEnd = log.getLogTimestamp().plusMinutes(15);

                    return activityLogRepository
                            .findByComputerIdAndLogTimestampBetween(
                                    currentLog.getComputerId(), searchStart, searchEnd)
                            .stream()
                            .filter(relatedLog -> !relatedLog.getProcessName().equals(currentLog.getProcessName()))
                            .map(ActivityLog::getProcessName);
                })
                .collect(Collectors.toList());

        if (relatedApps.isEmpty()) {
            log.info("🤷 [추천 로직] 어제 '{}' 앱과 함께 사용된 다른 앱이 없습니다.", currentLog.getProcessName());
            return;
        }

        Map.Entry<String, Long> mostFrequentApp = relatedApps.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        // ✨ [핵심 변경] 추천 결과를 DB에 저장!
        if (mostFrequentApp != null) {
            String recommendedApp = mostFrequentApp.getKey();
            Recommendation newRecommendation = new Recommendation(
                    currentLog.getComputerId(),
                    recommendedApp,
                    currentLog.getProcessName()
            );
            recommendationRepository.save(newRecommendation); // DB에 저장
            log.info("✅ [추천 저장] '{}'와 함께 사용하면 좋은 앱으로 '{}'을(를) DB에 저장했습니다!",
                    currentLog.getProcessName(), recommendedApp);
        }
    }
}