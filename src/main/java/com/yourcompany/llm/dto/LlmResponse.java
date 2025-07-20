// LlmResponse.java
package com.yourcompany.llm.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {
    
    private String id;
    private String model;
    private String content;
    private Integer tokensUsed;
    private String provider;
    private boolean success;
    private String error;
    private String finishReason;
    private Long responseTimeMs;
    private LocalDateTime timestamp;
    private String requestId;
    private boolean cached;
    private boolean streaming;
    private Usage usage;
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Double estimatedCost;
    }
    
    public static LlmResponse success(String model, String content, Integer tokensUsed, String provider) {
        return LlmResponse.builder()
            .model(model)
            .content(content)
            .tokensUsed(tokensUsed)
            .provider(provider)
            .success(true)
            .timestamp(LocalDateTime.now())
            .finishReason("stop")
            .build();
    }
    
    public static LlmResponse error(String model, String error) {
        return LlmResponse.builder()
            .model(model)
            .error(error)
            .success(false)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public LlmResponse withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    public boolean isCached() {
        return cached;
    }
    
    public boolean isSuccess() {
        return success;
    }
}