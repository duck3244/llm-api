// LlmService.java
package com.yourcompany.llm.service;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Simplified LLM Service Interface for vLLM + Llama 3.2
 */
public interface LlmService {
    
    /**
     * Llama 3.2 텍스트 생성
     */
    CompletableFuture<LlmResponse> generateText(LlmRequest request);
    
    /**
     * Llama 3.2 채팅 완성
     */
    CompletableFuture<LlmResponse> chatCompletion(LlmRequest request);
    
    /**
     * vLLM 헬스 체크
     */
    CompletableFuture<String> checkVllmHealth();
    
    /**
     * 요청 유효성 검증
     */
    ValidationResult validateRequest(LlmRequest request);
    
    /**
     * 유효성 검증 결과
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, "Valid request");
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}