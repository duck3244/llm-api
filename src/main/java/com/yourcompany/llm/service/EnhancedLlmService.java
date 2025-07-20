// EnhancedLlmService.java
package com.yourcompany.llm.service.impl;

import com.yourcompany.llm.config.LlmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.entity.Message;
import com.yourcompany.llm.repository.MessageRepository;
import com.yourcompany.llm.service.LlmService;
import com.yourcompany.llm.service.CacheService;
import com.yourcompany.llm.service.LlmUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service("enhancedLlmService")
@RequiredArgsConstructor
public class EnhancedLlmService implements LlmService {
    
    private final LlmService basicLlmService; // LlmServiceImpl 주입
    private final LlmConfigProperties llmConfig;
    private final CacheService cacheService;
    private final LlmUsageService usageService;
    private final MessageRepository messageRepository;
    private final Executor llmTaskExecutor;
    
    // 성능 모니터링
    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> responseTimes = new ConcurrentHashMap<>();
    
    // 회로 차단기
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // 요청 제한
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    // 대화 컨텍스트 관리
    private final Map<String, ConversationContext> conversations = new ConcurrentHashMap<>();
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public CompletableFuture<LlmResponse> generateText(LlmRequest request) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String modelName = request.getModel() != null ? request.getModel() : llmConfig.getDefaults().getModel();
            
            try {
                // 1. 요청 전처리 및 검증
                ValidationResult validation = validateEnhancedRequest(request);
                if (!validation.isValid()) {
                    return LlmResponse.error(modelName, validation.getMessage());
                }
                
                // 2. 레이트 리미팅 확인
                if (!checkRateLimit(request.getUser(), modelName)) {
                    return LlmResponse.error(modelName, "Rate limit exceeded");
                }
                
                // 3. 회로 차단기 확인
                CircuitBreaker circuitBreaker = getCircuitBreaker(modelName);
                if (circuitBreaker.isOpen()) {
                    return LlmResponse.error(modelName, "Circuit breaker is open");
                }
                
                // 4. 캐시 확인
                String cacheKey = generateCacheKey(request);
                LlmResponse cachedResponse = cacheService.getResponse(cacheKey);
                if (cachedResponse != null) {
                    log.debug("Cache hit for request: {}", requestId);
                    cachedResponse.setFromCache(true);
                    cachedResponse.setRequestId(requestId);
                    recordMetrics(modelName, System.currentTimeMillis() - startTime, true, true);
                    return cachedResponse;
                }
                
                // 5. 컨텍스트 최적화
                LlmRequest optimizedRequest = optimizeRequest(request);
                
                // 6. 실제 LLM 호출
                LlmResponse response = circuitBreaker.call(() -> 
                    basicLlmService.generateText(optimizedRequest).join()
                );
                
                // 7. 후처리
                response = postProcessResponse(response, request);
                response.setRequestId(requestId);
                
                // 8. 캐싱
                if (response.isSuccess() && shouldCache(request, response)) {
                    cacheService.cacheResponse(cacheKey, response);
                }
                
                // 9. 메트릭 기록
                recordMetrics(modelName, System.currentTimeMillis() - startTime, response.isSuccess(), false);
                
                // 10. 사용량 기록
                recordUsage(request, response);
                
                // 11. 메시지 저장
                saveMessage(request, response);
                
                return response;
                
            } catch (Exception e) {
                log.error("Enhanced LLM service error for model: {}", modelName, e);
                recordMetrics(modelName, System.currentTimeMillis() - startTime, false, false);
                return LlmResponse.error(modelName, "Service error: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<LlmResponse> chatCompletion(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 대화 컨텍스트 관리
                if (request.getUser() != null) {
                    ConversationContext context = getOrCreateConversationContext(request.getUser());
                    request = enhanceWithContext(request, context);
                    updateConversationContext(request.getUser(), request, null);
                }
                
                CompletableFuture<LlmResponse> future = generateText(request);
                LlmResponse response = future.join();
                
                // 대화 컨텍스트 업데이트
                if (request.getUser() != null && response.isSuccess()) {
                    updateConversationContext(request.getUser(), request, response);
                }
                
                return response;
                
            } catch (Exception e) {
                log.error("Error in enhanced chat completion", e);
                return LlmResponse.error(request.getModel(), "Chat completion failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<Void> generateTextStream(LlmRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                String requestId = UUID.randomUUID().toString();
                request.setRequestId(requestId);
                
                // 스트리밍용 향상된 콜백
                StreamCallback enhancedCallback = new StreamCallback() {
                    private StringBuilder fullContent = new StringBuilder();
                    private long startTime = System.currentTimeMillis();
                    
                    @Override
                    public void onData(String chunk) {
                        fullContent.append(chunk);
                        callback.onData(chunk);
                        
                        // 실시간 메트릭 업데이트
                        recordStreamingMetrics(request.getModel(), chunk.length());
                    }
                    
                    @Override
                    public void onComplete(LlmResponse finalResponse) {
                        try {
                            // 스트리밍 완료 처리
                            finalResponse.setRequestId(requestId);
                            finalResponse.setContent(fullContent.toString());
                            
                            // 사용량 기록
                            recordUsage(request, finalResponse);
                            
                            // 메시지 저장
                            saveMessage(request, finalResponse);
                            
                            // 메트릭 기록
                            recordMetrics(request.getModel(), 
                                System.currentTimeMillis() - startTime, true, false);
                            
                            callback.onComplete(finalResponse);
                            
                        } catch (Exception e) {
                            log.error("Error in streaming completion", e);
                            callback.onError("Streaming completion failed: " + e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        recordMetrics(request.getModel(), 
                            System.currentTimeMillis() - startTime, false, false);
                        callback.onError(error);
                    }
                    
                    @Override
                    public void onMetadata(Map<String, Object> metadata) {
                        callback.onMetadata(metadata);
                    }
                };
                
                basicLlmService.generateTextStream(request, enhancedCallback);
                
            } catch (Exception e) {
                log.error("Error in enhanced streaming", e);
                callback.onError("Enhanced streaming failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<Void> chatCompletionStream(LlmRequest request, StreamCallback callback) {
        return generateTextStream(request, callback);
    }
    
    @Override
    @Cacheable(value = "embeddings", key = "#model + '_' + #text.hashCode()")
    public CompletableFuture<List<Double>> createEmbeddings(String model, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 임베딩 요청 메트릭 기록
                recordEmbeddingRequest(model, text.length());
                
                return basicLlmService.createEmbeddings(model, text).join();
                
            } catch (Exception e) {
                log.error("Error creating enhanced embeddings", e);
                throw new RuntimeException("Enhanced embedding creation failed", e);
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<List<List<Double>>> createBatchEmbeddings(String model, List<String> texts) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 배치 크기 최적화
                int batchSize = getBatchSize(model);
                List<List<Double>> results = new ArrayList<>();
                
                for (int i = 0; i < texts.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, texts.size());
                    List<String> batch = texts.subList(i, end);
                    
                    List<CompletableFuture<List<Double>>> futures = batch.stream()
                        .map(text -> createEmbeddings(model, text))
                        .toList();
                    
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    
                    futures.forEach(future -> results.add(future.join()));
                }
                
                return results;
                
            } catch (Exception e) {
                log.error("Error in batch embeddings", e);
                throw new RuntimeException("Batch embedding creation failed", e);
            }
        }, llmTaskExecutor);
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private ValidationResult validateEnhancedRequest(LlmRequest request) {
        // 기본 검증
        ValidationResult basicValidation = basicLlmService.validateRequest(request);
        if (!basicValidation.isValid()) {
            return basicValidation;
        }
        
        List<String> errors = new ArrayList<>();
        
        // 향상된 검증 로직
        if (request.getMaxTokens() != null && request.getMaxTokens() > 32768) {
            errors.add("Max tokens cannot exceed 32768");
        }
        
        if (request.getTemperature() != null && 
            (request.getTemperature() < 0.0 || request.getTemperature() > 2.0)) {
            errors.add("Temperature must be between 0.0 and 2.0");
        }
        
        // 컨텍스트 길이 검증
        int estimatedTokens = request.estimateTokens();
        String modelName = request.getModel() != null ? request.getModel() : llmConfig.getDefaults().getModel();
        if (!llmConfig.isWithinLimits(modelName, estimatedTokens)) {
            errors.add("Request exceeds model context limit");
        }
        
        return errors.isEmpty() ? 
            ValidationResult.valid() : 
            ValidationResult.invalid(errors);
    }
    
    private boolean checkRateLimit(String userId, String model) {
        if (userId == null) return true;
        
        String key = userId + ":" + model;
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(key, k -> 
            new RateLimiter(getRateLimit(model)));
        
        return rateLimiter.tryAcquire();
    }
    
    private CircuitBreaker getCircuitBreaker(String model) {
        return circuitBreakers.computeIfAbsent(model, k -> 
            new CircuitBreaker(model, getCircuitBreakerConfig(model)));
    }
    
    private String generateCacheKey(LlmRequest request) {
        return cacheService.generateCacheKey(request);
    }
    
    private boolean shouldCache(LlmRequest request, LlmResponse response) {
        // 캐싱 조건 판단
        if (!response.isSuccess()) return false;
        if (request.getTemperature() != null && request.getTemperature() > 0.8) return false;
        if (request.getStream() != null && request.getStream()) return false;
        
        return true;
    }
    
    private LlmRequest optimizeRequest(LlmRequest request) {
        LlmRequest optimized = request.copy();
        
        // 컨텍스트 윈도우 최적화
        if (optimized.getMessages() != null) {
            String modelName = optimized.getModel() != null ? 
                optimized.getModel() : llmConfig.getDefaults().getModel();
            
            Optional<LlmConfigProperties.ModelConfig> config = llmConfig.getModelByName(modelName);
            if (config.isPresent()) {
                int maxTokens = config.get().getLimits().getMaxContextLength();
                List<LlmRequest.ChatMessage> optimizedMessages = 
                    optimizeContextWindow(optimized.getMessages(), maxTokens);
                optimized.setMessages(optimizedMessages);
            }
        }
        
        // 모델별 최적화
        optimized = applyModelSpecificOptimizations(optimized);
        
        return optimized;
    }
    
    private LlmResponse postProcessResponse(LlmResponse response, LlmRequest request) {
        if (!response.isSuccess()) return response;
        
        // 품질 점수 계산
        double qualityScore = evaluateResponseQuality(request, response);
        response.withMetadata("qualityScore", qualityScore);
        
        // 안전성 검사
        if (containsUnsafeContent(response.getContent())) {
            response.withMetadata("safetyWarning", true);
        }
        
        // 토큰 효율성 계산
        if (response.getUsage() != null) {
            double efficiency = (double) response.getUsage().getCompletionTokens() / 
                               response.getUsage().getPromptTokens();
            response.withMetadata("tokenEfficiency", efficiency);
        }
        
        return response;
    }
    
    private void recordMetrics(String model, long responseTime, boolean success, boolean fromCache) {
        // 요청 수 증가
        requestCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
        
        if (!success) {
            errorCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        // 응답 시간 기록
        responseTimes.computeIfAbsent(model, k -> new ArrayList<>()).add(responseTime);
        
        // 캐시 메트릭은 별도 처리
        if (fromCache) {
            cacheService.recordCacheHit(model);
        }
    }
    
    private void recordStreamingMetrics(String model, int chunkSize) {
        // 스트리밍 특화 메트릭
        usageService.recordStreamingChunk(model, chunkSize);
    }
    
    private void recordEmbeddingRequest(String model, int textLength) {
        usageService.recordEmbeddingRequest(model, textLength);
    }
    
    private void recordUsage(LlmRequest request, LlmResponse response) {
        try {
            usageService.recordUsage(request, response);
        } catch (Exception e) {
            log.error("Failed to record usage", e);
        }
    }
    
    @Transactional
    private void saveMessage(LlmRequest request, LlmResponse response) {
        try {
            Message message = Message.builder()
                .messageId(response.getId() != null ? response.getId() : UUID.randomUUID().toString())
                .userId(request.getUser())
                .role(Message.MessageRole.ASSISTANT)
                .content(response.getContent())
                .originalPrompt(request.getMessage())
                .systemPrompt(request.getSystemPrompt())
                .model(response.getModel())
                .provider(response.getProvider())
                .messageType(Message.MessageType.TEXT)
                .inputTokens(response.getUsage() != null ? response.getUsage().getPromptTokens() : null)
                .outputTokens(response.getUsage() != null ? response.getUsage().getCompletionTokens() : null)
                .totalTokens(response.getTokensUsed())
                .responseTimeMs(response.getResponseTimeMs())
                .estimatedCost(response.getUsage() != null ? response.getUsage().getEstimatedCost() : null)
                .currency("USD")
                .finishReason(response.getFinishReason())
                .success(response.isSuccess())
                .errorMessage(response.getError())
                .fromCache(response.isCached())
                .streaming(response.isStreaming())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .requestParameters(serializeRequestParameters(request))
                .responseMetadata(serializeResponseMetadata(response))
                .build();
                
            messageRepository.save(message);
            
        } catch (Exception e) {
            log.error("Failed to save message", e);
        }
    }
    
    // ===== 대화 컨텍스트 관리 =====
    
    private ConversationContext getOrCreateConversationContext(String userId) {
        return conversations.computeIfAbsent(userId, k -> new ConversationContext(userId));
    }
    
    private LlmRequest enhanceWithContext(LlmRequest request, ConversationContext context) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return request;
        }
        
        LlmRequest enhanced = request.copy();
        List<LlmRequest.ChatMessage> contextMessages = context.getRecentMessages(10);
        
        // 기존 메시지와 컨텍스트 병합
        List<LlmRequest.ChatMessage> allMessages = new ArrayList<>(contextMessages);
        allMessages.addAll(enhanced.getMessages());
        
        enhanced.setMessages(allMessages);
        return enhanced;
    }
    
    private void updateConversationContext(String userId, LlmRequest request, LlmResponse response) {
        ConversationContext context = getOrCreateConversationContext(userId);
        
        // 사용자 메시지 추가
        if (request.getMessages() != null) {
            request.getMessages().stream()
                .filter(msg -> "user".equals(msg.getRole()))
                .forEach(context::addMessage);
        }
        
        // 어시스턴트 응답 추가
        if (response != null && response.isSuccess()) {
            LlmRequest.ChatMessage assistantMessage = LlmRequest.ChatMessage.builder()
                .role("assistant")
                .content(response.getContent())
                .build();
            context.addMessage(assistantMessage);
        }
    }
    
    // ===== 설정 및 유틸리티 메서드들 =====
    
    private int getRateLimit(String model) {
        return llmConfig.getModelByName(model)
            .map(config -> config.getLimits().getMaxRequestsPerMinute())
            .orElse(60);
    }
    
    private CircuitBreakerConfig getCircuitBreakerConfig(String model) {
        return CircuitBreakerConfig.builder()
            .failureThreshold(5)
            .recoveryTimeout(30000)
            .build();
    }
    
    private int getBatchSize(String model) {
        return llmConfig.getModelByName(model)
            .map(config -> config.getLimits().getMaxConcurrentRequests())
            .orElse(10);
    }
    
    private LlmRequest applyModelSpecificOptimizations(LlmRequest request) {
        String modelName = request.getModel();
        if (modelName == null) return request;
        
        // 모델별 최적화 로직
        if (modelName.contains("gpt-4")) {
            // GPT-4 최적화
            if (request.getTemperature() == null) {
                request.setTemperature(0.7);
            }
        } else if (modelName.contains("claude")) {
            // Claude 최적화
            if (request.getMaxTokens() == null) {
                request.setMaxTokens(4096);
            }
        }
        
        return request;
    }
    
    private boolean containsUnsafeContent(String content) {
        // 안전성 검사 로직 (실제로는 더 정교한 필터링 필요)
        List<String> unsafePatterns = List.of("violence", "hate", "explicit");
        String lowerContent = content.toLowerCase();
        
        return unsafePatterns.stream().anyMatch(lowerContent::contains);
    }
    
    private String serializeRequestParameters(LlmRequest request) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("temperature", request.getTemperature());
            params.put("maxTokens", request.getMaxTokens());
            params.put("topP", request.getTopP());
            params.put("presencePenalty", request.getPresencePenalty());
            params.put("frequencyPenalty", request.getFrequencyPenalty());
            
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private String serializeResponseMetadata(LlmResponse response) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (response.getMetadata() != null) {
                metadata.putAll(response.getMetadata());
            }
            metadata.put("responseTimeMs", response.getResponseTimeMs());
            metadata.put("fromCache", response.isCached());
            
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    // ===== 위임 메서드들 (기본 구현으로 위임) =====
    
    @Override
    public CompletableFuture<Boolean> isModelAvailable(String modelName) {
        return basicLlmService.isModelAvailable(modelName);
    }
    
    @Override
    public CompletableFuture<Map<String, String>> checkAllProvidersHealth() {
        return basicLlmService.checkAllProvidersHealth();
    }
    
    @Override
    public CompletableFuture<String> checkProviderHealth(String provider) {
        return basicLlmService.checkProviderHealth(provider);
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> getModelInfo(String modelName) {
        return basicLlmService.getModelInfo(modelName);
    }
    
    @Override
    public CompletableFuture<List<Map<String, Object>>> getAvailableModels() {
        return basicLlmService.getAvailableModels();
    }
    
    @Override
    public CompletableFuture<List<Map<String, Object>>> getModelsByProvider(String provider) {
        return basicLlmService.getModelsByProvider(provider);
    }
    
    @Override
    public CompletableFuture<Integer> countTokens(String model, String text) {
        return basicLlmService.countTokens(model, text);
    }
    
    @Override
    public double calculateCost(String model, int inputTokens, int outputTokens) {
        return basicLlmService.calculateCost(model, inputTokens, outputTokens);
    }
    
    @Override
    public ValidationResult validateRequest(LlmRequest request) {
        return validateEnhancedRequest(request);
    }
    
    @Override
    public CompletableFuture<LlmResponse> getCachedResponse(LlmRequest request) {
        String cacheKey = generateCacheKey(request);
        return CompletableFuture.completedFuture(cacheService.getResponse(cacheKey));
    }
    
    @Override
    public CompletableFuture<Void> cacheResponse(LlmRequest request, LlmResponse response) {
        return CompletableFuture.runAsync(() -> {
            String cacheKey = generateCacheKey(request);
            cacheService.cacheResponse(cacheKey, response);
        });
    }
    
    @Override
    public CompletableFuture<List<LlmResponse>> generateBatchText(List<LlmRequest> requests) {
        return basicLlmService.generateBatchText(requests);
    }
    
    @Override
    public String recommendModel(String task, Map<String, Object> requirements) {
        return basicLlmService.recommendModel(task, requirements);
    }
    
    @Override
    public CompletableFuture<LlmResponse> handleFunctionCall(LlmRequest request) {
        return basicLlmService.handleFunctionCall(request);
    }
    
    @Override
    public String generatePromptFromTemplate(String templateName, Map<String, Object> variables) {
        return basicLlmService.generatePromptFromTemplate(templateName, variables);
    }
    
    @Override
    public CompletableFuture<List<LlmRequest.ChatMessage>> updateConversationContext(String conversationId, LlmRequest.ChatMessage message) {
        return basicLlmService.updateConversationContext(conversationId, message);
    }
    
    @Override
    public LlmResponse postProcessResponse(LlmResponse response, Map<String, Object> options) {
        return basicLlmService.postProcessResponse(response, options);
    }
    
    @Override
    public boolean supportsFeature(String provider, String feature) {
        return basicLlmService.supportsFeature(provider, feature);
    }
    
    @Override
    public int getAvailableConcurrency(String modelName) {
        return basicLlmService.getAvailableConcurrency(modelName);
    }
    
    @Override
    public Map<String, Object> getQueueStatus() {
        return basicLlmService.getQueueStatus();
    }
    
    @Override
    public CompletableFuture<Boolean> warmupModel(String modelName) {
        return basicLlmService.warmupModel(modelName);
    }
    
    @Override
    public double evaluateResponseQuality(LlmRequest request, LlmResponse response) {
        return basicLlmService.evaluateResponseQuality(request, response);
    }
    
    @Override
    public CompletableFuture<Map<String, LlmResponse>> compareModels(LlmRequest request, String modelA, String modelB) {
        return basicLlmService.compareModels(request, modelA, modelB);
    }
    
    @Override
    public CompletableFuture<LlmResponse> retryRequest(LlmRequest request, int maxRetries) {
        return basicLlmService.retryRequest(request, maxRetries);
    }
    
    @Override
    public List<LlmRequest.ChatMessage> optimizeContextWindow(List<LlmRequest.ChatMessage> messages, int maxTokens) {
        return basicLlmService.optimizeContextWindow(messages, maxTokens);
    }
    
    @Override
    public Map<String, Object> getServiceStatistics() {
        Map<String, Object> basicStats = basicLlmService.getServiceStatistics();
        Map<String, Object> enhancedStats = new HashMap<>(basicStats);
        
        // 향상된 통계 추가
        enhancedStats.put("circuitBreakers", circuitBreakers.size());
        enhancedStats.put("activeConversations", conversations.size());
        enhancedStats.put("rateLimiters", rateLimiters.size());
        
        return enhancedStats;
    }
    
    @Override
    public boolean updateSettings(Map<String, Object> settings) {
        return basicLlmService.updateSettings(settings);
    }
    
    @Override
    public CompletableFuture<Boolean> emergencyStop(String reason) {
        return basicLlmService.emergencyStop(reason);
    }
    
    @Override
    public CompletableFuture<Boolean> restart() {
        return basicLlmService.restart();
    }
    
    // ===== 내부 클래스들 =====
    
    private static class RateLimiter {
        private final int maxRequests;
        private final AtomicInteger currentRequests = new AtomicInteger(0);
        private volatile long lastResetTime = System.currentTimeMillis();
        private final long resetInterval = 60000; // 1분
        
        public RateLimiter(int maxRequestsPerMinute) {
            this.maxRequests = maxRequestsPerMinute;
        }
        
        public boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime >= resetInterval) {
                currentRequests.set(0);
                lastResetTime = now;
            }
            
            return currentRequests.incrementAndGet() <= maxRequests;
        }
    }
    
    private static class CircuitBreaker {
        private final String name;
        private final CircuitBreakerConfig config;
        private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long lastFailureTime = 0;
        
        public CircuitBreaker(String name, CircuitBreakerConfig config) {
            this.name = name;
            this.config = config;
        }
        
        public <T> T call(Supplier<T> supplier) throws Exception {
            if (state == CircuitBreakerState.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > config.getRecoveryTimeout()) {
                    state = CircuitBreakerState.HALF_OPEN;
                } else {
                    throw new RuntimeException("Circuit breaker is open for: " + name);
                }
            }
            
            try {
                T result = supplier.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw e;
            }
        }
        
        public boolean isOpen() {
            return state == CircuitBreakerState.OPEN;
        }
        
        private void onSuccess() {
            failureCount.set(0);
            state = CircuitBreakerState.CLOSED;
        }
        
        private void onFailure() {
            lastFailureTime = System.currentTimeMillis();
            if (failureCount.incrementAndGet() >= config.getFailureThreshold()) {
                state = CircuitBreakerState.OPEN;
            }
        }
    }
    
    @lombok.Builder
    @lombok.Data
    private static class CircuitBreakerConfig {
        private int failureThreshold;
        private long recoveryTimeout;
    }
    
    private enum CircuitBreakerState {
        CLOSED, OPEN, HALF_OPEN
    }
    
    private static class ConversationContext {
        private final String userId;
        private final List<LlmRequest.ChatMessage> messages = new ArrayList<>();
        private final int maxMessages = 50;
        private LocalDateTime lastActivity = LocalDateTime.now();
        
        public ConversationContext(String userId) {
            this.userId = userId;
        }
        
        public void addMessage(LlmRequest.ChatMessage message) {
            messages.add(message);
            lastActivity = LocalDateTime.now();
            
            // 메시지 수 제한
            if (messages.size() > maxMessages) {
                messages.remove(0);
            }
        }
        
        public List<LlmRequest.ChatMessage> getRecentMessages(int count) {
            int start = Math.max(0, messages.size() - count);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        
        public boolean isExpired(long maxIdleTimeMs) {
            return System.currentTimeMillis() - 
                   lastActivity.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
                   > maxIdleTimeMs;
        }
    }
}