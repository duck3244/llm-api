// CacheUtils.java
package com.yourcompany.llm.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUtils {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // 캐시 키 프리픽스
    private static final String CACHE_PREFIX = "llm:cache:";
    private static final String STATS_PREFIX = "llm:stats:";
    private static final String HEALTH_PREFIX = "llm:health:";
    private static final String CONFIG_PREFIX = "llm:config:";
    
    // 캐시 설정
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);
    private static final Duration HEALTH_TTL = Duration.ofMinutes(5);
    private static final Duration CONFIG_TTL = Duration.ofMinutes(30);
    
    // 제외할 파라미터 (캐시 키 생성 시)
    private static final Set<String> EXCLUDED_CACHE_PARAMS = Set.of(
        "timestamp", "requestId", "clientId", "sessionId"
    );
    
    // 민감한 정보 패턴 (캐시하지 않음)
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(?i)(password|token|key|secret|credit|ssn|email|phone)", Pattern.MULTILINE
    );
    
    /**
     * LLM 요청에 대한 캐시 키 생성
     */
    public String generateCacheKey(LlmRequest request) {
        if (request == null) {
            return null;
        }
        
        try {
            // 민감한 정보가 포함된 요청은 캐시하지 않음
            if (containsSensitiveInfo(request)) {
                log.debug("Request contains sensitive information, skipping cache");
                return null;
            }
            
            // 캐시용 정규화된 요청 생성
            LlmRequest normalizedRequest = normalizeRequestForCache(request);
            
            // JSON 직렬화
            String requestJson = objectMapper.writeValueAsString(normalizedRequest);
            
            // SHA-256 해시 생성
            String hash = generateSha256Hash(requestJson);
            
            // 모델명을 포함한 캐시 키
            String modelName = request.getModel() != null ? request.getModel() : "default";
            String cacheKey = CACHE_PREFIX + modelName + ":" + hash;
            
            log.debug("Generated cache key: {} for model: {}", cacheKey, modelName);
            return cacheKey;
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to generate cache key due to JSON processing error", e);
            return null;
        } catch (Exception e) {
            log.warn("Failed to generate cache key", e);
            return null;
        }
    }
    
    /**
     * 캐시에서 응답 조회
     */
    public Optional<LlmResponse> getCachedResponse(String cacheKey) {
        if (cacheKey == null) {
            return Optional.empty();
        }
        
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                log.debug("Cache miss for key: {}", cacheKey);
                return Optional.empty();
            }
            
            LlmResponse response = objectMapper.convertValue(cached, LlmResponse.class);
            
            // 캐시 히트 통계 업데이트
            updateCacheStats(cacheKey, true);
            
            log.debug("Cache hit for key: {}", cacheKey);
            return Optional.of(response);
            
        } catch (Exception e) {
            log.warn("Failed to get cached response for key: {}", cacheKey, e);
            return Optional.empty();
        }
    }
    
    /**
     * 응답을 캐시에 저장
     */
    public void cacheResponse(String cacheKey, LlmResponse response) {
        if (cacheKey == null || response == null || !response.isSuccess()) {
            return;
        }
        
        try {
            // 응답에 캐시 메타데이터 추가
            LlmResponse cacheableResponse = enhanceResponseForCache(response);
            
            // Redis에 저장
            redisTemplate.opsForValue().set(cacheKey, cacheableResponse, RESPONSE_TTL);
            
            // 캐시 미스 통계 업데이트
            updateCacheStats(cacheKey, false);
            
            // 캐시 인덱스 업데이트 (검색 및 관리용)
            updateCacheIndex(cacheKey, response.getModel());
            
            log.debug("Cached response for key: {} with TTL: {}", cacheKey, RESPONSE_TTL);
            
        } catch (Exception e) {
            log.warn("Failed to cache response for key: {}", cacheKey, e);
        }
    }
    
    /**
     * 모델별 캐시 통계 조회
     */
    public CacheStats getCacheStats(String modelName) {
        String statsKey = STATS_PREFIX + modelName;
        
        try {
            Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);
            
            if (stats.isEmpty()) {
                return CacheStats.empty(modelName);
            }
            
            return CacheStats.builder()
                .modelName(modelName)
                .hitCount(getLongValue(stats, "hits", 0L))
                .missCount(getLongValue(stats, "misses", 0L))
                .totalRequests(getLongValue(stats, "total", 0L))
                .hitRate(calculateHitRate(stats))
                .lastUpdated(getDateTimeValue(stats, "lastUpdated"))
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to get cache stats for model: {}", modelName, e);
            return CacheStats.empty(modelName);
        }
    }
    
    /**
     * 전체 캐시 통계 조회
     */
    public Map<String, CacheStats> getAllCacheStats() {
        Map<String, CacheStats> allStats = new HashMap<>();
        
        try {
            Set<String> keys = redisTemplate.keys(STATS_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String modelName = key.substring(STATS_PREFIX.length());
                    CacheStats stats = getCacheStats(modelName);
                    allStats.put(modelName, stats);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get all cache stats", e);
        }
        
        return allStats;
    }
    
    /**
     * 캐시 크기 및 메모리 사용량 조회
     */
    public CacheInfo getCacheInfo() {
        try {
            Set<String> allKeys = redisTemplate.keys(CACHE_PREFIX + "*");
            int totalKeys = allKeys != null ? allKeys.size() : 0;
            
            // 샘플링을 통한 평균 크기 추정
            long estimatedTotalSize = estimateTotalCacheSize(allKeys);
            
            return CacheInfo.builder()
                .totalKeys(totalKeys)
                .estimatedSizeBytes(estimatedTotalSize)
                .estimatedSizeMB(estimatedTotalSize / 1024 / 1024)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to get cache info", e);
            return CacheInfo.builder()
                .totalKeys(0)
                .estimatedSizeBytes(0L)
                .estimatedSizeMB(0L)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * 특정 모델의 캐시 클리어
     */
    public int clearModelCache(String modelName) {
        try {
            String pattern = CACHE_PREFIX + modelName + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} cache entries for model: {}", keys.size(), modelName);
                return keys.size();
            }
            
            return 0;
            
        } catch (Exception e) {
            log.warn("Failed to clear cache for model: {}", modelName, e);
            return 0;
        }
    }
    
    /**
     * 만료된 캐시 항목 클리어
     */
    public int clearExpiredCache() {
        int clearedCount = 0;
        
        try {
            Set<String> allKeys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (allKeys != null) {
                for (String key : allKeys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl == -2) { // 키가 존재하지 않음
                        redisTemplate.delete(key);
                        clearedCount++;
                    }
                }
            }
            
            if (clearedCount > 0) {
                log.info("Cleared {} expired cache entries", clearedCount);
            }
            
        } catch (Exception e) {
            log.warn("Failed to clear expired cache", e);
        }
        
        return clearedCount;
    }
    
    /**
     * 캐시 압축 (사용 빈도가 낮은 항목 제거)
     */
    public int compressCache(int maxEntries) {
        int removedCount = 0;
        
        try {
            Set<String> allKeys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (allKeys == null || allKeys.size() <= maxEntries) {
                return 0;
            }
            
            // TTL 기준으로 정렬하여 오래된 항목부터 제거
            List<String> keysList = new ArrayList<>(allKeys);
            keysList.sort((k1, k2) -> {
                Long ttl1 = redisTemplate.getExpire(k1);
                Long ttl2 = redisTemplate.getExpire(k2);
                return Long.compare(ttl1 != null ? ttl1 : 0, ttl2 != null ? ttl2 : 0);
            });
            
            int toRemove = keysList.size() - maxEntries;
            for (int i = 0; i < toRemove; i++) {
                redisTemplate.delete(keysList.get(i));
                removedCount++;
            }
            
            log.info("Cache compression completed: removed {} entries", removedCount);
            
        } catch (Exception e) {
            log.warn("Failed to compress cache", e);
        }
        
        return removedCount;
    }
    
    /**
     * 헬스 체크 결과 캐싱
     */
    public void cacheHealthStatus(String serverName, Object healthStatus) {
        String key = HEALTH_PREFIX + serverName;
        try {
            redisTemplate.opsForValue().set(key, healthStatus, HEALTH_TTL);
            log.debug("Cached health status for server: {}", serverName);
        } catch (Exception e) {
            log.warn("Failed to cache health status for server: {}", serverName, e);
        }
    }
    
    /**
     * 캐시된 헬스 체크 결과 조회
     */
    public Optional<Object> getCachedHealthStatus(String serverName) {
        String key = HEALTH_PREFIX + serverName;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(cached);
        } catch (Exception e) {
            log.warn("Failed to get cached health status for server: {}", serverName, e);
            return Optional.empty();
        }
    }
    
    /**
     * 설정 정보 캐싱
     */
    public void cacheConfig(String configKey, Object config) {
        String key = CONFIG_PREFIX + configKey;
        try {
            redisTemplate.opsForValue().set(key, config, CONFIG_TTL);
            log.debug("Cached config: {}", configKey);
        } catch (Exception e) {
            log.warn("Failed to cache config: {}", configKey, e);
        }
    }
    
    /**
     * 캐시된 설정 정보 조회
     */
    public <T> Optional<T> getCachedConfig(String configKey, Class<T> type) {
        String key = CONFIG_PREFIX + configKey;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                T config = objectMapper.convertValue(cached, type);
                return Optional.of(config);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to get cached config: {}", configKey, e);
            return Optional.empty();
        }
    }
    
    // Private helper methods
    
    private boolean containsSensitiveInfo(LlmRequest request) {
        List<String> textsToCheck = new ArrayList<>();
        
        if (request.getMessage() != null) {
            textsToCheck.add(request.getMessage());
        }
        
        if (request.getMessages() != null) {
            request.getMessages().stream()
                .filter(Objects::nonNull)
                .map(LlmRequest.Message::getContent)
                .filter(Objects::nonNull)
                .forEach(textsToCheck::add);
        }
        
        if (request.getSystemPrompt() != null) {
            textsToCheck.add(request.getSystemPrompt());
        }
        
        return textsToCheck.stream()
            .anyMatch(text -> SENSITIVE_PATTERN.matcher(text).find());
    }
    
    private LlmRequest normalizeRequestForCache(LlmRequest request) {
        LlmRequest normalized = new LlmRequest();
        
        // 캐시에 영향을 주는 필드만 복사
        normalized.setModel(request.getModel());
        normalized.setMessage(request.getMessage());
        normalized.setMessages(request.getMessages());
        normalized.setSystemPrompt(request.getSystemPrompt());
        
        // 파라미터 정규화 (소수점 2자리까지)
        if (request.getTemperature() != null) {
            normalized.setTemperature(Math.round(request.getTemperature() * 100.0) / 100.0);
        }
        
        if (request.getMaxTokens() != null) {
            normalized.setMaxTokens(request.getMaxTokens());
        }
        
        return normalized;
    }
    
    private String generateSha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private LlmResponse enhanceResponseForCache(LlmResponse response) {
        LlmResponse enhanced = new LlmResponse();
        
        // 기본 필드 복사
        enhanced.setId(response.getId());
        enhanced.setModel(response.getModel());
        enhanced.setContent(response.getContent());
        enhanced.setTokensUsed(response.getTokensUsed());
        enhanced.setProvider(response.getProvider());
        enhanced.setSuccess(response.isSuccess());
        enhanced.setError(response.getError());
        
        // 캐시 메타데이터 추가
        enhanced.setTimestamp(LocalDateTime.now());
        
        return enhanced;
    }
    
    private void updateCacheStats(String cacheKey, boolean isHit) {
        try {
            // 모델명 추출
            String modelName = extractModelNameFromCacheKey(cacheKey);
            String statsKey = STATS_PREFIX + modelName;
            
            String hitField = isHit ? "hits" : "misses";
            
            redisTemplate.opsForHash().increment(statsKey, hitField, 1);
            redisTemplate.opsForHash().increment(statsKey, "total", 1);
            redisTemplate.opsForHash().put(statsKey, "lastUpdated", 
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // 통계 TTL 설정 (7일)
            redisTemplate.expire(statsKey, Duration.ofDays(7));
            
        } catch (Exception e) {
            log.warn("Failed to update cache stats for key: {}", cacheKey, e);
        }
    }
    
    private void updateCacheIndex(String cacheKey, String modelName) {
        try {
            String indexKey = "llm:cache:index:" + modelName;
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // 캐시 키와 타임스탬프를 인덱스에 저장
            redisTemplate.opsForZSet().add(indexKey, cacheKey, System.currentTimeMillis());
            
            // 인덱스 크기 제한 (최근 1000개만 유지)
            redisTemplate.opsForZSet().removeRange(indexKey, 0, -1001);
            
            // 인덱스 TTL 설정
            redisTemplate.expire(indexKey, Duration.ofDays(7));
            
        } catch (Exception e) {
            log.warn("Failed to update cache index for key: {}", cacheKey, e);
        }
    }
    
    private String extractModelNameFromCacheKey(String cacheKey) {
        if (cacheKey == null || !cacheKey.startsWith(CACHE_PREFIX)) {
            return "unknown";
        }
        
        String remaining = cacheKey.substring(CACHE_PREFIX.length());
        int colonIndex = remaining.indexOf(':');
        
        return colonIndex > 0 ? remaining.substring(0, colonIndex) : "unknown";
    }
    
    private Long getLongValue(Map<Object, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private LocalDateTime getDateTimeValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                return LocalDateTime.now();
            }
        }
        return LocalDateTime.now();
    }
    
    private double calculateHitRate(Map<Object, Object> stats) {
        Long hits = getLongValue(stats, "hits", 0L);
        Long total = getLongValue(stats, "total", 0L);
        
        if (total == 0) {
            return 0.0;
        }
        
        return (double) hits / total;
    }
    
    private long estimateTotalCacheSize(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        
        // 샘플링: 최대 10개 키의 크기를 측정하고 평균을 구함
        List<String> sampleKeys = keys.stream()
            .limit(10)
            .toList();
        
        long totalSampleSize = 0;
        int validSamples = 0;
        
        for (String key : sampleKeys) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    String json = objectMapper.writeValueAsString(value);
                    totalSampleSize += json.getBytes(StandardCharsets.UTF_8).length;
                    validSamples++;
                }
            } catch (Exception e) {
                // 무시하고 다음 샘플로 진행
            }
        }
        
        if (validSamples == 0) {
            return 0L;
        }
        
        long averageSize = totalSampleSize / validSamples;
        return averageSize * keys.size();
    }
    
    /**
     * 캐시 워밍업 (자주 사용되는 요청들을 미리 캐시)
     */
    public void warmUpCache(List<LlmRequest> commonRequests) {
        log.info("Starting cache warm-up with {} requests", commonRequests.size());
        
        for (LlmRequest request : commonRequests) {
            try {
                String cacheKey = generateCacheKey(request);
                if (cacheKey != null) {
                    // 캐시 키만 생성하고 실제 요청은 하지 않음
                    // 실제 구현에서는 LLM 서비스를 호출하여 응답을 캐시할 수 있음
                    log.debug("Generated cache key for warm-up: {}", cacheKey);
                }
            } catch (Exception e) {
                log.warn("Failed to warm up cache for request", e);
            }
        }
        
        log.info("Cache warm-up completed");
    }
    
    /**
     * 캐시 키 패턴으로 검색
     */
    public Set<String> findCacheKeysByPattern(String pattern) {
        try {
            String fullPattern = CACHE_PREFIX + pattern;
            Set<String> keys = redisTemplate.keys(fullPattern);
            return keys != null ? keys : new HashSet<>();
        } catch (Exception e) {
            log.warn("Failed to find cache keys by pattern: {}", pattern, e);
            return new HashSet<>();
        }
    }
    
    /**
     * 캐시 키의 TTL 조회
     */
    public Duration getCacheTtl(String cacheKey) {
        try {
            Long expireSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            if (expireSeconds != null && expireSeconds > 0) {
                return Duration.ofSeconds(expireSeconds);
            }
        } catch (Exception e) {
            log.warn("Failed to get TTL for cache key: {}", cacheKey, e);
        }
        return Duration.ZERO;
    }
    
    // Data classes
    
    @lombok.Builder
    @lombok.Data
    public static class CacheStats {
        private String modelName;
        private Long hitCount;
        private Long missCount;
        private Long totalRequests;
        private Double hitRate;
        private LocalDateTime lastUpdated;
        
        public static CacheStats empty(String modelName) {
            return CacheStats.builder()
                .modelName(modelName)
                .hitCount(0L)
                .missCount(0L)
                .totalRequests(0L)
                .hitRate(0.0)
                .lastUpdated(LocalDateTime.now())
                .build();
        }
        
        public boolean isEmpty() {
            return totalRequests == 0;
        }
        
        public double getHitRatePercentage() {
            return hitRate * 100.0;
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class CacheInfo {
        private Integer totalKeys;
        private Long estimatedSizeBytes;
        private Long estimatedSizeMB;
        private LocalDateTime timestamp;
        
        public String getFormattedSize() {
            if (estimatedSizeBytes < 1024) {
                return estimatedSizeBytes + " B";
            } else if (estimatedSizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", estimatedSizeBytes / 1024.0);
            } else if (estimatedSizeBytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", estimatedSizeBytes / 1024.0 / 1024.0);
            } else {
                return String.format("%.1f GB", estimatedSizeBytes / 1024.0 / 1024.0 / 1024.0);
            }
        }
    }
    
    /**
     * 캐시 성능 메트릭
     */
    @lombok.Builder
    @lombok.Data
    public static class CacheMetrics {
        private Map<String, CacheStats> modelStats;
        private CacheInfo globalInfo;
        private Double overallHitRate;
        private Long totalCacheOperations;
        private LocalDateTime generatedAt;
        
        public static CacheMetrics from(Map<String, CacheStats> modelStats, CacheInfo globalInfo) {
            double totalHits = modelStats.values().stream()
                .mapToLong(stats -> stats.getHitCount() != null ? stats.getHitCount() : 0L)
                .sum();
            
            double totalRequests = modelStats.values().stream()
                .mapToLong(stats -> stats.getTotalRequests() != null ? stats.getTotalRequests() : 0L)
                .sum();
            
            double overallHitRate = totalRequests > 0 ? totalHits / totalRequests : 0.0;
            
            return CacheMetrics.builder()
                .modelStats(modelStats)
                .globalInfo(globalInfo)
                .overallHitRate(overallHitRate)
                .totalCacheOperations((long) totalRequests)
                .generatedAt(LocalDateTime.now())
                .build();
        }
    }
}