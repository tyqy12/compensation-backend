package com.yiyundao.compensation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CompensationAssistantSystemApplication {

	public static void main(String[] args) {
		log.info("启动薪酬助手系统 (Compensation Assistant System)...");
		SpringApplication.run(CompensationAssistantSystemApplication.class, args);
		log.info("薪酬助手系统启动完成！");
	}

}
