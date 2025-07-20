// LlmController.java
package com.yourcompany.llm.controller;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LlmController {
    
    private final LlmService llmService;
    
    /**
     * Llama 3.1 텍스트 생성 API
     */
    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<LlmResponse>> generateText(@Valid @RequestBody LlmRequest request) {
        log.info("Text generation request received - Message length: {}", 
                request.getMessage() != null ? request.getMessage().length() : 0);
        
        return llmService.generateText(request)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    log.debug("Text generation completed successfully - Tokens: {}", response.getTokensUsed());
                } else {
                    log.warn("Text generation failed - Error: {}", response.getError());
                }
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error in text generation", throwable);
                LlmResponse errorResponse = LlmResponse.error(
                    "llama3.1", "Internal server error: " + throwable.getMessage()
                );
                return ResponseEntity.status(500).body(errorResponse);
            });
    }
    
    /**
     * 채팅 완성 API
     */
    @PostMapping("/chat/completions")
    public CompletableFuture<ResponseEntity<LlmResponse>> chatCompletion(@Valid @RequestBody LlmRequest request) {
        log.info("Chat completion request received - Messages: {}", 
                request.getMessages() != null ? request.getMessages().size() : 0);
        
        return llmService.chatCompletion(request)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> {
                log.error("Error in chat completion", throwable);
                LlmResponse errorResponse = LlmResponse.error(
                    "llama3.1", "Chat completion failed: " + throwable.getMessage()
                );
                return ResponseEntity.status(500).body(errorResponse);
            });
    }
    
    /**
     * 모델 정보 조회
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModels() {
        Map<String, Object> response = Map.of(
            "models", Map.of(
                "name", "llama3.2",
                "provider", "vllm",
                "maxTokens", 8192,
                "description", "Meta Llama 3.2 model running on vLLM"
            ),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "vLLM Llama 3.2 API",
            "version", "1.0.0",
            "model", "llama3.2",
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 상세 헬스 체크
     */
    @GetMapping("/health/detailed")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> detailedHealthCheck() {
        return llmService.checkVllmHealth()
            .thenApply(healthStatus -> {
                Map<String, Object> health = Map.of(
                    "status", healthStatus,
                    "service", "vLLM Llama 3.2 API",
                    "vllm_status", healthStatus,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(health);
            });
    }
    
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
}