// UsageEntity.java
package com.yourcompany.llm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "usage_statistics", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_api_key_hash", columnList = "apiKeyHash"),
    @Index(name = "idx_model", columnList = "model"),
    @Index(name = "idx_provider", columnList = "provider"),
    @Index(name = "idx_usage_date", columnList = "usageDate"),
    @Index(name = "idx_usage_hour", columnList = "usageHour"),
    @Index(name = "idx_aggregation_type", columnList = "aggregationType"),
    @Index(name = "idx_user_model_date", columnList = "userId,model,usageDate"),
    @Index(name = "idx_provider_date", columnList = "provider,usageDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 사용자 ID
     */
    @Column(length = 100)
    private String userId;
    
    /**
     * API 키 해시
     */
    @Column(length = 64)
    private String apiKeyHash;
    
    /**
     * 모델명
     */
    @Column(length = 100)
    private String model;
    
    /**
     * 프로바이더명
     */
    @Column(length = 50)
    private String provider;
    
    /**
     * 사용 날짜
     */
    @Column(nullable = false)
    private LocalDate usageDate;
    
    /**
     * 사용 시간 (0-23)
     */
    private Integer usageHour;
    
    /**
     * 집계 타입 (hourly, daily, weekly, monthly)
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AggregationType aggregationType;
    
    /**
     * 총 요청 수
     */
    @Column(nullable = false)
    private Long totalRequests;
    
    /**
     * 성공한 요청 수
     */
    @Column(nullable = false)
    private Long successfulRequests;
    
    /**
     * 실패한 요청 수
     */
    @Column(nullable = false)
    private Long failedRequests;
    
    /**
     * 캐시에서 처리된 요청 수
     */
    @Column(nullable = false)
    private Long cachedRequests;
    
    /**
     * 총 입력 토큰 수
     */
    @Column(nullable = false)
    private Long totalInputTokens;
    
    /**
     * 총 출력 토큰 수
     */
    @Column(nullable = false)
    private Long totalOutputTokens;
    
    /**
     * 총 토큰 수
     */
    @Column(nullable = false)
    private Long totalTokens;
    
    /**
     * 총 예상 비용
     */
    @Column(precision = 12, scale = 6)
    private Double totalCost;
    
    /**
     * 통화
     */
    @Column(length = 3)
    private String currency;
    
    /**
     * 총 응답 시간 (밀리초)
     */
    private Long totalResponseTimeMs;
    
    /**
     * 평균 응답 시간 (밀리초)
     */
    private Double averageResponseTimeMs;
    
    /**
     * 최소 응답 시간 (밀리초)
     */
    private Long minResponseTimeMs;
    
    /**
     * 최대 응답 시간 (밀리초)
     */
    private Long maxResponseTimeMs;
    
    /**
     * 응답 시간 표준편차
     */
    private Double responseTimeStdDev;
    
    /**
     * 평균 입력 토큰 수
     */
    private Double averageInputTokens;
    
    /**
     * 평균 출력 토큰 수
     */
    private Double averageOutputTokens;
    
    /**
     * 평균 토큰 수
     */
    private Double averageTokens;
    
    /**
     * 평균 비용
     */
    private Double averageCost;
    
    /**
     * 에러율 (0.0 ~ 1.0)
     */
    @Column(precision = 5, scale = 4)
    private Double errorRate;
    
    /**
     * 캐시 히트율 (0.0 ~ 1.0)
     */
    @Column(precision = 5, scale = 4)
    private Double cacheHitRate;
    
    /**
     * 처리량 (초당 요청 수)
     */
    private Double throughputRps;
    
    /**
     * 동시 요청 수 (최대)
     */
    private Integer maxConcurrentRequests;
    
    /**
     * 평균 동시 요청 수
     */
    private Double averageConcurrentRequests;
    
    /**
     * 고유 사용자 수 (집계 기간 내)
     */
    private Integer uniqueUsers;
    
    /**
     * 고유 IP 수 (집계 기간 내)
     */
    private Integer uniqueIps;
    
    /**
     * 국가 코드
     */
    @Column(length = 2)
    private String countryCode;
    
    /**
     * 지역 코드
     */
    @Column(length = 10)
    private String region;
    
    /**
     * 평균 품질 점수
     */
    @Column(precision = 3, scale = 2)
    private Double averageQualityScore;
    
    /**
     * 평균 만족도 점수
     */
    @Column(precision = 3, scale = 2)
    private Double averageSatisfactionScore;
    
    /**
     * 스트리밍 요청 수
     */
    private Long streamingRequests;
    
    /**
     * 함수 호출 요청 수
     */
    private Long functionCallRequests;
    
    /**
     * 이미지 요청 수
     */
    private Long imageRequests;
    
    /**
     * 오디오 요청 수
     */
    private Long audioRequests;
    
    /**
     * 임베딩 요청 수
     */
    private Long embeddingRequests;
    
    /**
     * 세션 수 (고유 대화 수)
     */
    private Integer sessionCount;
    
    /**
     * 평균 세션 길이 (메시지 수)
     */
    private Double averageSessionLength;
    
    /**
     * 최대 세션 길이
     */
    private Integer maxSessionLength;
    
    /**
     * 커스텀 메트릭 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String customMetrics;
    
    /**
     * 추가 메타데이터 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * 생성 시간
     */
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 수정 시간
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    /**
     * 집계 타입 열거형
     */
    public enum AggregationType {
        HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
    }
    
    // 유틸리티 메서드들
    
    /**
     * 성공률 계산
     */
    public Double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return null;
        }
        return (double) successfulRequests / totalRequests;
    }
    
    /**
     * 실패률 계산
     */
    public Double getFailureRate() {
        if (totalRequests == null || totalRequests == 0) {
            return null;
        }
        return (double) failedRequests / totalRequests;
    }
    
    /**
     * 토큰 효율성 계산 (출력/입력 비율)
     */
    public Double getTokenEfficiency() {
        if (totalInputTokens == null || totalInputTokens == 0 || totalOutputTokens == null) {
            return null;
        }
        return (double) totalOutputTokens / totalInputTokens;
    }
    
    /**
     * 토큰당 평균 비용 계산
     */
    public Double getCostPerToken() {
        if (totalTokens == null || totalTokens == 0 || totalCost == null) {
            return null;
        }
        return totalCost / totalTokens;
    }
    
    /**
     * 요청당 평균 비용 계산
     */
    public Double getCostPerRequest() {
        if (totalRequests == null || totalRequests == 0 || totalCost == null) {
            return null;
        }
        return totalCost / totalRequests;
    }
    
    /**
     * 평균 토큰/초 계산
     */
    public Double getTokensPerSecond() {
        if (averageResponseTimeMs == null || averageResponseTimeMs == 0 || averageOutputTokens == null) {
            return null;
        }
        return averageOutputTokens / (averageResponseTimeMs / 1000.0);
    }
    
    /**
     * 사용률 증가/감소 계산 (이전 기간 대비)
     */
    public Double calculateGrowthRate(UsageEntity previousPeriod) {
        if (previousPeriod == null || previousPeriod.getTotalRequests() == 0) {
            return null;
        }
        
        return ((double) (this.totalRequests - previousPeriod.getTotalRequests()) / 
                previousPeriod.getTotalRequests()) * 100.0;
    }
    
    /**
     * 비용 증가/감소 계산 (이전 기간 대비)
     */
    public Double calculateCostGrowthRate(UsageEntity previousPeriod) {
        if (previousPeriod == null || previousPeriod.getTotalCost() == null || 
            previousPeriod.getTotalCost() == 0 || this.totalCost == null) {
            return null;
        }
        
        return ((this.totalCost - previousPeriod.getTotalCost()) / 
                previousPeriod.getTotalCost()) * 100.0;
    }
    
    /**
     * 일일 사용량인지 확인
     */
    public boolean isDailyUsage() {
        return AggregationType.DAILY.equals(aggregationType);
    }
    
    /**
     * 시간별 사용량인지 확인
     */
    public boolean isHourlyUsage() {
        return AggregationType.HOURLY.equals(aggregationType);
    }
    
    /**
     * 주간 사용량인지 확인
     */
    public boolean isWeeklyUsage() {
        return AggregationType.WEEKLY.equals(aggregationType);
    }
    
    /**
     * 월간 사용량인지 확인
     */
    public boolean isMonthlyUsage() {
        return AggregationType.MONTHLY.equals(aggregationType);
    }
    
    /**
     * 사용량 통계를 다른 UsageEntity와 병합
     */
    public UsageEntity merge(UsageEntity other) {
        if (other == null) {
            return this;
        }
        
        UsageEntity merged = new UsageEntity();
        merged.setUserId(this.userId != null ? this.userId : other.userId);
        merged.setModel(this.model != null ? this.model : other.model);
        merged.setProvider(this.provider != null ? this.provider : other.provider);
        merged.setUsageDate(this.usageDate != null ? this.usageDate : other.usageDate);
        merged.setAggregationType(this.aggregationType != null ? this.aggregationType : other.aggregationType);
        
        // 숫자 값들 합계
        merged.setTotalRequests((this.totalRequests != null ? this.totalRequests : 0L) + 
                               (other.totalRequests != null ? other.totalRequests : 0L));
        merged.setSuccessfulRequests((this.successfulRequests != null ? this.successfulRequests : 0L) + 
                                   (other.successfulRequests != null ? other.successfulRequests : 0L));
        merged.setFailedRequests((this.failedRequests != null ? this.failedRequests : 0L) + 
                                (other.failedRequests != null ? other.failedRequests : 0L));
        merged.setCachedRequests((this.cachedRequests != null ? this.cachedRequests : 0L) + 
                                (other.cachedRequests != null ? other.cachedRequests : 0L));
        merged.setTotalInputTokens((this.totalInputTokens != null ? this.totalInputTokens : 0L) + 
                                  (other.totalInputTokens != null ? other.totalInputTokens : 0L));
        merged.setTotalOutputTokens((this.totalOutputTokens != null ? this.totalOutputTokens : 0L) + 
                                   (other.totalOutputTokens != null ? other.totalOutputTokens : 0L));
        merged.setTotalTokens((this.totalTokens != null ? this.totalTokens : 0L) + 
                             (other.totalTokens != null ? other.totalTokens : 0L));
        merged.setTotalCost((this.totalCost != null ? this.totalCost : 0.0) + 
                           (other.totalCost != null ? other.totalCost : 0.0));
        
        // 평균값 재계산
        long totalReqs = merged.getTotalRequests();
        if (totalReqs > 0) {
            merged.setAverageResponseTimeMs(
                ((this.totalResponseTimeMs != null ? this.totalResponseTimeMs : 0L) + 
                 (other.totalResponseTimeMs != null ? other.totalResponseTimeMs : 0L)) / (double) totalReqs);
            merged.setAverageTokens(merged.getTotalTokens() / (double) totalReqs);
            merged.setAverageCost(merged.getTotalCost() / (double) totalReqs);
        }
        
        // 최대/최소값
        merged.setMaxResponseTimeMs(Math.max(
            this.maxResponseTimeMs != null ? this.maxResponseTimeMs : 0L,
            other.maxResponseTimeMs != null ? other.maxResponseTimeMs : 0L));
        merged.setMinResponseTimeMs(Math.min(
            this.minResponseTimeMs != null ? this.minResponseTimeMs : Long.MAX_VALUE,
            other.minResponseTimeMs != null ? other.minResponseTimeMs : Long.MAX_VALUE));
        
        return merged;
    }
    
    /**
     * 요약 정보 반환
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", id);
        summary.put("userId", userId);
        summary.put("model", model);
        summary.put("provider", provider);
        summary.put("usageDate", usageDate);
        summary.put("aggregationType", aggregationType);
        summary.put("totalRequests", totalRequests);
        summary.put("successfulRequests", successfulRequests);
        summary.put("totalTokens", totalTokens);
        summary.put("totalCost", totalCost);
        summary.put("averageResponseTime", averageResponseTimeMs);
        summary.put("errorRate", errorRate);
        summary.put("cacheHitRate", cacheHitRate);
        
        return summary;
    }
    
    /**
     * 성능 메트릭 반환
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("throughputRps", throughputRps);
        metrics.put("averageResponseTime", averageResponseTimeMs);
        metrics.put("errorRate", errorRate);
        metrics.put("cacheHitRate", cacheHitRate);
        metrics.put("tokenEfficiency", getTokenEfficiency());
        metrics.put("costPerToken", getCostPerToken());
        metrics.put("tokensPerSecond", getTokensPerSecond());
        metrics.put("averageQualityScore", averageQualityScore);
        
        return metrics;
    }
    
    /**
     * 비용 분석 반환
     */
    public Map<String, Object> getCostAnalysis() {
        Map<String, Object> analysis = new java.util.HashMap<>();
        analysis.put("totalCost", totalCost);
        analysis.put("currency", currency);
        analysis.put("costPerRequest", getCostPerRequest());
        analysis.put("costPerToken", getCostPerToken());
        analysis.put("averageCost", averageCost);
        
        return analysis;
    }
    
    /**
     * 사용량 검증
     */
    public boolean isValid() {
        return totalRequests != null && totalRequests >= 0 &&
               successfulRequests != null && successfulRequests >= 0 &&
               failedRequests != null && failedRequests >= 0 &&
               totalRequests.equals(successfulRequests + failedRequests) &&
               usageDate != null && aggregationType != null;
    }
    
    /**
     * 빈 사용량 데이터 생성
     */
    public static UsageEntity empty(String userId, String model, String provider, 
                                  LocalDate date, AggregationType type) {
        return UsageEntity.builder()
            .userId(userId)
            .model(model)
            .provider(provider)
            .usageDate(date)
            .aggregationType(type)
            .totalRequests(0L)
            .successfulRequests(0L)
            .failedRequests(0L)
            .cachedRequests(0L)
            .totalInputTokens(0L)
            .totalOutputTokens(0L)
            .totalTokens(0L)
            .totalCost(0.0)
            .currency("USD")
            .build();
    }
    
    /**
     * 특정 메트릭으로 정렬을 위한 값 반환
     */
    public Double getSortValue(String metric) {
        return switch (metric.toLowerCase()) {
            case "requests" -> totalRequests.doubleValue();
            case "tokens" -> totalTokens.doubleValue();
            case "cost" -> totalCost;
            case "response_time" -> averageResponseTimeMs;
            case "error_rate" -> errorRate;
            case "cache_hit_rate" -> cacheHitRate;
            case "throughput" -> throughputRps;
            case "quality_score" -> averageQualityScore;
            default -> 0.0;
        };
    }
    
    @Override
    public String toString() {
        return String.format("UsageEntity{id=%d, userId='%s', model='%s', date=%s, requests=%d, tokens=%d, cost=%.4f}", 
            id, userId, model, usageDate, totalRequests, totalTokens, totalCost);
    }
}