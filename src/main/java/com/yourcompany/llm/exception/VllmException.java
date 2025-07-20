// VllmException.java
package com.yourcompany.llm.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * vLLM 관련 예외의 기본 클래스
 */
@Getter
public class VllmException extends LlmException {
    
    private final String serverName;
    private final Integer port;
    private final String processId;
    private final VllmErrorType vllmErrorType;
    private final String gpuInfo;
    
    public VllmException(String message, String serverName) {
        super(message);
        this.serverName = serverName;
        this.port = null;
        this.processId = null;
        this.vllmErrorType = VllmErrorType.GENERIC;
        this.gpuInfo = null;
    }
    
    public VllmException(Builder builder) {
        super(LlmException.builder()
            .errorCode(builder.errorCode)
            .message(builder.message)
            .cause(builder.cause)
            .model(builder.model)
            .provider("vllm")
            .requestId(builder.requestId)
            .timestamp(builder.timestamp)
            .context(builder.context)
            .severity(builder.severity)
            .retryable(builder.retryable)
            .build());
        
        this.serverName = builder.serverName;
        this.port = builder.port;
        this.processId = builder.processId;
        this.vllmErrorType = builder.vllmErrorType != null ? builder.vllmErrorType : VllmErrorType.GENERIC;
        this.gpuInfo = builder.gpuInfo;
    }
    
    /**
     * vLLM 에러 타입 열거형
     */
    public enum VllmErrorType {
        GENERIC,                // 일반적인 오류
        SERVER_START_FAILED,    // 서버 시작 실패
        SERVER_STOP_FAILED,     // 서버 중지 실패
        MODEL_LOADING_FAILED,   // 모델 로딩 실패
        GPU_OUT_OF_MEMORY,      // GPU 메모리 부족
        CUDA_ERROR,            // CUDA 관련 오류
        PROCESS_DIED,          // 프로세스 비정상 종료
        PORT_ALREADY_IN_USE,   // 포트 사용 중
        HEALTH_CHECK_FAILED,   // 헬스 체크 실패
        API_UNAVAILABLE,       // API 서버 사용 불가
        QUANTIZATION_ERROR,    // 양자화 오류
        TOKENIZER_ERROR,       // 토크나이저 오류
        CONFIGURATION_ERROR,   // 설정 오류
        PERMISSION_DENIED,     // 권한 거부
        NETWORK_ERROR,         // 네트워크 오류
        TIMEOUT,              // 타임아웃
        RESOURCE_EXHAUSTED,   // 리소스 고갈
        LOAD_BALANCER_ERROR   // 로드 밸런서 오류
    }
    
    /**
     * 빌더 패턴
     */
    public static class Builder {
        private String errorCode = "VLLM_ERROR";
        private String message;
        private Throwable cause;
        private String model;
        private String requestId;
        private LocalDateTime timestamp;
        private Map<String, Object> context = new java.util.HashMap<>();
        private ErrorSeverity severity = ErrorSeverity.MEDIUM;
        private boolean retryable = false;
        private String serverName;
        private Integer port;
        private String processId;
        private VllmErrorType vllmErrorType;
        private String gpuInfo;
        
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
        
        public Builder addContext(String key, Object value) {
            this.context.put(key, value);
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
        
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }
        
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }
        
        public Builder processId(String processId) {
            this.processId = processId;
            return this;
        }
        
        public Builder vllmErrorType(VllmErrorType vllmErrorType) {
            this.vllmErrorType = vllmErrorType;
            return this;
        }
        
        public Builder gpuInfo(String gpuInfo) {
            this.gpuInfo = gpuInfo;
            return this;
        }
        
        public VllmException build() {
            return new VllmException(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * vLLM 에러 정보를 Map으로 반환
     */
    @Override
    public Map<String, Object> toErrorMap() {
        Map<String, Object> errorMap = super.toErrorMap();
        errorMap.put("serverName", serverName);
        errorMap.put("port", port);
        errorMap.put("processId", processId);
        errorMap.put("vllmErrorType", vllmErrorType);
        errorMap.put("gpuInfo", gpuInfo);
        
        return errorMap;
    }
    
    @Override
    public String toString() {
        return String.format("VllmException{serverName='%s', port=%d, vllmErrorType=%s, message='%s'}", 
            serverName, port, vllmErrorType, getMessage());
    }
}

/**
 * vLLM 서버 시작 실패 예외
 */
class VllmServerStartException extends VllmException {
    public VllmServerStartException(String serverName, String model, Integer port, String reason, Throwable cause) {
        super(VllmException.builder()
            .errorCode("VLLM_SERVER_START_FAILED")
            .message(String.format("Failed to start vLLM server '%s': %s", serverName, reason))
            .cause(cause)
            .model(model)
            .serverName(serverName)
            .port(port)
            .vllmErrorType(VllmErrorType.SERVER_START_FAILED)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .addContext("reason", reason));
    }
}

/**
 * vLLM 서버 중지 실패 예외
 */
class VllmServerStopException extends VllmException {
    public VllmServerStopException(String serverName, String processId, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_SERVER_STOP_FAILED")
            .message(String.format("Failed to stop vLLM server '%s': %s", serverName, reason))
            .serverName(serverName)
            .processId(processId)
            .vllmErrorType(VllmErrorType.SERVER_STOP_FAILED)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .addContext("reason", reason));
    }
}

/**
 * 모델 로딩 실패 예외
 */
class VllmModelLoadingException extends VllmException {
    public VllmModelLoadingException(String serverName, String model, String reason, Throwable cause) {
        super(VllmException.builder()
            .errorCode("VLLM_MODEL_LOADING_FAILED")
            .message(String.format("Failed to load model '%s' on server '%s': %s", model, serverName, reason))
            .cause(cause)
            .model(model)
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.MODEL_LOADING_FAILED)
            .severity(ErrorSeverity.HIGH)
            .retryable(false)
            .addContext("reason", reason));
    }
}

/**
 * GPU 메모리 부족 예외
 */
class VllmGpuOutOfMemoryException extends VllmException {
    public VllmGpuOutOfMemoryException(String serverName, String model, String gpuInfo, long requiredMemory, long availableMemory) {
        super(VllmException.builder()
            .errorCode("VLLM_GPU_OUT_OF_MEMORY")
            .message(String.format("GPU out of memory for model '%s': required %d MB, available %d MB", 
                model, requiredMemory / 1024 / 1024, availableMemory / 1024 / 1024))
            .model(model)
            .serverName(serverName)
            .gpuInfo(gpuInfo)
            .vllmErrorType(VllmErrorType.GPU_OUT_OF_MEMORY)
            .severity(ErrorSeverity.HIGH)
            .retryable(false)
            .addContext("requiredMemoryMB", requiredMemory / 1024 / 1024)
            .addContext("availableMemoryMB", availableMemory / 1024 / 1024));
    }
}

/**
 * CUDA 오류 예외
 */
class VllmCudaException extends VllmException {
    public VllmCudaException(String serverName, String model, String cudaError, String gpuInfo) {
        super(VllmException.builder()
            .errorCode("VLLM_CUDA_ERROR")
            .message(String.format("CUDA error on server '%s': %s", serverName, cudaError))
            .model(model)
            .serverName(serverName)
            .gpuInfo(gpuInfo)
            .vllmErrorType(VllmErrorType.CUDA_ERROR)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .addContext("cudaError", cudaError));
    }
}

/**
 * 프로세스 비정상 종료 예외
 */
class VllmProcessDiedException extends VllmException {
    public VllmProcessDiedException(String serverName, String processId, int exitCode, String lastLog) {
        super(VllmException.builder()
            .errorCode("VLLM_PROCESS_DIED")
            .message(String.format("vLLM process died unexpectedly: server '%s', exit code %d", serverName, exitCode))
            .serverName(serverName)
            .processId(processId)
            .vllmErrorType(VllmErrorType.PROCESS_DIED)
            .severity(ErrorSeverity.CRITICAL)
            .retryable(true)
            .addContext("exitCode", exitCode)
            .addContext("lastLog", lastLog));
    }
}

/**
 * 포트 사용 중 예외
 */
class VllmPortInUseException extends VllmException {
    public VllmPortInUseException(String serverName, Integer port, String conflictingProcess) {
        super(VllmException.builder()
            .errorCode("VLLM_PORT_IN_USE")
            .message(String.format("Port %d is already in use for server '%s'", port, serverName))
            .serverName(serverName)
            .port(port)
            .vllmErrorType(VllmErrorType.PORT_ALREADY_IN_USE)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .addContext("conflictingProcess", conflictingProcess));
    }
}

/**
 * 헬스 체크 실패 예외
 */
class VllmHealthCheckException extends VllmException {
    public VllmHealthCheckException(String serverName, Integer port, String healthEndpoint, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_HEALTH_CHECK_FAILED")
            .message(String.format("Health check failed for server '%s': %s", serverName, reason))
            .serverName(serverName)
            .port(port)
            .vllmErrorType(VllmErrorType.HEALTH_CHECK_FAILED)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .addContext("healthEndpoint", healthEndpoint)
            .addContext("reason", reason));
    }
}

/**
 * API 서버 사용 불가 예외
 */
class VllmApiUnavailableException extends VllmException {
    public VllmApiUnavailableException(String serverName, Integer port, String endpoint, int statusCode, String response) {
        super(VllmException.builder()
            .errorCode("VLLM_API_UNAVAILABLE")
            .message(String.format("vLLM API unavailable on server '%s:%d%s'", serverName, port, endpoint))
            .serverName(serverName)
            .port(port)
            .vllmErrorType(VllmErrorType.API_UNAVAILABLE)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .addContext("endpoint", endpoint)
            .addContext("statusCode", statusCode)
            .addContext("response", response));
    }
}

/**
 * 양자화 오류 예외
 */
class VllmQuantizationException extends VllmException {
    public VllmQuantizationException(String serverName, String model, String quantizationType, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_QUANTIZATION_ERROR")
            .message(String.format("Quantization error for model '%s' with type '%s': %s", 
                model, quantizationType, reason))
            .model(model)
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.QUANTIZATION_ERROR)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .addContext("quantizationType", quantizationType)
            .addContext("reason", reason));
    }
}

/**
 * 토크나이저 오류 예외
 */
class VllmTokenizerException extends VllmException {
    public VllmTokenizerException(String serverName, String model, String tokenizerPath, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_TOKENIZER_ERROR")
            .message(String.format("Tokenizer error for model '%s': %s", model, reason))
            .model(model)
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.TOKENIZER_ERROR)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .addContext("tokenizerPath", tokenizerPath)
            .addContext("reason", reason));
    }
}

/**
 * vLLM 설정 오류 예외
 */
class VllmConfigurationException extends VllmException {
    public VllmConfigurationException(String serverName, String configKey, String configValue, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_CONFIGURATION_ERROR")
            .message(String.format("Configuration error for server '%s': %s", serverName, reason))
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.CONFIGURATION_ERROR)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .addContext("configKey", configKey)
            .addContext("configValue", configValue)
            .addContext("reason", reason));
    }
}

/**
 * 권한 거부 예외
 */
class VllmPermissionDeniedException extends VllmException {
    public VllmPermissionDeniedException(String serverName, String operation, String resource, String user) {
        super(VllmException.builder()
            .errorCode("VLLM_PERMISSION_DENIED")
            .message(String.format("Permission denied for operation '%s' on server '%s'", operation, serverName))
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.PERMISSION_DENIED)
            .severity(ErrorSeverity.HIGH)
            .retryable(false)
            .addContext("operation", operation)
            .addContext("resource", resource)
            .addContext("user", user));
    }
}

/**
 * vLLM 네트워크 오류 예외
 */
class VllmNetworkException extends VllmException {
    public VllmNetworkException(String serverName, Integer port, String operation, Throwable cause) {
        super(VllmException.builder()
            .errorCode("VLLM_NETWORK_ERROR")
            .message(String.format("Network error during '%s' on server '%s:%d'", operation, serverName, port))
            .cause(cause)
            .serverName(serverName)
            .port(port)
            .vllmErrorType(VllmErrorType.NETWORK_ERROR)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .addContext("operation", operation));
    }
}

/**
 * vLLM 타임아웃 예외
 */
class VllmTimeoutException extends VllmException {
    public VllmTimeoutException(String serverName, String operation, long timeoutMs, String details) {
        super(VllmException.builder()
            .errorCode("VLLM_TIMEOUT")
            .message(String.format("Operation '%s' timed out after %d ms on server '%s'", 
                operation, timeoutMs, serverName))
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.TIMEOUT)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .addContext("operation", operation)
            .addContext("timeoutMs", timeoutMs)
            .addContext("details", details));
    }
}

/**
 * 리소스 고갈 예외
 */
class VllmResourceExhaustedException extends VllmException {
    public VllmResourceExhaustedException(String serverName, String resourceType, String currentUsage, String limit) {
        super(VllmException.builder()
            .errorCode("VLLM_RESOURCE_EXHAUSTED")
            .message(String.format("Resource '%s' exhausted on server '%s': %s/%s", 
                resourceType, serverName, currentUsage, limit))
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.RESOURCE_EXHAUSTED)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .addContext("resourceType", resourceType)
            .addContext("currentUsage", currentUsage)
            .addContext("limit", limit));
    }
}

/**
 * 로드 밸런서 오류 예외
 */
class VllmLoadBalancerException extends VllmException {
    public VllmLoadBalancerException(String operation, String strategy, String reason, int availableServers) {
        super(VllmException.builder()
            .errorCode("VLLM_LOAD_BALANCER_ERROR")
            .message(String.format("Load balancer error during '%s': %s", operation, reason))
            .vllmErrorType(VllmErrorType.LOAD_BALANCER_ERROR)
            .severity(ErrorSeverity.HIGH)
            .retryable(true)
            .addContext("operation", operation)
            .addContext("strategy", strategy)
            .addContext("reason", reason)
            .addContext("availableServers", availableServers));
    }
}

/**
 * vLLM 모니터링 예외
 */
class VllmMonitoringException extends VllmException {
    public VllmMonitoringException(String serverName, String metricType, String reason, Throwable cause) {
        super(VllmException.builder()
            .errorCode("VLLM_MONITORING_ERROR")
            .message(String.format("Monitoring error for metric '%s' on server '%s': %s", 
                metricType, serverName, reason))
            .cause(cause)
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.GENERIC)
            .severity(ErrorSeverity.LOW)
            .retryable(true)
            .addContext("metricType", metricType)
            .addContext("reason", reason));
    }
}

/**
 * vLLM 모델 호환성 예외
 */
class VllmModelCompatibilityException extends VllmException {
    public VllmModelCompatibilityException(String serverName, String model, String architecture, String supportedArchs) {
        super(VllmException.builder()
            .errorCode("VLLM_MODEL_COMPATIBILITY_ERROR")
            .message(String.format("Model '%s' with architecture '%s' is not compatible with vLLM", 
                model, architecture))
            .model(model)
            .serverName(serverName)
            .vllmErrorType(VllmErrorType.MODEL_LOADING_FAILED)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(false)
            .addContext("architecture", architecture)
            .addContext("supportedArchitectures", supportedArchs));
    }
}

/**
 * vLLM 스케일링 예외
 */
class VllmScalingException extends VllmException {
    public VllmScalingException(String operation, String targetState, String currentState, String reason) {
        super(VllmException.builder()
            .errorCode("VLLM_SCALING_ERROR")
            .message(String.format("Scaling operation '%s' failed: %s", operation, reason))
            .vllmErrorType(VllmErrorType.GENERIC)
            .severity(ErrorSeverity.MEDIUM)
            .retryable(true)
            .addContext("operation", operation)
            .addContext("targetState", targetState)
            .addContext("currentState", currentState)
            .addContext("reason", reason));
    }
}