// VllmController.java
package com.yourcompany.llm.controller;

import com.yourcompany.llm.service.vllm.*;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
                    "message", success ? "Server started successfully" : "Failed to start server",
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
    
    @PostMapping("/servers/start-all")
    public CompletableFuture<ResponseEntity<Map<String, Boolean>>> startAllServers() {
        return processManager.startServer("all") // 모든 활성화된 서버 시작하는 메서드 필요
            .thenApply(success -> ResponseEntity.ok(Map.of("allStarted", success)));
    }
    
    @PostMapping("/servers/stop-all")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> stopAllServers() {
        return processManager.stopAllServers()
            .thenApply(allStopped -> {
                Map<String, Object> response = Map.of(
                    "allStopped", allStopped,
                    "message", allStopped ? "All servers stopped successfully" : "Some servers failed to stop",
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
    
    @GetMapping("/servers/{serverName}/process-info")
    public ResponseEntity<VllmProcessManager.ProcessInfo> getProcessInfo(@PathVariable String serverName) {
        VllmProcessManager.ProcessInfo processInfo = processManager.getProcessInfo(serverName);
        return ResponseEntity.ok(processInfo);
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
    
    @GetMapping("/servers/{serverName}/metrics")
    public CompletableFuture<ResponseEntity<VllmHealthChecker.ServerMetrics>> getServerMetrics(@PathVariable String serverName) {
        return healthChecker.getServerMetrics(serverName)
            .thenApply(ResponseEntity::ok);
    }
    
    @GetMapping("/servers/{serverName}/model-info")
    public CompletableFuture<ResponseEntity<VllmHealthChecker.ModelInfo>> getModelInfo(@PathVariable String serverName) {
        return healthChecker.getModelInfo(serverName)
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
    
    @PostMapping("/servers/{serverName}/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> completion(
            @PathVariable String serverName, 
            @RequestBody LlmRequest request) {
        return apiClient.completion(serverName, request)
            .thenApply(ResponseEntity::ok);
    }
    
    @GetMapping("/servers/{serverName}/models")
    public CompletableFuture<ResponseEntity<List<VllmApiClient.ModelResponse>>> getModels(@PathVariable String serverName) {
        return apiClient.getModels(serverName)
            .thenApply(ResponseEntity::ok);
    }
    
    @PostMapping("/servers/{serverName}/embeddings")
    public CompletableFuture<ResponseEntity<VllmApiClient.EmbeddingResponse>> createEmbeddings(
            @PathVariable String serverName,
            @RequestBody VllmApiClient.EmbeddingRequest request) {
        return apiClient.createEmbeddings(serverName, request)
            .thenApply(ResponseEntity::ok);
    }
    
    // ===== 로드 밸런싱 =====
    
    @PostMapping("/load-balancer/select")
    public ResponseEntity<Map<String, Object>> selectServer(
            @RequestParam String modelName,
            @RequestParam(defaultValue = "HEALTH_BASED") VllmLoadBalancer.LoadBalancingStrategy strategy) {
        
        var selectedServer = loadBalancer.selectServer(modelName, strategy);
        
        Map<String, Object> response = Map.of(
            "modelName", modelName,
            "strategy", strategy.toString(),
            "selectedServer", selectedServer.orElse(null),
            "available", selectedServer.isPresent(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/load-balancer/complete-request")
    public ResponseEntity<Void> completeRequest(@RequestParam String serverName) {
        loadBalancer.completeRequest(serverName);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/load-balancer/status")
    public ResponseEntity<VllmLoadBalancer.LoadBalancerStatus> getLoadBalancerStatus() {
        VllmLoadBalancer.LoadBalancerStatus status = loadBalancer.getStatus();
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/load-balancer/statistics")
    public ResponseEntity<Map<String, VllmLoadBalancer.ServerStatistics>> getAllServerStatistics() {
        Map<String, VllmLoadBalancer.ServerStatistics> statistics = loadBalancer.getAllServerStatistics();
        return ResponseEntity.ok(statistics);
    }
    
    @GetMapping("/load-balancer/statistics/{serverName}")
    public ResponseEntity<VllmLoadBalancer.ServerStatistics> getServerStatistics(@PathVariable String serverName) {
        VllmLoadBalancer.ServerStatistics statistics = loadBalancer.getServerStatistics(serverName);
        return ResponseEntity.ok(statistics);
    }
    
    @PostMapping("/load-balancer/reset")
    public ResponseEntity<Map<String, Object>> resetLoadBalancer() {
        loadBalancer.reset();
        return ResponseEntity.ok(Map.of(
            "message", "Load balancer reset successfully",
            "timestamp", LocalDateTime.now()
        ));
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
    
    @GetMapping("/monitoring/alerts/{serverName}")
    public ResponseEntity<List<VllmMonitoringService.Alert>> getServerAlerts(@PathVariable String serverName) {
        List<VllmMonitoringService.Alert> alerts = monitoringService.getActiveAlerts(serverName);
        return ResponseEntity.ok(alerts);
    }
    
    @GetMapping("/monitoring/metrics/{serverName}")
    public ResponseEntity<List<VllmMonitoringService.MetricDataPoint>> getMetricHistory(
            @PathVariable String serverName,
            @RequestParam String category,
            @RequestParam String metricName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        
        List<VllmMonitoringService.MetricDataPoint> metrics = 
            monitoringService.getMetricHistory(serverName, category, metricName, from, to);
        return ResponseEntity.ok(metrics);
    }
    
    @PostMapping("/monitoring/alert-rules")
    public ResponseEntity<Map<String, Object>> setAlertRule(
            @RequestParam String ruleName,
            @RequestBody VllmMonitoringService.AlertRule rule) {
        
        monitoringService.setAlertRule(ruleName, rule);
        
        return ResponseEntity.ok(Map.of(
            "ruleName", ruleName,
            "message", "Alert rule set successfully",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    @PostMapping("/monitoring/collect-metrics/{serverName}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> collectMetrics(@PathVariable String serverName) {
        return CompletableFuture.allOf(
            monitoringService.collectHealthMetrics(serverName),
            monitoringService.collectPerformanceMetrics(serverName),
            monitoringService.collectResourceMetrics(serverName)
        ).thenApply(v -> ResponseEntity.ok(Map.of(
            "serverName", serverName,
            "message", "Metrics collected successfully",
            "timestamp", LocalDateTime.now()
        )));
    }
    
    // ===== 통합 API =====
    
    @PostMapping("/chat/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> smartChatCompletion(@RequestBody LlmRequest request) {
        // 모델명을 기반으로 최적의 서버 자동 선택
        String modelName = request.getModel() != null ? request.getModel() : "any";
        
        return loadBalancer.selectServer(modelName, VllmLoadBalancer.LoadBalancingStrategy.PERFORMANCE_BASED)
            .map(serverName -> 
                apiClient.chatCompletion(serverName, request)
                    .whenComplete((response, throwable) -> {
                        if (throwable == null) {
                            loadBalancer.completeRequest(serverName);
                        }
                    })
            )
            .orElse(CompletableFuture.completedFuture(
                LlmResponse.error(modelName, "No available servers for model: " + modelName)
            ))
            .thenApply(ResponseEntity::ok);
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        // 대시보드용 종합 정보
        VllmLoadBalancer.LoadBalancerStatus loadBalancerStatus = loadBalancer.getStatus();
        VllmMonitoringService.MonitoringSummary monitoringSummary = monitoringService.getMonitoringSummary();
        List<String> runningServers = processManager.getRunningServers();
        List<VllmMonitoringService.Alert> activeAlerts = monitoringService.getActiveAlerts();
        
        Map<String, Object> dashboard = Map.of(
            "loadBalancer", loadBalancerStatus,
            "monitoring", monitoringSummary,
            "runningServers", runningServers,
            "activeAlerts", activeAlerts,
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOverallStatus() {
        // 전체 시스템 상태 요약
        List<String> runningServers = processManager.getRunningServers();
        VllmMonitoringService.MonitoringSummary summary = monitoringService.getMonitoringSummary();
        
        String overallStatus;
        if (summary.getHealthyServers() == 0) {
            overallStatus = "DOWN";
        } else if (summary.getCriticalAlerts() > 0) {
            overallStatus = "DEGRADED";
        } else if (summary.getHealthyServers() < summary.getTotalServers()) {
            overallStatus = "PARTIAL";
        } else {
            overallStatus = "HEALTHY";
        }
        
        Map<String, Object> status = Map.of(
            "status", overallStatus,
            "totalServers", summary.getTotalServers(),
            "healthyServers", summary.getHealthyServers(),
            "runningServers", runningServers.size(),
            "activeAlerts", summary.getTotalActiveAlerts(),
            "criticalAlerts", summary.getCriticalAlerts(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(status);
    }
    
    // ===== 유틸리티 =====
    
    @PostMapping("/servers/{serverName}/restart")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> restartServer(@PathVariable String serverName) {
        return processManager.stopServer(serverName)
            .thenCompose(stopped -> {
                if (stopped) {
                    // 잠시 대기 후 재시작
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return processManager.startServer(serverName);
                } else {
                    return CompletableFuture.completedFuture(false);
                }
            })
            .thenApply(restarted -> {
                Map<String, Object> response = Map.of(
                    "serverName", serverName,
                    "restarted", restarted,
                    "message", restarted ? "Server restarted successfully" : "Failed to restart server",
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            });
    }
    
    @PostMapping("/servers/health-check-all")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> forceHealthCheckAll() {
        return healthChecker.checkAllServersHealth()
            .thenApply(healthStatuses -> {
                long healthyCount = healthStatuses.values().stream()
                    .mapToLong(status -> status.isHealthy() ? 1 : 0)
                    .sum();
                
                Map<String, Object> response = Map.of(
                    "totalServers", healthStatuses.size(),
                    "healthyServers", healthyCount,
                    "healthStatuses", healthStatuses,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            });
    }
    
    @DeleteMapping("/monitoring/cache")
    public ResponseEntity<Map<String, Object>> clearHealthCache() {
        healthChecker.clearHealthCache();
        
        return ResponseEntity.ok(Map.of(
            "message", "Health cache cleared successfully",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    @DeleteMapping("/monitoring/cache/{serverName}")
    public ResponseEntity<Map<String, Object>> clearServerHealthCache(@PathVariable String serverName) {
        healthChecker.clearHealthCache(serverName);
        
        return ResponseEntity.ok(Map.of(
            "serverName", serverName,
            "message", "Server health cache cleared successfully",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    // ===== 에러 핸들링 =====
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        Map<String, Object> error = Map.of(
            "error", "Internal Server Error",
            "message", e.getMessage(),
            "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.status(500).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> error = Map.of(
            "error", "Bad Request",
            "message", e.getMessage(),
            "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.status(400).body(error);
    }
}