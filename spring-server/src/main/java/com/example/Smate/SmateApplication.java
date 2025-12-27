package com.example.Smate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync; // ✨ Import 추가

@EnableAsync
@SpringBootApplication
public class SmateApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmateApplication.class, args);
	}

}
