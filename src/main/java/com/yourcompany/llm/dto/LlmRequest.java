// LlmRequest.java
package com.yourcompany.llm.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {
    
    /**
     * 사용할 모델명 (없으면 기본 모델 사용)
     */
    private String model;
    
    /**
     * 생성할 텍스트의 프롬프트/메시지
     */
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 10000, message = "Message cannot exceed 10000 characters")
    private String message;
    
    /**
     * 채팅 메시지 리스트 (OpenAI 호환)
     */
    private List<ChatMessage> messages;
    
    /**
     * 시스템 프롬프트
     */
    @Size(max = 2000, message = "System prompt cannot exceed 2000 characters")
    private String systemPrompt;
    
    /**
     * 최대 생성 토큰 수
     */
    @Min(value = 1, message = "Max tokens must be at least 1")
    @Max(value = 8192, message = "Max tokens cannot exceed 8192")
    private Integer maxTokens;
    
    /**
     * 생성 온도 (0.0 ~ 2.0)
     */
    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Temperature cannot exceed 2.0")
    private Double temperature;
    
    /**
     * Top-p 샘플링 값 (0.0 ~ 1.0)
     */
    @DecimalMin(value = "0.0", message = "Top-p must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Top-p cannot exceed 1.0")
    private Double topP;
    
    /**
     * 생성할 응답 개수
     */
    @Min(value = 1, message = "Number of responses must be at least 1")
    @Max(value = 5, message = "Number of responses cannot exceed 5")
    private Integer n = 1;
    
    /**
     * 스트리밍 여부
     */
    private Boolean stream = false;
    
    /**
     * 중지 시퀀스 리스트
     */
    private List<String> stop;
    
    /**
     * 존재 페널티 (-2.0 ~ 2.0)
     */
    @DecimalMin(value = "-2.0", message = "Presence penalty must be at least -2.0")
    @DecimalMax(value = "2.0", message = "Presence penalty cannot exceed 2.0")
    private Double presencePenalty = 0.0;
    
    /**
     * 빈도 페널티 (-2.0 ~ 2.0)
     */
    @DecimalMin(value = "-2.0", message = "Frequency penalty must be at least -2.0")
    @DecimalMax(value = "2.0", message = "Frequency penalty cannot exceed 2.0")
    private Double frequencyPenalty = 0.0;
    
    /**
     * 로그잇 바이어스
     */
    private Map<String, Double> logitBias;
    
    /**
     * 사용자 ID (추적용)
     */
    private String user;
    
    /**
     * 요청 ID (추적용)
     */
    private String requestId;
    
    /**
     * 메타데이터
     */
    private Map<String, Object> metadata;
    
    /**
     * 응답 포맷 지정
     */
    private ResponseFormat responseFormat;
    
    /**
     * 함수 호출 관련 설정
     */
    private List<Function> functions;
    
    /**
     * 함수 호출 모드
     */
    private String functionCall; // "none", "auto", {"name": "function_name"}
    
    /**
     * 채팅 메시지 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatMessage {
        
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "system|user|assistant|function", 
                message = "Role must be one of: system, user, assistant, function")
        private String role;
        
        @NotBlank(message = "Content is required")
        private String content;
        
        /**
         * 함수 호출 관련 필드들
         */
        private String name; // function role일 때 함수명
        
        private FunctionCall functionCall; // assistant가 함수를 호출할 때
        
        /**
         * 이미지 등 멀티모달 콘텐츠
         */
        private List<ContentPart> contentParts;
    }
    
    /**
     * 함수 호출 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FunctionCall {
        private String name;
        private String arguments; // JSON 문자열
    }
    
    /**
     * 멀티모달 콘텐츠 파트
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentPart {
        private String type; // "text", "image_url"
        private String text;
        private ImageUrl imageUrl;
    }
    
    /**
     * 이미지 URL 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImageUrl {
        private String url;
        private String detail; // "low", "high", "auto"
    }
    
    /**
     * 응답 포맷 지정
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseFormat {
        @Pattern(regexp = "text|json_object", 
                message = "Response format type must be 'text' or 'json_object'")
        private String type = "text";
    }
    
    /**
     * 함수 정의
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Function {
        @NotBlank(message = "Function name is required")
        private String name;
        
        private String description;
        
        /**
         * JSON Schema 형태의 파라미터 정의
         */
        private Map<String, Object> parameters;
    }
    
    // 유틸리티 메서드들
    
    /**
     * 단순 텍스트 요청 생성
     */
    public static LlmRequest simpleText(String message) {
        return LlmRequest.builder()
            .message(message)
            .build();
    }
    
    /**
     * 모델 지정 텍스트 요청 생성
     */
    public static LlmRequest withModel(String model, String message) {
        return LlmRequest.builder()
            .model(model)
            .message(message)
            .build();
    }
    
    /**
     * 채팅 요청 생성
     */
    public static LlmRequest chat(List<ChatMessage> messages) {
        return LlmRequest.builder()
            .messages(messages)
            .build();
    }
    
    /**
     * 시스템 프롬프트와 함께 요청 생성
     */
    public static LlmRequest withSystemPrompt(String systemPrompt, String userMessage) {
        return LlmRequest.builder()
            .systemPrompt(systemPrompt)
            .message(userMessage)
            .build();
    }
    
    /**
     * 사용자 메시지 생성 헬퍼
     */
    public static ChatMessage userMessage(String content) {
        return ChatMessage.builder()
            .role("user")
            .content(content)
            .build();
    }
    
    /**
     * 시스템 메시지 생성 헬퍼
     */
    public static ChatMessage systemMessage(String content) {
        return ChatMessage.builder()
            .role("system")
            .content(content)
            .build();
    }
    
    /**
     * 어시스턴트 메시지 생성 헬퍼
     */
    public static ChatMessage assistantMessage(String content) {
        return ChatMessage.builder()
            .role("assistant")
            .content(content)
            .build();
    }
    
    /**
     * 요청 유효성 검증
     */
    public boolean isValid() {
        // 메시지나 채팅 메시지 중 하나는 있어야 함
        boolean hasContent = (message != null && !message.trim().isEmpty()) ||
                           (messages != null && !messages.isEmpty());
        
        if (!hasContent) {
            return false;
        }
        
        // 채팅 메시지가 있는 경우 유효성 검증
        if (messages != null) {
            return messages.stream().allMatch(msg -> 
                msg.getRole() != null && 
                msg.getContent() != null && 
                !msg.getContent().trim().isEmpty()
            );
        }
        
        return true;
    }
    
    /**
     * 토큰 수 추정 (대략적)
     */
    public int estimateTokens() {
        int tokens = 0;
        
        if (message != null) {
            tokens += message.length() / 4; // 대략 4글자당 1토큰
        }
        
        if (messages != null) {
            tokens += messages.stream()
                .mapToInt(msg -> msg.getContent().length() / 4)
                .sum();
        }
        
        if (systemPrompt != null) {
            tokens += systemPrompt.length() / 4;
        }
        
        return tokens;
    }
    
    /**
     * 요청을 채팅 메시지 형태로 변환
     */
    public List<ChatMessage> toChatMessages() {
        List<ChatMessage> chatMessages = new java.util.ArrayList<>();
        
        // 시스템 프롬프트 추가
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            chatMessages.add(systemMessage(systemPrompt));
        }
        
        // 기존 메시지들 추가
        if (messages != null) {
            chatMessages.addAll(messages);
        }
        
        // 단순 메시지를 사용자 메시지로 추가
        if (message != null && !message.trim().isEmpty()) {
            chatMessages.add(userMessage(message));
        }
        
        return chatMessages;
    }
    
    /**
     * 요청 복사 (설정 변경용)
     */
    public LlmRequest copy() {
        return LlmRequest.builder()
            .model(this.model)
            .message(this.message)
            .messages(this.messages != null ? new java.util.ArrayList<>(this.messages) : null)
            .systemPrompt(this.systemPrompt)
            .maxTokens(this.maxTokens)
            .temperature(this.temperature)
            .topP(this.topP)
            .n(this.n)
            .stream(this.stream)
            .stop(this.stop != null ? new java.util.ArrayList<>(this.stop) : null)
            .presencePenalty(this.presencePenalty)
            .frequencyPenalty(this.frequencyPenalty)
            .logitBias(this.logitBias != null ? new java.util.HashMap<>(this.logitBias) : null)
            .user(this.user)
            .requestId(this.requestId)
            .metadata(this.metadata != null ? new java.util.HashMap<>(this.metadata) : null)
            .responseFormat(this.responseFormat)
            .functions(this.functions != null ? new java.util.ArrayList<>(this.functions) : null)
            .functionCall(this.functionCall)
            .build();
    }
}