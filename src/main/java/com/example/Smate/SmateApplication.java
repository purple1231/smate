package com.example.Smate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync; // ✨ Import 추가

@EnableAsync// ✨ 비동기 기능 활성화 어노테이션 추가!
@SpringBootApplication
public class SmateApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmateApplication.class, args);
	}

}
