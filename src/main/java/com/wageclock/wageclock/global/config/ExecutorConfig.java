package com.wageclock.wageclock.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {
    @Bean(name = "settlementExecutor")
    public ExecutorService settlementExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
