// LlmConfigProperties.java
package com.yourcompany.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
@Validated
public class LlmConfigProperties {
    
    @Valid
    @NotEmpty(message = "At least one model configuration is required")
    private List<ModelConfig> models;
    
    @Valid
    @NotNull(message = "Default configuration is required")
    private DefaultConfig defaults;
    
    @Valid
    private RetryConfig retry = new RetryConfig();
    
    @Valid
    private SecurityConfig security = new SecurityConfig();
    
    @Valid
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    @Valid
    private CacheConfig cache = new CacheConfig();
    
    /**
     * 개별 모델 설정
     */
    @Data
    public static class ModelConfig {
        
        @NotBlank(message = "Model name is required")
        private String name;
        
        @NotBlank(message = "Provider is required")
        @Pattern(regexp = "openai|anthropic|google|huggingface|ollama|vllm|local", 
                message = "Provider must be one of: openai, anthropic, google, huggingface, ollama, vllm, local")
        private String provider;
        
        @NotBlank(message = "Endpoint is required")
        private String endpoint;
        
        private String apiKey;
        
        private String model; // 실제 모델명 (Ollama, vLLM 등에서 사용)
        
        @Min(value = 1, message = "Max tokens must be at least 1")
        @Max(value = 32768, message = "Max tokens cannot exceed 32768")
        private Integer maxTokens = 4096;
        
        @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
        @DecimalMax(value = "2.0", message = "Temperature cannot exceed 2.0")
        private Double temperature = 0.7;
        
        @NotNull(message = "Enabled flag is required")
        private Boolean enabled = true;
        
        @Valid
        private ModelLimits limits = new ModelLimits();
        
        @Valid
        private ModelFeatures features = new ModelFeatures();
        
        private Map<String, Object> customProperties;
    }
    
    /**
     * 모델별 제한 설정
     */
    @Data
    public static class ModelLimits {
        
        @Min(value = 1, message = "Max requests per minute must be at least 1")
        private Integer maxRequestsPerMinute = 60;
        
        @Min(value = 1, message = "Max tokens per minute must be at least 1")
        private Integer maxTokensPerMinute = 40000;
        
        @Min(value = 1, message = "Max concurrent requests must be at least 1")
        private Integer maxConcurrentRequests = 10;
        
        @Min(value = 1, message = "Max context length must be at least 1")
        private Integer maxContextLength = 4096;
        
        @DecimalMin(value = "0.0", message = "Cost per token must be at least 0")
        private Double costPerInputToken = 0.0;
        
        @DecimalMin(value = "0.0", message = "Cost per output token must be at least 0")
        private Double costPerOutputToken = 0.0;
    }
    
    /**
     * 모델별 기능 설정
     */
    @Data
    public static class ModelFeatures {
        private Boolean supportsStreaming = false;
        private Boolean supportsSystemPrompt = true;
        private Boolean supportsFunctionCalling = false;
        private Boolean supportsVision = false;
        private Boolean supportsEmbeddings = false;
        private Boolean supportsFineTuning = false;
        private List<String> supportedLanguages;
        private List<String> specializations; // "coding", "reasoning", "creative", etc.
    }
    
    /**
     * 기본 설정
     */
    @Data
    public static class DefaultConfig {
        
        @NotBlank(message = "Default model is required")
        private String model;
        
        @Min(value = 1000, message = "Timeout must be at least 1000ms")
        @Max(value = 600000, message = "Timeout cannot exceed 600000ms (10 minutes)")
        private Integer timeout = 30000; // 30초
        
        @Min(value = 0, message = "Retry attempts must be at least 0")
        @Max(value = 10, message = "Retry attempts cannot exceed 10")
        private Integer retryAttempts = 3;
        
        @Min(value = 100, message = "Retry delay must be at least 100ms")
        private Integer retryDelayMs = 1000;
        
        @DecimalMin(value = "1.0", message = "Retry multiplier must be at least 1.0")
        @DecimalMax(value = "10.0", message = "Retry multiplier cannot exceed 10.0")
        private Double retryMultiplier = 2.0;
        
        private Boolean enableCircuitBreaker = true;
        
        private Boolean enableMetrics = true;
        
        private Boolean enableCaching = true;
    }
    
    /**
     * 재시도 설정
     */
    @Data
    public static class RetryConfig {
        
        @Min(value = 0, message = "Max attempts must be at least 0")
        private Integer maxAttempts = 3;
        
        @Min(value = 100, message = "Initial delay must be at least 100ms")
        private Long initialDelayMs = 1000L;
        
        @DecimalMin(value = "1.0", message = "Multiplier must be at least 1.0")
        private Double multiplier = 2.0;
        
        @Min(value = 1000, message = "Max delay must be at least 1000ms")
        private Long maxDelayMs = 30000L;
        
        private List<String> retryableErrors = List.of(
            "TIMEOUT", "CONNECTION_ERROR", "RATE_LIMIT", "SERVER_ERROR"
        );
        
        private Boolean enableJitter = true;
    }
    
    /**
     * 보안 설정
     */
    @Data
    public static class SecurityConfig {
        
        private Boolean enableApiKeyValidation = true;
        
        private Boolean enableRateLimiting = true;
        
        private Boolean enableRequestLogging = false;
        
        private Boolean enableResponseLogging = false;
        
        private Boolean maskSensitiveData = true;
        
        private List<String> allowedOrigins = List.of("*");
        
        private List<String> allowedHeaders = List.of(
            "Content-Type", "Authorization", "X-Requested-With", "X-API-Key"
        );
        
        private List<String> sensitiveFields = List.of(
            "apiKey", "password", "token", "secret"
        );
        
        @Min(value = 1, message = "Max request size must be at least 1KB")
        private Integer maxRequestSizeKb = 1024; // 1MB
        
        @Min(value = 1, message = "Request rate limit must be at least 1")
        private Integer requestsPerMinute = 100;
    }
    
    /**
     * 모니터링 설정
     */
    @Data
    public static class MonitoringConfig {
        
        private Boolean enableHealthChecks = true;
        
        private Boolean enableMetrics = true;
        
        private Boolean enableAlerts = true;
        
        private Boolean enablePerformanceTracking = true;
        
        @Min(value = 5, message = "Health check interval must be at least 5 seconds")
        private Integer healthCheckIntervalSeconds = 30;
        
        @Min(value = 1, message = "Metrics collection interval must be at least 1 second")
        private Integer metricsIntervalSeconds = 60;
        
        @Min(value = 1, message = "Alert check interval must be at least 1 second")
        private Integer alertCheckIntervalSeconds = 30;
        
        private List<String> enabledMetrics = List.of(
            "request_count", "response_time", "error_rate", "token_usage", "cache_hit_rate"
        );
        
        @Valid
        private AlertThresholds alertThresholds = new AlertThresholds();
    }
    
    /**
     * 알럿 임계값 설정
     */
    @Data
    public static class AlertThresholds {
        
        @DecimalMin(value = "0.0", message = "Error rate threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "Error rate threshold cannot exceed 1")
        private Double errorRateThreshold = 0.1; // 10%
        
        @Min(value = 100, message = "Response time threshold must be at least 100ms")
        private Integer responseTimeThresholdMs = 5000; // 5초
        
        @Min(value = 1, message = "Request volume threshold must be at least 1")
        private Integer requestVolumeThreshold = 1000;
        
        @DecimalMin(value = "0.0", message = "Cache hit rate threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "Cache hit rate threshold cannot exceed 1")
        private Double cacheHitRateThreshold = 0.8; // 80%
        
        @DecimalMin(value = "0.0", message = "CPU usage threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "CPU usage threshold cannot exceed 1")
        private Double cpuUsageThreshold = 0.8; // 80%
        
        @DecimalMin(value = "0.0", message = "Memory usage threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "Memory usage threshold cannot exceed 1")
        private Double memoryUsageThreshold = 0.85; // 85%
    }
    
    /**
     * 캐시 설정
     */
    @Data
    public static class CacheConfig {
        
        private Boolean enableResponseCaching = true;
        
        private Boolean enableModelInfoCaching = true;
        
        private Boolean enableConfigCaching = true;
        
        @Min(value = 60, message = "Default TTL must be at least 60 seconds")
        private Integer defaultTtlSeconds = 3600; // 1시간
        
        @Min(value = 60, message = "Response cache TTL must be at least 60 seconds")
        private Integer responseCacheTtlSeconds = 86400; // 24시간
        
        @Min(value = 60, message = "Model info cache TTL must be at least 60 seconds")
        private Integer modelInfoCacheTtlSeconds = 3600; // 1시간
        
        @Min(value = 60, message = "Config cache TTL must be at least 60 seconds")
        private Integer configCacheTtlSeconds = 1800; // 30분
        
        @Min(value = 1, message = "Max cache size must be at least 1")
        private Integer maxCacheSizeMb = 256; // 256MB
        
        private Boolean enableCacheMetrics = true;
        
        private Boolean enableCacheCompression = true;
        
        private List<String> cacheExcludePatterns = List.of(
            "health", "metrics", "status"
        );
    }
    
    // Helper methods
    
    /**
     * 이름으로 모델 설정 조회
     */
    public Optional<ModelConfig> getModelByName(String modelName) {
        return models.stream()
            .filter(model -> model.getName().equals(modelName))
            .filter(model -> model.getEnabled())
            .findFirst();
    }
    
    /**
     * 프로바이더별 모델 목록 조회
     */
    public List<ModelConfig> getModelsByProvider(String provider) {
        return models.stream()
            .filter(model -> model.getProvider().equals(provider))
            .filter(model -> model.getEnabled())
            .toList();
    }
    
    /**
     * 활성화된 모델 목록 조회
     */
    public List<ModelConfig> getEnabledModels() {
        return models.stream()
            .filter(model -> model.getEnabled())
            .toList();
    }
    
    /**
     * 특정 기능을 지원하는 모델 목록 조회
     */
    public List<ModelConfig> getModelsByFeature(String feature) {
        return models.stream()
            .filter(model -> model.getEnabled())
            .filter(model -> hasFeature(model, feature))
            .toList();
    }
    
    /**
     * 모델이 특정 기능을 지원하는지 확인
     */
    private boolean hasFeature(ModelConfig model, String feature) {
        ModelFeatures features = model.getFeatures();
        if (features == null) return false;
        
        return switch (feature.toLowerCase()) {
            case "streaming" -> Boolean.TRUE.equals(features.getSupportsStreaming());
            case "system_prompt" -> Boolean.TRUE.equals(features.getSupportsSystemPrompt());
            case "function_calling" -> Boolean.TRUE.equals(features.getSupportsFunctionCalling());
            case "vision" -> Boolean.TRUE.equals(features.getSupportsVision());
            case "embeddings" -> Boolean.TRUE.equals(features.getSupportsEmbeddings());
            case "fine_tuning" -> Boolean.TRUE.equals(features.getSupportsFineTuning());
            default -> features.getSpecializations() != null && 
                      features.getSpecializations().contains(feature);
        };
    }
    
    /**
     * 모델의 비용 계산
     */
    public double calculateCost(String modelName, int inputTokens, int outputTokens) {
        return getModelByName(modelName)
            .map(model -> {
                ModelLimits limits = model.getLimits();
                double inputCost = inputTokens * (limits.getCostPerInputToken() != null ? 
                    limits.getCostPerInputToken() : 0.0);
                double outputCost = outputTokens * (limits.getCostPerOutputToken() != null ? 
                    limits.getCostPerOutputToken() : 0.0);
                return inputCost + outputCost;
            })
            .orElse(0.0);
    }
    
    /**
     * 모델의 요청 제한 확인
     */
    public boolean isWithinLimits(String modelName, int tokensToProcess) {
        return getModelByName(modelName)
            .map(model -> {
                ModelLimits limits = model.getLimits();
                return tokensToProcess <= (limits.getMaxContextLength() != null ? 
                    limits.getMaxContextLength() : Integer.MAX_VALUE);
            })
            .orElse(false);
    }
    
    /**
     * 추천 모델 선택 (기능 기반)
     */
    public Optional<ModelConfig> recommendModel(String task, List<String> requiredFeatures) {
        List<ModelConfig> candidates = getEnabledModels().stream()
            .filter(model -> requiredFeatures.stream()
                .allMatch(feature -> hasFeature(model, feature)))
            .toList();
        
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        
        // 태스크별 우선순위 로직
        return switch (task.toLowerCase()) {
            case "coding", "code" -> candidates.stream()
                .filter(model -> hasFeature(model, "coding"))
                .findFirst()
                .or(() -> candidates.stream().findFirst());
            
            case "reasoning", "analysis" -> candidates.stream()
                .filter(model -> hasFeature(model, "reasoning"))
                .findFirst()
                .or(() -> candidates.stream().findFirst());
            
            case "creative", "writing" -> candidates.stream()
                .filter(model -> hasFeature(model, "creative"))
                .findFirst()
                .or(() -> candidates.stream().findFirst());
            
            default -> candidates.stream().findFirst();
        };
    }
    
    /**
     * 설정 유효성 검증
     */
    public void validate() {
        // 기본 모델이 존재하는지 확인
        if (getModelByName(defaults.getModel()).isEmpty()) {
            throw new IllegalArgumentException(
                "Default model '" + defaults.getModel() + "' not found in model configurations");
        }
        
        // 모델 이름 중복 확인
        long uniqueNames = models.stream()
            .map(ModelConfig::getName)
            .distinct()
            .count();
        
        if (uniqueNames != models.size()) {
            throw new IllegalArgumentException("Duplicate model names found in configuration");
        }
        
        // 활성화된 모델이 최소 하나는 있는지 확인
        if (getEnabledModels().isEmpty()) {
            throw new IllegalArgumentException("At least one model must be enabled");
        }
    }
    
    /**
     * 설정 요약 정보 반환
     */
    public ConfigSummary getSummary() {
        List<ModelConfig> enabled = getEnabledModels();
        Map<String, Long> providerCounts = enabled.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                ModelConfig::getProvider,
                java.util.stream.Collectors.counting()
            ));
        
        return ConfigSummary.builder()
            .totalModels(models.size())
            .enabledModels(enabled.size())
            .providerCounts(providerCounts)
            .defaultModel(defaults.getModel())
            .cachingEnabled(defaults.getEnableCaching())
            .metricsEnabled(defaults.getEnableMetrics())
            .build();
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ConfigSummary {
        private Integer totalModels;
        private Integer enabledModels;
        private Map<String, Long> providerCounts;
        private String defaultModel;
        private Boolean cachingEnabled;
        private Boolean metricsEnabled;
    }
}