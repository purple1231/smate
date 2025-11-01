package com.example.Smate.service;

import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.log.ActivityLog;
import com.example.Smate.repo.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Slf4j 로거 추가
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j // 로그 출력을 위해 추가
@Service
@RequiredArgsConstructor
public class LogService {

    private final ActivityLogRepository activityLogRepository;

    @Transactional
    public void saveLog(LogRequestDto requestDto) {
        // 1. DTO를 Entity로 변환합니다.
        ActivityLog activityLog = new ActivityLog(requestDto);
        // 2. Repository를 통해 DB에 저장합니다.
        activityLogRepository.save(activityLog);

        // 3. ✨ 추천 로직을 비동기로 호출합니다.
        recommendRelatedApp(requestDto);
    }





    /**
     * ✨ [신규] 사용자의 과거 활동 기반으로 연관 앱을 추천하는 메소드
     */
    @Async // 이 메소드를 별도의 스레드에서 비동기적으로 실행
    @Transactional(readOnly = true) // DB 변경이 없는 순수 조회 작업이므로 readOnly=true
    public void recommendRelatedApp(LogRequestDto currentLog) {
        log.info("▶️ [추천 로직 시작] '{}' 앱에 대한 추천을 시작합니다.", currentLog.getProcessName());

        // 1. 어제 날짜 범위 설정 (00:00:00 ~ 23:59:59)
        LocalDateTime yesterdayStart = currentLog.getLogTimestamp().minusDays(1).with(LocalTime.MIN);
        LocalDateTime yesterdayEnd = currentLog.getLogTimestamp().minusDays(1).with(LocalTime.MAX);

        // 2. 어제 하루 동안 '현재 앱'이 실행된 기록 조회
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

        // 3. 어제 기록들 주변 시간대(-15분 ~ +15분)에 실행된 다른 앱들을 모두 수집
        List<String> relatedApps = yesterdayLogs.stream()
                .flatMap(log -> {
                    LocalDateTime searchStart = log.getLogTimestamp().minusMinutes(15);
                    LocalDateTime searchEnd = log.getLogTimestamp().plusMinutes(15);

                    return activityLogRepository
                            .findByComputerIdAndLogTimestampBetween(
                                    currentLog.getComputerId(), searchStart, searchEnd)
                            .stream()
                            // 자기 자신(현재 앱)은 제외
                            .filter(relatedLog -> !relatedLog.getProcessName().equals(currentLog.getProcessName()))
                            .map(ActivityLog::getProcessName);
                })
                .collect(Collectors.toList());

        if (relatedApps.isEmpty()) {
            log.info("🤷 [추천 로직] 어제 '{}' 앱과 함께 사용된 다른 앱이 없습니다.", currentLog.getProcessName());
            return;
        }

        // 4. 가장 자주 등장한 앱 찾기 (빈도수 계산)
        Map.Entry<String, Long> mostFrequentApp = relatedApps.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        // 5. 최종 추천 결과 출력
        if (mostFrequentApp != null) {
            log.info("✅ [추천] 어제 이 시간대에 '{}'와(과) 함께 '{}'을(를) 자주 사용하셨어요!",
                    currentLog.getProcessName(), mostFrequentApp.getKey());
        }
    }
}