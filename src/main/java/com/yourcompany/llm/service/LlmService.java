// LlmService.java
package com.yourcompany.llm.service;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 서비스 인터페이스
 * 다양한 LLM 프로바이더와 통신하여 텍스트 생성, 채팅 완성, 임베딩 등의 기능을 제공
 */
public interface LlmService {
    
    /**
     * 텍스트 생성
     * @param request LLM 요청 객체
     * @return 생성된 텍스트 응답
     */
    CompletableFuture<LlmResponse> generateText(LlmRequest request);
    
    /**
     * 채팅 완성 (OpenAI 호환)
     * @param request 채팅 완성 요청
     * @return 채팅 완성 응답
     */
    CompletableFuture<LlmResponse> chatCompletion(LlmRequest request);
    
    /**
     * 스트리밍 텍스트 생성
     * @param request LLM 요청 객체
     * @param callback 스트리밍 콜백 함수
     * @return 스트리밍 완료 시점
     */
    CompletableFuture<Void> generateTextStream(LlmRequest request, StreamCallback callback);
    
    /**
     * 스트리밍 채팅 완성
     * @param request 채팅 완성 요청
     * @param callback 스트리밍 콜백 함수
     * @return 스트리밍 완료 시점
     */
    CompletableFuture<Void> chatCompletionStream(LlmRequest request, StreamCallback callback);
    
    /**
     * 텍스트 임베딩 생성
     * @param model 사용할 모델명
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터
     */
    CompletableFuture<List<Double>> createEmbeddings(String model, String text);
    
    /**
     * 배치 임베딩 생성
     * @param model 사용할 모델명
     * @param texts 임베딩할 텍스트 리스트
     * @return 임베딩 벡터 리스트
     */
    CompletableFuture<List<List<Double>>> createBatchEmbeddings(String model, List<String> texts);
    
    /**
     * 모델 가용성 확인
     * @param modelName 확인할 모델명
     * @return 모델 사용 가능 여부
     */
    CompletableFuture<Boolean> isModelAvailable(String modelName);
    
    /**
     * 모든 프로바이더 헬스 체크
     * @return 프로바이더별 헬스 상태
     */
    CompletableFuture<Map<String, String>> checkAllProvidersHealth();
    
    /**
     * 특정 프로바이더 헬스 체크
     * @param provider 프로바이더명
     * @return 헬스 상태
     */
    CompletableFuture<String> checkProviderHealth(String provider);
    
    /**
     * 모델 정보 조회
     * @param modelName 모델명
     * @return 모델 정보
     */
    CompletableFuture<Map<String, Object>> getModelInfo(String modelName);
    
    /**
     * 사용 가능한 모델 목록 조회
     * @return 모델 목록
     */
    CompletableFuture<List<Map<String, Object>>> getAvailableModels();
    
    /**
     * 프로바이더별 모델 목록 조회
     * @param provider 프로바이더명
     * @return 해당 프로바이더의 모델 목록
     */
    CompletableFuture<List<Map<String, Object>>> getModelsByProvider(String provider);
    
    /**
     * 토큰 수 계산
     * @param model 모델명
     * @param text 계산할 텍스트
     * @return 토큰 수
     */
    CompletableFuture<Integer> countTokens(String model, String text);
    
    /**
     * 비용 계산
     * @param model 모델명
     * @param inputTokens 입력 토큰 수
     * @param outputTokens 출력 토큰 수
     * @return 예상 비용
     */
    double calculateCost(String model, int inputTokens, int outputTokens);
    
    /**
     * 요청 유효성 검증
     * @param request 검증할 요청
     * @return 유효성 검증 결과
     */
    ValidationResult validateRequest(LlmRequest request);
    
    /**
     * 캐시에서 응답 조회
     * @param request 요청 객체
     * @return 캐시된 응답 (없으면 null)
     */
    CompletableFuture<LlmResponse> getCachedResponse(LlmRequest request);
    
    /**
     * 응답 캐시에 저장
     * @param request 요청 객체
     * @param response 응답 객체
     */
    CompletableFuture<Void> cacheResponse(LlmRequest request, LlmResponse response);
    
    /**
     * 배치 텍스트 생성
     * @param requests 요청 리스트
     * @return 응답 리스트
     */
    CompletableFuture<List<LlmResponse>> generateBatchText(List<LlmRequest> requests);
    
    /**
     * 최적 모델 선택
     * @param task 수행할 작업 유형
     * @param requirements 요구사항
     * @return 추천 모델명
     */
    String recommendModel(String task, Map<String, Object> requirements);
    
    /**
     * 함수 호출 처리
     * @param request 함수 호출 요청
     * @return 함수 호출 결과
     */
    CompletableFuture<LlmResponse> handleFunctionCall(LlmRequest request);
    
    /**
     * 템플릿 기반 프롬프트 생성
     * @param templateName 템플릿명
     * @param variables 템플릿 변수
     * @return 생성된 프롬프트
     */
    String generatePromptFromTemplate(String templateName, Map<String, Object> variables);
    
    /**
     * 대화 컨텍스트 관리
     * @param conversationId 대화 ID
     * @param message 새 메시지
     * @return 업데이트된 대화 컨텍스트
     */
    CompletableFuture<List<LlmRequest.ChatMessage>> updateConversationContext(String conversationId, LlmRequest.ChatMessage message);
    
    /**
     * 응답 후처리
     * @param response 원본 응답
     * @param options 후처리 옵션
     * @return 후처리된 응답
     */
    LlmResponse postProcessResponse(LlmResponse response, Map<String, Object> options);
    
    /**
     * 스트리밍 콜백 인터페이스
     */
    interface StreamCallback {
        /**
         * 스트림 데이터 수신
         * @param chunk 받은 데이터 청크
         */
        void onData(String chunk);
        
        /**
         * 스트림 완료
         * @param finalResponse 최종 완성된 응답
         */
        void onComplete(LlmResponse finalResponse);
        
        /**
         * 스트림 오류
         * @param error 오류 정보
         */
        void onError(String error);
        
        /**
         * 메타데이터 수신 (토큰 사용량 등)
         * @param metadata 메타데이터
         */
        default void onMetadata(Map<String, Object> metadata) {}
    }
    
    /**
     * 유효성 검증 결과
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, String message, List<String> errors) {
            this.valid = valid;
            this.message = message;
            this.errors = errors != null ? errors : List.of();
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, "Valid request", List.of());
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, List.of(message));
        }
        
        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, "Validation failed", errors);
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<String> getErrors() { return errors; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        
        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + message;
        }
    }
    
    /**
     * 프로바이더별 특수 기능 지원 확인
     * @param provider 프로바이더명
     * @param feature 기능명
     * @return 지원 여부
     */
    boolean supportsFeature(String provider, String feature);
    
    /**
     * 동시 요청 제한 확인
     * @param modelName 모델명
     * @return 현재 가능한 동시 요청 수
     */
    int getAvailableConcurrency(String modelName);
    
    /**
     * 요청 큐 상태 조회
     * @return 큐 상태 정보
     */
    Map<String, Object> getQueueStatus();
    
    /**
     * 모델 워밍업 (사전 로딩)
     * @param modelName 워밍업할 모델명
     * @return 워밍업 완료 여부
     */
    CompletableFuture<Boolean> warmupModel(String modelName);
    
    /**
     * 응답 품질 평가
     * @param request 원본 요청
     * @param response 생성된 응답
     * @return 품질 점수 (0.0 ~ 1.0)
     */
    double evaluateResponseQuality(LlmRequest request, LlmResponse response);
    
    /**
     * A/B 테스트를 위한 모델 비교
     * @param request 요청
     * @param modelA 모델 A
     * @param modelB 모델 B
     * @return 두 모델의 응답 비교 결과
     */
    CompletableFuture<Map<String, LlmResponse>> compareModels(LlmRequest request, String modelA, String modelB);
    
    /**
     * 요청 재시도 처리
     * @param request 재시도할 요청
     * @param maxRetries 최대 재시도 횟수
     * @return 재시도 결과
     */
    CompletableFuture<LlmResponse> retryRequest(LlmRequest request, int maxRetries);
    
    /**
     * 컨텍스트 윈도우 최적화
     * @param messages 메시지 리스트
     * @param maxTokens 최대 토큰 수
     * @return 최적화된 메시지 리스트
     */
    List<LlmRequest.ChatMessage> optimizeContextWindow(List<LlmRequest.ChatMessage> messages, int maxTokens);
    
    /**
     * 서비스 통계 조회
     * @return 서비스 통계 정보
     */
    Map<String, Object> getServiceStatistics();
    
    /**
     * 서비스 설정 업데이트
     * @param settings 새로운 설정
     * @return 업데이트 성공 여부
     */
    boolean updateSettings(Map<String, Object> settings);
    
    /**
     * 긴급 정지 (모든 요청 중단)
     * @param reason 정지 사유
     * @return 정지 완료 여부
     */
    CompletableFuture<Boolean> emergencyStop(String reason);
    
    /**
     * 서비스 재시작
     * @return 재시작 완료 여부
     */
    CompletableFuture<Boolean> restart();
}