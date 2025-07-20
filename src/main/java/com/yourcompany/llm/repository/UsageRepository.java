// UsageRepository.java
package com.yourcompany.llm.repository;

import com.yourcompany.llm.entity.UsageEntity;
import com.yourcompany.llm.entity.UsageEntity.AggregationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<UsageEntity, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 특정 사용자, 모델, 날짜의 사용량 조회
     */
    Optional<UsageEntity> findByUserIdAndModelAndUsageDateAndAggregationType(
        String userId, String model, LocalDate usageDate, AggregationType aggregationType);
    
    /**
     * 특정 사용자의 기간별 사용량 조회
     */
    List<UsageEntity> findByUserIdAndUsageDateBetweenAndAggregationType(
        String userId, LocalDate startDate, LocalDate endDate, AggregationType aggregationType);
    
    /**
     * 특정 모델의 기간별 사용량 조회
     */
    List<UsageEntity> findByModelAndUsageDateBetweenAndAggregationType(
        String model, LocalDate startDate, LocalDate endDate, AggregationType aggregationType);
    
    /**
     * 특정 프로바이더의 기간별 사용량 조회
     */
    List<UsageEntity> findByProviderAndUsageDateBetweenAndAggregationType(
        String provider, LocalDate startDate, LocalDate endDate, AggregationType aggregationType);
    
    /**
     * 특정 날짜의 모든 사용량 조회
     */
    List<UsageEntity> findByUsageDateAndAggregationType(
        LocalDate usageDate, AggregationType aggregationType);
    
    /**
     * 기간별 모든 사용량 조회 (페이징)
     */
    Page<UsageEntity> findByUsageDateBetweenAndAggregationType(
        LocalDate startDate, LocalDate endDate, AggregationType aggregationType, Pageable pageable);
    
    // ===== 집계 쿼리 메서드 =====
    
    /**
     * 특정 기간의 총 요청 수 집계
     */
    @Query("SELECT SUM(u.totalRequests) FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    Long sumTotalRequestsByDateRangeAndType(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 특정 기간의 총 토큰 수 집계
     */
    @Query("SELECT SUM(u.totalTokens) FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    Long sumTotalTokensByDateRangeAndType(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 특정 기간의 총 비용 집계
     */
    @Query("SELECT SUM(u.totalCost) FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    Double sumTotalCostByDateRangeAndType(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 특정 사용자의 기간별 총 비용
     */
    @Query("SELECT SUM(u.totalCost) FROM UsageEntity u " +
           "WHERE u.userId = :userId " +
           "AND u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    Double sumTotalCostByUserAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 특정 모델의 기간별 총 사용량
     */
    @Query("SELECT SUM(u.totalRequests) FROM UsageEntity u " +
           "WHERE u.model = :model " +
           "AND u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    Long sumRequestsByModelAndDateRange(
        @Param("model") String model,
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    // ===== 통계 쿼리 메서드 =====
    
    /**
     * 사용자별 사용량 통계 (상위 N명)
     */
    @Query("SELECT u.userId, SUM(u.totalRequests) as totalRequests, " +
           "SUM(u.totalTokens) as totalTokens, SUM(u.totalCost) as totalCost " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.userId " +
           "ORDER BY SUM(u.totalRequests) DESC")
    List<Object[]> findTopUsersByUsage(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType,
        Pageable pageable);
    
    /**
     * 모델별 사용량 통계
     */
    @Query("SELECT u.model, u.provider, SUM(u.totalRequests) as totalRequests, " +
           "SUM(u.totalTokens) as totalTokens, SUM(u.totalCost) as totalCost, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime, " +
           "AVG(u.errorRate) as avgErrorRate " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.model, u.provider " +
           "ORDER BY SUM(u.totalRequests) DESC")
    List<Object[]> findModelUsageStatistics(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 프로바이더별 사용량 통계
     */
    @Query("SELECT u.provider, SUM(u.totalRequests) as totalRequests, " +
           "SUM(u.totalTokens) as totalTokens, SUM(u.totalCost) as totalCost, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime, " +
           "AVG(u.errorRate) as avgErrorRate, " +
           "COUNT(DISTINCT u.userId) as uniqueUsers " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.provider " +
           "ORDER BY SUM(u.totalRequests) DESC")
    List<Object[]> findProviderUsageStatistics(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 일별 사용량 추세
     */
    @Query("SELECT u.usageDate, SUM(u.totalRequests) as totalRequests, " +
           "SUM(u.totalTokens) as totalTokens, SUM(u.totalCost) as totalCost, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.usageDate " +
           "ORDER BY u.usageDate")
    List<Object[]> findDailyUsageTrends(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 시간별 사용량 패턴 (일별 집계에서)
     */
    @Query("SELECT u.usageHour, AVG(u.totalRequests) as avgRequests, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = 'HOURLY' " +
           "AND u.usageHour IS NOT NULL " +
           "GROUP BY u.usageHour " +
           "ORDER BY u.usageHour")
    List<Object[]> findHourlyUsagePatterns(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate);
    
    // ===== 성능 분석 쿼리 =====
    
    /**
     * 응답 시간이 느린 모델 조회
     */
    @Query("SELECT u.model, u.provider, AVG(u.averageResponseTimeMs) as avgResponseTime, " +
           "SUM(u.totalRequests) as totalRequests " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.averageResponseTimeMs > :thresholdMs " +
           "GROUP BY u.model, u.provider " +
           "ORDER BY AVG(u.averageResponseTimeMs) DESC")
    List<Object[]> findSlowModels(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType,
        @Param("thresholdMs") Double thresholdMs);
    
    /**
     * 에러율이 높은 모델 조회
     */
    @Query("SELECT u.model, u.provider, AVG(u.errorRate) as avgErrorRate, " +
           "SUM(u.totalRequests) as totalRequests, SUM(u.failedRequests) as totalFailed " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.errorRate > :thresholdRate " +
           "GROUP BY u.model, u.provider " +
           "ORDER BY AVG(u.errorRate) DESC")
    List<Object[]> findHighErrorRateModels(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType,
        @Param("thresholdRate") Double thresholdRate);
    
    /**
     * 비용 효율성이 낮은 모델 조회
     */
    @Query("SELECT u.model, u.provider, " +
           "(SUM(u.totalCost) / SUM(u.totalTokens)) as costPerToken, " +
           "SUM(u.totalCost) as totalCost, SUM(u.totalTokens) as totalTokens " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.totalTokens > 0 " +
           "GROUP BY u.model, u.provider " +
           "ORDER BY (SUM(u.totalCost) / SUM(u.totalTokens)) DESC")
    List<Object[]> findCostlyModels(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    // ===== 사용자 분석 쿼리 =====
    
    /**
     * 특정 사용자의 모델별 사용 패턴
     */
    @Query("SELECT u.model, SUM(u.totalRequests) as requests, " +
           "SUM(u.totalTokens) as tokens, SUM(u.totalCost) as cost, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime " +
           "FROM UsageEntity u " +
           "WHERE u.userId = :userId " +
           "AND u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.model " +
           "ORDER BY SUM(u.totalRequests) DESC")
    List<Object[]> findUserModelUsagePattern(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 활성 사용자 수 (기간별)
     */
    @Query("SELECT COUNT(DISTINCT u.userId) FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.totalRequests > 0")
    Long countActiveUsers(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 신규 사용자 수 (특정 날짜에 처음 사용한 사용자)
     */
    @Query("SELECT COUNT(DISTINCT u.userId) FROM UsageEntity u " +
           "WHERE u.usageDate = :date " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.totalRequests > 0 " +
           "AND NOT EXISTS (SELECT 1 FROM UsageEntity u2 " +
           "                WHERE u2.userId = u.userId " +
           "                AND u2.usageDate < :date " +
           "                AND u2.aggregationType = :aggregationType)")
    Long countNewUsers(
        @Param("date") LocalDate date,
        @Param("aggregationType") AggregationType aggregationType);
    
    // ===== 데이터 관리 쿼리 =====
    
    /**
     * 특정 날짜 이전 데이터 삭제
     */
    @Modifying
    @Query("DELETE FROM UsageEntity u WHERE u.usageDate < :beforeDate")
    int deleteByUsageDateBefore(@Param("beforeDate") LocalDate beforeDate);
    
    /**
     * 특정 집계 타입의 오래된 데이터 삭제
     */
    @Modifying
    @Query("DELETE FROM UsageEntity u " +
           "WHERE u.usageDate < :beforeDate " +
           "AND u.aggregationType = :aggregationType")
    int deleteByUsageDateBeforeAndAggregationType(
        @Param("beforeDate") LocalDate beforeDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 특정 기간의 데이터 존재 여부 확인
     */
    @Query("SELECT COUNT(u) > 0 FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType")
    boolean existsByDateRangeAndType(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 최신 사용량 데이터 날짜 조회
     */
    @Query("SELECT MAX(u.usageDate) FROM UsageEntity u " +
           "WHERE u.aggregationType = :aggregationType")
    Optional<LocalDate> findLatestUsageDate(
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 가장 오래된 사용량 데이터 날짜 조회
     */
    @Query("SELECT MIN(u.usageDate) FROM UsageEntity u " +
           "WHERE u.aggregationType = :aggregationType")
    Optional<LocalDate> findOldestUsageDate(
        @Param("aggregationType") AggregationType aggregationType);
    
    // ===== 커스텀 집계 쿼리 =====
    
    /**
     * 지역별 사용량 통계
     */
    @Query("SELECT u.region, u.countryCode, SUM(u.totalRequests) as requests, " +
           "SUM(u.totalCost) as cost, COUNT(DISTINCT u.userId) as users " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.region IS NOT NULL " +
           "GROUP BY u.region, u.countryCode " +
           "ORDER BY SUM(u.totalRequests) DESC")
    List<Object[]> findUsageByRegion(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 캐시 효율성 통계
     */
    @Query("SELECT AVG(u.cacheHitRate) as avgCacheHitRate, " +
           "SUM(u.cachedRequests) as totalCachedRequests, " +
           "SUM(u.totalRequests) as totalRequests " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "AND u.cacheHitRate IS NOT NULL")
    Object[] findCacheEfficiencyStats(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
    
    /**
     * 월별 성장률 계산용 데이터
     */
    @Query("SELECT YEAR(u.usageDate) as year, MONTH(u.usageDate) as month, " +
           "SUM(u.totalRequests) as requests, SUM(u.totalCost) as cost " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = 'DAILY' " +
           "GROUP BY YEAR(u.usageDate), MONTH(u.usageDate) " +
           "ORDER BY YEAR(u.usageDate), MONTH(u.usageDate)")
    List<Object[]> findMonthlyGrowthData(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate);
    
    /**
     * 사용량 예측을 위한 트렌드 데이터
     */
    @Query("SELECT u.usageDate, SUM(u.totalRequests) as requests, " +
           "SUM(u.totalTokens) as tokens " +
           "FROM UsageEntity u " +
           "WHERE u.usageDate >= :fromDate " +
           "AND u.aggregationType = 'DAILY' " +
           "GROUP BY u.usageDate " +
           "ORDER BY u.usageDate")
    List<Object[]> findTrendDataForPrediction(@Param("fromDate") LocalDate fromDate);
    
    /**
     * 상위 사용자의 상세 통계
     */
    @Query("SELECT u.userId, u.model, SUM(u.totalRequests) as requests, " +
           "SUM(u.totalTokens) as tokens, SUM(u.totalCost) as cost, " +
           "AVG(u.averageResponseTimeMs) as avgResponseTime, " +
           "AVG(u.errorRate) as avgErrorRate " +
           "FROM UsageEntity u " +
           "WHERE u.userId IN :userIds " +
           "AND u.usageDate BETWEEN :startDate AND :endDate " +
           "AND u.aggregationType = :aggregationType " +
           "GROUP BY u.userId, u.model " +
           "ORDER BY u.userId, SUM(u.totalRequests) DESC")
    List<Object[]> findTopUsersDetailedStats(
        @Param("userIds") List<String> userIds,
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate,
        @Param("aggregationType") AggregationType aggregationType);
}