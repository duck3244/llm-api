// StatsController.java
package com.yourcompany.llm.controller;

import com.yourcompany.llm.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatsController {
    
    private final StatsService statsService;
    
    /**
     * 일일 사용량 통계
     */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        LocalDate targetDate = date != null ? date : LocalDate.now();
        
        Map<String, Object> dailyStats = statsService.getDailyStats(targetDate);
        
        return ResponseEntity.ok(dailyStats);
    }
    
    /**
     * 주간 사용량 통계
     */
    @GetMapping("/weekly")
    public ResponseEntity<Map<String, Object>> getWeeklyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        
        LocalDate weekStart = startDate != null ? startDate : LocalDate.now().minusDays(6);
        
        Map<String, Object> weeklyStats = statsService.getWeeklyStats(weekStart);
        
        return ResponseEntity.ok(weeklyStats);
    }
    
    /**
     * 월간 사용량 통계
     */
    @GetMapping("/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        
        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();
        
        Map<String, Object> monthlyStats = statsService.getMonthlyStats(targetYear, targetMonth);
        
        return ResponseEntity.ok(monthlyStats);
    }
    
    /**
     * 사용자별 통계
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> userStats = statsService.getUserStats(fromDate, toDate, limit);
        
        return ResponseEntity.ok(userStats);
    }
    
    /**
     * 모델별 통계
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModelStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> modelStats = statsService.getModelStats(fromDate, toDate);
        
        return ResponseEntity.ok(modelStats);
    }
    
    /**
     * 프로바이더별 통계
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> providerStats = statsService.getProviderStats(fromDate, toDate);
        
        return ResponseEntity.ok(providerStats);
    }
    
    /**
     * 실시간 통계 (현재 시간 기준)
     */
    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeStats() {
        Map<String, Object> realtimeStats = statsService.getRealtimeStats();
        
        return ResponseEntity.ok(realtimeStats);
    }
    
    /**
     * 토큰 사용량 통계
     */
    @GetMapping("/tokens")
    public ResponseEntity<Map<String, Object>> getTokenStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String groupBy) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        String groupByPeriod = groupBy != null ? groupBy : "hour";
        
        Map<String, Object> tokenStats = statsService.getTokenStats(fromDateTime, toDateTime, groupByPeriod);
        
        return ResponseEntity.ok(tokenStats);
    }
    
    /**
     * 응답 시간 통계
     */
    @GetMapping("/response-time")
    public ResponseEntity<Map<String, Object>> getResponseTimeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String model) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        
        Map<String, Object> responseTimeStats = statsService.getResponseTimeStats(fromDateTime, toDateTime, model);
        
        return ResponseEntity.ok(responseTimeStats);
    }
    
    /**
     * 에러율 통계
     */
    @GetMapping("/error-rate")
    public ResponseEntity<Map<String, Object>> getErrorRateStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String groupBy) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        String groupByPeriod = groupBy != null ? groupBy : "hour";
        
        Map<String, Object> errorStats = statsService.getErrorRateStats(fromDateTime, toDateTime, groupByPeriod);
        
        return ResponseEntity.ok(errorStats);
    }
    
    /**
     * 비용 통계
     */
    @GetMapping("/costs")
    public ResponseEntity<Map<String, Object>> getCostStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        String targetCurrency = currency != null ? currency : "USD";
        
        Map<String, Object> costStats = statsService.getCostStats(fromDate, toDate, targetCurrency);
        
        return ResponseEntity.ok(costStats);
    }
    
    /**
     * 사용량 추세 분석
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getUsageTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "daily") String period) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> trends = statsService.getUsageTrends(fromDate, toDate, period);
        
        return ResponseEntity.ok(trends);
    }
    
    /**
     * 특정 사용자의 상세 통계
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUserDetailStats(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> userDetailStats = statsService.getUserDetailStats(userId, fromDate, toDate);
        
        return ResponseEntity.ok(userDetailStats);
    }
    
    /**
     * 특정 모델의 상세 통계
     */
    @GetMapping("/models/{modelName}")
    public ResponseEntity<Map<String, Object>> getModelDetailStats(
            @PathVariable String modelName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        
        Map<String, Object> modelDetailStats = statsService.getModelDetailStats(modelName, fromDateTime, toDateTime);
        
        return ResponseEntity.ok(modelDetailStats);
    }
    
    /**
     * 시스템 성능 메트릭
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        
        Map<String, Object> performanceMetrics = statsService.getPerformanceMetrics(fromDateTime, toDateTime);
        
        return ResponseEntity.ok(performanceMetrics);
    }
    
    /**
     * 대시보드용 종합 통계
     */
    @GetMapping("/dashboard")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getDashboardStats() {
        return statsService.getDashboardStats()
            .thenApply(ResponseEntity::ok);
    }
    
    /**
     * 사용량 예측
     */
    @GetMapping("/forecast")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getUsageForecast(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String model) {
        
        return statsService.getUsageForecast(days, model)
            .thenApply(ResponseEntity::ok);
    }
    
    /**
     * 알럿 조건 확인
     */
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getActiveAlerts() {
        Map<String, Object> alerts = statsService.getActiveAlerts();
        
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * 사용량 보고서 생성
     */
    @PostMapping("/reports")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateReport(
            @RequestBody Map<String, Object> reportRequest) {
        
        return statsService.generateUsageReport(reportRequest)
            .thenApply(ResponseEntity::ok);
    }
    
    /**
     * 특정 기간의 상위 사용자
     */
    @GetMapping("/top-users")
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "requests") String sortBy) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        List<Map<String, Object>> topUsers = statsService.getTopUsers(fromDate, toDate, limit, sortBy);
        
        return ResponseEntity.ok(topUsers);
    }
    
    /**
     * 특정 기간의 상위 모델
     */
    @GetMapping("/top-models")
    public ResponseEntity<List<Map<String, Object>>> getTopModels(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "requests") String sortBy) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        List<Map<String, Object>> topModels = statsService.getTopModels(fromDate, toDate, limit, sortBy);
        
        return ResponseEntity.ok(topModels);
    }
    
    /**
     * 캐시 통계
     */
    @GetMapping("/cache")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> cacheStats = statsService.getCacheStats();
        
        return ResponseEntity.ok(cacheStats);
    }
    
    /**
     * API 키별 사용량 통계
     */
    @GetMapping("/api-keys")
    public ResponseEntity<Map<String, Object>> getApiKeyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> apiKeyStats = statsService.getApiKeyStats(fromDate, toDate);
        
        return ResponseEntity.ok(apiKeyStats);
    }
    
    /**
     * 지역별 사용량 통계
     */
    @GetMapping("/regions")
    public ResponseEntity<Map<String, Object>> getRegionalStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> regionalStats = statsService.getRegionalStats(fromDate, toDate);
        
        return ResponseEntity.ok(regionalStats);
    }
    
    /**
     * 처리량 통계 (TPS - Transactions Per Second)
     */
    @GetMapping("/throughput")
    public ResponseEntity<Map<String, Object>> getThroughputStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "minute") String resolution) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(1);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        
        Map<String, Object> throughputStats = statsService.getThroughputStats(fromDateTime, toDateTime, resolution);
        
        return ResponseEntity.ok(throughputStats);
    }
    
    /**
     * 큐 상태 통계
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        Map<String, Object> queueStats = statsService.getQueueStats();
        
        return ResponseEntity.ok(queueStats);
    }
    
    /**
     * 사용량 한도 확인
     */
    @GetMapping("/limits")
    public ResponseEntity<Map<String, Object>> getUsageLimits(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String apiKey) {
        
        Map<String, Object> limits = statsService.getUsageLimits(userId, apiKey);
        
        return ResponseEntity.ok(limits);
    }
    
    /**
     * 실시간 모니터링 데이터
     */
    @GetMapping("/monitoring")
    public ResponseEntity<Map<String, Object>> getMonitoringData() {
        Map<String, Object> monitoringData = statsService.getRealtimeMonitoringData();
        
        return ResponseEntity.ok(monitoringData);
    }
    
    /**
     * 서비스 가용성 통계
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> getAvailabilityStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> availabilityStats = statsService.getAvailabilityStats(fromDate, toDate);
        
        return ResponseEntity.ok(availabilityStats);
    }
    
    /**
     * 사용 패턴 분석
     */
    @GetMapping("/patterns")
    public ResponseEntity<Map<String, Object>> getUsagePatterns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "hourly") String granularity) {
        
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate toDate = to != null ? to : LocalDate.now();
        
        Map<String, Object> patterns = statsService.getUsagePatterns(fromDate, toDate, granularity);
        
        return ResponseEntity.ok(patterns);
    }
    
    /**
     * 통계 메타데이터
     */
    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getStatsMetadata() {
        Map<String, Object> metadata = Map.of(
            "availableMetrics", List.of(
                "requests", "tokens", "costs", "response_time", "error_rate", 
                "cache_hit_rate", "throughput", "availability"
            ),
            "availableGroupings", List.of(
                "minute", "hour", "day", "week", "month"
            ),
            "availableFilters", List.of(
                "model", "provider", "user", "api_key", "region"
            ),
            "retentionPeriods", Map.of(
                "realtime", "1 hour",
                "hourly", "7 days", 
                "daily", "90 days",
                "monthly", "2 years"
            ),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * 통계 데이터 내보내기
     */
    @PostMapping("/export")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> exportStats(
            @RequestBody Map<String, Object> exportRequest) {
        
        return statsService.exportStats(exportRequest)
            .thenApply(exportResult -> ResponseEntity.ok(exportResult))
            .exceptionally(throwable -> {
                log.error("Error exporting stats", throwable);
                Map<String, Object> error = Map.of(
                    "error", "Export failed",
                    "message", throwable.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.status(500).body(error);
            });
    }
    
    /**
     * 통계 데이터 정리 (관리자용)
     */
    @DeleteMapping("/cleanup")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> cleanupStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        
        return statsService.cleanupOldStats(before, dryRun)
            .thenApply(cleanupResult -> {
                Map<String, Object> response = Map.of(
                    "cleaned", cleanupResult.get("recordsDeleted"),
                    "dryRun", dryRun,
                    "cutoffDate", before,
                    "message", dryRun ? "Dry run completed" : "Cleanup completed",
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            });
    }
    
    /**
     * 사용자 정의 메트릭 조회
     */
    @GetMapping("/custom/{metricName}")
    public ResponseEntity<Map<String, Object>> getCustomMetric(
            @PathVariable String metricName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Map<String, String> filters) {
        
        LocalDateTime fromDateTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toDateTime = to != null ? to : LocalDateTime.now();
        
        Map<String, Object> customMetric = statsService.getCustomMetric(
            metricName, fromDateTime, toDateTime, filters);
        
        return ResponseEntity.ok(customMetric);
    }
    
    /**
     * 비교 통계 (기간 대 기간)
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1End,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2End,
            @RequestParam(defaultValue = "requests") String metric) {
        
        Map<String, Object> comparison = statsService.compareStats(
            period1Start, period1End, period2Start, period2End, metric);
        
        return ResponseEntity.ok(comparison);
    }
    
    // ===== 헬스 체크 =====
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "Stats API",
            "timestamp", LocalDateTime.now(),
            "dataRetention", "90 days",
            "metricsCollected", statsService.getCollectedMetricsCount()
        );
        
        return ResponseEntity.ok(health);
    }
    
    // ===== 에러 핸들링 =====
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Error in stats API", e);
        
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
        