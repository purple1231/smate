package com.example.Smate;

import com.example.Smate.dto.AppUsageStatDto;
import com.example.Smate.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
public class StatController {

    private final LogService logService;

    /**
     * API 1: 지난 7일간의 앱 사용 통계 (메인 그래프용)
     */
    @GetMapping("/weekly")
    public ResponseEntity<List<AppUsageStatDto>> getWeeklyStats(@RequestParam String computerId) {
        List<AppUsageStatDto> stats = logService.getWeeklyUsageStats(computerId);
        return ResponseEntity.ok(stats);
    }

    /**
     * API 2: 특정 앱과 연관된 앱 사용 통계 (클릭 시 서브 그래프용)
     */
    @GetMapping("/co-usage")
    public ResponseEntity<List<AppUsageStatDto>> getCoUsageStats(@RequestParam String computerId, @RequestParam String appName) {
        List<AppUsageStatDto> stats = logService.getCoUsageStats(computerId, appName);
        return ResponseEntity.ok(stats);
    }
}

