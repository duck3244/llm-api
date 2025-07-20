// GlobalExceptionHandler.java
package com.yourcompany.llm.exception;

import com.yourcompany.llm.service.LlmUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final LlmUsageService usageService;
    
    @Value("${llm.error.include-stack-trace:false}")
    private boolean includeStackTrace;
    
    @Value("${llm.error.include-sensitive-info:false}")
    private boolean includeSensitiveInfo;
    
    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;
    
    // ===== LLM 관련 예외 처리 =====
    
    /**
     * LLM 기본 예외 처리
     */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, Object>> handleLlmException(LlmException ex, HttpServletRequest request) {
        log.error("LLM Exception: {}", ex.getMessage(), ex);
        
        // 에러 메트릭 기록
        recordErrorMetrics(ex, request);
        
        // 알럿 발송 (심각도에 따라)
        sendAlertIfNecessary(ex);
        
        Map<String, Object> errorResponse = buildErrorResponse(ex, request);
        HttpStatus status = determineHttpStatus(ex);
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * vLLM 관련 예외 처리
     */
    @ExceptionHandler(VllmException.class)
    public ResponseEntity<Map<String, Object>> handleVllmException(VllmException ex, HttpServletRequest request) {
        log.error("vLLM Exception: {} on server: {}", ex.getMessage(), ex.getServerName(), ex);
        
        // vLLM 특화 메트릭 기록
        recordVllmErrorMetrics(ex, request);
        
        // vLLM 서버 상태 업데이트
        updateVllmServerStatus(ex);
        
        Map<String, Object> errorResponse = buildVllmErrorResponse(ex, request);
        HttpStatus status = determineVllmHttpStatus(ex);
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * 모델을 찾을 수 없는 경우
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleModelNotFoundException(ModelNotFoundException ex, HttpServletRequest request) {
        log.warn("Model not found: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "MODEL_NOT_FOUND",
            "message", ex.getMessage(),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI(),
            "suggestions", List.of(
                "Check if the model name is correct",
                "Verify that the model is enabled",
                "Use /api/llm/models to see available models"
            )
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 인증 실패
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "AUTHENTICATION_FAILED",
            "message", "Authentication required",
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * 권한 부족
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationException(AuthorizationException ex, HttpServletRequest request) {
        log.warn("Authorization failed: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "AUTHORIZATION_FAILED",
            "message", "Insufficient permissions",
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 레이트 리미트 초과
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceededException(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        
        Map<String, Object> context = ex.getContext();
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "RATE_LIMIT_EXCEEDED");
        errorResponse.put("message", "Too many requests");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("retryAfter", 60); // 1분 후 재시도
        
        if (context.containsKey("currentRate") && context.containsKey("limitRate")) {
            errorResponse.put("rateLimit", Map.of(
                "current", context.get("currentRate"),
                "limit", context.get("limitRate"),
                "resetTime", LocalDateTime.now().plusMinutes(1)
            ));
        }
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(errorResponse);
    }
    
    /**
     * 토큰 제한 초과
     */
    @ExceptionHandler(TokenLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTokenLimitExceededException(TokenLimitExceededException ex, HttpServletRequest request) {
        log.warn("Token limit exceeded: {}", ex.getMessage());
        
        Map<String, Object> context = ex.getContext();
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "TOKEN_LIMIT_EXCEEDED");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        
        if (context.containsKey("requestedTokens") && context.containsKey("maxTokens")) {
            errorResponse.put("tokenInfo", Map.of(
                "requested", context.get("requestedTokens"),
                "maximum", context.get("maxTokens"),
                "model", ex.getModel()
            ));
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 프로바이더 API 오류
     */
    @ExceptionHandler(ProviderApiException.class)
    public ResponseEntity<Map<String, Object>> handleProviderApiException(ProviderApiException ex, HttpServletRequest request) {
        log.error("Provider API error: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
            "error", "PROVIDER_API_ERROR",
            "message", "External API error",
            "provider", ex.getProvider(),
            "model", ex.getModel(),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI(),
            "retryable", ex.isRetryable()
        );
        
        // 5xx 에러는 502 Bad Gateway로, 4xx 에러는 그대로 전달
        Map<String, Object> context = ex.getContext();
        if (context.containsKey("statusCode")) {
            int statusCode = (Integer) context.get("statusCode");
            HttpStatus responseStatus = statusCode >= 500 ? 
                HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(statusCode);
            return new ResponseEntity<>(errorResponse, responseStatus);
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }
    
    /**
     * 회로 차단기 오픈
     */
    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitBreakerOpenException(CircuitBreakerOpenException ex, HttpServletRequest request) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "SERVICE_UNAVAILABLE",
            "message", "Service temporarily unavailable",
            "provider", ex.getProvider(),
            "model", ex.getModel(),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI(),
            "retryAfter", 30 // 30초 후 재시도
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "30")
            .body(errorResponse);
    }
    
    // ===== HTTP 및 검증 예외 처리 =====
    
    /**
     * 요청 검증 실패 (MethodArgumentNotValidException)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                "rejectedValue", error.getRejectedValue() != null ? error.getRejectedValue().toString() : "null"
            ))
            .collect(Collectors.toList());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "VALIDATION_FAILED",
            "message", "Request validation failed",
            "fieldErrors", fieldErrors,
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 제약 조건 위반 (ConstraintViolationException)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
            .map(violation -> Map.of(
                "property", violation.getPropertyPath().toString(),
                "message", violation.getMessage(),
                "invalidValue", violation.getInvalidValue() != null ? violation.getInvalidValue().toString() : "null"
            ))
            .collect(Collectors.toList());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "CONSTRAINT_VIOLATION",
            "message", "Constraint validation failed",
            "violations", violations,
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 바인딩 예외 (BindException)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException ex, HttpServletRequest request) {
        log.warn("Binding failed: {}", ex.getMessage());
        
        List<Map<String, String>> fieldErrors = ex.getFieldErrors().stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Binding error",
                "rejectedValue", error.getRejectedValue() != null ? error.getRejectedValue().toString() : "null"
            ))
            .collect(Collectors.toList());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "BINDING_FAILED",
            "message", "Request binding failed",
            "fieldErrors", fieldErrors,
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * HTTP 메시지 읽기 실패
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());
        
        String message = "Invalid JSON format";
        if (ex.getMessage().contains("JSON parse error")) {
            message = "JSON parsing failed";
        } else if (ex.getMessage().contains("Required request body is missing")) {
            message = "Request body is required";
        }
        
        Map<String, Object> errorResponse = Map.of(
            "error", "INVALID_JSON",
            "message", message,
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 지원하지 않는 HTTP 메서드
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "METHOD_NOT_ALLOWED",
            "message", String.format("HTTP method '%s' is not supported for this endpoint", request.getMethod()),
            "supportedMethods", Arrays.asList(ex.getSupportedMethods()),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    /**
     * 지원하지 않는 미디어 타입
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "UNSUPPORTED_MEDIA_TYPE",
            "message", String.format("Media type '%s' is not supported", ex.getContentType()),
            "supportedMediaTypes", ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.toList()),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
    
    /**
     * 누락된 요청 파라미터
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "MISSING_PARAMETER",
            "message", String.format("Required parameter '%s' is missing", ex.getParameterName()),
            "parameterName", ex.getParameterName(),
            "parameterType", ex.getParameterType(),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 타입 불일치
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "TYPE_MISMATCH",
            "message", String.format("Parameter '%s' has invalid type", ex.getName()),
            "parameterName", ex.getName(),
            "expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
            "providedValue", ex.getValue() != null ? ex.getValue().toString() : "null",
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 핸들러를 찾을 수 없음
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler found: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "NOT_FOUND",
            "message", String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
            "method", ex.getHttpMethod(),
            "timestamp", LocalDateTime.now(),
            "path", request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "INTERNAL_SERVER_ERROR");
        errorResponse.put("message", "An unexpected error occurred");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        
        // 개발 환경에서만 상세 정보 포함
        if ("dev".equals(activeProfile) || includeStackTrace) {
            errorResponse.put("exception", ex.getClass().getSimpleName());
            errorResponse.put("details", ex.getMessage());
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private void recordErrorMetrics(LlmException ex, HttpServletRequest request) {
        try {
            // 에러 메트릭을 비동기로 기록
            CompletableFuture.runAsync(() -> {
                Map<String, Object> errorData = Map.of(
                    "errorCode", ex.getErrorCode(),
                    "model", ex.getModel() != null ? ex.getModel() : "unknown",
                    "provider", ex.getProvider() != null ? ex.getProvider() : "unknown",
                    "severity", ex.getSeverity(),
                    "retryable", ex.isRetryable(),
                    "endpoint", request.getRequestURI(),
                    "method", request.getMethod(),
                    "userAgent", request.getHeader("User-Agent"),
                    "clientIp", getClientIpAddress(request)
                );
                
                usageService.recordErrorEvent(errorData);
            });
        } catch (Exception e) {
            log.warn("Failed to record error metrics", e);
        }
    }
    
    private void recordVllmErrorMetrics(VllmException ex, HttpServletRequest request) {
        try {
            CompletableFuture.runAsync(() -> {
                Map<String, Object> vllmErrorData = Map.of(
                    "errorCode", ex.getErrorCode(),
                    "vllmErrorType", ex.getVllmErrorType(),
                    "serverName", ex.getServerName() != null ? ex.getServerName() : "unknown",
                    "port", ex.getPort() != null ? ex.getPort() : 0,
                    "model", ex.getModel() != null ? ex.getModel() : "unknown",
                    "severity", ex.getSeverity(),
                    "endpoint", request.getRequestURI()
                );
                
    private void recordVllmErrorMetrics(VllmException ex, HttpServletRequest request) {
        try {
            CompletableFuture.runAsync(() -> {
                Map<String, Object> vllmErrorData = Map.of(
                    "errorCode", ex.getErrorCode(),
                    "vllmErrorType", ex.getVllmErrorType(),
                    "serverName", ex.getServerName() != null ? ex.getServerName() : "unknown",
                    "port", ex.getPort() != null ? ex.getPort() : 0,
                    "model", ex.getModel() != null ? ex.getModel() : "unknown",
                    "severity", ex.getSeverity(),
                    "endpoint", request.getRequestURI()
                );
                
                usageService.recordVllmErrorEvent(vllmErrorData);
            });
        } catch (Exception e) {
            log.warn("Failed to record vLLM error metrics", e);
        }
    }
    
    private void sendAlertIfNecessary(LlmException ex) {
        if (ex.getSeverity() == LlmException.ErrorSeverity.HIGH || 
            ex.getSeverity() == LlmException.ErrorSeverity.CRITICAL) {
            
            try {
                // 알럿 서비스 호출 (실제 구현 필요)
                CompletableFuture.runAsync(() -> {
                    log.warn("ALERT: {} - {} (Model: {}, Provider: {})", 
                        ex.getSeverity(), ex.getMessage(), ex.getModel(), ex.getProvider());
                    
                    // 실제로는 Slack, 이메일, PagerDuty 등으로 알럿 전송
                    // alertService.sendAlert(ex);
                });
            } catch (Exception e) {
                log.error("Failed to send alert", e);
            }
        }
    }
    
    private void updateVllmServerStatus(VllmException ex) {
        if (ex.getServerName() != null) {
            try {
                // vLLM 서버 상태 업데이트 (실제 구현 필요)
                CompletableFuture.runAsync(() -> {
                    switch (ex.getVllmErrorType()) {
                        case SERVER_START_FAILED:
                        case PROCESS_DIED:
                        case API_UNAVAILABLE:
                            // 서버를 비활성화 상태로 마크
                            log.warn("Marking vLLM server {} as unhealthy due to: {}", 
                                ex.getServerName(), ex.getVllmErrorType());
                            break;
                        case HEALTH_CHECK_FAILED:
                            // 헬스 체크 실패 횟수 증가
                            log.debug("Health check failed for server: {}", ex.getServerName());
                            break;
                        default:
                            // 기타 에러는 로그만 기록
                            break;
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to update vLLM server status", e);
            }
        }
    }
    
    private HttpStatus determineHttpStatus(LlmException ex) {
        return switch (ex.getErrorCode()) {
            case "MODEL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "AUTHENTICATION_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "AUTHORIZATION_FAILED" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "TOKEN_LIMIT_EXCEEDED", "INVALID_REQUEST_FORMAT" -> HttpStatus.BAD_REQUEST;
            case "PROVIDER_API_ERROR" -> HttpStatus.BAD_GATEWAY;
            case "CIRCUIT_BREAKER_OPEN", "MODEL_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "NETWORK_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "CONFIGURATION_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
    
    private HttpStatus determineVllmHttpStatus(VllmException ex) {
        return switch (ex.getVllmErrorType()) {
            case SERVER_START_FAILED, SERVER_STOP_FAILED, PROCESS_DIED -> HttpStatus.SERVICE_UNAVAILABLE;
            case MODEL_LOADING_FAILED, MODEL_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case GPU_OUT_OF_MEMORY, RESOURCE_EXHAUSTED -> HttpStatus.INSUFFICIENT_STORAGE;
            case PORT_ALREADY_IN_USE, CONFIGURATION_ERROR -> HttpStatus.CONFLICT;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NETWORK_ERROR, TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case API_UNAVAILABLE, HEALTH_CHECK_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case QUANTIZATION_ERROR, TOKENIZER_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
    
    private Map<String, Object> buildErrorResponse(LlmException ex, HttpServletRequest request) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        // 기본 에러 정보
        errorResponse.put("error", ex.getErrorCode());
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", ex.getTimestamp());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());
        
        // LLM 관련 정보
        if (ex.getModel() != null) {
            errorResponse.put("model", ex.getModel());
        }
        if (ex.getProvider() != null) {
            errorResponse.put("provider", ex.getProvider());
        }
        if (ex.getRequestId() != null) {
            errorResponse.put("requestId", ex.getRequestId());
        }
        
        // 재시도 가능 여부
        errorResponse.put("retryable", ex.isRetryable());
        
        // 심각도 (개발 환경에서만)
        if ("dev".equals(activeProfile)) {
            errorResponse.put("severity", ex.getSeverity());
        }
        
        // 컨텍스트 정보 (민감하지 않은 정보만)
        if (!ex.getContext().isEmpty()) {
            Map<String, Object> safeContext = ex.getContext().entrySet().stream()
                .filter(entry -> !isSensitiveKey(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
            
            if (!safeContext.isEmpty()) {
                errorResponse.put("context", safeContext);
            }
        }
        
        // 근본 원인 (개발 환경이고 스택 트레이스 포함 설정인 경우)
        if (includeStackTrace && ex.getCause() != null) {
            errorResponse.put("rootCause", Map.of(
                "type", ex.getCause().getClass().getSimpleName(),
                "message", ex.getCause().getMessage()
            ));
        }
        
        // 도움말 정보
        errorResponse.put("help", generateHelpInfo(ex));
        
        return errorResponse;
    }
    
    private Map<String, Object> buildVllmErrorResponse(VllmException ex, HttpServletRequest request) {
        Map<String, Object> errorResponse = buildErrorResponse(ex, request);
        
        // vLLM 특화 정보 추가
        errorResponse.put("vllmErrorType", ex.getVllmErrorType());
        
        if (ex.getServerName() != null) {
            errorResponse.put("serverName", ex.getServerName());
        }
        if (ex.getPort() != null) {
            errorResponse.put("port", ex.getPort());
        }
        if (ex.getProcessId() != null && includeSensitiveInfo) {
            errorResponse.put("processId", ex.getProcessId());
        }
        if (ex.getGpuInfo() != null) {
            errorResponse.put("gpuInfo", ex.getGpuInfo());
        }
        
        // vLLM 특화 도움말
        errorResponse.put("vllmHelp", generateVllmHelpInfo(ex));
        
        return errorResponse;
    }
    
    private boolean isSensitiveKey(String key) {
        List<String> sensitiveKeys = List.of(
            "password", "token", "apiKey", "secret", "key", 
            "credentials", "auth", "sessionId", "userId"
        );
        
        return sensitiveKeys.stream()
            .anyMatch(sensitiveKey -> key.toLowerCase().contains(sensitiveKey.toLowerCase()));
    }
    
    private Map<String, Object> generateHelpInfo(LlmException ex) {
        Map<String, Object> help = new HashMap<>();
        
        switch (ex.getErrorCode()) {
            case "MODEL_NOT_FOUND":
                help.put("suggestion", "Use /api/llm/models to see available models");
                help.put("documentation", "/docs/models.md");
                break;
                
            case "RATE_LIMIT_EXCEEDED":
                help.put("suggestion", "Reduce request frequency or upgrade your plan");
                help.put("retryAfter", "60 seconds");
                break;
                
            case "TOKEN_LIMIT_EXCEEDED":
                help.put("suggestion", "Reduce input text length or use a model with higher token limit");
                help.put("documentation", "/docs/token-limits.md");
                break;
                
            case "PROVIDER_API_ERROR":
                if (ex.isRetryable()) {
                    help.put("suggestion", "This is a temporary issue. Please retry in a few moments");
                } else {
                    help.put("suggestion", "Check your API configuration and credentials");
                }
                break;
                
            case "CIRCUIT_BREAKER_OPEN":
                help.put("suggestion", "Service is temporarily unavailable. Please try again later");
                help.put("retryAfter", "30 seconds");
                break;
                
            default:
                help.put("suggestion", "Please check the documentation or contact support");
                help.put("support", "/docs/troubleshooting.md");
                break;
        }
        
        return help;
    }
    
    private Map<String, Object> generateVllmHelpInfo(VllmException ex) {
        Map<String, Object> help = new HashMap<>();
        
        switch (ex.getVllmErrorType()) {
            case SERVER_START_FAILED:
                help.put("suggestion", "Check server configuration and available resources");
                help.put("troubleshooting", List.of(
                    "Verify GPU availability",
                    "Check port availability",
                    "Review server logs",
                    "Validate model path"
                ));
                break;
                
            case GPU_OUT_OF_MEMORY:
                help.put("suggestion", "Reduce model size or GPU memory utilization");
                help.put("troubleshooting", List.of(
                    "Use model quantization",
                    "Reduce max_model_len",
                    "Lower gpu_memory_utilization",
                    "Try tensor parallelism"
                ));
                break;
                
            case MODEL_LOADING_FAILED:
                help.put("suggestion", "Verify model compatibility and availability");
                help.put("troubleshooting", List.of(
                    "Check model format",
                    "Verify model path",
                    "Review model architecture compatibility",
                    "Check disk space"
                ));
                break;
                
            case PORT_ALREADY_IN_USE:
                help.put("suggestion", "Use a different port or stop the conflicting process");
                help.put("troubleshooting", List.of(
                    "Check running processes on the port",
                    "Use netstat to find conflicting services",
                    "Configure alternative port"
                ));
                break;
                
            case HEALTH_CHECK_FAILED:
                help.put("suggestion", "Check server health and network connectivity");
                help.put("troubleshooting", List.of(
                    "Verify server is running",
                    "Check network connectivity",
                    "Review server logs",
                    "Test API endpoints manually"
                ));
                break;
                
            default:
                help.put("suggestion", "Check vLLM server logs for detailed error information");
                help.put("documentation", "/docs/vllm-troubleshooting.md");
                break;
        }
        
        return help;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}