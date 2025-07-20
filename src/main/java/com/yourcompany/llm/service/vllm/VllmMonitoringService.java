// VllmMonitoringService.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VllmMonitoringService {
    
    private final VllmConfigProperties vllmConfig;
    private final VllmHealthChecker healthChecker;
    private final VllmLoadBalancer loadBalancer;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final Map<String, List<MetricDataPoint>> metricsHistory = new ConcurrentHashMap<>();
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    
    private static final String METRICS_KEY_PREFIX = "vllm:metrics:";
    private static final String ALERT_KEY_PREFIX = "vllm:alerts:";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 주기적인 서버 모니터링 (30초마다)
     */
    @Scheduled(fixedRate = 30000)
    public void monitorServers() {
        log.debug("Starting scheduled server monitoring");
        
        vllmConfig.getEnabledServers().forEach(serverConfig -> {
            String serverName = serverConfig.getName();
            
            CompletableFuture.allOf(
                collectHealthMetrics(serverName),
                collectPerformanceMetrics(serverName),
                collectResourceMetrics(serverName)
            ).thenRun(() -> {
                checkAlertRules(serverName);
                updateDashboardMetrics(serverName);
            }).exceptionally(throwable -> {
                log.error("Error monitoring server: {}", serverName, throwable);
                return null;
            });
        });
    }
    
    /**
     * 헬스 메트릭 수집
     */
    public CompletableFuture<Void> collectHealthMetrics(String serverName) {
        return healthChecker.checkServerHealth(serverName)
            .thenAccept(healthStatus -> {
                Map<String, Double> metrics = new HashMap<>();
                metrics.put("health_status", healthStatus.isHealthy() ? 1.0 : 0.0);
                
                if (healthStatus.getResponseTime() != null) {
                    metrics.put("response_time_ms", (double) healthStatus.getResponseTime().toMillis());
                }
                
                recordMetrics(serverName, "health", metrics);
            });
    }
    
    /**
     * 성능 메트릭 수집
     */
    public CompletableFuture<Void> collectPerformanceMetrics(String serverName) {
        return healthChecker.getServerMetrics(serverName)
            .thenAccept(serverMetrics -> {
                if (serverMetrics.getAvailable() && serverMetrics.getMetrics() != null) {
                    Map<String, Double> metrics = new HashMap<>(serverMetrics.getMetrics());
                    
                    // 로드 밸런서에서 현재 요청 수 가져오기
                    VllmLoadBalancer.ServerStatistics stats = loadBalancer.getServerStatistics(serverName);
                    metrics.put("current_requests", (double) stats.getCurrentRequests());
                    metrics.put("performance_score", stats.getPerformanceScore());
                    
                    recordMetrics(serverName, "performance", metrics);
                }
            });
    }
    
    /**
     * 리소스 메트릭 수집
     */
    public CompletableFuture<Void> collectResourceMetrics(String serverName) {
        return CompletableFuture.runAsync(() -> {
            try {
                VllmConfigProperties.VllmServerConfig config = vllmConfig.getServerByName(serverName);
                if (config == null) return;
                
                Map<String, Double> metrics = new HashMap<>();
                
                // GPU 메모리 사용률 추정
                if (config.getPerformanceSettings() != null) {
                    Double gpuMemUtil = config.getPerformanceSettings().getGpuMemoryUtilization();
                    if (gpuMemUtil != null) {
                        metrics.put("gpu_memory_utilization", gpuMemUtil);
                    }
                }
                
                // 시스템 리소스는 별도 모니터링 도구나 API를 통해 수집
                // 여기서는 예시 데이터
                metrics.put("cpu_usage", Math.random() * 100);
                metrics.put("memory_usage", Math.random() * 100);
                
                recordMetrics(serverName, "resource", metrics);
                
            } catch (Exception e) {
                log.error("Error collecting resource metrics for server: {}", serverName, e);
            }
        });
    }
    
    /**
     * 메트릭 기록
     */
    private void recordMetrics(String serverName, String category, Map<String, Double> metrics) {
        LocalDateTime timestamp = LocalDateTime.now();
        
        metrics.forEach((metricName, value) -> {
            MetricDataPoint dataPoint = MetricDataPoint.builder()
                .serverName(serverName)
                .category(category)
                .metricName(metricName)
                .value(value)
                .timestamp(timestamp)
                .build();
            
            // 메모리에 저장 (최근 1000개 데이터포인트만)
            String key = serverName + ":" + category + ":" + metricName;
            metricsHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(dataPoint);
            
            List<MetricDataPoint> history = metricsHistory.get(key);
            if (history.size() > 1000) {
                history.remove(0);
            }
            
            // Redis에 저장 (24시간 TTL)
            String redisKey = METRICS_KEY_PREFIX + key + ":" + timestamp.format(TIMESTAMP_FORMATTER);
            redisTemplate.opsForValue().set(redisKey, dataPoint, 24, TimeUnit.HOURS);
        });
    }
    
    /**
     * 알럿 규칙 설정
     */
    public void setAlertRule(String ruleName, AlertRule rule) {
        alertRules.put(ruleName, rule);
        log.info("Alert rule set: {} -> {}", ruleName, rule);
    }
    
    /**
     * 기본 알럿 규칙 초기화
     */
    public void initializeDefaultAlertRules() {
        // 서버 다운 알럿
        setAlertRule("server_down", AlertRule.builder()
            .metricName("health_status")
            .operator(AlertRule.Operator.LESS_THAN)
            .threshold(1.0)
            .duration(Duration.ofMinutes(1))
            .severity(AlertRule.Severity.CRITICAL)
            .description("Server is down or unhealthy")
            .build());
        
        // 높은 응답 시간 알럿
        setAlertRule("high_response_time", AlertRule.builder()
            .metricName("response_time_ms")
            .operator(AlertRule.Operator.GREATER_THAN)
            .threshold(5000.0)
            .duration(Duration.ofMinutes(2))
            .severity(AlertRule.Severity.WARNING)
            .description("High response time detected")
            .build());
        
        // 높은 부하 알럿
        setAlertRule("high_load", AlertRule.builder()
            .metricName("current_requests")
            .operator(AlertRule.Operator.GREATER_THAN)
            .threshold(100.0)
            .duration(Duration.ofMinutes(5))
            .severity(AlertRule.Severity.WARNING)
            .description("High request load detected")
            .build());
        
        // GPU 메모리 사용률 알럿
        setAlertRule("gpu_memory_high", AlertRule.builder()
            .metricName("gpu_memory_utilization")
            .operator(AlertRule.Operator.GREATER_THAN)
            .threshold(95.0)
            .duration(Duration.ofMinutes(3))
            .severity(AlertRule.Severity.WARNING)
            .description("GPU memory utilization is very high")
            .build());
    }
    
    /**
     * 알럿 규칙 확인
     */
    private void checkAlertRules(String serverName) {
        alertRules.forEach((ruleName, rule) -> {
            checkSingleAlertRule(serverName, ruleName, rule);
        });
    }
    
    private void checkSingleAlertRule(String serverName, String ruleName, AlertRule rule) {
        String metricKey = serverName + ":performance:" + rule.getMetricName();
        List<MetricDataPoint> history = metricsHistory.get(metricKey);
        
        if (history == null || history.isEmpty()) {
            return;
        }
        
        LocalDateTime cutoffTime = LocalDateTime.now().minus(rule.getDuration());
        
        // 지정된 기간 동안 조건을 만족하는지 확인
        boolean conditionMet = history.stream()
            .filter(point -> point.getTimestamp().isAfter(cutoffTime))
            .allMatch(point -> evaluateCondition(point.getValue(), rule.getOperator(), rule.getThreshold()));
        
        String alertKey = serverName + ":" + ruleName;
        
        if (conditionMet && !activeAlerts.containsKey(alertKey)) {
            // 새로운 알럿 생성
            Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .serverName(serverName)
                .ruleName(ruleName)
                .metricName(rule.getMetricName())
                .severity(rule.getSeverity())
                .description(rule.getDescription())
                .startTime(LocalDateTime.now())
                .status(Alert.Status.ACTIVE)
                .build();
            
            activeAlerts.put(alertKey, alert);
            sendAlert(alert);
            
        } else if (!conditionMet && activeAlerts.containsKey(alertKey)) {
            // 알럿 해결
            Alert alert = activeAlerts.remove(alertKey);
            alert.setStatus(Alert.Status.RESOLVED);
            alert.setEndTime(LocalDateTime.now());
            
            sendAlertResolution(alert);
        }
    }
    
    private boolean evaluateCondition(Double value, AlertRule.Operator operator, Double threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        
        return switch (operator) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUALS -> value.equals(threshold);
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN_OR_EQUAL -> value <= threshold;
        };
    }
    
    /**
     * 알럿 전송
     */
    private void sendAlert(Alert alert) {
        log.warn("ALERT: {} - {} on server {}: {}", 
            alert.getSeverity(), alert.getRuleName(), alert.getServerName(), alert.getDescription());
        
        // Redis에 알럿 저장
        String alertKey = ALERT_KEY_PREFIX + alert.getId();
        redisTemplate.opsForValue().set(alertKey, alert, 7, TimeUnit.DAYS);
        
        // 외부 알럿 시스템으로 전송 (Slack, 이메일 등)
        // 구현 필요
    }
    
    private void sendAlertResolution(Alert alert) {
        log.info("ALERT RESOLVED: {} - {} on server {}", 
            alert.getRuleName(), alert.getServerName(), alert.getDescription());
        
        // 알럿 해결 정보 업데이트
        String alertKey = ALERT_KEY_PREFIX + alert.getId();
        redisTemplate.opsForValue().set(alertKey, alert, 7, TimeUnit.DAYS);
    }
    
    /**
     * 대시보드 메트릭 업데이트
     */
    private void updateDashboardMetrics(String serverName) {
        VllmLoadBalancer.ServerStatistics stats = loadBalancer.getServerStatistics(serverName);
        
        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("serverName", serverName);
        dashboardData.put("currentRequests", stats.getCurrentRequests());
        dashboardData.put("healthStatus", stats.getHealthStatus().getStatus());
        dashboardData.put("performanceScore", stats.getPerformanceScore());
        dashboardData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        
        String dashboardKey = "vllm:dashboard:" + serverName;
        redisTemplate.opsForValue().set(dashboardKey, dashboardData, 1, TimeUnit.HOURS);
    }
    
    /**
     * 메트릭 히스토리 조회
     */
    public List<MetricDataPoint> getMetricHistory(String serverName, String category, String metricName, 
                                                  LocalDateTime from, LocalDateTime to) {
        String key = serverName + ":" + category + ":" + metricName;
        List<MetricDataPoint> history = metricsHistory.get(key);
        
        if (history == null) {
            return List.of();
        }
        
        return history.stream()
            .filter(point -> point.getTimestamp().isAfter(from) && point.getTimestamp().isBefore(to))
            .toList();
    }
    
    /**
     * 활성 알럿 조회
     */
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }
    
    /**
     * 서버별 활성 알럿 조회
     */
    public List<Alert> getActiveAlerts(String serverName) {
        return activeAlerts.values().stream()
            .filter(alert -> alert.getServerName().equals(serverName))
            .toList();
    }
    
    /**
     * 모니터링 요약 정보
     */
    public MonitoringSummary getMonitoringSummary() {
        Map<String, VllmLoadBalancer.ServerStatistics> allStats = loadBalancer.getAllServerStatistics();
        
        int totalServers = allStats.size();
        long healthyServers = allStats.values().stream()
            .mapToLong(stats -> stats.getHealthStatus().isHealthy() ? 1 : 0)
            .sum();
        
        int totalActiveAlerts = activeAlerts.size();
        long criticalAlerts = activeAlerts.values().stream()
            .mapToLong(alert -> alert.getSeverity() == AlertRule.Severity.CRITICAL ? 1 : 0)
            .sum();
        
        return MonitoringSummary.builder()
            .totalServers(totalServers)
            .healthyServers((int) healthyServers)
            .totalActiveAlerts(totalActiveAlerts)
            .criticalAlerts((int) criticalAlerts)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    // 데이터 클래스들
    @lombok.Builder
    @lombok.Data
    public static class MetricDataPoint {
        private String serverName;
        private String category;
        private String metricName;
        private Double value;
        private LocalDateTime timestamp;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class AlertRule {
        private String metricName;
        private Operator operator;
        private Double threshold;
        private java.time.Duration duration;
        private Severity severity;
        private String description;
        
        public enum Operator {
            GREATER_THAN, LESS_THAN, EQUALS, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
        }
        
        public enum Severity {
            INFO, WARNING, CRITICAL
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class Alert {
        private String id;
        private String serverName;
        private String ruleName;
        private String metricName;
        private AlertRule.Severity severity;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Status status;
        
        public enum Status {
            ACTIVE, RESOLVED, SUPPRESSED
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class MonitoringSummary {
        private Integer totalServers;
        private Integer healthyServers;
        private Integer totalActiveAlerts;
        private Integer criticalAlerts;
        private LocalDateTime timestamp;
    }
}