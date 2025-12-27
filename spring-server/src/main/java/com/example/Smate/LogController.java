package com.example.Smate;


import com.example.Smate.dto.LogRequestDto;
import com.example.Smate.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LogController {

    private final LogService logService;

    // POST http://localhost:8080/api/logs 로 오는 요청을 처리합니다.
    @PostMapping("/logs")
    public ResponseEntity<String> createLog(@RequestBody LogRequestDto requestDto) {
        // Service에게 실제 업무를 위임합니다.
        logService.saveLog(requestDto);
        // 성공적으로 처리되었다는 응답(201 CREATED)을 보냅니다.
        return new ResponseEntity<>("Log saved successfully.", HttpStatus.CREATED);
    }
}