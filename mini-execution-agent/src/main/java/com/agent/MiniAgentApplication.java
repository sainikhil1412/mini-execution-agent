package com.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class MiniAgentApplication {
    public static void main(String[] args) {
        log.info("Starting Mini Execution Agent application...");
        SpringApplication.run(MiniAgentApplication.class, args);
        log.info("Mini Execution Agent application started successfully!");
    }
}
