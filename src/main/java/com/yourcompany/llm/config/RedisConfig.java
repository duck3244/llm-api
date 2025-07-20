// RedisConfig.java
package com.yourcompany.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {
    
    private final RedisProperties redisProperties;
    private final ObjectMapper objectMapper;
    
    /**
     * Redis 연결 팩토리 설정
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        // Redis 서버 설정
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        
        if (redisProperties.getPassword() != null) {
            config.setPassword(redisProperties.getPassword());
        }
        
        if (redisProperties.getDatabase() != 0) {
            config.setDatabase(redisProperties.getDatabase());
        }
        
        // Lettuce 클라이언트 설정 (연결 풀링)
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ofSeconds(3))
            .build();
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setValidateConnection(true);
        
        log.info("🔧 Redis connection factory configured - Host: {}:{}, DB: {}", 
            config.getHostName(), config.getPort(), config.getDatabase());
        
        return factory;
    }
    
    /**
     * 메인 RedisTemplate 설정
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key 직렬화 (String)
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        
        // Value 직렬화 (JSON)
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);
        
        // 트랜잭션 지원 활성화
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        log.info("📦 Main RedisTemplate configured with JSON serialization");
        
        return template;
    }
    
    /**
     * String 전용 RedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        
        log.info("📝 StringRedisTemplate configured");
        
        return template;
    }
    
    /**
     * LLM 응답 캐싱용 전용 RedisTemplate
     */
    @Bean("llmCacheRedisTemplate")
    public RedisTemplate<String, Object> llmCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // LLM 응답은 압축된 JSON으로 저장
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // 커스텀 JSON 직렬화 (LLM 응답 최적화)
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        
        log.info("🤖 LLM Cache RedisTemplate configured");
        
        return template;
    }
    
    /**
     * 메트릭 저장용 RedisTemplate
     */
    @Bean("metricsRedisTemplate")
    public RedisTemplate<String, Object> metricsRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 메트릭은 빠른 직렬화를 위해 단순 형태 사용
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));
        
        template.afterPropertiesSet();
        
        log.info("📊 Metrics RedisTemplate configured");
        
        return template;
    }
    
    /**
     * Redis 캐시 매니저 설정
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // 기본 1시간 TTL
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer(objectMapper)))
            .disableCachingNullValues();
        
        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // LLM 응답 캐시 (24시간)
        cacheConfigurations.put("llm-responses", 
            defaultConfig.entryTtl(Duration.ofHours(24)));
        
        // 모델 정보 캐시 (1시간)
        cacheConfigurations.put("model-info", 
            defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // 서버 헬스 캐시 (5분)
        cacheConfigurations.put("server-health", 
            defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 사용량 통계 캐시 (10분)
        cacheConfigurations.put("usage-stats", 
            defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // 설정 캐시 (30분)
        cacheConfigurations.put("config-cache", 
            defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
        
        log.info("🗄️ Redis CacheManager configured with {} cache types", cacheConfigurations.size());
        
        return cacheManager;
    }
    
    /**
     * Redis 헬스 체크를 위한 유틸리티 빈
     */
    @Bean
    public RedisHealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }
    
    /**
     * 개발 환경 전용 Redis 설정
     */
    @Configuration
    @Profile("dev")
    static class DevelopmentRedisConfig {
        
        @Bean
        public String devRedisNotice() {
            log.info("🛠️ Development Redis configuration loaded");
            log.info("   - Extended TTL for debugging");
            log.info("   - Verbose logging enabled");
            return "dev-redis";
        }
        
        /**
         * 개발용 캐시 매니저 (긴 TTL)
         */
        @Bean("devCacheManager")
        public CacheManager devCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(12))  // 개발용 긴 TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(objectMapper)));
            
            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
        }
    }
    
    /**
     * 프로덕션 환경 전용 Redis 설정
     */
    @Configuration
    @Profile("prod")
    static class ProductionRedisConfig {
        
        @Bean
        public String prodRedisNotice() {
            log.info("🏭 Production Redis configuration loaded");
            log.info("   - Optimized TTL settings");
            log.info("   - Connection pooling enabled");
            return "prod-redis";
        }
    }
    
    /**
     * 테스트 환경 전용 Redis 설정
     */
    @Configuration
    @Profile("test")
    static class TestRedisConfig {
        
        @Bean
        public String testRedisNotice() {
            log.info("🧪 Test Redis configuration loaded");
            log.info("   - Short TTL for fast tests");
            return "test-redis";
        }
        
        /**
         * 테스트용 캐시 매니저 (짧은 TTL)
         */
        @Bean("testCacheManager")
        public CacheManager testCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))  // 테스트용 짧은 TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(objectMapper)));
            
            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
        }
    }
    
    /**
     * Redis 헬스 인디케이터 구현
     */
    public static class RedisHealthIndicator {
        private final RedisConnectionFactory connectionFactory;
        
        public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }
        
        public boolean isHealthy() {
            try {
                connectionFactory.getConnection().ping();
                return true;
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return false;
            }
        }
        
        public Map<String, Object> getHealth() {
            Map<String, Object> health = new HashMap<>();
            try {
                connectionFactory.getConnection().ping();
                health.put("status", "UP");
                health.put("details", "Redis is reachable");
            } catch (Exception e) {
                health.put("status", "DOWN");
                health.put("details", "Redis connection failed: " + e.getMessage());
            }
            return health;
        }
    }
}