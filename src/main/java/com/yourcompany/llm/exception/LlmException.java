// LlmException.java
package com.yourcompany.llm.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 서비스 관련 예외의 기본 클래스
 */
@Getter
public class LlmException extends RuntimeException {
    
    private final String errorCode;
    private final String model;
    private final String provider;
    private final String requestId;
    private final LocalDateTime timestamp;
    private final Map<String, Object> context;
    private final ErrorSeverity severity;
    private final boolean retryable;
    
    public LlmException(String message) {
        super(message);
        this.errorCode = "LLM_GENERIC_ERROR";
        this.model = null;
        this.provider = null;
        this.requestId = null;
        this.timestamp = LocalDateTime.now();
        this.context = Map.of();
        this.severity = ErrorSeverity.MEDIUM;
        this.retryable = false;
    }
    
    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "LLM_GENERIC_ERROR";
        this.model = null;
        this.provider = null;
        this.requestId = null;
        this.timestamp = LocalDateTime.now();
        this.context = Map.of();
        this.severity = ErrorSeverity.MEDIUM;
        this.retryable = false;
    }
    
    public LlmException(String errorCode, String message, String model, String provider) {
        super(message);
        this.errorCode = errorCode;
        this.model = model;
        this.provider = provider;
        this.requestId = null;
        this.timestamp = LocalDateTime.now();
        this.context = Map.of();
        this.severity = ErrorSeverity.MEDIUM;
        this.retryable = false;
    }
    
    public LlmException(Builder builder) {
        super(builder.message, builder.cause);
        this.errorCode = builder.errorCode;
        this.model = builder.model;
        this.provider = builder.provider;
        this.requestId = builder.requestId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.context = builder.context != null ? builder.context : Map.of();
        this.severity = builder.severity != null ? builder.severity : ErrorSeverity.MEDIUM;
        this.retryable = builder.retryable;
    }
    
    /**
     * 에러 심각도 열거형
     */
    public enum ErrorSeverity {
        LOW,      // 로그만 기록
        MEDIUM,   // 로그 + 메트릭 기록
        HIGH,     // 로그 + 메트릭 + 알럿
        CRITICAL  // 로그 + 메트릭 + 알럿 + 긴급 대응
    }
    
    /**
     * 빌더 패턴
     */
    public static class Builder {
        private String errorCode;
        private String message;
        private Throwable cause;
        private String model;
        private String provider;
        private String requestId;
        private LocalDateTime timestamp;
        private Map<String, Object> context;
        private ErrorSeverity severity;
        private boolean retryable = false;
        
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }
        
        public Builder severity(ErrorSeverity severity) {
            this.severity = severity;
            return this;
        }
        
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }
        
        public LlmException build() {
            return new LlmException(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 컨텍스트 정보 추가
     */
    public LlmException withContext(String key, Object value) {
        Map<String, Object> newContext = new java.util.HashMap<>(this.context);
        newContext.put(key, value);
        
        return LlmException.builder()
            .errorCode(this.errorCode)
            .message(this.getMessage())
            .cause(this.getCause())
            .model(this.model)
            .provider(this.provider)
            .requestId(this.requestId)
            .timestamp(this.timestamp)
            .context(newContext)
            .severity(this.severity)
            .retryable(this.retryable)
            .build();
    }
    
    /**
     * 에러 정보를 Map으로 반환
     */
    public Map<String, Object> toErrorMap() {
        Map<String, Object> errorMap = new java.util.HashMap<>();
        errorMap.put("errorCode", errorCode);
        errorMap.put("message", getMessage());
        errorMap.put("model", model);
        errorMap.put("provider", provider);
        errorMap.put("requestId", requestId);
        errorMap.put("timestamp", timestamp);
        errorMap.put("severity", severity);
        errorMap.put("retryable", retryable);
        
        if (!context.isEmpty()) {
            errorMap.put("context", context);
        }
        
        if (getCause() != null) {
            errorMap.put("rootCause", getCause().getClass().getSimpleName());
            errorMap.put("rootCauseMessage", getCause().getMessage());
        }
        
        return errorMap;
    }
    
    @Override
    public String toString() {
        return String.format("LlmException{errorCode='%s', model='%s', provider='%s', message='%s', severity=%s, retryable=%s}", 
            errorCode, model, provider, getMessage(), severity, retryable);
    }
}

/**
 * 모델을 찾을 수 없는 경우의 예외
 */
class ModelNotFoundException extends LlmException {
    public ModelNotFoundException(String modelName) {
        super(LlmException.builder()
            .errorCode("MODEL_NOT_FOUND")
            .message("Model not found: " + modelName)
            .model(modelName)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false));
    }
}

/**
 * 인증 실패 예외
 */
class AuthenticationException extends LlmException {
    public AuthenticationException(String message, String provider) {
        super(LlmException.builder()
            .errorCode("AUTHENTICATION_FAILED")
            .message(message)
            .provider(provider)
            .severity(ErrorSeverity.HIGH)
            .retryable(false));
    }
}

/**
 * 권한 부족 예외
 */
class AuthorizationException extends LlmException {
    public AuthorizationException(String message, String userId, String model) {
        super(LlmException.builder()
            .errorCode("AUTHORIZATION_FAILED")
            .message(message)
            .model(model)
            .severity(ErrorSeverity.HIGH)
            .retryable(false)
            .context(Map.of("userId", userId)));
    }
}

/**
 * 레이트 리미트 초과 예외
 */
class RateLimitExceededException extends LlmException {
    public RateLimitExceededException(String userId, String model, int currentRate, int limitRate) {
        super(LlmException.builder()
            .errorCode("RATE_LIMIT_EXCEEDED")
            .message(String.format("Rate limit exceeded for user %s on model %s: %d/%d", 
                userId, model, currentRate, limitRate))
            .model(model)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .context(Map.of(
                "userId", userId,
                "currentRate", currentRate,
                "limitRate", limitRate
            )));
    }
}

/**
 * 토큰 제한 초과 예외
 */
class TokenLimitExceededException extends LlmException {
    public TokenLimitExceededException(String model, int requestedTokens, int maxTokens) {
        super(LlmException.builder()
            .errorCode("TOKEN_LIMIT_EXCEEDED")
            .message(String.format("Token limit exceeded for model %s: requested %d, max %d", 
                model, requestedTokens, maxTokens))
            .model(model)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .context(Map.of(
                "requestedTokens", requestedTokens,
                "maxTokens", maxTokens
            )));
    }
}

/**
 * 프로바이더 API 오류 예외
 */
class ProviderApiException extends LlmException {
    public ProviderApiException(String provider, String model, int statusCode, String apiMessage) {
        super(LlmException.builder()
            .errorCode("PROVIDER_API_ERROR")
            .message(String.format("Provider API error: %s", apiMessage))
            .model(model)
            .provider(provider)
            .severity(ErrorSeverity.HIGH)
            .retryable(statusCode >= 500) // 5xx 에러는 재시도 가능
            .context(Map.of(
                "statusCode", statusCode,
                "apiMessage", apiMessage
            )));
    }
}

/**
 * 네트워크 타임아웃 예외
 */
class NetworkTimeoutException extends LlmException {
    public NetworkTimeoutException(String provider, String model, long timeoutMs) {
        super(LlmException.builder()
            .errorCode("NETWORK_TIMEOUT")
            .message(String.format("Network timeout after %d ms", timeoutMs))
            .model(model)
            .provider(provider)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .context(Map.of("timeoutMs", timeoutMs)));
    }
}

/**
 * 회로 차단기 오픈 예외
 */
class CircuitBreakerOpenException extends LlmException {
    public CircuitBreakerOpenException(String model, String provider) {
        super(LlmException.builder()
            .errorCode("CIRCUIT_BREAKER_OPEN")
            .message("Circuit breaker is open for the service")
            .model(model)
            .provider(provider)
            .severity(ErrorSeverity.HIGH)
            .retryable(true));
    }
}

/**
 * 큐 용량 초과 예외
 */
class QueueCapacityExceededException extends LlmException {
    public QueueCapacityExceededException(String model, int queueSize, int maxQueueSize) {
        super(LlmException.builder()
            .errorCode("QUEUE_CAPACITY_EXCEEDED")
            .message(String.format("Queue capacity exceeded: %d/%d", queueSize, maxQueueSize))
            .model(model)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .context(Map.of(
                "queueSize", queueSize,
                "maxQueueSize", maxQueueSize
            )));
    }
}

/**
 * 잘못된 요청 형식 예외
 */
class InvalidRequestFormatException extends LlmException {
    public InvalidRequestFormatException(String message, String field) {
        super(LlmException.builder()
            .errorCode("INVALID_REQUEST_FORMAT")
            .message(message)
            .severity(ErrorSeverity.LOW)
            .retryable(false)
            .context(Map.of("invalidField", field)));
    }
}

/**
 * 모델 사용 불가 예외
 */
class ModelUnavailableException extends LlmException {
    public ModelUnavailableException(String model, String provider, String reason) {
        super(LlmException.builder()
            .errorCode("MODEL_UNAVAILABLE")
            .message(String.format("Model %s is currently unavailable: %s", model, reason))
            .model(model)
            .provider(provider)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .context(Map.of("reason", reason)));
    }
}

/**
 * 콘텐츠 필터링 예외
 */
class ContentFilteredException extends LlmException {
    public ContentFilteredException(String model, String filterType, String reason) {
        super(LlmException.builder()
            .errorCode("CONTENT_FILTERED")
            .message(String.format("Content was filtered: %s", reason))
            .model(model)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .context(Map.of(
                "filterType", filterType,
                "reason", reason
            )));
    }
}

/**
 * 설정 오류 예외
 */
class ConfigurationException extends LlmException {
    public ConfigurationException(String message, String configKey) {
        super(LlmException.builder()
            .errorCode("CONFIGURATION_ERROR")
            .message(message)
            .severity(ErrorSeverity.HIGH)
            .retryable(false)
            .context(Map.of("configKey", configKey)));
    }
}

/**
 * 캐시 오류 예외
 */
class CacheException extends LlmException {
    public CacheException(String message, String operation, Throwable cause) {
        super(LlmException.builder()
            .errorCode("CACHE_ERROR")
            .message(message)
            .cause(cause)
            .severity(ErrorSeverity.LOW)
            .retryable(true)
            .context(Map.of("operation", operation)));
    }
}

/**
 * 데이터베이스 오류 예외
 */
class DatabaseException extends LlmException {
    public DatabaseException(String message, String operation, Throwable cause) {
        super(LlmException.builder()
            .errorCode("DATABASE_ERROR")
            .message(message)
            .cause(cause)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .context(Map.of("operation", operation)));
    }
}