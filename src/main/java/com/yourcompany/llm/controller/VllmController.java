// VllmController.java
package com.yourcompany.llm.controller;

import com.yourcompany.llm.service.vllm.*;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/vllm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VllmController {
    
    private final VllmProcessManager processManager;
    private final VllmHealthChecker healthChecker;
    private final VllmApiClient apiClient;
    private final VllmLoadBalancer loadBalancer;
    private final VllmMonitoringService monitoringService;
    
    // ===== 프로세스 관리 =====
    
    @PostMapping("/servers/{serverName}/start")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> startServer(@PathVariable String serverName) {
        return processManager.startServer(serverName)
            .thenApply(success -> {
                Map<String, Object> response = Map.of(
                    "serverName", serverName,
                    "started", success,
                    "message", success ? "Llama 3.2 server started successfully" : "Failed to start server",
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            });
    }
    
    @PostMapping("/servers/{serverName}/stop")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> stopServer(@PathVariable String serverName) {
        return processManager.stopServer(serverName)
            .thenApply(stopped -> {
                Map<String, Object> response = Map.of(
                    "serverName", serverName,
                    "stopped", stopped,
                    "message", stopped ? "Server stopped successfully" : "Failed to stop server",
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            });
    }
    
    @GetMapping("/servers/running")
    public ResponseEntity<List<String>> getRunningServers() {
        List<String> runningServers = processManager.getRunningServers();
        return ResponseEntity.ok(runningServers);
    }
    
    // ===== 헬스 체크 =====
    
    @GetMapping("/servers/{serverName}/health")
    public CompletableFuture<ResponseEntity<VllmHealthChecker.HealthStatus>> checkServerHealth(@PathVariable String serverName) {
        return healthChecker.checkServerHealth(serverName)
            .thenApply(ResponseEntity::ok);
    }
    
    @GetMapping("/servers/health")
    public CompletableFuture<ResponseEntity<Map<String, VllmHealthChecker.HealthStatus>>> checkAllServersHealth() {
        return healthChecker.checkAllServersHealth()
            .thenApply(ResponseEntity::ok);
    }
    
    // ===== API 호출 =====
    
    @PostMapping("/servers/{serverName}/chat/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> chatCompletion(
            @PathVariable String serverName, 
            @RequestBody LlmRequest request) {
        return apiClient.chatCompletion(serverName, request)
            .thenApply(ResponseEntity::ok);
    }
    
    // ===== 로드 밸런싱 =====
    
    @PostMapping("/load-balancer/select")
    public ResponseEntity<Map<String, Object>> selectServer(
            @RequestParam(defaultValue = "HEALTH_BASED") VllmLoadBalancer.LoadBalancingStrategy strategy) {
        
        var selectedServer = loadBalancer.selectServer("llama3.2", strategy);
        
        Map<String, Object> response = Map.of(
            "model", "llama3.2",
            "strategy", strategy.toString(),
            "selectedServer", selectedServer.orElse(null),
            "available", selectedServer.isPresent(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/load-balancer/status")
    public ResponseEntity<VllmLoadBalancer.LoadBalancerStatus> getLoadBalancerStatus() {
        VllmLoadBalancer.LoadBalancerStatus status = loadBalancer.getStatus();
        return ResponseEntity.ok(status);
    }
    
    // ===== 모니터링 =====
    
    @GetMapping("/monitoring/summary")
    public ResponseEntity<VllmMonitoringService.MonitoringSummary> getMonitoringSummary() {
        VllmMonitoringService.MonitoringSummary summary = monitoringService.getMonitoringSummary();
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/monitoring/alerts")
    public ResponseEntity<List<VllmMonitoringService.Alert>> getActiveAlerts() {
        List<VllmMonitoringService.Alert> alerts = monitoringService.getActiveAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    // ===== 통합 API =====
    
    @PostMapping("/chat/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> smartChatCompletion(@RequestBody LlmRequest request) {
        // 최적의 Llama 3.2 서버 자동 선택
        return loadBalancer.selectServer("llama3.2", VllmLoadBalancer.LoadBalancingStrategy.PERFORMANCE_BASED)
            .map(serverName -> 
                apiClient.chatCompletion(serverName, request)
                    .whenComplete((response, throwable) -> {
                        if (throwable == null) {
                            loadBalancer.completeRequest(serverName);
                        }
                    })
            )
            .orElse(CompletableFuture.completedFuture(
                LlmResponse.error("llama3.2", "No available Llama 3.2 servers")
            ))
            .thenApply(ResponseEntity::ok);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOverallStatus() {
        List<String> runningServers = processManager.getRunningServers();
        VllmMonitoringService.MonitoringSummary summary = monitoringService.getMonitoringSummary();
        
        String overallStatus;
        if (summary.getHealthyServers() == 0) {
            overallStatus = "DOWN";
        } else if (summary.getCriticalAlerts() > 0) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "HEALTHY";
        }
        
        Map<String, Object> status = Map.of(
            "status", overallStatus,
            "model", "llama3.1",
            "totalServers", summary.getTotalServers(),
            "healthyServers", summary.getHealthyServers(),
            "runningServers", runningServers.size(),
            "activeAlerts", summary.getTotalActiveAlerts(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        VllmLoadBalancer.LoadBalancerStatus loadBalancerStatus = loadBalancer.getStatus();
        VllmMonitoringService.MonitoringSummary monitoringSummary = monitoringService.getMonitoringSummary();
        List<String> runningServers = processManager.getRunningServers();
        List<VllmMonitoringService.Alert> activeAlerts = monitoringService.getActiveAlerts();
        
        Map<String, Object> dashboard = Map.of(
            "model", "llama3.2",
            "loadBalancer", loadBalancerStatus,
            "monitoring", monitoringSummary,
            "runningServers", runningServers,
            "activeAlerts", activeAlerts,
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(dashboard);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        Map<String, Object> error = Map.of(
            "error", "Internal Server Error",
            "message", e.getMessage(),
            "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.status(500).body(error);
    }
}