// VllmLoadBalancer.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VllmLoadBalancer {
    
    private final VllmConfigProperties vllmConfig;
    private final VllmHealthChecker healthChecker;
    private final Map<String, ServerMetrics> serverMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    /**
     * 로드 밸런싱 전략
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        LEAST_CONNECTIONS,
        WEIGHTED_ROUND_ROBIN,
        HEALTH_BASED,
        PERFORMANCE_BASED,
        RANDOM
    }
    
    /**
     * 최적의 서버 선택
     */
    public Optional<String> selectServer(String modelName, LoadBalancingStrategy strategy) {
        List<String> availableServers = getAvailableServersForModel(modelName);
        
        if (availableServers.isEmpty()) {
            log.warn("No available servers found for model: {}", modelName);
            return Optional.empty();
        }
        
        String selectedServer = switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(availableServers);
            case LEAST_CONNECTIONS -> selectLeastConnections(availableServers);
            case WEIGHTED_ROUND_ROBIN -> selectWeightedRoundRobin(availableServers);
            case HEALTH_BASED -> selectHealthBased(availableServers);
            case PERFORMANCE_BASED -> selectPerformanceBased(availableServers);
            case RANDOM -> selectRandom(availableServers);
        };
        
        // 선택된 서버의 요청 수 증가
        requestCounts.computeIfAbsent(selectedServer, k -> new AtomicInteger(0))
                    .incrementAndGet();
        
        log.debug("Selected server: {} using strategy: {} for model: {}", 
                selectedServer, strategy, modelName);
        
        return Optional.of(selectedServer);
    }
    
    /**
     * 기본 전략으로 서버 선택
     */
    public Optional<String> selectServer(String modelName) {
        return selectServer(modelName, LoadBalancingStrategy.HEALTH_BASED);
    }
    
    /**
     * 요청 완료 후 카운터 감소
     */
    public void completeRequest(String serverName) {
        AtomicInteger counter = requestCounts.get(serverName);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
    
    /**
     * 서버 메트릭 업데이트
     */
    public void updateServerMetrics(String serverName, ServerMetrics metrics) {
        serverMetrics.put(serverName, metrics);
    }
    
    /**
     * 현재 로드 밸런싱 상태 조회
     */
    public LoadBalancerStatus getStatus() {
        Map<String, Integer> currentLoads = requestCounts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        
        Map<String, VllmHealthChecker.HealthStatus> healthStatuses = 
            healthChecker.getAllCachedHealthStatus();
        
        return LoadBalancerStatus.builder()
            .serverLoads(currentLoads)
            .healthStatuses(healthStatuses)
            .serverMetrics(new HashMap<>(serverMetrics))
            .totalRequests(currentLoads.values().stream().mapToInt(Integer::intValue).sum())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private List<String> getAvailableServersForModel(String modelName) {
        return vllmConfig.getEnabledServers().stream()
            .filter(server -> server.getModel().contains(modelName) || 
                             modelName.equals("any") ||
                             server.getName().contains(modelName))
            .filter(this::isServerHealthy)
            .map(VllmConfigProperties.VllmServerConfig::getName)
            .toList();
    }
    
    private boolean isServerHealthy(VllmConfigProperties.VllmServerConfig serverConfig) {
        VllmHealthChecker.HealthStatus status = healthChecker.getCachedHealthStatus(serverConfig.getName());
        return status.isHealthy();
    }
    
    private String selectRoundRobin(List<String> servers) {
        if (servers.isEmpty()) return null;
        
        int index = roundRobinCounter.getAndIncrement() % servers.size();
        return servers.get(index);
    }
    
    private String selectLeastConnections(List<String> servers) {
        return servers.stream()
            .min(Comparator.comparingInt(this::getActiveConnections))
            .orElse(servers.get(0));
    }
    
    private String selectWeightedRoundRobin(List<String> servers) {
        // 서버별 가중치 계산 (GPU 메모리, 성능 등 기반)
        Map<String, Double> weights = calculateServerWeights(servers);
        
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double random = Math.random() * totalWeight;
        
        double currentWeight = 0;
        for (String server : servers) {
            currentWeight += weights.getOrDefault(server, 1.0);
            if (random <= currentWeight) {
                return server;
            }
        }
        
        return servers.get(0);
    }
    
    private String selectHealthBased(List<String> servers) {
        // 건강한 서버들 중에서 응답 시간이 가장 빠른 서버 선택
        return servers.stream()
            .filter(this::isServerResponsive)
            .min(Comparator.comparingLong(this::getAverageResponseTime))
            .orElse(selectRoundRobin(servers));
    }
    
    private String selectPerformanceBased(List<String> servers) {
        // 처리량, 응답 시간, GPU 사용률 등을 종합하여 최적 서버 선택
        return servers.stream()
            .max(Comparator.comparingDouble(this::calculatePerformanceScore))
            .orElse(servers.get(0));
    }
    
    private String selectRandom(List<String> servers) {
        Random random = new Random();
        return servers.get(random.nextInt(servers.size()));
    }
    
    private int getActiveConnections(String serverName) {
        AtomicInteger counter = requestCounts.get(serverName);
        return counter != null ? counter.get() : 0;
    }
    
    private Map<String, Double> calculateServerWeights(List<String> servers) {
        Map<String, Double> weights = new HashMap<>();
        
        for (String serverName : servers) {
            VllmConfigProperties.VllmServerConfig config = vllmConfig.getServerByName(serverName);
            double weight = 1.0;
            
            // GPU 메모리 기반 가중치
            if (config.getPerformanceSettings() != null && 
                config.getPerformanceSettings().getGpuMemoryUtilization() != null) {
                weight *= config.getPerformanceSettings().getGpuMemoryUtilization();
            }
            
            // 텐서 병렬 크기 기반 가중치
            if (config.getPerformanceSettings() != null && 
                config.getPerformanceSettings().getTensorParallelSize() != null) {
                weight *= config.getPerformanceSettings().getTensorParallelSize();
            }
            
            // 현재 부하 반영
            int currentLoad = getActiveConnections(serverName);
            weight = weight / (1 + currentLoad * 0.1); // 부하가 높을수록 가중치 감소
            
            weights.put(serverName, weight);
        }
        
        return weights;
    }
    
    private boolean isServerResponsive(String serverName) {
        VllmHealthChecker.HealthStatus status = healthChecker.getCachedHealthStatus(serverName);
        return status.isHealthy() && 
               (status.getResponseTime() == null || status.getResponseTime().toMillis() < 5000);
    }
    
    private long getAverageResponseTime(String serverName) {
        ServerMetrics metrics = serverMetrics.get(serverName);
        if (metrics != null && metrics.getMetric("avg_response_time") != null) {
            return metrics.getMetric("avg_response_time").longValue();
        }
        
        VllmHealthChecker.HealthStatus status = healthChecker.getCachedHealthStatus(serverName);
        if (status.getResponseTime() != null) {
            return status.getResponseTime().toMillis();
        }
        
        return Long.MAX_VALUE;
    }
    
    private double calculatePerformanceScore(String serverName) {
        double score = 1.0;
        
        // 응답 시간 점수 (낮을수록 좋음)
        long responseTime = getAverageResponseTime(serverName);
        if (responseTime != Long.MAX_VALUE) {
            score *= Math.max(0.1, 1.0 - (responseTime / 10000.0)); // 10초 기준
        }
        
        // 현재 부하 점수 (낮을수록 좋음)
        int currentLoad = getActiveConnections(serverName);
        score *= Math.max(0.1, 1.0 - (currentLoad / 100.0)); // 100개 요청 기준
        
        // 서버 메트릭 기반 점수
        ServerMetrics metrics = serverMetrics.get(serverName);
        if (metrics != null) {
            // GPU 사용률 (적당한 수준이 좋음)
            Double gpuUtilization = metrics.getMetric("gpu_utilization");
            if (gpuUtilization != null) {
                score *= (gpuUtilization > 0.9) ? 0.5 : (gpuUtilization < 0.1) ? 0.7 : 1.0;
            }
            
            // 처리량 (높을수록 좋음)
            Double throughput = metrics.getMetric("requests_per_second");
            if (throughput != null) {
                score *= Math.min(2.0, 1.0 + (throughput / 100.0));
            }
        }
        
        return score;
    }
    
    /**
     * 서버별 상세 통계
     */
    public ServerStatistics getServerStatistics(String serverName) {
        AtomicInteger requestCounter = requestCounts.get(serverName);
        int currentRequests = requestCounter != null ? requestCounter.get() : 0;
        
        VllmHealthChecker.HealthStatus healthStatus = healthChecker.getCachedHealthStatus(serverName);
        ServerMetrics metrics = serverMetrics.get(serverName);
        VllmConfigProperties.VllmServerConfig config = vllmConfig.getServerByName(serverName);
        
        return ServerStatistics.builder()
            .serverName(serverName)
            .currentRequests(currentRequests)
            .healthStatus(healthStatus)
            .metrics(metrics)
            .config(config)
            .performanceScore(calculatePerformanceScore(serverName))
            .averageResponseTime(getAverageResponseTime(serverName))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 전체 서버 통계
     */
    public Map<String, ServerStatistics> getAllServerStatistics() {
        return vllmConfig.getServers().stream()
            .collect(Collectors.toMap(
                VllmConfigProperties.VllmServerConfig::getName,
                config -> getServerStatistics(config.getName())
            ));
    }
    
    /**
     * 로드 밸런서 재설정
     */
    public void reset() {
        requestCounts.clear();
        serverMetrics.clear();
        roundRobinCounter.set(0);
        log.info("Load balancer reset completed");
    }
    
    /**
     * 특정 서버 비활성화 (유지보수 등)
     */
    public void drainServer(String serverName) {
        // 새로운 요청은 받지 않도록 설정
        // 기존 요청이 완료될 때까지 대기
        log.info("Draining server: {}", serverName);
        // 실제 구현에서는 서버 상태를 DRAINING으로 변경
    }
    
    // 내부 클래스들
    @lombok.Builder
    @lombok.Data
    public static class LoadBalancerStatus {
        private Map<String, Integer> serverLoads;
        private Map<String, VllmHealthChecker.HealthStatus> healthStatuses;
        private Map<String, ServerMetrics> serverMetrics;
        private Integer totalRequests;
        private LocalDateTime timestamp;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ServerStatistics {
        private String serverName;
        private Integer currentRequests;
        private VllmHealthChecker.HealthStatus healthStatus;
        private ServerMetrics metrics;
        private VllmConfigProperties.VllmServerConfig config;
        private Double performanceScore;
        private Long averageResponseTime;
        private LocalDateTime timestamp;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ServerMetrics {
        private String serverName;
        private Map<String, Double> metrics;
        private LocalDateTime timestamp;
        
        public Double getMetric(String metricName) {
            return metrics != null ? metrics.get(metricName) : null;
        }
    }
}