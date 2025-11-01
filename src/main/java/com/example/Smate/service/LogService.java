package com.example.Smate.service;


import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.log.ActivityLog;
import com.example.Smate.repo.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // final이 붙은 필드의 생성자를 자동으로 만들어줍니다.
public class LogService {

    private final ActivityLogRepository activityLogRepository;

    @Transactional // 이 메소드 내의 모든 DB 작업이 하나의 단위로 처리되도록 보장합니다.
    public void saveLog(LogRequestDto requestDto) {
        // 1. DTO를 Entity로 변환합니다.
        ActivityLog activityLog = new ActivityLog(requestDto);
        // 2. Repository를 통해 DB에 저장합니다.
        activityLogRepository.save(activityLog);
    }
}