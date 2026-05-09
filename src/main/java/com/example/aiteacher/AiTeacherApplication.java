package com.example.aiteacher;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.example.aiteacher.Mapper")
@EnableAsync
public class AiTeacherApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiTeacherApplication.class, args);
	}

}
