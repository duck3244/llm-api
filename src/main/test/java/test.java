package com.yourcompany.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class TestApp {
    public static void main(String[] args) {
        log.info("Testing Spring Boot and Lombok...");
        SpringApplication.run(TestApp.class, args);
    }
}