package com.example.Smate.repo;

import com.example.Smate.dto.AppUsageStatDto;
import com.example.Smate.log.ActivityLog;
// ⭐️ [수정] 중복된 import 1개 제거
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// JpaRepository를 상속받는 것만으로 기본적인 CRUD 기능이 모두 자동 생성됩니다.
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // (기존 메소드)
    List<ActivityLog> findByProcessNameAndComputerIdAndLogTimestampBetween(
            String processName, String computerId, LocalDateTime start, LocalDateTime end);

    // (기존 메소드)
    List<ActivityLog> findByComputerIdAndLogTimestampBetween(
            String computerId, LocalDateTime start, LocalDateTime end);

    // (기존 메소드 - 통계용)
    @Query("SELECT new com.example.Smate.dto.AppUsageStatDto(a.processName, COUNT(a)) " +
            "FROM ActivityLog a " +
            "WHERE a.computerId = :computerId AND a.logTimestamp BETWEEN :start AND :end " +
            "AND a.logType = 'START' " +
            "GROUP BY a.processName " +
            "ORDER BY COUNT(a) DESC")
    List<AppUsageStatDto> findUsageStatsByComputerIdAndTimestamp(
            @Param("computerId") String computerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );


    // ⭐️ [핵심 수정] 메서드 이름에 'AndLogType'이 꼭 포함되어야 합니다!
    Optional<ActivityLog> findTopByComputerIdAndProcessNameAndLogTypeOrderByIdDesc(
            String computerId,
            String processName,
            ActivityLog.LogType logType
    );

    Optional<ActivityLog> findTopByComputerIdAndProcessNameOrderByIdDesc(
            String computerId,
            String processName
    );
}