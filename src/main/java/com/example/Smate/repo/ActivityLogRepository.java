package com.example.Smate.repo;


import com.example.Smate.log.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

// JpaRepository를 상속받는 것만으로 기본적인 CRUD 기능이 모두 자동 생성됩니다.
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // ✨ 새로 추가된 메소드 1
    // 특정 기간 동안 특정 컴퓨터에서 실행된 특정 프로세스 이름의 로그를 찾습니다.
    List<ActivityLog> findByProcessNameAndComputerIdAndLogTimestampBetween(
            String processName, String computerId, LocalDateTime start, LocalDateTime end);

    // ✨ 새로 추가된 메소드 2
    // 특정 기간 동안 특정 컴퓨터에서 실행된 모든 로그를 찾습니다.
    List<ActivityLog> findByComputerIdAndLogTimestampBetween(
            String computerId, LocalDateTime start, LocalDateTime end);



}