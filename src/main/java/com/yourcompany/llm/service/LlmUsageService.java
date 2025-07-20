// LlmUsageService.java
package com.yourcompany.llm.service.impl;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.entity.UsageEntity;
import com.yourcompany.llm.entity.UsageEntity.AggregationType;
import com.yourcompany.llm.repository.UsageRepository;
import com.yourcompany.llm.service.LlmUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsageService {
    
    private final UsageRepository usageRepository;
    
    // 실시간 메트릭 누적
    private final Map<String, UsageAccumulator> realTimeAccumulators = new ConcurrentHashMap<>();
    
    // 스트리밍 메트릭
    private final Map<String, AtomicLong> streamingMetrics = new ConcurrentHashMap<>();
    
    // 임베딩 메트릭
    private final Map<String, AtomicLong> embeddingMetrics = new ConcurrentHashMap<>();
    
    /**
     * 사용량 기록 (비동기)
     */
    @Async
    public CompletableFuture<Void> recordUsage(LlmRequest request, LlmResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                String userId = request.getUser();
                String model = response.getModel();
                String provider = response.getProvider();
                LocalDate today = LocalDate.now();
                
                // 실시간 누적기에 추가
                String accumulatorKey = generateAccumulatorKey(userId, model, provider, today);
                UsageAccumulator accumulator = realTimeAccumulators.computeIfAbsent(
                    accumulatorKey, k -> new UsageAccumulator(userId, model, provider, today));
                
                // 메트릭 누적
                accumulator.addUsage(request, response);
                
                log.debug("Recorded usage for user: {}, model: {}, tokens: {}", 
                    userId, model, response.getTokensUsed());
                
            } catch (Exception e) {
                log.error("Failed to record usage", e);
            }
        });
    }
    
    /**
     * 스트리밍 청크 기록
     */
    public void recordStreamingChunk(String model, int chunkSize) {
        String key = "streaming:" + model;
        streamingMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(chunkSize);
    }
    
    /**
     * 임베딩 요청 기록
     */
    public void recordEmbeddingRequest(String model, int textLength) {
        String key = "embedding:" + model;
        embeddingMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        String lengthKey = "embedding_length:" + model;
        embeddingMetrics.computeIfAbsent(lengthKey, k -> new AtomicLong(0)).addAndGet(textLength);
    }
    
    /**
     * 실시간 메트릭 플러시 (5분마다)
     */
    @Scheduled(fixedRate = 300000) // 5분
    @Transactional
    public void flushRealTimeMetrics() {
        log.debug("Flushing real-time metrics to database");
        
        try {
            // 누적기 스냅샷 생성 및 초기화
            Map<String, UsageAccumulator> snapshot = new HashMap<>(realTimeAccumulators);
            realTimeAccumulators.clear();
            
            // 각 누적기의 데이터를 데이터베이스에 저장
            for (UsageAccumulator accumulator : snapshot.values()) {
                if (accumulator.hasData()) {
                    saveOrUpdateUsage(accumulator);
                }
            }
            
            log.debug("Flushed {} usage accumulators", snapshot.size());
            
        } catch (Exception e) {
            log.error("Failed to flush real-time metrics", e);
        }
    }
    
    /**
     * 일일 집계 생성 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void generateDailyAggregates() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Generating daily aggregates for date: {}", yesterday);
        
        try {
            // 시간별 데이터를 일일 데이터로 집계
            aggregateHourlyToDaily(yesterday);
            
            // 사용자별, 모델별, 프로바이더별 집계
            generateUserDailyAggregates(yesterday);
            generateModelDailyAggregates(yesterday);
            generateProviderDailyAggregates(yesterday);
            
            log.info("Completed daily aggregates for date: {}", yesterday);
            
        } catch (Exception e) {
            log.error("Failed to generate daily aggregates", e);
        }
    }
    
    /**
     * 주간 집계 생성 (매주 월요일)
     */
    @Scheduled(cron = "0 0 1 * * MON") // 매주 월요일 1시
    @Transactional
    public void generateWeeklyAggregates() {
        LocalDate lastWeekStart = LocalDate.now().minusDays(7);
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);
        
        log.info("Generating weekly aggregates for period: {} to {}", lastWeekStart, lastWeekEnd);
        
        try {
            generateAggregatesByPeriod(lastWeekStart, lastWeekEnd, AggregationType.WEEKLY);
            log.info("Completed weekly aggregates");
            
        } catch (Exception e) {
            log.error("Failed to generate weekly aggregates", e);
        }
    }
    
    /**
     * 월간 집계 생성 (매월 1일)
     */
    @Scheduled(cron = "0 0 2 1 * *") // 매월 1일 2시
    @Transactional
    public void generateMonthlyAggregates() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        LocalDate monthStart = lastMonth.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        
        log.info("Generating monthly aggregates for period: {} to {}", monthStart, monthEnd);
        
        try {
            generateAggregatesByPeriod(monthStart, monthEnd, AggregationType.MONTHLY);
            log.info("Completed monthly aggregates");
            
        } catch (Exception e) {
            log.error("Failed to generate monthly aggregates", e);
        }
    }
    
    /**
     * 사용량 통계 조회
     */
    public Map<String, Object> getUsageStatistics(String userId, LocalDate from, LocalDate to) {
        try {
            List<UsageEntity> usageData = usageRepository.findByUserIdAndUsageDateBetweenAndAggregationType(
                userId, from, to, AggregationType.DAILY);
            
            return calculateStatistics(usageData);
            
        } catch (Exception e) {
            log.error("Failed to get usage statistics", e);
            return Map.of("error", "Failed to retrieve statistics");
        }
    }
    
    /**
     * 모델별 사용량 통계
     */
    public Map<String, Object> getModelUsageStatistics(LocalDate from, LocalDate to) {
        try {
            List<Object[]> modelStats = usageRepository.findModelUsageStatistics(
                from, to, AggregationType.DAILY);
            
            return processModelStatistics(modelStats);
            
        } catch (Exception e) {
            log.error("Failed to get model usage statistics", e);
            return Map.of("error", "Failed to retrieve model statistics");
        }
    }
    
    /**
     * 비용 분석
     */
    public Map<String, Object> getCostAnalysis(String userId, LocalDate from, LocalDate to) {
        try {
            Double totalCost = usageRepository.sumTotalCostByUserAndDateRange(
                userId, from, to, AggregationType.DAILY);
            
            List<UsageEntity> dailyCosts = usageRepository.findByUserIdAndUsageDateBetweenAndAggregationType(
                userId, from, to, AggregationType.DAILY);
            
            return Map.of(
                "totalCost", totalCost != null ? totalCost : 0.0,
                "currency", "USD",
                "period", Map.of("from", from, "to", to),
                "dailyCosts", dailyCosts.stream()
                    .collect(Collectors.toMap(
                        usage -> usage.getUsageDate().toString(),
                        UsageEntity::getTotalCost,
                        Double::sum)),
                "averageDailyCost", calculateAverageDailyCost(dailyCosts),
                "costByModel", calculateCostByModel(dailyCosts)
            );
            
        } catch (Exception e) {
            log.error("Failed to get cost analysis", e);
            return Map.of("error", "Failed to retrieve cost analysis");
        }
    }
    
    /**
     * 사용량 예측
     */
    public Map<String, Object> predictUsage(String userId, int futureDays) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate historicalStart = today.minusDays(30); // 30일 히스토리
            
            List<UsageEntity> historicalData = usageRepository.findByUserIdAndUsageDateBetweenAndAggregationType(
                userId, historicalStart, today, AggregationType.DAILY);
            
            return generateUsagePrediction(historicalData, futureDays);
            
        } catch (Exception e) {
            log.error("Failed to predict usage", e);
            return Map.of("error", "Failed to generate prediction");
        }
    }
    
    /**
     * 사용량 한도 확인
     */
    public Map<String, Object> checkUsageLimits(String userId, String model) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            
            // 월간 사용량 조회
            List<UsageEntity> monthlyUsage = usageRepository.findByUserIdAndUsageDateBetweenAndAggregationType(
                userId, monthStart, today, AggregationType.DAILY);
            
            long totalRequests = monthlyUsage.stream()
                .mapToLong(UsageEntity::getTotalRequests)
                .sum();
            
            long totalTokens = monthlyUsage.stream()
                .mapToLong(UsageEntity::getTotalTokens)
                .sum();
            
            double totalCost = monthlyUsage.stream()
                .mapToDouble(usage -> usage.getTotalCost() != null ? usage.getTotalCost() : 0.0)
                .sum();
            
            // 한도 설정 (실제로는 사용자별 설정에서 가져와야 함)
            long requestLimit = 10000; // 월 10,000 요청
            long tokenLimit = 1000000;  // 월 1M 토큰
            double costLimit = 100.0;   // 월 $100
            
            return Map.of(
                "userId", userId,
                "model", model,
                "period", "monthly",
                "usage", Map.of(
                    "requests", Map.of(
                        "used", totalRequests,
                        "limit", requestLimit,
                        "remaining", Math.max(0, requestLimit - totalRequests),
                        "percentage", (double) totalRequests / requestLimit * 100
                    ),
                    "tokens", Map.of(
                        "used", totalTokens,
                        "limit", tokenLimit,
                        "remaining", Math.max(0, tokenLimit - totalTokens),
                        "percentage", (double) totalTokens / tokenLimit * 100
                    ),
                    "cost", Map.of(
                        "used", totalCost,
                        "limit", costLimit,
                        "remaining", Math.max(0, costLimit - totalCost),
                        "percentage", totalCost / costLimit * 100
                    )
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to check usage limits", e);
            return Map.of("error", "Failed to check limits");
        }
    }
    
    /**
     * 실시간 사용량 조회
     */
    public Map<String, Object> getRealTimeUsage() {
        Map<String, Object> realTimeData = new HashMap<>();
        
        // 실시간 누적기 데이터
        Map<String, Object> accumulatorData = realTimeAccumulators.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getSummary()
            ));
        
        realTimeData.put("accumulators", accumulatorData);
        
        // 스트리밍 메트릭
        Map<String, Long> streamingData = streamingMetrics.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        
        realTimeData.put("streaming", streamingData);
        
        // 임베딩 메트릭
        Map<String, Long> embeddingData = embeddingMetrics.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        
        realTimeData.put("embeddings", embeddingData);
        
        realTimeData.put("timestamp", LocalDateTime.now());
        
        return realTimeData;
    }
    
    /**
     * 사용량 경고 확인
     */
    public List<Map<String, Object>> checkUsageAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        try {
            LocalDate today = LocalDate.now();
            
            // 비정상적으로 높은 사용량 확인
            List<Object[]> topUsers = usageRepository.findTopUsersByUsage(
                today, today, AggregationType.DAILY, 
                org.springframework.data.domain.PageRequest.of(0, 10));
            
            for (Object[] userData : topUsers) {
                String userId = (String) userData[0];
                Long requests = (Long) userData[1];
                
                if (requests > 1000) { // 일일 1000회 초과
                    alerts.add(Map.of(
                        "type", "HIGH_USAGE",
                        "userId", userId,
                        "requests", requests,
                        "date", today,
                        "severity", "WARNING"
                    ));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check usage alerts", e);
        }
        
        return alerts;
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private String generateAccumulatorKey(String userId, String model, String provider, LocalDate date) {
        return String.format("%s:%s:%s:%s", userId, model, provider, date);
    }
    
    @Transactional
    private void saveOrUpdateUsage(UsageAccumulator accumulator) {
        try {
            // 기존 사용량 엔티티 조회 또는 생성
            Optional<UsageEntity> existingUsage = usageRepository.findByUserIdAndModelAndUsageDateAndAggregationType(
                accumulator.getUserId(), accumulator.getModel(), 
                accumulator.getDate(), AggregationType.DAILY);
            
            UsageEntity usageEntity;
            if (existingUsage.isPresent()) {
                usageEntity = existingUsage.get();
                // 기존 데이터와 병합
                usageEntity = usageEntity.merge(accumulator.toUsageEntity());
            } else {
                usageEntity = accumulator.toUsageEntity();
                usageEntity.setAggregationType(AggregationType.DAILY);
            }
            
            usageRepository.save(usageEntity);
            
        } catch (Exception e) {
            log.error("Failed to save or update usage", e);
        }
    }
    
    private void aggregateHourlyToDaily(LocalDate date) {
        // 시간별 데이터가 있다면 일일로 집계하는 로직
        // 현재는 실시간 누적기에서 바로 일일 데이터를 생성하므로 생략
    }
    
    private void generateUserDailyAggregates(LocalDate date) {
        // 사용자별 일일 집계 생성
        List<Object[]> userStats = usageRepository.findTopUsersByUsage(
            date, date, AggregationType.DAILY,
            org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
        
        // 필요시 추가 집계 로직 구현
    }
    
    private void generateModelDailyAggregates(LocalDate date) {
        // 모델별 일일 집계 생성
        List<Object[]> modelStats = usageRepository.findModelUsageStatistics(
            date, date, AggregationType.DAILY);
        
        // 필요시 추가 집계 로직 구현
    }
    
    private void generateProviderDailyAggregates(LocalDate date) {
        // 프로바이더별 일일 집계 생성
        List<Object[]> providerStats = usageRepository.findProviderUsageStatistics(
            date, date, AggregationType.DAILY);
        
        // 필요시 추가 집계 로직 구현
    }
    
    private void generateAggregatesByPeriod(LocalDate startDate, LocalDate endDate, AggregationType aggregationType) {
        // 기간별 집계 생성 로직
        List<UsageEntity> dailyData = usageRepository.findByUsageDateBetweenAndAggregationType(
            startDate, endDate, AggregationType.DAILY,
            org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        
        // 사용자별, 모델별, 프로바이더별로 그룹핑하여 집계
        Map<String, List<UsageEntity>> groupedData = dailyData.stream()
            .collect(Collectors.groupingBy(usage -> 
                usage.getUserId() + ":" + usage.getModel() + ":" + usage.getProvider()));
        
        for (Map.Entry<String, List<UsageEntity>> entry : groupedData.entrySet()) {
            try {
                String[] parts = entry.getKey().split(":");
                String userId = parts[0];
                String model = parts[1];
                String provider = parts[2];
                
                UsageEntity aggregated = entry.getValue().stream()
                    .reduce(UsageEntity.empty(userId, model, provider, startDate, aggregationType),
                            UsageEntity::merge);
                
                aggregated.setUsageDate(startDate);
                aggregated.setAggregationType(aggregationType);
                
                usageRepository.save(aggregated);
                
            } catch (Exception e) {
                log.error("Failed to generate aggregate for group: {}", entry.getKey(), e);
            }
        }
    }
    
    private Map<String, Object> calculateStatistics(List<UsageEntity> usageData) {
        if (usageData.isEmpty()) {
            return Map.of();
        }
        
        long totalRequests = usageData.stream().mapToLong(UsageEntity::getTotalRequests).sum();
        long totalTokens = usageData.stream().mapToLong(UsageEntity::getTotalTokens).sum();
        double totalCost = usageData.stream()
            .mapToDouble(usage -> usage.getTotalCost() != null ? usage.getTotalCost() : 0.0).sum();
        
        double avgResponseTime = usageData.stream()
            .filter(usage -> usage.getAverageResponseTimeMs() != null)
            .mapToDouble(UsageEntity::getAverageResponseTimeMs)
            .average().orElse(0.0);
        
        return Map.of(
            "totalRequests", totalRequests,
            "totalTokens", totalTokens,
            "totalCost", totalCost,
            "averageResponseTime", avgResponseTime,
            "period", Map.of(
                "from", usageData.stream().map(UsageEntity::getUsageDate).min(LocalDate::compareTo).orElse(null),
                "to", usageData.stream().map(UsageEntity::getUsageDate).max(LocalDate::compareTo).orElse(null)
            )
        );
    }
    
    private Map<String, Object> processModelStatistics(List<Object[]> modelStats) {
        Map<String, Object> result = new HashMap<>();
        
        for (Object[] stat : modelStats) {
            String model = (String) stat[0];
            String provider = (String) stat[1];
            Long requests = (Long) stat[2];
            Long tokens = (Long) stat[3];
            Double cost = (Double) stat[4];
            Double avgResponseTime = (Double) stat[5];
            Double avgErrorRate = (Double) stat[6];
            
            result.put(model, Map.of(
                "provider", provider,
                "requests", requests,
                "tokens", tokens,
                "cost", cost,
                "averageResponseTime", avgResponseTime,
                "averageErrorRate", avgErrorRate
            ));
        }
        
        return result;
    }
    
    private double calculateAverageDailyCost(List<UsageEntity> dailyCosts) {
        return dailyCosts.stream()
            .filter(usage -> usage.getTotalCost() != null)
            .mapToDouble(UsageEntity::getTotalCost)
            .average().orElse(0.0);
    }
    
    private Map<String, Double> calculateCostByModel(List<UsageEntity> usageData) {
        return usageData.stream()
            .filter(usage -> usage.getTotalCost() != null)
            .collect(Collectors.groupingBy(
                UsageEntity::getModel,
                Collectors.summingDouble(usage -> usage.getTotalCost() != null ? usage.getTotalCost() : 0.0)
            ));
    }
    
    private Map<String, Object> generateUsagePrediction(List<UsageEntity> historicalData, int futureDays) {
        if (historicalData.isEmpty()) {
            return Map.of("prediction", "No historical data available");
        }
        
        // 간단한 선형 트렌드 예측
        double avgDailyRequests = historicalData.stream()
            .mapToDouble(usage -> usage.getTotalRequests().doubleValue())
            .average().orElse(0.0);
        
        double avgDailyTokens = historicalData.stream()
            .mapToDouble(usage -> usage.getTotalTokens().doubleValue())
            .average().orElse(0.0);
        
        double avgDailyCost = historicalData.stream()
            .filter(usage -> usage.getTotalCost() != null)
            .mapToDouble(UsageEntity::getTotalCost)
            .average().orElse(0.0);
        
        return Map.of(
            "predictionDays", futureDays,
            "predictedUsage", Map.of(
                "dailyRequests", avgDailyRequests,
                "dailyTokens", avgDailyTokens,
                "dailyCost", avgDailyCost,
                "totalRequests", avgDailyRequests * futureDays,
                "totalTokens", avgDailyTokens * futureDays,
                "totalCost", avgDailyCost * futureDays
            ),
            "confidence", "LOW", // 간단한 평균 기반이므로 낮은 신뢰도
            "basedOnDays", historicalData.size()
        );
    }
    
    // ===== 내부 클래스 =====
    
    private static class UsageAccumulator {
        private final String userId;
        private final String model;
        private final String provider;
        private final LocalDate date;
        
        private long totalRequests = 0;
        private long successfulRequests = 0;
        private long failedRequests = 0;
        private long cachedRequests = 0;
        private long totalInputTokens = 0;
        private long totalOutputTokens = 0;
        private long totalTokens = 0;
        private double totalCost = 0.0;
        private long totalResponseTime = 0;
        private final List<Long> responseTimes = new ArrayList<>();
        
        public UsageAccumulator(String userId, String model, String provider, LocalDate date) {
            this.userId = userId;
            this.model = model;
            this.provider = provider;
            this.date = date;
        }
        
        public synchronized void addUsage(LlmRequest request, LlmResponse response) {
            totalRequests++;
            
            if (response.isSuccess()) {
                successfulRequests++;
            } else {
                failedRequests++;
            }
            
            if (response.isCached()) {
                cachedRequests++;
            }
            
            if (response.getUsage() != null) {
                if (response.getUsage().getPromptTokens() != null) {
                    totalInputTokens += response.getUsage().getPromptTokens();
                }
                if (response.getUsage().getCompletionTokens() != null) {
                    totalOutputTokens += response.getUsage().getCompletionTokens();
                }
                if (response.getUsage().getTotalTokens() != null) {
                    totalTokens += response.getUsage().getTotalTokens();
                }
                if (response.getUsage().getEstimatedCost() != null) {
                    totalCost += response.getUsage().getEstimatedCost();
                }
            }
            
            if (response.getResponseTimeMs() != null) {
                totalResponseTime += response.getResponseTimeMs();
                responseTimes.add(response.getResponseTimeMs());
            }
        }
        
        public boolean hasData() {
            return totalRequests > 0;
        }
        
        public UsageEntity toUsageEntity() {
            double avgResponseTime = responseTimes.isEmpty() ? 0.0 : 
                responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            Long minResponseTime = responseTimes.isEmpty() ? null :
                responseTimes.stream().mapToLong(Long::longValue).min().orElse(0L);
            
            Long maxResponseTime = responseTimes.isEmpty() ? null :
                responseTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
            
            double errorRate = totalRequests > 0 ? (double) failedRequests / totalRequests : 0.0;
            double cacheHitRate = totalRequests > 0 ? (double) cachedRequests / totalRequests : 0.0;
            
            return UsageEntity.builder()
                .userId(userId)
                .model(model)
                .provider(provider)
                .usageDate(date)
                .aggregationType(AggregationType.DAILY)
                .totalRequests(totalRequests)
                .successfulRequests(successfulRequests)
                .failedRequests(failedRequests)
                .cachedRequests(cachedRequests)
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .totalTokens(totalTokens)
                .totalCost(totalCost)
                .currency("USD")
                .totalResponseTimeMs(totalResponseTime)
                .averageResponseTimeMs(avgResponseTime)
                .minResponseTimeMs(minResponseTime)
                .maxResponseTimeMs(maxResponseTime)
                .averageInputTokens(totalRequests > 0 ? (double) totalInputTokens / totalRequests : 0.0)
                .averageOutputTokens(totalRequests > 0 ? (double) totalOutputTokens / totalRequests : 0.0)
                .averageTokens(totalRequests > 0 ? (double) totalTokens / totalRequests : 0.0)
                .averageCost(totalRequests > 0 ? totalCost / totalRequests : 0.0)
                .errorRate(errorRate)
                .cacheHitRate(cacheHitRate)
                .build();
        }
        
        public Map<String, Object> getSummary() {
            return Map.of(
                "userId", userId,
                "model", model,
                "provider", provider,
                "date", date,
                "totalRequests", totalRequests,
                "successfulRequests", successfulRequests,
                "totalTokens", totalTokens,
                "totalCost", totalCost
            );
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getModel() { return model; }
        public String getProvider() { return provider; }
        public LocalDate getDate() { return date; }
    }
}