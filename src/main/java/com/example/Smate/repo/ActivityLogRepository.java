package com.example.Smate.repo;

import com.example.Smate.dto.AppUsageStatDto;
import com.example.Smate.log.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional; // ✨ [추가]

// JpaRepository를 상속받는 것만으로 기본적인 CRUD 기능이 모두 자동 생성됩니다.
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // (기존 메소드)
    List<ActivityLog> findByProcessNameAndComputerIdAndLogTimestampBetween(
            String processName, String computerId, LocalDateTime start, LocalDateTime end);

    // (기존 메소드)
    List<ActivityLog> findByComputerIdAndLogTimestampBetween(
            String computerId, LocalDateTime start, LocalDateTime end);

    // (기존 메소드 - 통계용)
    // ✨ [권장] 통계의 정확도를 위해 START 로그만 집계하도록 수정
    @Query("SELECT new com.example.Smate.dto.AppUsageStatDto(a.processName, COUNT(a)) " +
            "FROM ActivityLog a " +
            "WHERE a.computerId = :computerId AND a.logTimestamp BETWEEN :start AND :end " +
            "AND a.logType = 'START' " + // ✨ [추가] START 로그만 집계
            "GROUP BY a.processName " +
            "ORDER BY COUNT(a) DESC")
    List<AppUsageStatDto> findUsageStatsByComputerIdAndTimestamp(
            @Param("computerId") String computerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ✨ [신규] 특정 컴퓨터, 특정 앱의 ID가 가장 큰 (최신) 로그 1개를 찾음
    Optional<ActivityLog> findTopByComputerIdAndProcessNameOrderByIdDesc(
            String computerId, String processName);
}