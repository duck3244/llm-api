// VllmMonitoringService.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VllmMonitoringService {
    
    private final VllmConfigProperties vllmConfig;
    private final VllmHealthChecker healthChecker;
    private final VllmLoadBalancer loadBalancer;
    
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 30000) // 30초마다
    public void monitorServers() {
        log.debug("Starting scheduled Llama 3.2 server monitoring");
        
        vllmConfig.getEnabledServers().forEach(serverConfig -> {
            String serverName = serverConfig.getName();
            
            try {
                // 헬스 체크
                healthChecker.checkServerHealth(serverName).thenAccept(healthStatus -> {
                    checkAlertRules(serverName, healthStatus);
                });
            } catch (Exception e) {
                log.error("Error monitoring server: {}", serverName, e);
            }
        });
    }
    
    public void initializeDefaultAlertRules() {
        // 서버 다운 알럿
        setAlertRule("server_down", AlertRule.builder()
            .metricName("health_status")
            .operator(AlertRule.Operator.EQUALS)
            .threshold("DOWN")
            .severity(AlertRule.Severity.CRITICAL)
            .description("Llama 3.2 server is down")
            .build());
        
        // 높은 부하 알럿
        setAlertRule("high_load", AlertRule.builder()
            .metricName("current_requests")
            .operator(AlertRule.Operator.GREATER_THAN)
            .threshold("50")
            .severity(AlertRule.Severity.WARNING)
            .description("High request load on Llama 3.2 server")
            .build());
            
        log.info("Default alert rules initialized for Llama 3.2 monitoring");
    }
    
    public void setAlertRule(String ruleName, AlertRule rule) {
        alertRules.put(ruleName, rule);
        log.info("Alert rule set: {} -> {}", ruleName, rule.getDescription());
    }
    
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }
    
    public List<Alert> getActiveAlerts(String serverName) {
        return activeAlerts.values().stream()
            .filter(alert -> alert.getServerName().equals(serverName))
            .toList();
    }
    
    public MonitoringSummary getMonitoringSummary() {
        var allStats = loadBalancer.getStatus();
        
        int totalServers = vllmConfig.getEnabledServers().size();
        long healthyServers = allStats.getHealthStatuses().values().stream()
            .mapToLong(status -> status.isHealthy() ? 1 : 0)
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
    
    private void checkAlertRules(String serverName, VllmHealthChecker.HealthStatus healthStatus) {
        alertRules.forEach((ruleName, rule) -> {
            checkSingleAlertRule(serverName, ruleName, rule, healthStatus);
        });
    }
    
    private void checkSingleAlertRule(String serverName, String ruleName, AlertRule rule, 
                                     VllmHealthChecker.HealthStatus healthStatus) {
        String alertKey = serverName + ":" + ruleName;
        boolean conditionMet = false;
        
        // 알럿 조건 확인
        if ("health_status".equals(rule.getMetricName())) {
            conditionMet = evaluateHealthCondition(healthStatus, rule);
        } else if ("current_requests".equals(rule.getMetricName())) {
            VllmLoadBalancer.ServerStatistics stats = loadBalancer.getServerStatistics(serverName);
            conditionMet = evaluateNumericCondition(stats.getCurrentRequests().doubleValue(), rule);
        }
        
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
    
    private boolean evaluateHealthCondition(VllmHealthChecker.HealthStatus healthStatus, AlertRule rule) {
        return switch (rule.getOperator()) {
            case EQUALS -> healthStatus.getStatus().equals(rule.getThreshold());
            case NOT_EQUALS -> !healthStatus.getStatus().equals(rule.getThreshold());
            default -> false;
        };
    }
    
    private boolean evaluateNumericCondition(Double value, AlertRule rule) {
        if (value == null) return false;
        
        double threshold;
        try {
            threshold = Double.parseDouble(rule.getThreshold());
        } catch (NumberFormatException e) {
            return false;
        }
        
        return switch (rule.getOperator()) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUALS -> value.equals(threshold);
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN_OR_EQUAL -> value <= threshold;
            default -> false;
        };
    }
    
    private void sendAlert(Alert alert) {
        log.warn("LLAMA 3.2 ALERT: {} - {} on server {}: {}", 
            alert.getSeverity(), alert.getRuleName(), alert.getServerName(), alert.getDescription());
    }
    
    private void sendAlertResolution(Alert alert) {
        log.info("LLAMA 3.2 ALERT RESOLVED: {} - {} on server {}", 
            alert.getRuleName(), alert.getServerName(), alert.getDescription());
    }
    
    // Data classes
    @lombok.Builder
    @lombok.Data
    public static class AlertRule {
        private String metricName;
        private Operator operator;
        private String threshold;
        private Severity severity;
        private String description;
        
        public enum Operator {
            GREATER_THAN, LESS_THAN, EQUALS, NOT_EQUALS, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
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
            ACTIVE, RESOLVED
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