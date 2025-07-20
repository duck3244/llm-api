// VllmHealthChecker.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VllmHealthChecker {
    
    private final VllmConfigProperties vllmConfig;
    private final RestTemplate restTemplate;
    private final Map<String, HealthStatus> healthCache = new ConcurrentHashMap<>();
    
    /**
     * 단일 서버 헬스 체크
     */
    public CompletableFuture<HealthStatus> checkServerHealth(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return HealthStatus.unknown(serverName, "Server configuration not found");
            }
            
            HealthStatus status = performHealthCheck(serverConfig);
            healthCache.put(serverName, status);
            return status;
        });
    }
    
    /**
     * 모든 서버 헬스 체크
     */
    public CompletableFuture<Map<String, HealthStatus>> checkAllServersHealth() {
        List<CompletableFuture<HealthStatus>> futures = vllmConfig.getServers().stream()
            .map(server -> checkServerHealth(server.getName()))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, HealthStatus> results = new HashMap<>();
                futures.forEach(future -> {
                    HealthStatus status = future.join();
                    results.put(status.getServerName(), status);
                });
                return results;
            });
    }
    
    /**
     * 포트 연결 가능 여부 확인
     */
    public boolean isPortOpen(String host, int port) {
        return isPortOpen(host, port, 1000);
    }
    
    public boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 서버 메트릭 조회
     */
    public CompletableFuture<ServerMetrics> getServerMetrics(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return ServerMetrics.unavailable(serverName);
            }
            
            try {
                String metricsUrl = String.format("http://%s:%d/metrics", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                ResponseEntity<String> response = restTemplate.getForEntity(metricsUrl, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseMetrics(serverName, response.getBody());
                } else {
                    return ServerMetrics.error(serverName, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.debug("Failed to get metrics for server: {}", serverName, e);
                return ServerMetrics.error(serverName, e.getMessage());
            }
        });
    }
    
    /**
     * 모델 정보 조회
     */
    public CompletableFuture<ModelInfo> getModelInfo(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return ModelInfo.unavailable(serverName);
            }
            
            try {
                String modelsUrl = String.format("http://%s:%d/v1/models", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                ResponseEntity<Map> response = restTemplate.getForEntity(modelsUrl, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseModelInfo(serverName, response.getBody());
                } else {
                    return ModelInfo.error(serverName, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.debug("Failed to get model info for server: {}", serverName, e);
                return ModelInfo.error(serverName, e.getMessage());
            }
        });
    }
    
    /**
     * 캐시된 헬스 상태 조회
     */
    public HealthStatus getCachedHealthStatus(String serverName) {
        return healthCache.getOrDefault(serverName, HealthStatus.unknown(serverName, "No health check performed"));
    }
    
    /**
     * 모든 캐시된 헬스 상태 조회
     */
    public Map<String, HealthStatus> getAllCachedHealthStatus() {
        return new HashMap<>(healthCache);
    }
    
    /**
     * 헬스 체크 캐시 클리어
     */
    public void clearHealthCache() {
        healthCache.clear();
    }
    
    /**
     * 특정 서버 헬스 캐시 클리어
     */
    public void clearHealthCache(String serverName) {
        healthCache.remove(serverName);
    }
    
    private HealthStatus performHealthCheck(VllmConfigProperties.VllmServerConfig serverConfig) {
        String serverName = serverConfig.getName();
        String host = serverConfig.getHost();
        Integer port = serverConfig.getPort();
        
        LocalDateTime checkTime = LocalDateTime.now();
        
        // 1. 포트 연결 확인
        if (!isPortOpen(host, port, 2000)) {
            return HealthStatus.down(serverName, "Port not accessible", checkTime);
        }
        
        // 2. Health endpoint 확인
        try {
            String healthUrl = String.format("http://%s:%d/health", host, port);
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return HealthStatus.up(serverName, "Server is healthy", checkTime);
            } else {
                return HealthStatus.degraded(serverName, 
                    "Health endpoint returned: " + response.getStatusCode(), checkTime);
            }
            
        } catch (Exception e) {
            // 3. vLLM 서버는 /health 엔드포인트가 없을 수 있으므로 /v1/models로 확인
            try {
                String modelsUrl = String.format("http://%s:%d/v1/models", host, port);
                ResponseEntity<String> response = restTemplate.getForEntity(modelsUrl, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return HealthStatus.up(serverName, "Server is responding to API calls", checkTime);
                } else {
                    return HealthStatus.degraded(serverName, 
                        "API endpoint returned: " + response.getStatusCode(), checkTime);
                }
                
            } catch (Exception apiException) {
                return HealthStatus.down(serverName, 
                    "Neither health nor API endpoints responding: " + apiException.getMessage(), checkTime);
            }
        }
    }
    
    private ServerMetrics parseMetrics(String serverName, String metricsText) {
        // Prometheus 메트릭 파싱 (간단한 구현)
        Map<String, Double> metrics = new HashMap<>();
        
        if (metricsText != null) {
            String[] lines = metricsText.split("\n");
            for (String line : lines) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try {
                        String metricName = parts[0];
                        Double value = Double.parseDouble(parts[1]);
                        metrics.put(metricName, value);
                    } catch (NumberFormatException e) {
                        // 파싱 실패한 메트릭은 무시
                    }
                }
            }
        }
        
        return ServerMetrics.builder()
            .serverName(serverName)
            .available(true)
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private ModelInfo parseModelInfo(String serverName, Map<String, Object> responseBody) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
        
        if (data != null && !data.isEmpty()) {
            Map<String, Object> model = data.get(0);
            String modelId = (String) model.get("id");
            String object = (String) model.get("object");
            Long created = model.get("created") != null ? ((Number) model.get("created")).longValue() : null;
            String ownedBy = (String) model.get("owned_by");
            
            return ModelInfo.builder()
                .serverName(serverName)
                .available(true)
                .modelId(modelId)
                .object(object)
                .created(created)
                .ownedBy(ownedBy)
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        return ModelInfo.error(serverName, "No model information available");
    }
    
    @lombok.Builder
    @lombok.Data
    public static class HealthStatus {
        private String serverName;
        private String status; // UP, DOWN, DEGRADED, UNKNOWN
        private String message;
        private LocalDateTime timestamp;
        private Duration responseTime;
        
        public static HealthStatus up(String serverName, String message, LocalDateTime timestamp) {
            return HealthStatus.builder()
                .serverName(serverName)
                .status("UP")
                .message(message)
                .timestamp(timestamp)
                .build();
        }
        
        public static HealthStatus down(String serverName, String message, LocalDateTime timestamp) {
            return HealthStatus.builder()
                .serverName(serverName)
                .status("DOWN")
                .message(message)
                .timestamp(timestamp)
                .build();
        }
        
        public static HealthStatus degraded(String serverName, String message, LocalDateTime timestamp) {
            return HealthStatus.builder()
                .serverName(serverName)
                .status("DEGRADED")
                .message(message)
                .timestamp(timestamp)
                .build();
        }
        
        public static HealthStatus unknown(String serverName, String message) {
            return HealthStatus.builder()
                .serverName(serverName)
                .status("UNKNOWN")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        public boolean isHealthy() {
            return "UP".equals(status);
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ServerMetrics {
        private String serverName;
        private Boolean available;
        private Map<String, Double> metrics;
        private LocalDateTime timestamp;
        private String errorMessage;
        
        public static ServerMetrics unavailable(String serverName) {
            return ServerMetrics.builder()
                .serverName(serverName)
                .available(false)
                .errorMessage("Server configuration not found")
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        public static ServerMetrics error(String serverName, String errorMessage) {
            return ServerMetrics.builder()
                .serverName(serverName)
                .available(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        public Double getMetric(String metricName) {
            return metrics != null ? metrics.get(metricName) : null;
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ModelInfo {
        private String serverName;
        private Boolean available;
        private String modelId;
        private String object;
        private Long created;
        private String ownedBy;
        private LocalDateTime timestamp;
        private String errorMessage;
        
        public static ModelInfo unavailable(String serverName) {
            return ModelInfo.builder()
                .serverName(serverName)
                .available(false)
                .errorMessage("Server configuration not found")
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        public static ModelInfo error(String serverName, String errorMessage) {
            return ModelInfo.builder()
                .serverName(serverName)
                .available(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
}
        