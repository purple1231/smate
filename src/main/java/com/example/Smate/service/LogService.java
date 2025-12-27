package com.example.Smate.service;

import com.example.Smate.domain.Recommendation;
import com.example.Smate.dto.AppUsageStatDto;
import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.log.ActivityLog;
import com.example.Smate.log.ActivityLog.LogType; // ✨ (필수) LogType Enum 임포트
import com.example.Smate.repo.ActivityLogRepository;
import com.example.Smate.repo.RecommendationRepository;
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
@Slf4j // 👈 @Slf4j 어노테이션은 'log'라는 이름의 로거를 만들어줍니다.
public class LogService {

    // --- 의존성 주입 ---
    private final ActivityLogRepository activityLogRepository;
    private final RecommendationRepository recommendationRepository;
    private final PersonaCacheService personaCacheService;

    // --- ⚙️ 설정값 ---
    @Value("${analysis.usage.threshold:10}")
    private int USAGE_THRESHOLD;
    @Value("${analysis.duration.days:7}")
    private int ANALYSIS_DURATION_DAYS;
    @Value("${analysis.window.minutes:5}")
    private int CO_USAGE_WINDOW_MINUTES;


    // --- 💬 페르소나별 멘트 저장소 ---
    private static final Map<String, List<String>> WITTY_COMMENTS = new HashMap<>();
    private static final Map<String, List<String>> USAGE_COMMENTS = new HashMap<>();
    private static final Random random = new Random();

    static {
        // (멘트 내용 수정 없음...)
        WITTY_COMMENTS.put("tsundere", List.of("흐응~ %s? 자코, 또 그런 거나 하고 있는 거야? 😒", "어라, %s? 너, 취향 참... 풉.", "또 %s(이)네... 너한텐 그게 한계라는 거구나, 불쌍하게도. 😜", "이딴 걸 열다니, %s? 정말 구제불능이네, 너. 🤣", "정말~? %s? 혹시 M이야? 🤨"));
        WITTY_COMMENTS.put("kirby", List.of("와! %s(이)다! 🤩 포요!", "하~이! %s 시작하는구나! 힘내! 💪", "%s? 재밌겠다! 나도 알려줘! 😮", "포요! %s(이)구나! 맛있는 거랑 비슷해? 🍰", "%s 하는 거야? 멋지다! ✨"));
        USAGE_COMMENTS.put("tsundere", List.of("흥, 또 %s이랑 %s 같이 쓰네? 뻔하다니까. 실행해줘? 😒", "어라, %s? ...당연히 %s(이)겠지. 자코는 하는 짓이 똑같다니까. 열어줄까? 😜", "맨날 %s 쓰면 %s(이)더라. 혹시... 뇌가 없니? 풉. 🧠 열어줘?", "이 조합(%s, %s) 슬슬 지겨운데. ...뭐, 열어는 줄게. 🥱", "%s? %s? 너 혹시 이 조합밖에 몰라? ...한심해. 실행할거야? 🙄"));
        USAGE_COMMENTS.put("kirby", List.of("포요! %s(이)랑 %s(은)는 단짝이구나! 💖 같이 부를까?", "와! %s(이)랑 %s(은)는 최고의 팀이네! 🚀 실행할래?", "알아! %s 다음엔 %s(이)지! 🥪 나 잘 알지! 열어줄까?", "우와! %s(이)랑 %s(을)를 같이 쓰네! 🌟 모험을 시작할까?", "%s(이)랑 %s! 맛있는 조합이야! 🍰 열까?"));
    }
    // --- 멘트 저장소 끝 ---


    /**
     * ✨ [수정됨] ✨ 로거(log)와 엔티티 변수(activityLog) 이름 충돌 해결
     */
    @Transactional
    public void saveLog(LogRequestDto requestDto) {
        // 1. DTO를 Entity로 변환
        // ✨ [수정] 변수 이름을 'log' -> 'activityLog'로 변경 (로거와의 충돌 방지)
        ActivityLog activityLog = new ActivityLog(requestDto);

        // 2. 로그 DB에 저장
        activityLogRepository.save(activityLog);

        // 3. 앱이 "START"될 때만 분석을 트리거
        // ✨ [수정] 'log' -> 'activityLog'로 변경
        if (activityLog.getLogType() == LogType.START) {
            triggerAnalysis(requestDto); // 비동기 분석 호출
            // ✨ [수정] 'log.info'는 이제 @Slf4j의 로거를 가리킴 (정상)
            log.info("Log (START) saved for {}. Triggering async analysis.", requestDto.getProcessName());
        } else {
            // "STOP" 로그일 경우
            // ✨ [수정] 'log.info'는 이제 @Slf4j의 로거를 가리킴 (정상)
            log.info("Log (STOP) saved for {}. No analysis triggered.", requestDto.getProcessName());
        }
    }

    /**
     * (수정 없음) 비동기 분석 실행
     */
    @Async
    @Transactional
    public void triggerAnalysis(LogRequestDto requestDto) {
        try {
            String processName = requestDto.getProcessName();
            String computerId = requestDto.getComputerId();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime analysisStartDate = now.minusDays(ANALYSIS_DURATION_DAYS);

            // (참고: 이 쿼리는 START/STOP 로그를 둘 다 가져오지만,
            //  triggerAnalysis 자체가 START 로그일 때만 호출되므로 processName 기준으로는 START 로그임)
            List<ActivityLog> recentAppLogs = activityLogRepository.findByProcessNameAndComputerIdAndLogTimestampBetween(
                    processName, computerId, analysisStartDate, now);

            // (START 로그만 집계하도록 쿼리를 수정하는 것이 더 정확할 수 있음)
            int usageCount = recentAppLogs.stream()
                    .filter(log -> log.getLogType() == LogType.START)
                    .toList().size();

            log.info("Analysis for [{}]: {} uses in last {} days. (Threshold: {})",
                    processName, usageCount, ANALYSIS_DURATION_DAYS, USAGE_THRESHOLD);

            recommendationRepository.deleteAllByComputerId(computerId);

            if (usageCount < USAGE_THRESHOLD) {
                generateWittyRecommendation(processName, computerId);
            } else {
                // recentAppLogs에는 STOP 로그가 포함될 수 있으므로, START 로그만 필터링해서 넘겨줌
                List<ActivityLog> startLogs = recentAppLogs.stream()
                        .filter(log -> log.getLogType() == LogType.START)
                        .collect(Collectors.toList());

                if (!startLogs.isEmpty()) {
                    generateUsageRecommendation(processName, computerId, analysisStartDate, now, startLogs);
                }
            }
        } catch (Exception e) {
            log.error("Error during async analysis for {}: {}", requestDto.getProcessName(), e.getMessage(), e);
        }
    }

    /**
     * (수정 없음) [10회 미만] 재치 있는 멘트 생성
     */
    private void generateWittyRecommendation(String processName, String computerId) {
        log.info("  [Witty] Generating witty comment for {}", processName);
        String personaName = personaCacheService.getPersona(computerId);
        List<String> comments = WITTY_COMMENTS.getOrDefault(personaName, WITTY_COMMENTS.get("kirby"));
        String randomTemplate = comments.get(random.nextInt(comments.size()));
        String wittyMessage = String.format(randomTemplate, processName);
        Recommendation rec = new Recommendation(computerId, "Chat", processName, null, wittyMessage);
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
     * ✨ [수정됨] ✨ 람다(lambda)에서 'final' 변수를 사용하도록 수정
     */
    private void generateUsageRecommendation(String processName, String computerId,
                                             LocalDateTime start, LocalDateTime end,
                                             List<ActivityLog> recentAppLogs) { // recentAppLogs는 이제 START 로그만 받음

        log.info("Generating usage recommendation for {}", processName);

        // 1. 연관 분석 (수정 없음)
        Map<String, Integer> coAppCounts = findCoUsageApps(processName, computerId, start, end, recentAppLogs);

        // 2. 1순위 추천 앱 찾기 (수정 없음)
        Optional<Map.Entry<String, Integer>> maxEntry = coAppCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        if (maxEntry.isEmpty()) {
            System.out.println("⚠️ [" + processName + "] 같이 사용된 앱이 없어 추천 안함.");
            return;
        }

        // 3. ✨ [수정] 1순위 앱 (재할당될 수 있으므로 'final'이 아님)
        String recommendedAppName = maxEntry.get().getKey();
        log.info("  [Analysis] 1st recommendation is '{}'", recommendedAppName);

        // 4. 1순위 앱이 "이미 실행 중"인지 확인
        if (isAppCurrentlyRunning(computerId, recommendedAppName)) {
            log.warn("  [Skip] 1st choice '{}' is already running. Finding 2nd best...", recommendedAppName);
            coAppCounts.remove(recommendedAppName);
            maxEntry = coAppCounts.entrySet().stream().max(Map.Entry.comparingByValue());

            if (maxEntry.isEmpty()) {
                log.info("  [Skip] No 2nd recommendation found.");
                return;
            }

            // ✨ [수정] recommendedAppName 변수가 여기서 재할당됨
            recommendedAppName = maxEntry.get().getKey();
            log.info("  [Analysis] 2nd recommendation is '{}'", recommendedAppName);
        }

        // 5. ✨ [신규] 람다에서 사용하기 위해 final 변수에 재할당
        // ( recommendedAppName은 3번 또는 4번에서 값이 확정됨 )
        final String finalRecommendedAppName = recommendedAppName;

        // 6. 최종 추천 앱의 경로 찾기
        List<ActivityLog> allLogsInPeriod = activityLogRepository.findByComputerIdAndLogTimestampBetween(computerId, start, end);
        String recommendedPath = allLogsInPeriod.stream()
                // ✨ [수정] 'finalRecommendedAppName' 변수를 사용
                .filter(log -> log.getProcessName().equals(finalRecommendedAppName) && log.getProcessPath() != null)
                .sorted(Comparator.comparing(ActivityLog::getLogTimestamp).reversed())
                .map(ActivityLog::getProcessPath)
                .findFirst()
                .orElse(null);

        // 7. 멘트 생성 및 저장 (수정 없음)
        log.info("  [CACHE-GET] LogService received Key: '{}'", computerId);
        String personaName = personaCacheService.getPersona(computerId);
        log.info("  [CACHE-GET] Cache returned Value: '{}'", personaName);
        log.info("Current persona for {}: {}", computerId, personaName);

        List<String> comments = USAGE_COMMENTS.getOrDefault(personaName, USAGE_COMMENTS.get("kirby"));
        String randomTemplate = comments.get(random.nextInt(comments.size()));
        // ✨ [수정] 'finalRecommendedAppName' 변수를 사용
        String usageMessage = String.format(randomTemplate, processName, finalRecommendedAppName);

        Recommendation rec = new Recommendation(
                computerId,
                finalRecommendedAppName, // ✨ [수정] 'finalRecommendedAppName' 변수를 사용
                processName,
                recommendedPath,
                usageMessage
        );

        System.out.println("💾 [USAGE 저장 예정]");
        System.out.println(" ├─ Computer ID : " + computerId);
        System.out.println(" ├─ Process Name : " + processName);
        System.out.println(" ├─ Recommended App : " + finalRecommendedAppName); // ✨ [수정]
        System.out.println(" ├─ Recommended Path : " + recommendedPath);
        System.out.println(" ├─ Persona : " + personaName);
        System.out.println(" └─ Message : " + usageMessage);

        recommendationRepository.save(rec);
        log.info("Saved usage recommendation for {}: {} -> {}", computerId, processName, finalRecommendedAppName);
    }

    // ---
    // --- 헬퍼 메소드 및 통계 API 메소드 ---
    // ---

    /**
     * (신규) 특정 앱이 현재 실행 중인지 확인하는 헬퍼 메소드 (수정 없음)
     */
    private boolean isAppCurrentlyRunning(String computerId, String appName) {
        Optional<ActivityLog> lastLog = activityLogRepository
                .findTopByComputerIdAndProcessNameOrderByIdDesc(computerId, appName);
        if (lastLog.isEmpty()) {
            return false;
        }
        return lastLog.get().getLogType() == LogType.START;
    }

    /**
     * (API용) 1. 주간 사용 통계 조회 (수정 없음)
     */
    @Transactional(readOnly = true)
    public List<AppUsageStatDto> getWeeklyUsageStats(String computerId) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(ANALYSIS_DURATION_DAYS);
        log.info("[Stats API] getWeeklyUsageStats for '{}' from {} to {}", computerId, start, end);
        // (참고: 이 쿼리는 START/STOP 로그를 둘 다 집계할 수 있으므로,
        //  ActivityLogRepository의 JPQL 쿼리에 "WHERE a.logType = 'START'" 조건을 추가하는 것이 더 정확함)
        return activityLogRepository.findUsageStatsByComputerIdAndTimestamp(computerId, start, end);
    }

    /**
     * (API용) 2. 특정 앱과 연관 사용된 앱 목록 조회 (수정 없음)
     */
    @Transactional(readOnly = true)
    public List<AppUsageStatDto> getCoUsageStats(String computerId, String baseAppName) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(ANALYSIS_DURATION_DAYS);
        log.info("[Stats API] getCoUsageStats for '{}' based on '{}'", computerId, baseAppName);

        // (참고: 이 쿼리도 STOP 로그를 가져올 수 있으므로,
        //  START 로그만 가져오도록 Repository 쿼리를 수정하는 것이 좋음)
        List<ActivityLog> recentAppLogs = activityLogRepository.findByProcessNameAndComputerIdAndLogTimestampBetween(
                baseAppName, computerId, start, end);

        // START 로그만 필터링
        List<ActivityLog> startLogs = recentAppLogs.stream()
                .filter(log -> log.getLogType() == LogType.START)
                .collect(Collectors.toList());

        if (startLogs.isEmpty()) {
            log.warn("  -> No logs found for base app '{}'. Returning empty list.", baseAppName);
            return Collections.emptyList();
        }

        Map<String, Integer> coAppCounts = findCoUsageApps(baseAppName, computerId, start, end, startLogs);

        return coAppCounts.entrySet().stream()
                .map(entry -> new AppUsageStatDto(entry.getKey(), entry.getValue().longValue()))
                .sorted(Comparator.comparingLong(AppUsageStatDto::getUsageCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 연관 사용 앱 분석 로직 (수정 없음)
     * (recentAppLogs가 START 로그만 받는다고 가정)
     */
    private Map<String, Integer> findCoUsageApps(String processName, String computerId,
                                                 LocalDateTime start, LocalDateTime end,
                                                 List<ActivityLog> recentAppLogs) { // recentAppLogs는 START 로그의 리스트

        // (참고: allLogsInPeriod는 START/STOP 로그가 모두 필요함 - 시간창 검색을 위해)
        List<ActivityLog> allLogsInPeriod = activityLogRepository.findByComputerIdAndLogTimestampBetween(computerId, start, end);
        Map<String, Integer> coAppCounts = new HashMap<>();
        Set<String> processedInWindow = new HashSet<>();

        // recentAppLogs는 START 로그이므로, 이 로그를 기준으로 시간창을 생성
        for (ActivityLog targetLog : recentAppLogs) {
            LocalDateTime windowStart = targetLog.getLogTimestamp().minusMinutes(CO_USAGE_WINDOW_MINUTES);
            LocalDateTime windowEnd = targetLog.getLogTimestamp().plusMinutes(CO_USAGE_WINDOW_MINUTES);
            processedInWindow.clear();

            for (ActivityLog log : allLogsInPeriod) {
                // 시간창 내에 있고,
                if (!log.getLogTimestamp().isBefore(windowStart) && !log.getLogTimestamp().isAfter(windowEnd)
                        // 기준 앱 자신이 아니며,
                        && !log.getProcessName().equals(processName)
                        // [중요] START 로그만 연관 앱으로 카운트
                        && log.getLogType() == LogType.START
                        // 이번 윈도우에서 아직 카운트되지 않았다면
                        && processedInWindow.add(log.getProcessName())) {
                    coAppCounts.put(log.getProcessName(), coAppCounts.getOrDefault(log.getProcessName(), 0) + 1);
                }
            }
        }
        return coAppCounts;
    }
}