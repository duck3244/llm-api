// CacheService.java
package com.yourcompany.llm.service.impl;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    
    // 캐시 통계
    private final Map<String, AtomicLong> cacheHits = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cacheMisses = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cacheEvictions = new ConcurrentHashMap<>();
    
    // 캐시 설정
    private static final String RESPONSE_CACHE_PREFIX = "llm:response:";
    private static final String MODEL_INFO_CACHE_PREFIX = "llm:model:";
    private static final String EMBEDDING_CACHE_PREFIX = "llm:embedding:";
    private static final String STATS_CACHE_PREFIX = "llm:stats:";
    
    private static final int DEFAULT_TTL_SECONDS = 3600; // 1시간
    private static final int RESPONSE_TTL_SECONDS = 86400; // 24시간
    private static final int MODEL_INFO_TTL_SECONDS = 3600; // 1시간
    private static final int EMBEDDING_TTL_SECONDS = 604800; // 1주일
    
    /**
     * LLM 응답 캐싱
     */
    public void cacheResponse(String cacheKey, LlmResponse response) {
        try {
            String key = RESPONSE_CACHE_PREFIX + cacheKey;
            
            // 응답을 직렬화하여 저장
            CachedResponse cachedResponse = CachedResponse.builder()
                .response(response)
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(RESPONSE_TTL_SECONDS))
                .build();
            
            redisTemplate.opsForValue().set(key, cachedResponse, RESPONSE_TTL_SECONDS, TimeUnit.SECONDS);
            
            // 캐시 인덱스 업데이트
            updateCacheIndex(response.getModel(), cacheKey);
            
            log.debug("Cached LLM response with key: {}", cacheKey);
            
        } catch (Exception e) {
            log.error("Failed to cache response", e);
        }
    }
    
    /**
     * 캐시된 LLM 응답 조회
     */
    public LlmResponse getResponse(String cacheKey) {
        try {
            String key = RESPONSE_CACHE_PREFIX + cacheKey;
            CachedResponse cachedResponse = (CachedResponse) redisTemplate.opsForValue().get(key);
            
            if (cachedResponse == null) {
                recordCacheMiss("response");
                return null;
            }
            
            // 만료 확인
            if (cachedResponse.getExpiresAt().isBefore(LocalDateTime.now())) {
                redisTemplate.delete(key);
                recordCacheMiss("response");
                return null;
            }
            
            recordCacheHit("response");
            
            // 캐시된 응답임을 표시
            LlmResponse response = cachedResponse.getResponse();
            response.setFromCache(true);
            response.setTimestamp(LocalDateTime.now());
            
            log.debug("Cache hit for key: {}", cacheKey);
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get cached response", e);
            recordCacheMiss("response");
            return null;
        }
    }
    
    /**
     * 임베딩 캐싱
     */
    public void cacheEmbedding(String model, String text, List<Double> embedding) {
        try {
            String cacheKey = generateEmbeddingCacheKey(model, text);
            String key = EMBEDDING_CACHE_PREFIX + cacheKey;
            
            CachedEmbedding cachedEmbedding = CachedEmbedding.builder()
                .model(model)
                .text(text)
                .embedding(embedding)
                .cachedAt(LocalDateTime.now())
                .build();
            
            redisTemplate.opsForValue().set(key, cachedEmbedding, EMBEDDING_TTL_SECONDS, TimeUnit.SECONDS);
            
            log.debug("Cached embedding for model: {}, text length: {}", model, text.length());
            
        } catch (Exception e) {
            log.error("Failed to cache embedding", e);
        }
    }
    
    /**
     * 캐시된 임베딩 조회
     */
    public List<Double> getEmbedding(String model, String text) {
        try {
            String cacheKey = generateEmbeddingCacheKey(model, text);
            String key = EMBEDDING_CACHE_PREFIX + cacheKey;
            
            CachedEmbedding cachedEmbedding = (CachedEmbedding) redisTemplate.opsForValue().get(key);
            
            if (cachedEmbedding == null) {
                recordCacheMiss("embedding");
                return null;
            }
            
            recordCacheHit("embedding");
            log.debug("Cache hit for embedding: {}", cacheKey);
            
            return cachedEmbedding.getEmbedding();
            
        } catch (Exception e) {
            log.error("Failed to get cached embedding", e);
            recordCacheMiss("embedding");
            return null;
        }
    }
    
    /**
     * 모델 정보 캐싱
     */
    public void cacheModelInfo(String model, Map<String, Object> modelInfo) {
        try {
            String key = MODEL_INFO_CACHE_PREFIX + model;
            
            CachedModelInfo cachedInfo = CachedModelInfo.builder()
                .model(model)
                .info(modelInfo)
                .cachedAt(LocalDateTime.now())
                .build();
            
            redisTemplate.opsForValue().set(key, cachedInfo, MODEL_INFO_TTL_SECONDS, TimeUnit.SECONDS);
            
            log.debug("Cached model info for: {}", model);
            
        } catch (Exception e) {
            log.error("Failed to cache model info", e);
        }
    }
    
    /**
     * 캐시된 모델 정보 조회
     */
    public Map<String, Object> getModelInfo(String model) {
        try {
            String key = MODEL_INFO_CACHE_PREFIX + model;
            CachedModelInfo cachedInfo = (CachedModelInfo) redisTemplate.opsForValue().get(key);
            
            if (cachedInfo == null) {
                recordCacheMiss("model_info");
                return null;
            }
            
            recordCacheHit("model_info");
            return cachedInfo.getInfo();
            
        } catch (Exception e) {
            log.error("Failed to get cached model info", e);
            recordCacheMiss("model_info");
            return null;
        }
    }
    
    /**
     * 통계 데이터 캐싱
     */
    public void cacheStats(String statsKey, Object stats, int ttlSeconds) {
        try {
            String key = STATS_CACHE_PREFIX + statsKey;
            
            CachedStats cachedStats = CachedStats.builder()
                .key(statsKey)
                .data(stats)
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(ttlSeconds))
                .build();
            
            redisTemplate.opsForValue().set(key, cachedStats, ttlSeconds, TimeUnit.SECONDS);
            
            log.debug("Cached stats with key: {}", statsKey);
            
        } catch (Exception e) {
            log.error("Failed to cache stats", e);
        }
    }
    
    /**
     * 캐시된 통계 데이터 조회
     */
    public Object getStats(String statsKey) {
        try {
            String key = STATS_CACHE_PREFIX + statsKey;
            CachedStats cachedStats = (CachedStats) redisTemplate.opsForValue().get(key);
            
            if (cachedStats == null) {
                recordCacheMiss("stats");
                return null;
            }
            
            // 만료 확인
            if (cachedStats.getExpiresAt() != null && 
                cachedStats.getExpiresAt().isBefore(LocalDateTime.now())) {
                redisTemplate.delete(key);
                recordCacheMiss("stats");
                return null;
            }
            
            recordCacheHit("stats");
            return cachedStats.getData();
            
        } catch (Exception e) {
            log.error("Failed to get cached stats", e);
            recordCacheMiss("stats");
            return null;
        }
    }
    
    /**
     * 캐시 키 생성
     */
    public String generateCacheKey(LlmRequest request) {
        try {
            // 캐시 키에 영향을 주는 요소들
            Map<String, Object> keyComponents = new HashMap<>();
            keyComponents.put("model", request.getModel());
            keyComponents.put("message", request.getMessage());
            keyComponents.put("messages", request.getMessages());
            keyComponents.put("systemPrompt", request.getSystemPrompt());
            keyComponents.put("temperature", request.getTemperature());
            keyComponents.put("maxTokens", request.getMaxTokens());
            keyComponents.put("topP", request.getTopP());
            keyComponents.put("presencePenalty", request.getPresencePenalty());
            keyComponents.put("frequencyPenalty", request.getFrequencyPenalty());
            keyComponents.put("stop", request.getStop());
            
            // JSON 직렬화 후 해시 생성
            String json = objectMapper.writeValueAsString(keyComponents);
            return generateHash(json);
            
        } catch (Exception e) {
            log.error("Failed to generate cache key", e);
            return "fallback_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 임베딩 캐시 키 생성
     */
    public String generateEmbeddingCacheKey(String model, String text) {
        String combined = model + ":" + text;
        return generateHash(combined);
    }
    
    /**
     * 캐시 무효화
     */
    public void invalidateCache(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                recordCacheEvictions(keys.size());
                log.info("Invalidated {} cache entries matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate cache", e);
        }
    }
    
    /**
     * 특정 모델의 캐시 무효화
     */
    public void invalidateModelCache(String model) {
        invalidateCache(RESPONSE_CACHE_PREFIX + "*");
        invalidateCache(MODEL_INFO_CACHE_PREFIX + model);
        invalidateCache(EMBEDDING_CACHE_PREFIX + "*" + model + "*");
    }
    
    /**
     * 만료된 캐시 정리
     */
    public void cleanupExpiredCache() {
        try {
            // 응답 캐시 정리
            cleanupExpiredEntries(RESPONSE_CACHE_PREFIX + "*");
            
            // 통계 캐시 정리
            cleanupExpiredEntries(STATS_CACHE_PREFIX + "*");
            
            log.info("Completed expired cache cleanup");
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired cache", e);
        }
    }
    
    /**
     * 캐시 통계 조회
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 히트율 계산
        for (String cacheType : Set.of("response", "embedding", "model_info", "stats")) {
            long hits = cacheHits.getOrDefault(cacheType, new AtomicLong(0)).get();
            long misses = cacheMisses.getOrDefault(cacheType, new AtomicLong(0)).get();
            long total = hits + misses;
            
            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("hits", hits);
            typeStats.put("misses", misses);
            typeStats.put("total", total);
            typeStats.put("hitRate", total > 0 ? (double) hits / total : 0.0);
            
            stats.put(cacheType, typeStats);
        }
        
        // 전체 통계
        long totalHits = cacheHits.values().stream().mapToLong(AtomicLong::get).sum();
        long totalMisses = cacheMisses.values().stream().mapToLong(AtomicLong::get).sum();
        long totalRequests = totalHits + totalMisses;
        
        stats.put("overall", Map.of(
            "totalHits", totalHits,
            "totalMisses", totalMisses,
            "totalRequests", totalRequests,
            "overallHitRate", totalRequests > 0 ? (double) totalHits / totalRequests : 0.0,
            "cacheSize", getCacheSize(),
            "memoryUsage", getCacheMemoryUsage()
        ));
        
        return stats;
    }
    
    /**
     * 캐시 크기 조회
     */
    public Map<String, Long> getCacheSize() {
        Map<String, Long> sizes = new HashMap<>();
        
        try {
            sizes.put("responses", countKeys(RESPONSE_CACHE_PREFIX + "*"));
            sizes.put("embeddings", countKeys(EMBEDDING_CACHE_PREFIX + "*"));
            sizes.put("modelInfo", countKeys(MODEL_INFO_CACHE_PREFIX + "*"));
            sizes.put("stats", countKeys(STATS_CACHE_PREFIX + "*"));
            
        } catch (Exception e) {
            log.error("Failed to get cache size", e);
        }
        
        return sizes;
    }
    
    /**
     * 캐시 메모리 사용량 추정
     */
    public Map<String, String> getCacheMemoryUsage() {
        Map<String, String> usage = new HashMap<>();
        
        try {
            // Redis 메모리 사용량 조회 (추정값)
            Map<String, Long> sizes = getCacheSize();
            
            sizes.forEach((type, count) -> {
                long estimatedBytes = count * 1024; // 대략적인 추정
                usage.put(type, formatBytes(estimatedBytes));
            });
            
        } catch (Exception e) {
            log.error("Failed to get cache memory usage", e);
        }
        
        return usage;
    }
    
    /**
     * 캐시 워밍업
     */
    public void warmupCache(List<String> models) {
        log.info("Starting cache warmup for models: {}", models);
        
        for (String model : models) {
            try {
                // 모델 정보 프리로드
                preloadModelInfo(model);
                
                // 자주 사용되는 프롬프트 프리로드
                preloadCommonPrompts(model);
                
            } catch (Exception e) {
                log.error("Failed to warmup cache for model: {}", model, e);
            }
        }
        
        log.info("Cache warmup completed");
    }
    
    /**
     * 캐시 히트 기록
     */
    public void recordCacheHit(String cacheType) {
        cacheHits.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 캐시 미스 기록
     */
    public void recordCacheMiss(String cacheType) {
        cacheMisses.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private String generateHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate hash", e);
            return String.valueOf(input.hashCode());
        }
    }
    
    private void updateCacheIndex(String model, String cacheKey) {
        try {
            String indexKey = "cache_index:" + model;
            redisTemplate.opsForSet().add(indexKey, cacheKey);
            redisTemplate.expire(indexKey, RESPONSE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to update cache index", e);
        }
    }
    
    private void cleanupExpiredEntries(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null) return;
            
            int cleaned = 0;
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof CachedResponse) {
                    CachedResponse cached = (CachedResponse) value;
                    if (cached.getExpiresAt().isBefore(LocalDateTime.now())) {
                        redisTemplate.delete(key);
                        cleaned++;
                    }
                } else if (value instanceof CachedStats) {
                    CachedStats cached = (CachedStats) value;
                    if (cached.getExpiresAt() != null && 
                        cached.getExpiresAt().isBefore(LocalDateTime.now())) {
                        redisTemplate.delete(key);
                        cleaned++;
                    }
                }
            }
            
            if (cleaned > 0) {
                recordCacheEvictions(cleaned);
                log.debug("Cleaned up {} expired entries for pattern: {}", cleaned, pattern);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired entries", e);
        }
    }
    
    private long countKeys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to count keys", e);
            return 0;
        }
    }
    
    private void recordCacheEvictions(int count) {
        cacheEvictions.computeIfAbsent("total", k -> new AtomicLong(0)).addAndGet(count);
    }
    
    private String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
    
    private void preloadModelInfo(String model) {
        // 모델 정보 프리로드 로직
        try {
            // 실제 구현에서는 모델 서비스에서 정보를 가져와 캐시에 저장
            log.debug("Preloading model info for: {}", model);
        } catch (Exception e) {
            log.error("Failed to preload model info", e);
        }
    }
    
    private void preloadCommonPrompts(String model) {
        // 자주 사용되는 프롬프트 프리로드
        List<String> commonPrompts = List.of(
            "Hello, how are you?",
            "What is the weather like?",
            "Tell me a joke",
            "Explain quantum computing"
        );
        
        for (String prompt : commonPrompts) {
            try {
                LlmRequest request = LlmRequest.builder()
                    .model(model)
                    .message(prompt)
                    .temperature(0.7)
                    .maxTokens(100)
                    .build();
                
                String cacheKey = generateCacheKey(request);
                log.debug("Generated cache key for common prompt: {}", cacheKey);
                
            } catch (Exception e) {
                log.error("Failed to preload common prompt", e);
            }
        }
    }
    
    // ===== 내부 클래스들 =====
    
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CachedResponse {
        private LlmResponse response;
        private LocalDateTime cachedAt;
        private LocalDateTime expiresAt;
    }
    
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CachedEmbedding {
        private String model;
        private String text;
        private List<Double> embedding;
        private LocalDateTime cachedAt;
    }
    
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CachedModelInfo {
        private String model;
        private Map<String, Object> info;
        private LocalDateTime cachedAt;
    }
    
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CachedStats {
        private String key;
        private Object data;
        private LocalDateTime cachedAt;
        private LocalDateTime expiresAt;
    }
}