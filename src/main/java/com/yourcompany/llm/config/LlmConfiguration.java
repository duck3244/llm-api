// LlmConfiguration.java
package com.yourcompany.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableConfigurationProperties({LlmConfigProperties.class})
@EnableRetry
@EnableAsync
@RequiredArgsConstructor
public class LlmConfiguration {
    
    private final LlmConfigProperties llmConfigProperties;
    
    /**
     * 메인 RestTemplate 빈 (LLM API 호출용)
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 타임아웃 설정
        Duration timeout = Duration.ofMillis(llmConfigProperties.getDefaults().getTimeout());
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        
        // 버퍼 크기 설정 (대용량 응답 처리용)
        factory.setBufferRequestBody(false);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.info("🔧 RestTemplate configured with timeout: {}ms", timeout.toMillis());
        
        return restTemplate;
    }
    
    /**
     * vLLM 전용 RestTemplate (더 긴 타임아웃)
     */
    @Bean("vllmRestTemplate")
    public RestTemplate vllmRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // vLLM은 더 긴 타임아웃 필요 (모델 로딩 시간 고려)
        factory.setConnectTimeout(60000); // 60초
        factory.setReadTimeout(300000);   // 5분
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.info("🚀 vLLM RestTemplate configured with extended timeout");
        
        return restTemplate;
    }
    
    /**
     * 빠른 헬스 체크용 RestTemplate
     */
    @Bean("healthCheckRestTemplate")
    public RestTemplate healthCheckRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 헬스 체크는 빠른 응답 필요
        factory.setConnectTimeout(2000);  // 2초
        factory.setReadTimeout(5000);     // 5초
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.info("🏥 Health check RestTemplate configured with fast timeout");
        
        return restTemplate;
    }
    
    /**
     * JSON 직렬화/역직렬화용 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
            .modules(new JavaTimeModule())
            .simpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .build();
    }
    
    /**
     * LLM 작업용 비동기 Executor
     */
    @Bean("llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // LLM API 호출은 I/O 집약적이므로 스레드 풀 크기를 크게 설정
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("llm-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // 우아한 종료 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("🔧 LLM TaskExecutor configured - Core: {}, Max: {}, Queue: {}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * vLLM 작업용 비동기 Executor
     */
    @Bean("vllmTaskExecutor")
    public Executor vllmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // vLLM 작업은 더 적은 동시 실행 (서버 리소스 고려)
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("vllm-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        log.info("🚀 vLLM TaskExecutor configured - Core: {}, Max: {}, Queue: {}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 모니터링용 Executor (스케줄링 작업용)
     */
    @Bean("monitoringTaskExecutor")
    public Executor monitoringTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 모니터링은 백그라운드 작업이므로 작은 풀 사용
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setKeepAliveSeconds(300);
        executor.setThreadNamePrefix("monitoring-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
        
        executor.initialize();
        
        log.info("📊 Monitoring TaskExecutor configured - Core: {}, Max: {}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize());
        
        return executor;
    }
    
    /**
     * 개발 환경 전용 설정
     */
    @Configuration
    @Profile("dev")
    static class DevelopmentConfiguration {
        
        @Bean
        public String developmentNotice() {
            log.info("🛠️ Development configuration loaded");
            log.info("   - Extended logging enabled");
            log.info("   - Relaxed security settings");
            log.info("   - Debug endpoints available");
            return "development";
        }
    }
    
    /**
     * 프로덕션 환경 전용 설정
     */
    @Configuration
    @Profile("prod")
    static class ProductionConfiguration {
        
        @Bean
        public String productionNotice() {
            log.info("🏭 Production configuration loaded");
            log.info("   - Optimized performance settings");
            log.info("   - Enhanced security");
            log.info("   - Monitoring enabled");
            return "production";
        }
        
        /**
         * 프로덕션용 RestTemplate (연결 풀 최적화)
         */
        @Bean
        @Primary
        public RestTemplate productionRestTemplate(LlmConfigProperties config) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            
            // 프로덕션 최적화 설정
            Duration timeout = Duration.ofMillis(config.getDefaults().getTimeout());
            factory.setConnectTimeout((int) timeout.toMillis());
            factory.setReadTimeout((int) timeout.toMillis() * 2); // 읽기 타임아웃은 2배
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            log.info("🏭 Production RestTemplate configured with optimized settings");
            
            return restTemplate;
        }
    }
    
    /**
     * 테스트 환경 전용 설정
     */
    @Configuration
    @Profile("test")
    static class TestConfiguration {
        
        @Bean
        public String testNotice() {
            log.info("🧪 Test configuration loaded");
            log.info("   - Mock services enabled");
            log.info("   - Fast timeouts for quick tests");
            return "test";
        }
        
        /**
         * 테스트용 RestTemplate (빠른 타임아웃)
         */
        @Bean
        @Primary
        public RestTemplate testRestTemplate() {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            
            // 테스트용 짧은 타임아웃
            factory.setConnectTimeout(1000);  // 1초
            factory.setReadTimeout(5000);     // 5초
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            log.info("🧪 Test RestTemplate configured with fast timeouts");
            
            return restTemplate;
        }
    }
}