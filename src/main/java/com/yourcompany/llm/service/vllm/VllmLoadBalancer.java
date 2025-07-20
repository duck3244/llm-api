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

@Slf4j
@Component
@RequiredArgsConstructor
public class VllmLoadBalancer {
    
    private final VllmConfigProperties vllmConfig;
    private final VllmHealthChecker healthChecker;
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        LEAST_CONNECTIONS,
        HEALTH_BASED,
        PERFORMANCE_BASED,
        RANDOM
    }
    
    public Optional<String> selectServer(String modelName, LoadBalancingStrategy strategy) {
        List<String> availableServers = getAvailableServers();
        
        if (availableServers.isEmpty()) {
            log.warn("No available Llama 3.2 servers found");
            return Optional.empty();
        }
        
        String selectedServer = switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(availableServers);
            case LEAST_CONNECTIONS -> selectLeastConnections(availableServers);
            case HEALTH_BASED -> selectHealthBased(availableServers);
            case PERFORMANCE_BASED -> selectPerformanceBased(availableServers);
            case RANDOM -> selectRandom(availableServers);
        };
        
        requestCounts.computeIfAbsent(selectedServer, k -> new AtomicInteger(0)).incrementAndGet();
        
        log.debug("Selected Llama 3.2 server: {} using strategy: {}", selectedServer, strategy);
        return Optional.of(selectedServer);
    }
    
    public void completeRequest(String serverName) {
        AtomicInteger counter = requestCounts.get(serverName);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
    
    public LoadBalancerStatus getStatus() {
        Map<String, Integer> currentLoads = requestCounts.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        
        Map<String, VllmHealthChecker.HealthStatus> healthStatuses = 
            healthChecker.getAllCachedHealthStatus();
        
        return LoadBalancerStatus.builder()
            .serverLoads(currentLoads)
            .healthStatuses(healthStatuses)
            .totalRequests(currentLoads.values().stream().mapToInt(Integer::intValue).sum())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public ServerStatistics getServerStatistics(String serverName) {
        AtomicInteger requestCounter = requestCounts.get(serverName);
        int currentRequests = requestCounter != null ? requestCounter.get() : 0;
        
        VllmHealthChecker.HealthStatus healthStatus = healthChecker.getCachedHealthStatus(serverName);
        VllmConfigProperties.VllmServerConfig config = vllmConfig.getServerByName(serverName);
        
        return ServerStatistics.builder()
            .serverName(serverName)
            .currentRequests(currentRequests)
            .healthStatus(healthStatus)
            .config(config)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public void reset() {
        requestCounts.clear();
        roundRobinCounter.set(0);
        log.info("Load balancer reset completed");
    }
    
    private List<String> getAvailableServers() {
        return vllmConfig.getEnabledServers().stream()
            .filter(this::isServerHealthy)
            .map(VllmConfigProperties.VllmServerConfig::getName)
            .toList();
    }
    
    private boolean isServerHealthy(VllmConfigProperties.VllmServerConfig serverConfig) {
        VllmHealthChecker.HealthStatus status = healthChecker.getCachedHealthStatus(serverConfig.getName());
        return status.isHealthy();
    }
    
    private String selectRoundRobin(List<String> servers) {
        int index = roundRobinCounter.getAndIncrement() % servers.size();
        return servers.get(index);
    }
    
    private String selectLeastConnections(List<String> servers) {
        return servers.stream()
            .min(Comparator.comparingInt(this::getActiveConnections))
            .orElse(servers.get(0));
    }
    
    private String selectHealthBased(List<String> servers) {
        return servers.stream()
            .filter(this::isServerResponsive)
            .findFirst()
            .orElse(selectRoundRobin(servers));
    }
    
    private String selectPerformanceBased(List<String> servers) {
        return servers.stream()
            .min(Comparator.comparingInt(this::getActiveConnections))
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
    
    private boolean isServerResponsive(String serverName) {
        VllmHealthChecker.HealthStatus status = healthChecker.getCachedHealthStatus(serverName);
        return status.isHealthy();
    }
    
    @lombok.Builder
    @lombok.Data
    public static class LoadBalancerStatus {
        private Map<String, Integer> serverLoads;
        private Map<String, VllmHealthChecker.HealthStatus> healthStatuses;
        private Integer totalRequests;
        private LocalDateTime timestamp;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ServerStatistics {
        private String serverName;
        private Integer currentRequests;
        private VllmHealthChecker.HealthStatus healthStatus;
        private VllmConfigProperties.VllmServerConfig config;
        private LocalDateTime timestamp;
    }
}