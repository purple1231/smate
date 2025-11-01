package com.example.Smate.repo;


import com.example.Smate.log.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository를 상속받는 것만으로 기본적인 CRUD 기능이 모두 자동 생성됩니다.
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
}