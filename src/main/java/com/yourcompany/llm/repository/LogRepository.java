// LogRepository.java
package com.yourcompany.llm.repository;

import com.yourcompany.llm.entity.LogEntity;
import com.yourcompany.llm.entity.LogEntity.LogLevel;
import com.yourcompany.llm.entity.LogEntity.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LogRepository extends JpaRepository<LogEntity, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 로그 ID로 조회
     */
    Optional<LogEntity> findByLogId(String logId);
    
    /**
     * 상관관계 ID로 관련 로그들 조회
     */
    List<LogEntity> findByCorrelationIdOrderByTimestampAsc(String correlationId);
    
    /**
     * 세션 ID로 로그들 조회
     */
    List<LogEntity> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    /**
     * 특정 사용자의 로그 조회
     */
    Page<LogEntity> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * 특정 모델의 로그 조회
     */
    Page<LogEntity> findByModelOrderByTimestampDesc(String model, Pageable pageable);
    
    /**
     * 특정 프로바이더의 로그 조회
     */
    Page<LogEntity> findByProviderOrderByTimestampDesc(String provider, Pageable pageable);
    
    /**
     * 로그 레벨별 조회
     */
    Page<LogEntity> findByLogLevelOrderByTimestampDesc(LogLevel logLevel, Pageable pageable);
    
    /**
     * 이벤트 타입별 조회
     */
    Page<LogEntity> findByEventTypeOrderByTimestampDesc(EventType eventType, Pageable pageable);
    
    /**
     * 기간별 로그 조회
     */
    Page<LogEntity> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    // ===== 에러 로그 조회 =====
    
    /**
     * 에러 로그만 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.logLevel IN ('ERROR', 'FATAL') " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findErrorLogs(Pageable pageable);
    
    /**
     * 특정 기간의 에러 로그 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.logLevel IN ('ERROR', 'FATAL') " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY l.timestamp DESC")
    List<LogEntity> findErrorLogsByTimeRange(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 특정 에러 코드의 로그 조회
     */
    List<LogEntity> findByErrorCodeOrderByTimestampDesc(String errorCode);
    
    /**
     * 특정 예외 타입의 로그 조회
     */
    List<LogEntity> findByExceptionTypeOrderByTimestampDesc(String exceptionType);
    
    /**
     * 재시도가 발생한 로그 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.retryCount > 0 " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findLogsWithRetries(Pageable pageable);
    
    // ===== 성능 분석 쿼리 =====
    
    /**
     * 느린 요청 로그 조회 (임계값 이상)
     */
    @Query("SELECT l FROM LogEntity l WHERE l.processingTimeMs > :thresholdMs " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY l.processingTimeMs DESC")
    List<LogEntity> findSlowRequests(
        @Param("thresholdMs") Long thresholdMs,
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 평균 응답 시간 통계 (모델별)
     */
    @Query("SELECT l.model, AVG(l.processingTimeMs) as avgTime, COUNT(l) as requestCount " +
           "FROM LogEntity l " +
           "WHERE l.processingTimeMs IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.model " +
           "ORDER BY AVG(l.processingTimeMs) DESC")
    List<Object[]> findAverageResponseTimeByModel(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 시간별 요청 통계
     */
    @Query("SELECT HOUR(l.timestamp) as hour, COUNT(l) as requestCount, " +
           "AVG(l.processingTimeMs) as avgResponseTime " +
           "FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "AND l.eventType = 'API_REQUEST' " +
           "GROUP BY HOUR(l.timestamp) " +
           "ORDER BY HOUR(l.timestamp)")
    List<Object[]> findHourlyRequestStatistics(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 처리 시간 분포 통계
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN l.processingTimeMs < 100 THEN 'FAST' " +
           "  WHEN l.processingTimeMs < 1000 THEN 'NORMAL' " +
           "  WHEN l.processingTimeMs < 5000 THEN 'SLOW' " +
           "  ELSE 'VERY_SLOW' " +
           "END as category, " +
           "COUNT(l) as count " +
           "FROM LogEntity l " +
           "WHERE l.processingTimeMs IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY " +
           "CASE " +
           "  WHEN l.processingTimeMs < 100 THEN 'FAST' " +
           "  WHEN l.processingTimeMs < 1000 THEN 'NORMAL' " +
           "  WHEN l.processingTimeMs < 5000 THEN 'SLOW' " +
           "  ELSE 'VERY_SLOW' " +
           "END")
    List<Object[]> findResponseTimeDistribution(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    // ===== 보안 및 모니터링 쿼리 =====
    
    /**
     * 보안 이벤트 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.eventType IN " +
           "('SECURITY_VIOLATION', 'SUSPICIOUS_ACTIVITY', 'UNAUTHORIZED_ACCESS') " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findSecurityEvents(Pageable pageable);
    
    /**
     * 특정 IP의 요청 로그 조회
     */
    List<LogEntity> findByClientIpOrderByTimestampDesc(String clientIp);
    
    /**
     * 의심스러운 활동 탐지 (같은 IP에서 짧은 시간 내 많은 요청)
     */
    @Query("SELECT l.clientIp, COUNT(l) as requestCount " +
           "FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "AND l.clientIp IS NOT NULL " +
           "GROUP BY l.clientIp " +
           "HAVING COUNT(l) > :threshold " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findSuspiciousIpActivity(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime,
        @Param("threshold") Long threshold);
    
    /**
     * 실패한 인증 시도 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.eventType = 'AUTH_FAILURE' " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY l.timestamp DESC")
    List<LogEntity> findFailedAuthAttempts(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 특정 사용자의 로그인 기록
     */
    @Query("SELECT l FROM LogEntity l WHERE l.userId = :userId " +
           "AND l.eventType IN ('USER_LOGIN', 'USER_LOGOUT') " +
           "ORDER BY l.timestamp DESC")
    List<LogEntity> findUserLoginHistory(@Param("userId") String userId);
    
    // ===== 통계 및 집계 쿼리 =====
    
    /**
     * 로그 레벨별 개수 통계
     */
    @Query("SELECT l.logLevel, COUNT(l) FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.logLevel")
    List<Object[]> countLogsByLevel(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 이벤트 타입별 개수 통계
     */
    @Query("SELECT l.eventType, COUNT(l) FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.eventType " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> countLogsByEventType(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * HTTP 상태 코드별 개수 통계
     */
    @Query("SELECT l.statusCode, COUNT(l) FROM LogEntity l " +
           "WHERE l.statusCode IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.statusCode " +
           "ORDER BY l.statusCode")
    List<Object[]> countLogsByStatusCode(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 일별 로그 개수 통계
     */
    @Query("SELECT DATE(l.timestamp) as logDate, COUNT(l) as logCount " +
           "FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE(l.timestamp) " +
           "ORDER BY DATE(l.timestamp)")
    List<Object[]> countLogsByDay(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 모델별 에러율 통계
     */
    @Query("SELECT l.model, " +
           "COUNT(CASE WHEN l.logLevel IN ('ERROR', 'FATAL') THEN 1 END) as errorCount, " +
           "COUNT(l) as totalCount, " +
           "(COUNT(CASE WHEN l.logLevel IN ('ERROR', 'FATAL') THEN 1 END) * 100.0 / COUNT(l)) as errorRate " +
           "FROM LogEntity l " +
           "WHERE l.model IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.model " +
           "HAVING COUNT(l) > 10 " +
           "ORDER BY errorRate DESC")
    List<Object[]> findErrorRateByModel(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 캐시 히트/미스 통계
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN l.cacheHit = true THEN 1 END) as cacheHits, " +
           "COUNT(CASE WHEN l.cacheHit = false THEN 1 END) as cacheMisses, " +
           "COUNT(l) as totalRequests " +
           "FROM LogEntity l " +
           "WHERE l.cacheHit IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime")
    Object[] findCacheStatistics(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    // ===== 검색 및 필터링 쿼리 =====
    
    /**
     * 메시지 내용으로 로그 검색
     */
    @Query("SELECT l FROM LogEntity l WHERE l.message LIKE %:keyword% " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findByMessageContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 복합 조건으로 로그 검색
     */
    @Query("SELECT l FROM LogEntity l WHERE " +
           "(:logLevel IS NULL OR l.logLevel = :logLevel) AND " +
           "(:eventType IS NULL OR l.eventType = :eventType) AND " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:model IS NULL OR l.model = :model) AND " +
           "(:startTime IS NULL OR l.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR l.timestamp <= :endTime) " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findByMultipleCriteria(
        @Param("logLevel") LogLevel logLevel,
        @Param("eventType") EventType eventType,
        @Param("userId") String userId,
        @Param("model") String model,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);
    
    /**
     * 특정 컴포넌트의 로그 조회
     */
    List<LogEntity> findByComponentOrderByTimestampDesc(String component);
    
    /**
     * 알럿이 발생한 로그 조회
     */
    @Query("SELECT l FROM LogEntity l WHERE l.alertTriggered = true " +
           "ORDER BY l.timestamp DESC")
    Page<LogEntity> findLogsWithAlerts(Pageable pageable);
    
    /**
     * 특정 알럿 ID와 관련된 로그 조회
     */
    List<LogEntity> findByAlertIdOrderByTimestampDesc(String alertId);
    
    // ===== 데이터 정리 및 관리 쿼리 =====
    
    /**
     * 특정 날짜 이전 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM LogEntity l WHERE l.timestamp < :beforeTime")
    int deleteByTimestampBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 특정 로그 레벨의 오래된 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM LogEntity l WHERE l.timestamp < :beforeTime " +
           "AND l.logLevel = :logLevel")
    int deleteByTimestampBeforeAndLogLevel(
        @Param("beforeTime") LocalDateTime beforeTime,
        @Param("logLevel") LogLevel logLevel);
    
    /**
     * 특정 기간의 로그 존재 여부 확인
     */
    @Query("SELECT COUNT(l) > 0 FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime")
    boolean existsByTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 최신 로그 시간 조회
     */
    @Query("SELECT MAX(l.timestamp) FROM LogEntity l")
    Optional<LocalDateTime> findLatestLogTime();
    
    /**
     * 가장 오래된 로그 시간 조회
     */
    @Query("SELECT MIN(l.timestamp) FROM LogEntity l")
    Optional<LocalDateTime> findOldestLogTime();
    
    /**
     * 로그 데이터 크기 통계
     */
    @Query("SELECT COUNT(l) as totalLogs, " +
           "AVG(l.requestSize) as avgRequestSize, " +
           "AVG(l.responseSize) as avgResponseSize, " +
           "SUM(l.requestSize + l.responseSize) as totalDataSize " +
           "FROM LogEntity l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "AND l.requestSize IS NOT NULL AND l.responseSize IS NOT NULL")
    Object[] findDataSizeStatistics(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    // ===== 고급 분석 쿼리 =====
    
    /**
     * 사용자별 활동 패턴 분석
     */
    @Query("SELECT l.userId, " +
           "HOUR(l.timestamp) as hour, " +
           "COUNT(l) as activityCount " +
           "FROM LogEntity l " +
           "WHERE l.userId IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.userId, HOUR(l.timestamp) " +
           "ORDER BY l.userId, HOUR(l.timestamp)")
    List<Object[]> findUserActivityPatterns(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 지역별 요청 통계
     */
    @Query("SELECT l.region, l.countryCode, COUNT(l) as requestCount, " +
           "AVG(l.processingTimeMs) as avgResponseTime " +
           "FROM LogEntity l " +
           "WHERE l.region IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.region, l.countryCode " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findRequestsByRegion(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 시스템 리소스 사용량 추세
     */
    @Query("SELECT DATE_TRUNC('hour', l.timestamp) as hour, " +
           "AVG(l.memoryUsageMb) as avgMemoryUsage, " +
           "AVG(l.cpuUsagePercent) as avgCpuUsage, " +
           "COUNT(l) as requestCount " +
           "FROM LogEntity l " +
           "WHERE l.memoryUsageMb IS NOT NULL " +
           "AND l.cpuUsagePercent IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE_TRUNC('hour', l.timestamp) " +
           "ORDER BY DATE_TRUNC('hour', l.timestamp)")
    List<Object[]> findResourceUsageTrends(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * API 엔드포인트별 성능 통계
     */
    @Query("SELECT l.requestUri, COUNT(l) as requestCount, " +
           "AVG(l.processingTimeMs) as avgResponseTime, " +
           "MIN(l.processingTimeMs) as minResponseTime, " +
           "MAX(l.processingTimeMs) as maxResponseTime " +
           "FROM LogEntity l " +
           "WHERE l.requestUri IS NOT NULL " +
           "AND l.processingTimeMs IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.requestUri " +
           "HAVING COUNT(l) > 10 " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> findEndpointPerformanceStats(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 회로 차단기 상태 변화 추적
     */
    @Query("SELECT l.timestamp, l.circuitBreakerState, l.component " +
           "FROM LogEntity l " +
           "WHERE l.circuitBreakerState IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY l.timestamp")
    List<Object[]> findCircuitBreakerStateChanges(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 비정상적인 응답 시간 탐지
     */
    @Query("SELECT l FROM LogEntity l " +
           "WHERE l.processingTimeMs > (" +
           "  SELECT AVG(l2.processingTimeMs) + (3 * STDDEV(l2.processingTimeMs)) " +
           "  FROM LogEntity l2 " +
           "  WHERE l2.model = l.model " +
           "  AND l2.timestamp BETWEEN :startTime AND :endTime" +
           ") " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY l.processingTimeMs DESC")
    List<LogEntity> findAnomalousResponseTimes(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 상관관계별 요청 추적 통계
     */
    @Query("SELECT l.correlationId, COUNT(l) as stepCount, " +
           "MIN(l.timestamp) as startTime, MAX(l.timestamp) as endTime, " +
           "SUM(l.processingTimeMs) as totalProcessingTime " +
           "FROM LogEntity l " +
           "WHERE l.correlationId IS NOT NULL " +
           "AND l.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY l.correlationId " +
           "HAVING COUNT(l) > 1 " +
           "ORDER BY SUM(l.processingTimeMs) DESC")
    List<Object[]> findRequestTraceStatistics(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 특정 기간의 로그 샘플링
     */
    @Query(value = "SELECT * FROM api_logs l " +
           "WHERE l.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY RANDOM() LIMIT :sampleSize", 
           nativeQuery = true)
    List<LogEntity> findRandomSample(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime,
        @Param("sampleSize") int sampleSize);
}