// LogEntity.java
package com.yourcompany.llm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "api_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_api_key_hash", columnList = "apiKeyHash"),
    @Index(name = "idx_model", columnList = "model"),
    @Index(name = "idx_provider", columnList = "provider"),
    @Index(name = "idx_log_level", columnList = "logLevel"),
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_status_code", columnList = "statusCode"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_client_ip", columnList = "clientIp"),
    @Index(name = "idx_session_id", columnList = "sessionId"),
    @Index(name = "idx_correlation_id", columnList = "correlationId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 로그 고유 ID (UUID)
     */
    @Column(unique = true, nullable = false, length = 36)
    private String logId;
    
    /**
     * 상관관계 ID (요청 추적용)
     */
    @Column(length = 36)
    private String correlationId;
    
    /**
     * 세션 ID
     */
    @Column(length = 36)
    private String sessionId;
    
    /**
     * 사용자 ID
     */
    @Column(length = 100)
    private String userId;
    
    /**
     * API 키 해시
     */
    @Column(length = 64)
    private String apiKeyHash;
    
    /**
     * 로그 레벨
     */
    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private LogLevel logLevel;
    
    /**
     * 이벤트 타입
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    
    /**
     * 컴포넌트명
     */
    @Column(length = 100)
    private String component;
    
    /**
     * 메시지
     */
    @Column(columnDefinition = "TEXT")
    private String message;
    
    /**
     * 상세 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * HTTP 메서드
     */
    @Column(length = 10)
    private String httpMethod;
    
    /**
     * 요청 URI
     */
    @Column(length = 500)
    private String requestUri;
    
    /**
     * HTTP 상태 코드
     */
    private Integer statusCode;
    
    /**
     * 요청 헤더 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String requestHeaders;
    
    /**
     * 응답 헤더 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String responseHeaders;
    
    /**
     * 요청 본문 (큰 데이터는 해시만 저장)
     */
    @Column(columnDefinition = "TEXT")
    private String requestBody;
    
    /**
     * 응답 본문 (큰 데이터는 해시만 저장)
     */
    @Column(columnDefinition = "TEXT")
    private String responseBody;
    
    /**
     * 요청 크기 (바이트)
     */
    private Long requestSize;
    
    /**
     * 응답 크기 (바이트)
     */
    private Long responseSize;
    
    /**
     * 모델명
     */
    @Column(length = 100)
    private String model;
    
    /**
     * 프로바이더명
     */
    @Column(length = 50)
    private String provider;
    
    /**
     * 처리 시간 (밀리초)
     */
    private Long processingTimeMs;
    
    /**
     * 외부 API 호출 시간 (밀리초)
     */
    private Long externalApiTimeMs;
    
    /**
     * 큐 대기 시간 (밀리초)
     */
    private Long queueTimeMs;
    
    /**
     * 토큰 수
     */
    private Integer tokenCount;
    
    /**
     * 예상 비용
     */
    @Column(precision = 10, scale = 6)
    private Double estimatedCost;
    
    /**
     * 통화
     */
    @Column(length = 3)
    private String currency;
    
    /**
     * 클라이언트 IP 주소
     */
    @Column(length = 45)
    private String clientIp;
    
    /**
     * 사용자 에이전트
     */
    @Column(length = 500)
    private String userAgent;
    
    /**
     * 리퍼러
     */
    @Column(length = 500)
    private String referer;
    
    /**
     * 지역 정보
     */
    @Column(length = 10)
    private String region;
    
    /**
     * 국가 코드
     */
    @Column(length = 2)
    private String countryCode;
    
    /**
     * 도시
     */
    @Column(length = 100)
    private String city;
    
    /**
     * ISP 정보
     */
    @Column(length = 100)
    private String isp;
    
    /**
     * 에러 코드
     */
    @Column(length = 50)
    private String errorCode;
    
    /**
     * 에러 메시지
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 스택 트레이스
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    /**
     * 예외 타입
     */
    @Column(length = 200)
    private String exceptionType;
    
    /**
     * 캐시 히트 여부
     */
    private Boolean cacheHit;
    
    /**
     * 캐시 키
     */
    @Column(length = 100)
    private String cacheKey;
    
    /**
     * 재시도 횟수
     */
    private Integer retryCount;
    
    /**
     * 회로 차단기 상태
     */
    @Column(length = 20)
    private String circuitBreakerState;
    
    /**
     * 로드 밸런서 노드
     */
    @Column(length = 50)
    private String loadBalancerNode;
    
    /**
     * 서버 인스턴스 ID
     */
    @Column(length = 50)
    private String serverInstanceId;
    
    /**
     * 스레드 이름
     */
    @Column(length = 100)
    private String threadName;
    
    /**
     * 메모리 사용량 (MB)
     */
    private Double memoryUsageMb;
    
    /**
     * CPU 사용률 (%)
     */
    private Double cpuUsagePercent;
    
    /**
     * 액션 수행 여부
     */
    private Boolean actionTaken;
    
    /**
     * 액션 상세 정보
     */
    @Column(columnDefinition = "TEXT")
    private String actionDetails;
    
    /**
     * 알럿 발생 여부
     */
    private Boolean alertTriggered;
    
    /**
     * 알럿 ID
     */
    @Column(length = 36)
    private String alertId;
    
    /**
     * 비즈니스 컨텍스트 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String businessContext;
    
    /**
     * 커스텀 속성 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String customAttributes;
    
    /**
     * 태그 (콤마 구분)
     */
    @Column(length = 500)
    private String tags;
    
    /**
     * 추가 메타데이터 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * 로그 생성 시간
     */
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * 로그 레벨 열거형
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
    
    /**
     * 이벤트 타입 열거형
     */
    public enum EventType {
        // API 이벤트
        API_REQUEST, API_RESPONSE, API_ERROR,
        
        // 인증 이벤트
        AUTH_SUCCESS, AUTH_FAILURE, AUTH_TOKEN_EXPIRED, AUTH_RATE_LIMIT,
        
        // 모델 이벤트
        MODEL_REQUEST, MODEL_RESPONSE, MODEL_ERROR, MODEL_TIMEOUT,
        
        // 시스템 이벤트
        SYSTEM_START, SYSTEM_STOP, SYSTEM_ERROR, SYSTEM_HEALTH_CHECK,
        
        // 캐시 이벤트
        CACHE_HIT, CACHE_MISS, CACHE_EVICTION, CACHE_ERROR,
        
        // 데이터베이스 이벤트
        DB_QUERY, DB_ERROR, DB_SLOW_QUERY, DB_CONNECTION_ERROR,
        
        // 외부 서비스 이벤트
        EXTERNAL_API_CALL, EXTERNAL_API_ERROR, EXTERNAL_API_TIMEOUT,
        
        // 보안 이벤트
        SECURITY_VIOLATION, SUSPICIOUS_ACTIVITY, UNAUTHORIZED_ACCESS,
        
        // 성능 이벤트
        PERFORMANCE_DEGRADATION, HIGH_MEMORY_USAGE, HIGH_CPU_USAGE,
        
        // 비즈니스 이벤트
        USER_SIGNUP, USER_LOGIN, USER_LOGOUT, SUBSCRIPTION_CHANGE,
        
        // 알럿 이벤트
        ALERT_TRIGGERED, ALERT_RESOLVED, THRESHOLD_EXCEEDED,
        
        // 기타
        OTHER
    }
    
    // 유틸리티 메서드들
    
    /**
     * 에러 로그인지 확인
     */
    public boolean isError() {
        return LogLevel.ERROR.equals(logLevel) || LogLevel.FATAL.equals(logLevel);
    }
    
    /**
     * 경고 로그인지 확인
     */
    public boolean isWarning() {
        return LogLevel.WARN.equals(logLevel);
    }
    
    /**
     * 정보 로그인지 확인
     */
    public boolean isInfo() {
        return LogLevel.INFO.equals(logLevel);
    }
    
    /**
     * 디버그 로그인지 확인
     */
    public boolean isDebug() {
        return LogLevel.DEBUG.equals(logLevel) || LogLevel.TRACE.equals(logLevel);
    }
    
    /**
     * API 이벤트인지 확인
     */
    public boolean isApiEvent() {
        return eventType != null && eventType.name().startsWith("API_");
    }
    
    /**
     * 시스템 이벤트인지 확인
     */
    public boolean isSystemEvent() {
        return eventType != null && eventType.name().startsWith("SYSTEM_");
    }
    
    /**
     * 보안 이벤트인지 확인
     */
    public boolean isSecurityEvent() {
        return eventType != null && eventType.name().startsWith("SECURITY_");
    }
    
    /**
     * 성능 이벤트인지 확인
     */
    public boolean isPerformanceEvent() {
        return eventType != null && eventType.name().startsWith("PERFORMANCE_");
    }
    
    /**
     * 성공적인 요청인지 확인
     */
    public boolean isSuccessfulRequest() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * 클라이언트 오류인지 확인
     */
    public boolean isClientError() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * 서버 오류인지 확인
     */
    public boolean isServerError() {
        return statusCode != null && statusCode >= 500;
    }
    
    /**
     * 느린 요청인지 확인 (임계값: 5초)
     */
    public boolean isSlowRequest() {
        return processingTimeMs != null && processingTimeMs > 5000;
    }
    
    /**
     * 캐시 히트인지 확인
     */
    public boolean isCacheHit() {
        return Boolean.TRUE.equals(cacheHit);
    }
    
    /**
     * 재시도가 발생했는지 확인
     */
    public boolean hasRetries() {
        return retryCount != null && retryCount > 0;
    }
    
    /**
     * 알럿이 발생했는지 확인
     */
    public boolean hasAlert() {
        return Boolean.TRUE.equals(alertTriggered);
    }
    
    /**
     * 태그 리스트로 반환
     */
    public java.util.List<String> getTagsList() {
        if (tags == null || tags.trim().isEmpty()) {
            return java.util.List.of();
        }
        return java.util.Arrays.asList(tags.split(","))
            .stream()
            .map(String::trim)
            .filter(tag -> !tag.isEmpty())
            .toList();
    }
    
    /**
     * 태그 설정 (리스트에서)
     */
    public void setTagsList(java.util.List<String> tagsList) {
        if (tagsList == null || tagsList.isEmpty()) {
            this.tags = null;
        } else {
            this.tags = String.join(",", tagsList);
        }
    }
    
    /**
     * 태그 추가
     */
    public void addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        
        java.util.List<String> currentTags = new java.util.ArrayList<>(getTagsList());
        if (!currentTags.contains(tag.trim())) {
            currentTags.add(tag.trim());
            setTagsList(currentTags);
        }
    }
    
    /**
     * 처리 시간 분류 반환
     */
    public String getResponseTimeCategory() {
        if (processingTimeMs == null) {
            return "UNKNOWN";
        }
        
        if (processingTimeMs < 100) return "FAST";
        if (processingTimeMs < 1000) return "NORMAL";
        if (processingTimeMs < 5000) return "SLOW";
        return "VERY_SLOW";
    }
    
    /**
     * 심각도 레벨 반환
     */
    public String getSeverityLevel() {
        if (isError()) {
            return "HIGH";
        } else if (isWarning() || isSlowRequest()) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * 로그 요약 정보 반환
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", id);
        summary.put("logId", logId);
        summary.put("correlationId", correlationId);
        summary.put("logLevel", logLevel);
        summary.put("eventType", eventType);
        summary.put("message", message);
        summary.put("statusCode", statusCode);
        summary.put("processingTime", processingTimeMs);
        summary.put("model", model);
        summary.put("provider", provider);
        summary.put("userId", userId);
        summary.put("clientIp", clientIp);
        summary.put("timestamp", timestamp);
        
        return summary;
    }
    
    /**
     * 성능 메트릭 반환
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("processingTime", processingTimeMs);
        metrics.put("externalApiTime", externalApiTimeMs);
        metrics.put("queueTime", queueTimeMs);
        metrics.put("requestSize", requestSize);
        metrics.put("responseSize", responseSize);
        metrics.put("memoryUsage", memoryUsageMb);
        metrics.put("cpuUsage", cpuUsagePercent);
        metrics.put("responseTimeCategory", getResponseTimeCategory());
        
        return metrics;
    }
    
    /**
     * 컨텍스트 정보 반환
     */
    public Map<String, Object> getContextInfo() {
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("sessionId", sessionId);
        context.put("userId", userId);
        context.put("model", model);
        context.put("provider", provider);
        context.put("clientIp", clientIp);
        context.put("userAgent", userAgent);
        context.put("region", region);
        context.put("countryCode", countryCode);
        context.put("serverInstanceId", serverInstanceId);
        
        return context;
    }
    
    /**
     * 로그 검색용 키워드 생성
     */
    public String generateSearchKeywords() {
        java.util.List<String> keywords = new java.util.ArrayList<>();
        
        if (userId != null) keywords.add(userId);
        if (model != null) keywords.add(model);
        if (provider != null) keywords.add(provider);
        if (eventType != null) keywords.add(eventType.name().toLowerCase());
        if (component != null) keywords.add(component);
        if (errorCode != null) keywords.add(errorCode);
        if (message != null) {
            // 메시지에서 주요 키워드 추출
            String[] words = message.toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() > 3) {
                    keywords.add(word);
                }
            }
        }
        
        return String.join(" ", keywords);
    }
    
    /**
     * 민감한 정보 마스킹
     */
    public void maskSensitiveData() {
        if (apiKeyHash != null && apiKeyHash.length() > 8) {
            apiKeyHash = apiKeyHash.substring(0, 8) + "***";
        }
        
        if (requestBody != null && requestBody.contains("password")) {
            requestBody = requestBody.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
        }
        
        if (responseBody != null && responseBody.contains("token")) {
            responseBody = responseBody.replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"");
        }
    }
    
    /**
     * 로그 데이터 압축 (오래된 로그용)
     */
    public void compress() {
        // 상세 정보 제거하여 저장 공간 절약
        if (requestBody != null && requestBody.length() > 1000) {
            requestBody = requestBody.substring(0, 1000) + "... [truncated]";
        }
        
        if (responseBody != null && responseBody.length() > 1000) {
            responseBody = responseBody.substring(0, 1000) + "... [truncated]";
        }
        
        if (stackTrace != null && stackTrace.length() > 2000) {
            stackTrace = stackTrace.substring(0, 2000) + "... [truncated]";
        }
        
        // 불필요한 헤더 정보 제거
        requestHeaders = null;
        responseHeaders = null;
        customAttributes = null;
    }
    
    @Override
    public String toString() {
        return String.format("LogEntity{id=%d, level=%s, eventType=%s, message='%s', timestamp=%s}", 
            id, logLevel, eventType, message, timestamp);
    }
}