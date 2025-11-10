package com.example.Smate.repo;

import com.example.Smate.dto.AppUsageStatDto; // 👈 [추가] DTO 임포트
import com.example.Smate.log.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // 👈 [추가]
import org.springframework.data.repository.query.Param; // 👈 [추가]
import java.util.Optional; // 👈 [추가]

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // ... (기존 메소드들) ...
    List<ActivityLog> findByProcessNameAndComputerIdAndLogTimestampBetween(
            String processName, String computerId, LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByComputerIdAndLogTimestampBetween(
            String computerId, LocalDateTime start, LocalDateTime end);


    // ✨ [추가] 주간 통계용 쿼리 (JPQL)
    // AppUsageStatDto 형태로 데이터를 집계하여 반환합니다.
    @Query("SELECT new com.example.Smate.dto.AppUsageStatDto(a.processName, COUNT(a)) " +
            "FROM ActivityLog a " +
            "WHERE a.computerId = :computerId AND a.logTimestamp BETWEEN :start AND :end " +
            "GROUP BY a.processName " +
            "ORDER BY COUNT(a) DESC")
    List<AppUsageStatDto> findUsageStatsByComputerIdAndTimestamp(
            @Param("computerId") String computerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ✨ [추가] 특정 컴퓨터, 특정 앱의 ID가 가장 큰 (최신) 로그 1개를 찾음
    Optional<ActivityLog> findTopByComputerIdAndProcessNameOrderByIdDesc(
            String computerId, String processName);
}