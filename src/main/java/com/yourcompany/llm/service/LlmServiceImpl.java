// LlmServiceImpl.java
package com.yourcompany.llm.service.impl;

import com.yourcompany.llm.config.LlmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {
    
    private final LlmConfigProperties llmConfig;
    private final RestTemplate restTemplate;
    private final Executor llmTaskExecutor;
    
    // 프로바이더별 클라이언트 매핑
    private final Map<String, String> providerHealthStatus = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHealthCheck = new ConcurrentHashMap<>();
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public CompletableFuture<LlmResponse> generateText(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 요청 유효성 검증
                ValidationResult validation = validateRequest(request);
                if (!validation.isValid()) {
                    return LlmResponse.error(request.getModel(), 
                        "Validation failed: " + validation.getMessage());
                }
                
                // 모델 설정 조회
                String modelName = request.getModel() != null ? 
                    request.getModel() : llmConfig.getDefaults().getModel();
                
                Optional<LlmConfigProperties.ModelConfig> modelConfig = 
                    llmConfig.getModelByName(modelName);
                
                if (modelConfig.isEmpty()) {
                    return LlmResponse.error(modelName, "Model not found or disabled");
                }
                
                // 캐시 확인
                LlmResponse cachedResponse = getCachedResponse(request).join();
                if (cachedResponse != null) {
                    log.debug("Returning cached response for model: {}", modelName);
                    return cachedResponse;
                }
                
                // 프로바이더별 처리
                LlmResponse response = processRequestByProvider(request, modelConfig.get());
                
                // 응답 캐시 저장
                if (response.isSuccess()) {
                    cacheResponse(request, response);
                }
                
                return response;
                
            } catch (Exception e) {
                log.error("Error generating text for model: {}", request.getModel(), e);
                return LlmResponse.error(request.getModel(), 
                    "Text generation failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<LlmResponse> chatCompletion(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 채팅 메시지 형태로 변환
                if (request.getMessages() == null && request.getMessage() != null) {
                    List<LlmRequest.ChatMessage> messages = request.toChatMessages();
                    request.setMessages(messages);
                }
                
                return generateText(request).join();
                
            } catch (Exception e) {
                log.error("Error in chat completion for model: {}", request.getModel(), e);
                return LlmResponse.error(request.getModel(), 
                    "Chat completion failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<Void> generateTextStream(LlmRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 현재는 스트리밍을 일반 응답으로 시뮬레이션
                // 실제 구현에서는 WebClient나 Server-Sent Events 사용
                LlmResponse response = generateText(request).join();
                
                if (response.isSuccess()) {
                    // 텍스트를 청크로 나누어 스트리밍 시뮬레이션
                    String content = response.getContent();
                    int chunkSize = 50;
                    
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, content.length());
                        String chunk = content.substring(i, end);
                        callback.onData(chunk);
                        
                        // 스트리밍 느낌을 위한 지연
                        try { Thread.sleep(100); } catch (InterruptedException e) { 
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    callback.onComplete(response);
                } else {
                    callback.onError(response.getError());
                }
                
            } catch (Exception e) {
                callback.onError("Streaming failed: " + e.getMessage());
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
                // 임베딩 모델 확인
                Optional<LlmConfigProperties.ModelConfig> modelConfig = 
                    llmConfig.getModelByName(model);
                
                if (modelConfig.isEmpty() || 
                    !Boolean.TRUE.equals(modelConfig.get().getFeatures().getSupportsEmbeddings())) {
                    throw new IllegalArgumentException("Model does not support embeddings: " + model);
                }
                
                // 실제 구현에서는 프로바이더 API 호출
                // 현재는 더미 임베딩 반환
                Random random = new Random(text.hashCode());
                List<Double> embedding = new ArrayList<>();
                for (int i = 0; i < 1536; i++) { // OpenAI embedding 차원
                    embedding.add(random.nextGaussian());
                }
                
                log.debug("Generated embedding for text length: {} using model: {}", 
                    text.length(), model);
                
                return embedding;
                
            } catch (Exception e) {
                log.error("Error creating embeddings with model: {}", model, e);
                throw new RuntimeException("Embedding creation failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<List<List<Double>>> createBatchEmbeddings(String model, List<String> texts) {
        List<CompletableFuture<List<Double>>> futures = texts.stream()
            .map(text -> createEmbeddings(model, text))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    @Override
    public CompletableFuture<Boolean> isModelAvailable(String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<LlmConfigProperties.ModelConfig> modelConfig = 
                llmConfig.getModelByName(modelName);
            
            if (modelConfig.isEmpty()) {
                return false;
            }
            
            // 프로바이더 헬스 체크
            String provider = modelConfig.get().getProvider();
            return checkProviderHealth(provider).join().equals("UP");
        });
    }
    
    @Override
    public CompletableFuture<Map<String, String>> checkAllProvidersHealth() {
        Map<String, String> healthStatuses = new HashMap<>();
        
        Set<String> providers = llmConfig.getEnabledModels().stream()
            .map(LlmConfigProperties.ModelConfig::getProvider)
            .collect(java.util.stream.Collectors.toSet());
        
        List<CompletableFuture<Void>> futures = providers.stream()
            .map(provider -> checkProviderHealth(provider)
                .thenAccept(status -> healthStatuses.put(provider, status)))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> healthStatuses);
    }
    
    @Override
    public CompletableFuture<String> checkProviderHealth(String provider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 캐시된 헬스 상태 확인 (5분 캐시)
                LocalDateTime lastCheck = lastHealthCheck.get(provider);
                if (lastCheck != null && 
                    lastCheck.isAfter(LocalDateTime.now().minusMinutes(5))) {
                    return providerHealthStatus.getOrDefault(provider, "UNKNOWN");
                }
                
                // 프로바이더별 헬스 체크 로직
                String healthStatus = performProviderHealthCheck(provider);
                
                providerHealthStatus.put(provider, healthStatus);
                lastHealthCheck.put(provider, LocalDateTime.now());
                
                return healthStatus;
                
            } catch (Exception e) {
                log.error("Health check failed for provider: {}", provider, e);
                providerHealthStatus.put(provider, "DOWN");
                return "DOWN";
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> getModelInfo(String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<LlmConfigProperties.ModelConfig> modelConfig = 
                llmConfig.getModelByName(modelName);
            
            if (modelConfig.isEmpty()) {
                return Map.of("error", "Model not found: " + modelName);
            }
            
            LlmConfigProperties.ModelConfig config = modelConfig.get();
            Map<String, Object> info = new HashMap<>();
            info.put("name", config.getName());
            info.put("provider", config.getProvider());
            info.put("maxTokens", config.getMaxTokens());
            info.put("temperature", config.getTemperature());
            info.put("enabled", config.getEnabled());
            info.put("features", config.getFeatures());
            info.put("limits", config.getLimits());
            
            return info;
        });
    }
    
    @Override
    public CompletableFuture<List<Map<String, Object>>> getAvailableModels() {
        return CompletableFuture.supplyAsync(() -> 
            llmConfig.getEnabledModels().stream()
                .map(model -> {
                    Map<String, Object> modelInfo = new HashMap<>();
                    modelInfo.put("name", model.getName());
                    modelInfo.put("provider", model.getProvider());
                    modelInfo.put("maxTokens", model.getMaxTokens());
                    modelInfo.put("features", model.getFeatures());
                    return modelInfo;
                })
                .toList()
        );
    }
    
    @Override
    public CompletableFuture<List<Map<String, Object>>> getModelsByProvider(String provider) {
        return CompletableFuture.supplyAsync(() -> 
            llmConfig.getModelsByProvider(provider).stream()
                .map(model -> Map.of(
                    "name", model.getName(),
                    "maxTokens", model.getMaxTokens(),
                    "enabled", model.getEnabled()
                ))
                .toList()
        );
    }
    
    @Override
    public CompletableFuture<Integer> countTokens(String model, String text) {
        return CompletableFuture.supplyAsync(() -> {
            // 간단한 토큰 계산 (실제로는 토크나이저 사용)
            return text.length() / 4; // 대략 4글자당 1토큰
        });
    }
    
    @Override
    public double calculateCost(String model, int inputTokens, int outputTokens) {
        return llmConfig.calculateCost(model, inputTokens, outputTokens);
    }
    
    @Override
    public ValidationResult validateRequest(LlmRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request == null) {
            return ValidationResult.invalid("Request cannot be null");
        }
        
        // 메시지 유효성 검증
        if ((request.getMessage() == null || request.getMessage().trim().isEmpty()) &&
            (request.getMessages() == null || request.getMessages().isEmpty())) {
            errors.add("Either message or messages must be provided");
        }
        
        // 모델 확인
        String modelName = request.getModel() != null ? 
            request.getModel() : llmConfig.getDefaults().getModel();
        
        if (!llmConfig.getModelByName(modelName).isPresent()) {
            errors.add("Model not found or disabled: " + modelName);
        }
        
        // 토큰 제한 확인
        if (request.getMaxTokens() != null) {
            if (!llmConfig.isWithinLimits(modelName, request.getMaxTokens())) {
                errors.add("Max tokens exceeds model limit");
            }
        }
        
        // 메시지 유효성 검증
        if (request.getMessages() != null) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                LlmRequest.ChatMessage msg = request.getMessages().get(i);
                if (msg.getRole() == null || msg.getRole().trim().isEmpty()) {
                    errors.add("Message " + i + " role is required");
                }
                if (msg.getContent() == null || msg.getContent().trim().isEmpty()) {
                    errors.add("Message " + i + " content is required");
                }
            }
        }
        
        return errors.isEmpty() ? 
            ValidationResult.valid() : 
            ValidationResult.invalid(errors);
    }
    
    @Override
    @Cacheable(value = "llm-responses", key = "#request.hashCode()")
    public CompletableFuture<LlmResponse> getCachedResponse(LlmRequest request) {
        // 실제 구현에서는 Redis 등에서 캐시 조회
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> cacheResponse(LlmRequest request, LlmResponse response) {
        return CompletableFuture.runAsync(() -> {
            // 실제 구현에서는 Redis 등에 캐시 저장
            log.debug("Caching response for model: {}", response.getModel());
        }, llmTaskExecutor);
    }
    
    @Override
    public CompletableFuture<List<LlmResponse>> generateBatchText(List<LlmRequest> requests) {
        List<CompletableFuture<LlmResponse>> futures = requests.stream()
            .map(this::generateText)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    @Override
    public String recommendModel(String task, Map<String, Object> requirements) {
        List<String> requiredFeatures = (List<String>) requirements.getOrDefault("features", List.of());
        
        return llmConfig.recommendModel(task, requiredFeatures)
            .map(LlmConfigProperties.ModelConfig::getName)
            .orElse(llmConfig.getDefaults().getModel());
    }
    
    @Override
    public CompletableFuture<LlmResponse> handleFunctionCall(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // 함수 호출 처리 로직
            if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
                return LlmResponse.error(request.getModel(), "No functions defined for function call");
            }
            
            // 실제 구현에서는 함수 실행 및 결과 반환
            String functionName = request.getFunctions().get(0).getName();
            String arguments = "{}"; // 실제 함수 호출 결과
            
            return LlmResponse.functionCall(request.getModel(), functionName, arguments, "function_provider");
        }, llmTaskExecutor);
    }
    
    @Override
    public String generatePromptFromTemplate(String templateName, Map<String, Object> variables) {
        // 템플릿 엔진 사용 (예: Mustache, Thymeleaf)
        String template = getTemplate(templateName);
        return processTemplate(template, variables);
    }
    
    @Override
    public CompletableFuture<List<LlmRequest.ChatMessage>> updateConversationContext(
            String conversationId, LlmRequest.ChatMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            // 대화 컨텍스트 관리 로직
            List<LlmRequest.ChatMessage> context = getConversationContext(conversationId);
            context.add(message);
            
            // 컨텍스트 윈도우 최적화
            if (context.size() > 50) { // 최대 50개 메시지
                context = context.subList(context.size() - 50, context.size());
            }
            
            saveConversationContext(conversationId, context);
            return context;
        });
    }
    
    @Override
    public LlmResponse postProcessResponse(LlmResponse response, Map<String, Object> options) {
        if (!response.isSuccess()) {
            return response;
        }
        
        String content = response.getContent();
        
        // 후처리 옵션 적용
        Boolean trim = (Boolean) options.getOrDefault("trim", true);
        if (Boolean.TRUE.equals(trim)) {
            content = content.trim();
        }
        
        Boolean filterProfanity = (Boolean) options.getOrDefault("filterProfanity", false);
        if (Boolean.TRUE.equals(filterProfanity)) {
            content = filterProfanity(content);
        }
        
        Boolean formatMarkdown = (Boolean) options.getOrDefault("formatMarkdown", false);
        if (Boolean.TRUE.equals(formatMarkdown)) {
            content = formatAsMarkdown(content);
        }
        
        response.setContent(content);
        return response;
    }
    
    @Override
    public boolean supportsFeature(String provider, String feature) {
        return llmConfig.getModelsByProvider(provider).stream()
            .anyMatch(model -> hasFeature(model, feature));
    }
    
    @Override
    public int getAvailableConcurrency(String modelName) {
        return llmConfig.getModelByName(modelName)
            .map(model -> model.getLimits().getMaxConcurrentRequests())
            .orElse(1);
    }
    
    @Override
    public Map<String, Object> getQueueStatus() {
        return Map.of(
            "pending", 0,
            "processing", 0,
            "completed", 0,
            "failed", 0,
            "timestamp", LocalDateTime.now()
        );
    }
    
    @Override
    public CompletableFuture<Boolean> warmupModel(String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 워밍업 요청 생성
                LlmRequest warmupRequest = LlmRequest.builder()
                    .model(modelName)
                    .message("Hello, this is a warmup request.")
                    .maxTokens(10)
                    .build();
                
                LlmResponse response = generateText(warmupRequest).join();
                return response.isSuccess();
                
            } catch (Exception e) {
                log.warn("Model warmup failed for: {}", modelName, e);
                return false;
            }
        }, llmTaskExecutor);
    }
    
    @Override
    public double evaluateResponseQuality(LlmRequest request, LlmResponse response) {
        if (!response.isSuccess()) {
            return 0.0;
        }
        
        double score = 1.0;
        
        // 응답 길이 평가
        String content = response.getContent();
        if (content == null || content.trim().isEmpty()) {
            return 0.0;
        }
        
        // 응답 시간 평가
        if (response.getResponseTimeMs() != null && response.getResponseTimeMs() > 10000) {
            score *= 0.8; // 10초 이상이면 점수 감점
        }
        
        // 토큰 효율성 평가
        if (response.getTokensUsed() != null) {
            int estimatedTokens = request.estimateTokens();
            if (response.getTokensUsed() > estimatedTokens * 3) {
                score *= 0.9; // 예상보다 3배 이상 토큰 사용 시 감점
            }
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    @Override
    public CompletableFuture<Map<String, LlmResponse>> compareModels(
            LlmRequest request, String modelA, String modelB) {
        
        LlmRequest requestA = request.copy();
        requestA.setModel(modelA);
        
        LlmRequest requestB = request.copy();
        requestB.setModel(modelB);
        
        CompletableFuture<LlmResponse> futureA = generateText(requestA);
        CompletableFuture<LlmResponse> futureB = generateText(requestB);
        
        return CompletableFuture.allOf(futureA, futureB)
            .thenApply(v -> Map.of(
                modelA, futureA.join(),
                modelB, futureB.join()
            ));
    }
    
    @Override
    public CompletableFuture<LlmResponse> retryRequest(LlmRequest request, int maxRetries) {
        return generateText(request)
            .handle((response, throwable) -> {
                if (throwable != null || !response.isSuccess()) {
                    if (maxRetries > 0) {
                        log.warn("Request failed, retrying... ({} retries left)", maxRetries);
                        return retryRequest(request, maxRetries - 1).join();
                    } else {
                        return response != null ? response : 
                            LlmResponse.error(request.getModel(), "Max retries exceeded");
                    }
                }
                return response;
            });
    }
    
    @Override
    public List<LlmRequest.ChatMessage> optimizeContextWindow(
            List<LlmRequest.ChatMessage> messages, int maxTokens) {
        
        int totalTokens = messages.stream()
            .mapToInt(msg -> msg.getContent().length() / 4)
            .sum();
        
        if (totalTokens <= maxTokens) {
            return messages;
        }
        
        // 시스템 메시지는 유지하고 오래된 메시지부터 제거
        List<LlmRequest.ChatMessage> optimized = new ArrayList<>();
        List<LlmRequest.ChatMessage> systemMessages = messages.stream()
            .filter(msg -> "system".equals(msg.getRole()))
            .toList();
        
        optimized.addAll(systemMessages);
        
        List<LlmRequest.ChatMessage> otherMessages = messages.stream()
            .filter(msg -> !"system".equals(msg.getRole()))
            .toList();
        
        // 최근 메시지부터 추가
        Collections.reverse(otherMessages);
        int currentTokens = systemMessages.stream()
            .mapToInt(msg -> msg.getContent().length() / 4)
            .sum();
        
        for (LlmRequest.ChatMessage msg : otherMessages) {
            int msgTokens = msg.getContent().length() / 4;
            if (currentTokens + msgTokens <= maxTokens) {
                optimized.add(0, msg); // 앞에 추가
                currentTokens += msgTokens;
            } else {
                break;
            }
        }
        
        return optimized;
    }
    
    @Override
    public Map<String, Object> getServiceStatistics() {
        return Map.of(
            "totalRequests", 0,
            "successfulRequests", 0,
            "failedRequests", 0,
            "averageResponseTime", 0.0,
            "totalTokensUsed", 0,
            "cacheHitRate", 0.0,
            "timestamp", LocalDateTime.now()
        );
    }
    
    @Override
    public boolean updateSettings(Map<String, Object> settings) {
        try {
            // 설정 업데이트 로직
            log.info("Updating service settings: {}", settings);
            return true;
        } catch (Exception e) {
            log.error("Failed to update settings", e);
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> emergencyStop(String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.warn("Emergency stop requested: {}", reason);
                // 모든 진행 중인 요청 중단
                return true;
            } catch (Exception e) {
                log.error("Emergency stop failed", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> restart() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Service restart requested");
                // 서비스 재시작 로직
                return true;
            } catch (Exception e) {
                log.error("Service restart failed", e);
                return false;
            }
        });
    }
    
    // 헬퍼 메서드들
    
    private LlmResponse processRequestByProvider(LlmRequest request, 
                                                LlmConfigProperties.ModelConfig modelConfig) {
        String provider = modelConfig.getProvider();
        long startTime = System.currentTimeMillis();
        
        try {
            LlmResponse response = switch (provider.toLowerCase()) {
                case "openai" -> processOpenAIRequest(request, modelConfig);
                case "anthropic" -> processAnthropicRequest(request, modelConfig);
                case "google" -> processGoogleRequest(request, modelConfig);
                case "huggingface" -> processHuggingFaceRequest(request, modelConfig);
                case "ollama" -> processOllamaRequest(request, modelConfig);
                case "vllm" -> processVllmRequest(request, modelConfig);
                case "local" -> processLocalRequest(request, modelConfig);
                default -> LlmResponse.error(modelConfig.getName(), 
                    "Unsupported provider: " + provider);
            };
            
            long responseTime = System.currentTimeMillis() - startTime;
            response.setResponseTimeMs(responseTime);
            
            return response;
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("Error processing request with provider: {}", provider, e);
            
            LlmResponse errorResponse = LlmResponse.error(modelConfig.getName(), 
                "Provider error: " + e.getMessage());
            errorResponse.setResponseTimeMs(responseTime);
            
            return errorResponse;
        }
    }
    
    private LlmResponse processOpenAIRequest(LlmRequest request, 
                                           LlmConfigProperties.ModelConfig modelConfig) {
        // OpenAI API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from OpenAI", 150, "openai");
    }
    
    private LlmResponse processAnthropicRequest(LlmRequest request, 
                                              LlmConfigProperties.ModelConfig modelConfig) {
        // Anthropic API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from Anthropic", 120, "anthropic");
    }
    
    private LlmResponse processGoogleRequest(LlmRequest request, 
                                           LlmConfigProperties.ModelConfig modelConfig) {
        // Google API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from Google", 100, "google");
    }
    
    private LlmResponse processHuggingFaceRequest(LlmRequest request, 
                                                LlmConfigProperties.ModelConfig modelConfig) {
        // HuggingFace API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from HuggingFace", 200, "huggingface");
    }
    
    private LlmResponse processOllamaRequest(LlmRequest request, 
                                           LlmConfigProperties.ModelConfig modelConfig) {
        // Ollama API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from Ollama", 180, "ollama");
    }
    
    private LlmResponse processVllmRequest(LlmRequest request, 
                                         LlmConfigProperties.ModelConfig modelConfig) {
        // vLLM API 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from vLLM", 90, "vllm");
    }
    
    private LlmResponse processLocalRequest(LlmRequest request, 
                                          LlmConfigProperties.ModelConfig modelConfig) {
        // 로컬 모델 호출 로직
        return LlmResponse.success(modelConfig.getName(), 
            "This is a mock response from local model", 300, "local");
    }
    
    private String performProviderHealthCheck(String provider) {
        // 실제 구현에서는 각 프로바이더의 헬스 엔드포인트 호출
        try {
            Thread.sleep(100); // 네트워크 지연 시뮬레이션
            return Math.random() > 0.1 ? "UP" : "DOWN"; // 90% 확률로 UP
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "DOWN";
        }
    }
    
    private boolean hasFeature(LlmConfigProperties.ModelConfig model, String feature) {
        if (model.getFeatures() == null) return false;
        
        return switch (feature.toLowerCase()) {
            case "streaming" -> Boolean.TRUE.equals(model.getFeatures().getSupportsStreaming());
            case "function_calling" -> Boolean.TRUE.equals(model.getFeatures().getSupportsFunctionCalling());
            case "vision" -> Boolean.TRUE.equals(model.getFeatures().getSupportsVision());
            case "embeddings" -> Boolean.TRUE.equals(model.getFeatures().getSupportsEmbeddings());
            default -> false;
        };
    }
    
    private String getTemplate(String templateName) {
        // 템플릿 로딩 로직
        return "Hello {{name}}, how can I help you with {{task}}?";
    }
    
    private String processTemplate(String template, Map<String, Object> variables) {
        // 간단한 템플릿 처리
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", 
                String.valueOf(entry.getValue()));
        }
        return result;
    }
    
    private List<LlmRequest.ChatMessage> getConversationContext(String conversationId) {
        // 실제 구현에서는 데이터베이스에서 조회
        return new ArrayList<>();
    }
    
    private void saveConversationContext(String conversationId, 
                                       List<LlmRequest.ChatMessage> context) {
        // 실제 구현에서는 데이터베이스에 저장
        log.debug("Saving conversation context for: {}", conversationId);
    }
    
    private String filterProfanity(String content) {
        // 욕설 필터링 로직
        return content; // 현재는 그대로 반환
    }
    
    private String formatAsMarkdown(String content) {
        // 마크다운 포맷팅 로직
        return content; // 현재는 그대로 반환
    }
}