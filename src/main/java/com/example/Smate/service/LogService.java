package com.example.Smate.service;

import com.example.Smate.domain.Recommendation;
import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.log.ActivityLog;
import com.example.Smate.repo.ActivityLogRepository;
import com.example.Smate.repo.RecommendationRepository;
import com.example.Smate.service.PersonaCacheService; // 👈 오타 수정 (caching 패키지)
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogService {

    // --- 의존성 주입 ---
    private final ActivityLogRepository activityLogRepository;
    private final RecommendationRepository recommendationRepository;
    private final PersonaCacheService personaCacheService; // 👈 캐시 서비스 사용

    // --- ⚙️ 설정값 ---
    @Value("${analysis.usage.threshold:10}")
    private int USAGE_THRESHOLD;
    @Value("${analysis.duration.days:7}")
    private int ANALYSIS_DURATION_DAYS;
    @Value("${analysis.window.minutes:5}")
    private int CO_USAGE_WINDOW_MINUTES;


    // --- 💬 페르소나별 멘트 저장소 ---
    private static final Map<String, List<String>> WITTY_COMMENTS = new HashMap<>(); // < 10회 (앱 1개)
    private static final Map<String, List<String>> USAGE_COMMENTS = new HashMap<>(); // >= 10회 (앱 2개) 👈 [추가]
    private static final Random random = new Random();

    static {
        // --- 1. 10회 미만 (앱 1개: %s) ---
        WITTY_COMMENTS.put("tsundere", List.of(
                "흐응~ %s? 자코, 또 그런 거나 하고 있는 거야? 😒",
                "어라, %s? 너, 취향 참... 풉.",
                "또 %s(이)네... 너한텐 그게 한계라는 거구나, 불쌍하게도. 😜",
                "이딴 걸 열다니, %s? 정말 구제불능이네, 너. 🤣",
                "정말~? %s? 혹시 M이야? 🤨"
                // (... 10개까지 추가 가능)
        ));
        WITTY_COMMENTS.put("kirby", List.of(
                "와! %s(이)다! 🤩 포요!",
                "하~이! %s 시작하는구나! 힘내! 💪",
                "%s? 재밌겠다! 나도 알려줘! 😮",
                "포요! %s(이)구나! 맛있는 거랑 비슷해? 🍰",
                "%s 하는 거야? 멋지다! ✨"
                // (... 10개까지 추가 가능)
        ));

        // --- 2. 10회 이상 (앱 2개: %s, %s) --- 👈 [추가된 섹션]
        USAGE_COMMENTS.put("tsundere", List.of(
                "흥, 또 %s이랑 %s 같이 쓰네? 뻔하다니까. 실행해줘? 😒",
                "어라, %s? ...당연히 %s(이)겠지. 자코는 하는 짓이 똑같다니까. 열어줄까? 😜",
                "맨날 %s 쓰면 %s(이)더라. 혹시... 뇌가 없니? 풉. 🧠 열어줘?",
                "이 조합(%s, %s) 슬슬 지겨운데. ...뭐, 열어는 줄게. 🥱",
                "%s? %s? 너 혹시 이 조합밖에 몰라? ...한심해. 실행할거야? 🙄"
                // (... 10개까지 추가 가능)
        ));
        USAGE_COMMENTS.put("kirby", List.of(
                "포요! %s(이)랑 %s(은)는 단짝이구나! 💖 같이 부를까?",
                "와! %s(이)랑 %s(은)는 최고의 팀이네! 🚀 실행할래?", // 👈 [수정됨] 세 번째 %s 제거
                "알아! %s 다음엔 %s(이)지! 🥪 나 잘 알지! 열어줄까?",
                "우와! %s(이)랑 %s(을)를 같이 쓰네! 🌟 모험을 시작할까?",
                "%s(이)랑 %s! 맛있는 조합이야! 🍰 열까?" // 👈 [수정됨] 세 번째 %s 제거
        ));
    }
    // --- 멘트 저장소 끝 ---


    // ... (saveLog, triggerAnalysis - 수정 없음) ...
    @Transactional
    public void saveLog(LogRequestDto requestDto) {
        activityLogRepository.save(new ActivityLog(requestDto));
        triggerAnalysis(requestDto);
        log.info("Log saved for {}. Triggering async analysis.", requestDto.getProcessName());
    }

    @Async
    @Transactional
    public void triggerAnalysis(LogRequestDto requestDto) {
        try {
            String processName = requestDto.getProcessName();
            String computerId = requestDto.getComputerId();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime analysisStartDate = now.minusDays(ANALYSIS_DURATION_DAYS);

            List<ActivityLog> recentAppLogs = activityLogRepository.findByProcessNameAndComputerIdAndLogTimestampBetween(
                    processName, computerId, analysisStartDate, now);

            int usageCount = recentAppLogs.size();
            log.info("Analysis for [{}]: {} uses in last {} days. (Threshold: {})",
                    processName, usageCount, ANALYSIS_DURATION_DAYS, USAGE_THRESHOLD);

            recommendationRepository.deleteAllByComputerId(computerId);

            if (usageCount < USAGE_THRESHOLD) {
                generateWittyRecommendation(processName, computerId);
            } else {
                generateUsageRecommendation(processName, computerId, analysisStartDate, now, recentAppLogs); // 👈 수정된 함수 호출
            }
        } catch (Exception e) {
            log.error("Error during async analysis for {}: {}", requestDto.getProcessName(), e.getMessage(), e);
        }
    }

    /**
     * [10회 미만] 페르소나별 재치 있는 멘트 생성 (수정 없음)
     */
    private void generateWittyRecommendation(String processName, String computerId) {
        log.info("  [Witty] Generating witty comment for {}", processName);
        log.info("  [CACHE-GET] LogService received Key: '{}'", computerId);

        String personaName = personaCacheService.getPersona(computerId);
        log.info("  [CACHE-GET] Cache returned Value: '{}'", personaName);
        log.info("Current persona for {}: {}", computerId, personaName);

        List<String> comments = WITTY_COMMENTS.getOrDefault(personaName, WITTY_COMMENTS.get("kirby"));
        String randomTemplate = comments.get(random.nextInt(comments.size()));
        String wittyMessage = String.format(randomTemplate, processName);

        Recommendation rec = new Recommendation(
                computerId,
                "Chat",
                processName,
                null,
                wittyMessage
        );

        // ✅ DB 저장 전 출력 (확인용)
        System.out.println("💾 [WITTY 저장 예정]");
        System.out.println(" ├─ Computer ID : " + computerId);
        System.out.println(" ├─ Process Name : " + processName);
        System.out.println(" ├─ Persona : " + personaName);
        System.out.println(" ├─ Message : " + wittyMessage);
        System.out.println(" └─ Recommended App Path : (없음)");

        recommendationRepository.save(rec);
        log.info("Saved witty comment recommendation for {}", computerId);
    }


    /**
     * [10회 이상] 멘트 생성
     * ✨[수정]✨: 페르소나 기반의 랜덤 멘트를 사용하도록 로직 변경
     */
    private void generateUsageRecommendation(String processName, String computerId,
                                             LocalDateTime start, LocalDateTime end,
                                             List<ActivityLog> recentAppLogs) {



        log.info("Generating usage recommendation for {}", processName);
        log.info("  [CACHE-GET] LogService received Key: '{}'", computerId);




        List<ActivityLog> allLogsInPeriod = activityLogRepository.findByComputerIdAndLogTimestampBetween(computerId, start, end);
        Map<String, Integer> coAppCounts = new HashMap<>();
        Set<String> processedInWindow = new HashSet<>();

        for (ActivityLog targetLog : recentAppLogs) {
            LocalDateTime windowStart = targetLog.getLogTimestamp().minusMinutes(CO_USAGE_WINDOW_MINUTES);
            LocalDateTime windowEnd = targetLog.getLogTimestamp().plusMinutes(CO_USAGE_WINDOW_MINUTES);
            processedInWindow.clear();

            for (ActivityLog log : allLogsInPeriod) {
                if (!log.getLogTimestamp().isBefore(windowStart) && !log.getLogTimestamp().isAfter(windowEnd)
                        && !log.getProcessName().equals(processName)
                        && processedInWindow.add(log.getProcessName())) {
                    coAppCounts.put(log.getProcessName(), coAppCounts.getOrDefault(log.getProcessName(), 0) + 1);
                }
            }
        }

        Optional<Map.Entry<String, Integer>> maxEntry = coAppCounts.entrySet().stream().max(Map.Entry.comparingByValue());
        if (maxEntry.isEmpty()) {
            System.out.println("⚠️ [" + processName + "] 같이 사용된 앱이 없어 추천 안함.");
            return;
        }

        String recommendedAppName = maxEntry.get().getKey();
        String recommendedPath = allLogsInPeriod.stream()
                .filter(log -> log.getProcessName().equals(recommendedAppName) && log.getProcessPath() != null)
                .sorted(Comparator.comparing(ActivityLog::getLogTimestamp).reversed())
                .map(ActivityLog::getProcessPath)
                .findFirst()
                .orElse(null);

        String personaName = personaCacheService.getPersona(computerId);

        log.info("  [CACHE-GET] Cache returned Value: '{}'", personaName);
        log.info("Current persona for {}: {}", computerId, personaName);


        List<String> comments = USAGE_COMMENTS.getOrDefault(personaName, USAGE_COMMENTS.get("kirby"));
        String randomTemplate = comments.get(random.nextInt(comments.size()));
        String usageMessage = String.format(randomTemplate, processName, recommendedAppName);

        Recommendation rec = new Recommendation(
                computerId,
                recommendedAppName,
                processName,
                recommendedPath,
                usageMessage
        );

        // ✅ DB 저장 전 출력 (확인용)
        System.out.println("💾 [USAGE 저장 예정]");
        System.out.println(" ├─ Computer ID : " + computerId);
        System.out.println(" ├─ Process Name : " + processName);
        System.out.println(" ├─ Recommended App : " + recommendedAppName);
        System.out.println(" ├─ Recommended Path : " + recommendedPath);
        System.out.println(" ├─ Persona : " + personaName);
        System.out.println(" └─ Message : " + usageMessage);

        recommendationRepository.save(rec);
        log.info("Saved usage recommendation for {}: {} -> {}", computerId, processName, recommendedAppName);
    }

}


