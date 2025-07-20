// AsyncConfig.java
package com.yourcompany.llm.config.vllm;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import lombok.extern.slf4j.Slf4j;

@Slf4j @Configuration @EnableAsync @EnableScheduling
public class AsyncConfig {

    /**
     * LLM 작업을 위한 비동기 실행자
     */
    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("llm-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("✅ LLM Task Executor configured - Core: {}, Max: {}, Queue: {}", executor.getCorePoolSize(),
                executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * vLLM 모니터링을 위한 스케줄러
     */
    @Bean(name = "vllmTaskScheduler")
    public ThreadPoolTaskScheduler vllmTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("vllm-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();

        log.info("✅ vLLM Task Scheduler configured - Pool Size: {}", scheduler.getPoolSize());

        return scheduler;
    }

    /**
     * 일반적인 비동기 작업을 위한 실행자
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();

        log.info("✅ General Task Executor configured - Core: {}, Max: {}, Queue: {}", executor.getCorePoolSize(),
                executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}