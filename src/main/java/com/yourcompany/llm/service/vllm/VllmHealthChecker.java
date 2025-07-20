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
import java.time.LocalDateTime;
import java.util.HashMap;
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
    
    public CompletableFuture<Map<String, HealthStatus>> checkAllServersHealth() {
        Map<String, HealthStatus> results = new HashMap<>();
        
        vllmConfig.getEnabledServers().forEach(server -> {
            HealthStatus status = checkServerHealth(server.getName()).join();
            results.put(server.getName(), status);
        });
        
        return CompletableFuture.completedFuture(results);
    }
    
    public HealthStatus getCachedHealthStatus(String serverName) {
        return healthCache.getOrDefault(serverName, 
            HealthStatus.unknown(serverName, "No health check performed"));
    }
    
    public Map<String, HealthStatus> getAllCachedHealthStatus() {
        return new HashMap<>(healthCache);
    }
    
    public void clearHealthCache() {
        healthCache.clear();
    }
    
    public void clearHealthCache(String serverName) {
        healthCache.remove(serverName);
    }
    
    private HealthStatus performHealthCheck(VllmConfigProperties.VllmServerConfig serverConfig) {
        String serverName = serverConfig.getName();
        String host = serverConfig.getHost();
        Integer port = serverConfig.getPort();
        LocalDateTime checkTime = LocalDateTime.now();
        
        // 포트 연결 확인
        if (!isPortOpen(host, port, 2000)) {
            return HealthStatus.down(serverName, "Port not accessible", checkTime);
        }
        
        // API 엔드포인트 확인
        try {
            String healthUrl = String.format("http://%s:%d/v1/models", host, port);
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return HealthStatus.up(serverName, "Llama 3.2 server is healthy", checkTime);
            } else {
                return HealthStatus.degraded(serverName, 
                    "API endpoint returned: " + response.getStatusCode(), checkTime);
            }
        } catch (Exception e) {
            return HealthStatus.down(serverName, 
                "API endpoint not responding: " + e.getMessage(), checkTime);
        }
    }
    
    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class HealthStatus {
        private String serverName;
        private String status; // UP, DOWN, DEGRADED, UNKNOWN
        private String message;
        private LocalDateTime timestamp;
        
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
}