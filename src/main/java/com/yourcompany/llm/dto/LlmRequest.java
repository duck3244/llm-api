// LlmRequest.java
package com.yourcompany.llm.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.DecimalMax;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    
    private String model = "llama3.2";
    
    private String message;
    
    private List<Message> messages;
    
    private String systemPrompt;
    
    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Temperature cannot exceed 2.0")
    private Double temperature = 0.7;
    
    @Min(value = 1, message = "Max tokens must be at least 1")
    private Integer maxTokens = 1000;
    
    private String requestId;
    private String user;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role; // "system", "user", "assistant"
        private String content;
    }
    
    public LlmRequest copy() {
        return LlmRequest.builder()
            .model(this.model)
            .message(this.message)
            .messages(this.messages)
            .systemPrompt(this.systemPrompt)
            .temperature(this.temperature)
            .maxTokens(this.maxTokens)
            .requestId(this.requestId)
            .user(this.user)
            .build();
    }
}