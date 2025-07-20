// LlmController.java
package com.yourcompany.llm.controller;

import com.yourcompany.llm.config.LlmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class LlmController {
    
    private final LlmService llmService;
    private final LlmConfigProperties llmConfigProperties;
    
    /**
     * 텍스트 생성 API
     */
    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<LlmResponse>> generateText(@Valid @RequestBody LlmRequest request) {
        log.info("Text generation request received - Model: {}, Message length: {}", 
                request.getModel(), request.getMessage() != null ? request.getMessage().length() : 0);
        
        return llmService.generateText(request)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    log.debug("Text generation completed successfully - Model: {}, Tokens: {}", 
                            response.getModel(), response.getTokensUsed());
                } else {
                    log.warn("Text generation failed - Model: {}, Error: {}", 
                            response.getModel(), response.getError());
                }
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error in text generation", throwable);
                LlmResponse errorResponse = LlmResponse.error(
                    request.getModel() != null ? request.getModel() : "unknown",
                    "Internal server error: " + throwable.getMessage()
                );
                return ResponseEntity.status(500).body(errorResponse);
            });
    }
    
    /**
     * 스트리밍 텍스트 생성 API
     */
    @PostMapping("/generate/stream")
    public CompletableFuture<ResponseEntity<String>> generateTextStream(@Valid @RequestBody LlmRequest request) {
        log.info("Streaming text generation request received - Model: {}", request.getModel());
        
        // 스트리밍 구현은 WebFlux나 Server-Sent Events 필요
        // 현재는 기본 응답 반환
        return llmService.generateText(request)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    return ResponseEntity.ok()
                        .header("Content-Type", "text/event-stream")
                        .body("data: " + response.getContent() + "\n\n");
                } else {
                    return ResponseEntity.status(500)
                        .body("data: {\"error\": \"" + response.getError() + "\"}\n\n");
                }
            });
    }
    
    /**
     * 채팅 완성 API (OpenAI 호환)
     */
    @PostMapping("/chat/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> chatCompletion(@Valid @RequestBody LlmRequest request) {
        log.info("Chat completion request received - Model: {}, Messages: {}", 
                request.getModel(), request.getMessages() != null ? request.getMessages().size() : 0);
        
        return llmService.chatCompletion(request)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> {
                log.error("Error in chat completion", throwable);
                LlmResponse errorResponse = LlmResponse.error(
                    request.getModel() != null ? request.getModel() : "unknown",
                    "Chat completion failed: " + throwable.getMessage()
                );
                return ResponseEntity.status(500).body(errorResponse);
            });
    }
    
    /**
     * 이용 가능한 모델 목록 조회
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModels() {
        List<LlmConfigProperties.ModelConfig> models = llmConfigProperties.getEnabledModels();
        
        Map<String, Object> response = Map.of(
            "models", models.stream()
                .map(model -> Map.of(
                    "name", model.getName(),
                    "provider", model.getProvider(),
                    "maxTokens", model.getMaxTokens(),
                    "features", model.getFeatures() != null ? model.getFeatures() : Map.of(),
                    "enabled", model.getEnabled()
                ))
                .toList(),
            "total", models.size(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 모델 정보 조회
     */
    @GetMapping("/models/{modelName}")
    public ResponseEntity<Map<String, Object>> getModelInfo(@PathVariable String modelName) {
        return llmConfigProperties.getModelByName(modelName)
            .map(model -> {
                Map<String, Object> modelInfo = Map.of(
                    "name", model.getName(),
                    "provider", model.getProvider(),
                    "endpoint", model.getEndpoint(),
                    "maxTokens", model.getMaxTokens(),
                    "temperature", model.getTemperature(),
                    "enabled", model.getEnabled(),
                    "limits", model.getLimits() != null ? model.getLimits() : Map.of(),
                    "features", model.getFeatures() != null ? model.getFeatures() : Map.of(),
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(modelInfo);
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 모델별 비용 계산
     */
    @PostMapping("/models/{modelName}/calculate-cost")
    public ResponseEntity<Map<String, Object>> calculateCost(
            @PathVariable String modelName,
            @RequestParam int inputTokens,
            @RequestParam int outputTokens) {
        
        double cost = llmConfigProperties.calculateCost(modelName, inputTokens, outputTokens);
        
        Map<String, Object> response = Map.of(
            "modelName", modelName,
            "inputTokens", inputTokens,
            "outputTokens", outputTokens,
            "totalCost", cost,
            "currency", "USD",
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 프로바이더별 모델 목록 조회
     */
    @GetMapping("/providers/{provider}/models")
    public ResponseEntity<Map<String, Object>> getModelsByProvider(@PathVariable String provider) {
        List<LlmConfigProperties.ModelConfig> models = llmConfigProperties.getModelsByProvider(provider);
        
        Map<String, Object> response = Map.of(
            "provider", provider,
            "models", models.stream()
                .map(model -> Map.of(
                    "name", model.getName(),
                    "maxTokens", model.getMaxTokens(),
                    "enabled", model.getEnabled()
                ))
                .toList(),
            "total", models.size(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 기능별 모델 추천
     */
    @GetMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommendModel(
            @RequestParam String task,
            @RequestParam(required = false) List<String> requiredFeatures) {
        
        List<String> features = requiredFeatures != null ? requiredFeatures : List.of();
        
        return llmConfigProperties.recommendModel(task, features)
            .map(model -> {
                Map<String, Object> response = Map.of(
                    "task", task,
                    "requiredFeatures", features,
                    "recommendedModel", Map.of(
                        "name", model.getName(),
                        "provider", model.getProvider(),
                        "reason", "Best match for task: " + task,
                        "features", model.getFeatures() != null ? model.getFeatures() : Map.of()
                    ),
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.ok(Map.of(
                "task", task,
                "requiredFeatures", features,
                "recommendedModel", null,
                "message", "No suitable model found for the specified requirements",
                "timestamp", LocalDateTime.now()
            )));
    }
    
    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "LLM API",
            "version", "1.0.0",
            "availableModels", llmConfigProperties.getEnabledModels().size(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 상세 헬스 체크 (모든 모델 프로바이더 확인)
     */
    @GetMapping("/health/detailed")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> detailedHealthCheck() {
        return llmService.checkAllProvidersHealth()
            .thenApply(healthStatuses -> {
                Map<String, Object> health = Map.of(
                    "status", healthStatuses.values().stream().allMatch(status -> "UP".equals(status)) ? "UP" : "DEGRADED",
                    "service", "LLM API",
                    "providers", healthStatuses,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(health);
            });
    }
    
    /**
     * 설정 정보 조회
     */
    @GetMapping("/config/summary")
    public ResponseEntity<LlmConfigProperties.ConfigSummary> getConfigSummary() {
        LlmConfigProperties.ConfigSummary summary = llmConfigProperties.getSummary();
        return ResponseEntity.ok(summary);
    }
    
    /**
     * 텍스트 임베딩 생성
     */
    @PostMapping("/embeddings")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createEmbeddings(
            @RequestBody Map<String, Object> request) {
        
        String input = (String) request.get("input");
        String model = (String) request.getOrDefault("model", llmConfigProperties.getDefaults().getModel());
        
        return llmService.createEmbeddings(model, input)
            .thenApply(embeddings -> {
                Map<String, Object> response = Map.of(
                    "object", "list",
                    "data", embeddings,
                    "model", model,
                    "usage", Map.of(
                        "prompt_tokens", input.length() / 4, // 대략적인 토큰 수
                        "total_tokens", input.length() / 4
                    )
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error creating embeddings", throwable);
                Map<String, Object> error = Map.of(
                    "error", Map.of(
                        "message", "Failed to create embeddings: " + throwable.getMessage(),
                        "type", "embedding_error"
                    )
                );
                return ResponseEntity.status(500).body(error);
            });
    }
    
    /**
     * 배치 텍스트 생성
     */
    @PostMapping("/generate/batch")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateBatch(
            @Valid @RequestBody Map<String, Object> batchRequest) {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = (List<Map<String, Object>>) batchRequest.get("requests");
        
        List<CompletableFuture<LlmResponse>> futures = requests.stream()
            .map(req -> {
                LlmRequest llmRequest = new LlmRequest();
                llmRequest.setModel((String) req.get("model"));
                llmRequest.setMessage((String) req.get("message"));
                return llmService.generateText(llmRequest);
            })
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<LlmResponse> responses = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                Map<String, Object> batchResponse = Map.of(
                    "responses", responses,
                    "total", responses.size(),
                    "successful", responses.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum(),
                    "timestamp", LocalDateTime.now()
                );
                
                return ResponseEntity.ok(batchResponse);
            });
    }
    
    /**
     * 모델 유효성 검증
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateRequest(@Valid @RequestBody LlmRequest request) {
        String modelName = request.getModel();
        
        if (modelName == null) {
            modelName = llmConfigProperties.getDefaults().getModel();
        }
        
        boolean isValid = llmConfigProperties.getModelByName(modelName).isPresent();
        
        Map<String, Object> validation = Map.of(
            "valid", isValid,
            "modelName", modelName,
            "message", isValid ? "Request is valid" : "Model not found or disabled",
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(validation);
    }
    
    // ===== 에러 핸들링 =====
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unexpected error in LLM API", e);
        
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