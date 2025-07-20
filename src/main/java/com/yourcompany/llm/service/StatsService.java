// StatsService.java
package com.yourcompany.llm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 통계 서비스 인터페이스
 * LLM API 사용량, 성능, 비용 등의 통계 데이터를 수집하고 분석하는 기능을 제공
 */
public interface StatsService {
    
    // ===== 기본 통계 조회 =====
    
    /**
     * 일일 사용량 통계
     * @param date 조회할 날짜 (null이면 오늘)
     * @return 일일 통계 데이터
     */
    Map<String, Object> getDailyStats(LocalDate date);
    
    /**
     * 주간 사용량 통계
     * @param startDate 주간 시작일 (null이면 최근 7일)
     * @return 주간 통계 데이터
     */
    Map<String, Object> getWeeklyStats(LocalDate startDate);
    
    /**
     * 월간 사용량 통계
     * @param year 연도
     * @param month 월
     * @return 월간 통계 데이터
     */
    Map<String, Object> getMonthlyStats(int year, int month);
    
    /**
     * 실시간 통계 (현재 시점)
     * @return 실시간 통계 데이터
     */
    Map<String, Object> getRealtimeStats();
    
    // ===== 세부 통계 조회 =====
    
    /**
     * 사용자별 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param limit 반환할 사용자 수 제한
     * @return 사용자별 통계 데이터
     */
    Map<String, Object> getUserStats(LocalDate from, LocalDate to, int limit);
    
    /**
     * 모델별 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 모델별 통계 데이터
     */
    Map<String, Object> getModelStats(LocalDate from, LocalDate to);
    
    /**
     * 프로바이더별 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 프로바이더별 통계 데이터
     */
    Map<String, Object> getProviderStats(LocalDate from, LocalDate to);
    
    /**
     * 토큰 사용량 통계
     * @param from 시작 시간
     * @param to 종료 시간
     * @param groupBy 그룹핑 단위 ("hour", "day", "week")
     * @return 토큰 사용량 통계
     */
    Map<String, Object> getTokenStats(LocalDateTime from, LocalDateTime to, String groupBy);
    
    /**
     * 응답 시간 통계
     * @param from 시작 시간
     * @param to 종료 시간
     * @param model 특정 모델 (선택사항)
     * @return 응답 시간 통계
     */
    Map<String, Object> getResponseTimeStats(LocalDateTime from, LocalDateTime to, String model);
    
    /**
     * 에러율 통계
     * @param from 시작 시간
     * @param to 종료 시간
     * @param groupBy 그룹핑 단위
     * @return 에러율 통계
     */
    Map<String, Object> getErrorRateStats(LocalDateTime from, LocalDateTime to, String groupBy);
    
    /**
     * 비용 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param currency 통화 단위
     * @return 비용 통계
     */
    Map<String, Object> getCostStats(LocalDate from, LocalDate to, String currency);
    
    // ===== 분석 및 예측 =====
    
    /**
     * 사용량 추세 분석
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param period 분석 주기 ("daily", "weekly", "monthly")
     * @return 추세 분석 결과
     */
    Map<String, Object> getUsageTrends(LocalDate from, LocalDate to, String period);
    
    /**
     * 사용량 예측
     * @param days 예측할 일수
     * @param model 특정 모델 (선택사항)
     * @return 사용량 예측 결과
     */
    CompletableFuture<Map<String, Object>> getUsageForecast(int days, String model);
    
    /**
     * 사용 패턴 분석
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param granularity 분석 세분화 ("hourly", "daily", "weekly")
     * @return 사용 패턴 분석 결과
     */
    Map<String, Object> getUsagePatterns(LocalDate from, LocalDate to, String granularity);
    
    /**
     * 이상 탐지
     * @param from 시작 시간
     * @param to 종료 시간
     * @param metric 분석할 메트릭
     * @return 이상 탐지 결과
     */
    Map<String, Object> detectAnomalies(LocalDateTime from, LocalDateTime to, String metric);
    
    // ===== 상세 조회 =====
    
    /**
     * 특정 사용자 상세 통계
     * @param userId 사용자 ID
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 사용자 상세 통계
     */
    Map<String, Object> getUserDetailStats(String userId, LocalDate from, LocalDate to);
    
    /**
     * 특정 모델 상세 통계
     * @param modelName 모델명
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 모델 상세 통계
     */
    Map<String, Object> getModelDetailStats(String modelName, LocalDateTime from, LocalDateTime to);
    
    /**
     * 시스템 성능 메트릭
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 성능 메트릭
     */
    Map<String, Object> getPerformanceMetrics(LocalDateTime from, LocalDateTime to);
    
    /**
     * 캐시 통계
     * @return 캐시 성능 통계
     */
    Map<String, Object> getCacheStats();
    
    /**
     * API 키별 사용량 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return API 키별 통계
     */
    Map<String, Object> getApiKeyStats(LocalDate from, LocalDate to);
    
    /**
     * 지역별 사용량 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 지역별 통계
     */
    Map<String, Object> getRegionalStats(LocalDate from, LocalDate to);
    
    /**
     * 처리량 통계 (TPS)
     * @param from 시작 시간
     * @param to 종료 시간
     * @param resolution 해상도 ("second", "minute", "hour")
     * @return 처리량 통계
     */
    Map<String, Object> getThroughputStats(LocalDateTime from, LocalDateTime to, String resolution);
    
    /**
     * 큐 상태 통계
     * @return 큐 상태 정보
     */
    Map<String, Object> getQueueStats();
    
    /**
     * 사용량 한도 확인
     * @param userId 사용자 ID (선택사항)
     * @param apiKey API 키 (선택사항)
     * @return 사용량 한도 정보
     */
    Map<String, Object> getUsageLimits(String userId, String apiKey);
    
    /**
     * 서비스 가용성 통계
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 가용성 통계
     */
    Map<String, Object> getAvailabilityStats(LocalDate from, LocalDate to);
    
    // ===== 대시보드 및 보고서 =====
    
    /**
     * 대시보드용 종합 통계
     * @return 대시보드 통계 데이터
     */
    CompletableFuture<Map<String, Object>> getDashboardStats();
    
    /**
     * 실시간 모니터링 데이터
     * @return 실시간 모니터링 데이터
     */
    Map<String, Object> getRealtimeMonitoringData();
    
    /**
     * 사용량 보고서 생성
     * @param reportRequest 보고서 생성 요청
     * @return 생성된 보고서
     */
    CompletableFuture<Map<String, Object>> generateUsageReport(Map<String, Object> reportRequest);
    
    /**
     * 활성 알럿 조회
     * @return 활성 알럿 목록
     */
    Map<String, Object> getActiveAlerts();
    
    // ===== 순위 및 비교 =====
    
    /**
     * 상위 사용자 조회
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param limit 반환할 사용자 수
     * @param sortBy 정렬 기준 ("requests", "tokens", "cost")
     * @return 상위 사용자 목록
     */
    List<Map<String, Object>> getTopUsers(LocalDate from, LocalDate to, int limit, String sortBy);
    
    /**
     * 상위 모델 조회
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param limit 반환할 모델 수
     * @param sortBy 정렬 기준
     * @return 상위 모델 목록
     */
    List<Map<String, Object>> getTopModels(LocalDate from, LocalDate to, int limit, String sortBy);
    
    /**
     * 기간별 비교 통계
     * @param period1Start 기간 1 시작
     * @param period1End 기간 1 종료
     * @param period2Start 기간 2 시작
     * @param period2End 기간 2 종료
     * @param metric 비교할 메트릭
     * @return 비교 결과
     */
    Map<String, Object> compareStats(LocalDate period1Start, LocalDate period1End, 
                                   LocalDate period2Start, LocalDate period2End, String metric);
    
    /**
     * 사용자 정의 메트릭 조회
     * @param metricName 메트릭명
     * @param from 시작 시간
     * @param to 종료 시간
     * @param filters 필터 조건
     * @return 사용자 정의 메트릭 데이터
     */
    Map<String, Object> getCustomMetric(String metricName, LocalDateTime from, LocalDateTime to, Map<String, String> filters);
    
    // ===== 데이터 관리 =====
    
    /**
     * 통계 데이터 기록
     * @param requestData 요청 데이터
     * @param responseData 응답 데이터
     * @return 기록 성공 여부
     */
    CompletableFuture<Boolean> recordUsage(Map<String, Object> requestData, Map<String, Object> responseData);
    
    /**
     * 메트릭 업데이트
     * @param metricName 메트릭명
     * @param value 값
     * @param tags 태그
     * @return 업데이트 성공 여부
     */
    CompletableFuture<Boolean> updateMetric(String metricName, double value, Map<String, String> tags);
    
    /**
     * 배치 메트릭 업데이트
     * @param metrics 메트릭 데이터 리스트
     * @return 업데이트 성공 여부
     */
    CompletableFuture<Boolean> updateMetricsBatch(List<Map<String, Object>> metrics);
    
    /**
     * 이벤트 로깅
     * @param eventType 이벤트 타입
     * @param eventData 이벤트 데이터
     * @return 로깅 성공 여부
     */
    CompletableFuture<Boolean> logEvent(String eventType, Map<String, Object> eventData);
    
    /**
     * 통계 데이터 내보내기
     * @param exportRequest 내보내기 요청
     * @return 내보내기 결과
     */
    CompletableFuture<Map<String, Object>> exportStats(Map<String, Object> exportRequest);
    
    /**
     * 오래된 통계 데이터 정리
     * @param before 기준 날짜 (이전 데이터 삭제)
     * @param dryRun 실제 삭제 여부 (false면 실제 삭제)
     * @return 정리 결과
     */
    CompletableFuture<Map<String, Object>> cleanupOldStats(LocalDate before, boolean dryRun);
    
    // ===== 알럿 및 모니터링 =====
    
    /**
     * 알럿 규칙 설정
     * @param ruleName 규칙명
     * @param condition 알럿 조건
     * @return 설정 성공 여부
     */
    boolean setAlertRule(String ruleName, Map<String, Object> condition);
    
    /**
     * 알럿 규칙 삭제
     * @param ruleName 규칙명
     * @return 삭제 성공 여부
     */
    boolean removeAlertRule(String ruleName);
    
    /**
     * 모든 알럿 규칙 조회
     * @return 알럿 규칙 목록
     */
    Map<String, Map<String, Object>> getAlertRules();
    
    /**
     * 임계값 확인
     * @param metricName 메트릭명
     * @param currentValue 현재 값
     * @return 임계값 초과 여부
     */
    boolean checkThreshold(String metricName, double currentValue);
    
    /**
     * 헬스 체크 수행
     * @return 서비스 상태
     */
    Map<String, Object> performHealthCheck();
    
    // ===== 집계 및 계산 =====
    
    /**
     * 평균 응답 시간 계산
     * @param from 시작 시간
     * @param to 종료 시간
     * @param model 모델명 (선택사항)
     * @return 평균 응답 시간 (밀리초)
     */
    double getAverageResponseTime(LocalDateTime from, LocalDateTime to, String model);
    
    /**
     * 총 토큰 사용량 계산
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 총 토큰 사용량
     */
    long getTotalTokenUsage(LocalDate from, LocalDate to);
    
    /**
     * 총 비용 계산
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @param currency 통화
     * @return 총 비용
     */
    double getTotalCost(LocalDate from, LocalDate to, String currency);
    
    /**
     * 에러율 계산
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 에러율 (0.0 ~ 1.0)
     */
    double getErrorRate(LocalDateTime from, LocalDateTime to);
    
    /**
     * 캐시 히트율 계산
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 캐시 히트율 (0.0 ~ 1.0)
     */
    double getCacheHitRate(LocalDateTime from, LocalDateTime to);
    
    /**
     * 처리량 계산 (RPS)
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 초당 요청 수
     */
    double getRequestsPerSecond(LocalDateTime from, LocalDateTime to);
    
    // ===== 유틸리티 =====
    
    /**
     * 수집된 메트릭 수 조회
     * @return 수집된 메트릭 수
     */
    long getCollectedMetricsCount();
    
    /**
     * 데이터 보존 기간 조회
     * @return 보존 기간 정보
     */
    Map<String, String> getRetentionPeriods();
    
    /**
     * 통계 서비스 설정 조회
     * @return 현재 설정
     */
    Map<String, Object> getServiceConfiguration();
    
    /**
     * 통계 서비스 설정 업데이트
     * @param config 새 설정
     * @return 업데이트 성공 여부
     */
    boolean updateServiceConfiguration(Map<String, Object> config);
    
    /**
     * 캐시 무효화
     * @param cacheKey 캐시 키 (null이면 전체 무효화)
     * @return 무효화 성공 여부
     */
    boolean invalidateCache(String cacheKey);
    
    /**
     * 통계 재계산
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 재계산 작업 ID
     */
    CompletableFuture<String> recalculateStats(LocalDate from, LocalDate to);
    
    /**
     * 데이터 일관성 검증
     * @return 검증 결과
     */
    CompletableFuture<Map<String, Object>> validateDataConsistency();
    
    /**
     * 백업 생성
     * @param backupType 백업 유형
     * @return 백업 결과
     */
    CompletableFuture<Map<String, Object>> createBackup(String backupType);
    
    /**
     * 백업 복원
     * @param backupId 백업 ID
     * @return 복원 결과
     */
    CompletableFuture<Map<String, Object>> restoreBackup(String backupId);
}