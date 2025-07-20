// LlmResponse.java
package com.yourcompany.llm.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {
    
    /**
     * 응답 ID
     */
    private String id;
    
    /**
     * 요청이 성공했는지 여부
     */
    private Boolean success;
    
    /**
     * 사용된 모델명
     */
    private String model;
    
    /**
     * 생성된 텍스트 내용
     */
    private String content;
    
    /**
     * 선택지들 (여러 응답이 생성된 경우)
     */
    private List<Choice> choices;
    
    /**
     * 토큰 사용량 정보
     */
    private Usage usage;
    
    /**
     * 응답 생성 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 응답 시간 (밀리초)
     */
    private Long responseTimeMs;
    
    /**
     * 사용된 토큰 수
     */
    private Integer tokensUsed;
    
    /**
     * 모델 프로바이더
     */
    private String provider;
    
    /**
     * 오류 메시지 (실패 시)
     */
    private String error;
    
    /**
     * 오류 코드 (실패 시)
     */
    private String errorCode;
    
    /**
     * 완료 이유
     */
    private String finishReason; // "stop", "length", "function_call", "content_filter"
    
    /**
     * 메타데이터
     */
    private Map<String, Object> metadata;
    
    /**
     * 캐시에서 가져온 응답인지 여부
     */
    private Boolean fromCache = false;
    
    /**
     * 요청 ID (추적용)
     */
    private String requestId;
    
    /**
     * 함수 호출 정보 (함수 호출이 있는 경우)
     */
    private FunctionCall functionCall;
    
    /**
     * 스트리밍 관련 정보
     */
    private StreamInfo streamInfo;
    
    /**
     * 응답 선택지
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Choice {
        private Integer index;
        private String text;
        private Message message;
        private String finishReason;
        private Double logprobs;
    }
    
    /**
     * 메시지 (채팅 완성 응답용)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private String content;
        private FunctionCall functionCall;
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
        private String arguments;
    }
    
    /**
     * 토큰 사용량
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Double estimatedCost;
        private String currency = "USD";
    }
    
    /**
     * 스트리밍 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StreamInfo {
        private Boolean isStreaming;
        private Integer chunkIndex;
        private Boolean isComplete;
        private String streamId;
    }
    
    // 정적 팩토리 메서드들
    
    /**
     * 성공 응답 생성
     */
    public static LlmResponse success(String model, String content, Integer tokensUsed, String provider) {
        return LlmResponse.builder()
            .success(true)
            .model(model)
            .content(content)
            .tokensUsed(tokensUsed)
            .provider(provider)
            .timestamp(LocalDateTime.now())
            .finishReason("stop")
            .fromCache(false)
            .build();
    }
    
    /**
     * 오류 응답 생성
     */
    public static LlmResponse error(String model, String errorMessage) {
        return LlmResponse.builder()
            .success(false)
            .model(model)
            .error(errorMessage)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 오류 응답 생성 (코드 포함)
     */
    public static LlmResponse error(String model, String errorMessage, String errorCode) {
        return LlmResponse.builder()
            .success(false)
            .model(model)
            .error(errorMessage)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 캐시된 응답 생성
     */
    public static LlmResponse cached(String model, String content, Integer tokensUsed, String provider) {
        return LlmResponse.builder()
            .success(true)
            .model(model)
            .content(content)
            .tokensUsed(tokensUsed)
            .provider(provider)
            .timestamp(LocalDateTime.now())
            .finishReason("stop")
            .fromCache(true)
            .responseTimeMs(0L) // 캐시는 즉시 응답
            .build();
    }
    
    /**
     * 스트리밍 청크 응답 생성
     */
    public static LlmResponse streamChunk(String model, String content, Integer chunkIndex, Boolean isComplete) {
        return LlmResponse.builder()
            .success(true)
            .model(model)
            .content(content)
            .timestamp(LocalDateTime.now())
            .streamInfo(StreamInfo.builder()
                .isStreaming(true)
                .chunkIndex(chunkIndex)
                .isComplete(isComplete)
                .build())
            .build();
    }
    
    /**
     * 함수 호출 응답 생성
     */
    public static LlmResponse functionCall(String model, String functionName, String arguments, String provider) {
        return LlmResponse.builder()
            .success(true)
            .model(model)
            .provider(provider)
            .timestamp(LocalDateTime.now())
            .finishReason("function_call")
            .functionCall(FunctionCall.builder()
                .name(functionName)
                .arguments(arguments)
                .build())
            .build();
    }
    
    /**
     * 채팅 완성 응답 생성
     */
    public static LlmResponse chatCompletion(String model, String content, String role, Integer tokensUsed, String provider) {
        Choice choice = Choice.builder()
            .index(0)
            .message(Message.builder()
                .role(role)
                .content(content)
                .build())
            .finishReason("stop")
            .build();
        
        return LlmResponse.builder()
            .success(true)
            .model(model)
            .content(content)
            .choices(List.of(choice))
            .tokensUsed(tokensUsed)
            .provider(provider)
            .timestamp(LocalDateTime.now())
            .finishReason("stop")
            .build();
    }
    
    // 유틸리티 메서드들
    
    /**
     * 응답이 성공했는지 확인
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 응답이 실패했는지 확인
     */
    public boolean isError() {
        return !isSuccess();
    }
    
    /**
     * 캐시된 응답인지 확인
     */
    public boolean isCached() {
        return Boolean.TRUE.equals(fromCache);
    }
    
    /**
     * 스트리밍 응답인지 확인
     */
    public boolean isStreaming() {
        return streamInfo != null && Boolean.TRUE.equals(streamInfo.getIsStreaming());
    }
    
    /**
     * 함수 호출 응답인지 확인
     */
    public boolean hasFunctionCall() {
        return functionCall != null || 
               (choices != null && choices.stream().anyMatch(c -> 
                   c.getMessage() != null && c.getMessage().getFunctionCall() != null));
    }
    
    /**
     * 완료된 스트림인지 확인
     */
    public boolean isStreamComplete() {
        return streamInfo != null && Boolean.TRUE.equals(streamInfo.getIsComplete());
    }
    
    /**
     * 첫 번째 선택지의 내용 반환
     */
    public String getFirstChoiceContent() {
        if (content != null) {
            return content;
        }
        
        if (choices != null && !choices.isEmpty()) {
            Choice firstChoice = choices.get(0);
            if (firstChoice.getMessage() != null) {
                return firstChoice.getMessage().getContent();
            }
            return firstChoice.getText();
        }
        
        return null;
    }
    
    /**
     * 토큰 사용량 정보 설정
     */
    public LlmResponse withUsage(Integer promptTokens, Integer completionTokens, Double cost) {
        this.usage = Usage.builder()
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .totalTokens(promptTokens + completionTokens)
            .estimatedCost(cost)
            .currency("USD")
            .build();
        
        this.tokensUsed = promptTokens + completionTokens;
        return this;
    }
    
    /**
     * 응답 시간 설정
     */
    public LlmResponse withResponseTime(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
        return this;
    }
    
    /**
     * 메타데이터 추가
     */
    public LlmResponse withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    /**
     * 요청 ID 설정
     */
    public LlmResponse withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    /**
     * OpenAI 호환 형식으로 변환
     */
    public Map<String, Object> toOpenAIFormat() {
        Map<String, Object> response = new java.util.HashMap<>();
        
        response.put("id", id != null ? id : "chatcmpl-" + java.util.UUID.randomUUID().toString());
        response.put("object", choices != null ? "chat.completion" : "text_completion");
        response.put("created", timestamp != null ? 
            timestamp.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() : 
            System.currentTimeMillis() / 1000);
        response.put("model", model);
        
        if (choices != null) {
            response.put("choices", choices.stream().map(choice -> {
                Map<String, Object> choiceMap = new java.util.HashMap<>();
                choiceMap.put("index", choice.getIndex());
                choiceMap.put("message", choice.getMessage());
                choiceMap.put("finish_reason", choice.getFinishReason());
                return choiceMap;
            }).toList());
        } else {
            List<Map<String, Object>> choicesList = List.of(Map.of(
                "index", 0,
                "text", content != null ? content : "",
                "finish_reason", finishReason != null ? finishReason : "stop"
            ));
            response.put("choices", choicesList);
        }
        
        if (usage != null) {
            Map<String, Object> usageMap = new java.util.HashMap<>();
            usageMap.put("prompt_tokens", usage.getPromptTokens());
            usageMap.put("completion_tokens", usage.getCompletionTokens());
            usageMap.put("total_tokens", usage.getTotalTokens());
            response.put("usage", usageMap);
        }
        
        return response;
    }
    
    /**
     * 요약 정보 반환
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("success", success);
        summary.put("model", model);
        summary.put("provider", provider);
        summary.put("tokensUsed", tokensUsed);
        summary.put("responseTimeMs", responseTimeMs);
        summary.put("fromCache", fromCache);
        summary.put("timestamp", timestamp);
        
        if (error != null) {
            summary.put("error", error);
            summary.put("errorCode", errorCode);
        }
        
        return summary;
    }
    
    /**
     * 응답 복사
     */
    public LlmResponse copy() {
        return LlmResponse.builder()
            .id(this.id)
            .success(this.success)
            .model(this.model)
            .content(this.content)
            .choices(this.choices != null ? new java.util.ArrayList<>(this.choices) : null)
            .usage(this.usage)
            .timestamp(this.timestamp)
            .responseTimeMs(this.responseTimeMs)
            .tokensUsed(this.tokensUsed)
            .provider(this.provider)
            .error(this.error)
            .errorCode(this.errorCode)
            .finishReason(this.finishReason)
            .metadata(this.metadata != null ? new java.util.HashMap<>(this.metadata) : null)
            .fromCache(this.fromCache)
            .requestId(this.requestId)
            .functionCall(this.functionCall)
            .streamInfo(this.streamInfo)
            .build();
    }
    
    /**
     * 응답 병합 (스트리밍용)
     */
    public LlmResponse merge(LlmResponse other) {
        if (other == null) {
            return this;
        }
        
        LlmResponse merged = this.copy();
        
        // 내용 병합
        if (other.getContent() != null) {
            String currentContent = merged.getContent() != null ? merged.getContent() : "";
            merged.setContent(currentContent + other.getContent());
        }
        
        // 토큰 사용량 누적
        if (other.getTokensUsed() != null) {
            Integer currentTokens = merged.getTokensUsed() != null ? merged.getTokensUsed() : 0;
            merged.setTokensUsed(currentTokens + other.getTokensUsed());
        }
        
        // 마지막 청크의 정보로 업데이트
        if (other.getFinishReason() != null) {
            merged.setFinishReason(other.getFinishReason());
        }
        
        if (other.getStreamInfo() != null) {
            merged.setStreamInfo(other.getStreamInfo());
        }
        
        return merged;
    }
    
    /**
     * 검증 메서드
     */
    public boolean isValid() {
        if (success == null) {
            return false;
        }
        
        if (isSuccess()) {
            // 성공 응답은 모델과 내용이 있어야 함
            return model != null && 
                   (content != null || (choices != null && !choices.isEmpty()));
        } else {
            // 실패 응답은 에러 메시지가 있어야 함
            return error != null;
        }
    }
    
    /**
     * 디버그 정보 반환
     */
    @Override
    public String toString() {
        if (isSuccess()) {
            return String.format("LlmResponse{success=true, model='%s', provider='%s', tokens=%d, responseTime=%dms, fromCache=%s}", 
                model, provider, tokensUsed, responseTimeMs, fromCache);
        } else {
            return String.format("LlmResponse{success=false, model='%s', error='%s', errorCode='%s'}", 
                model, error, errorCode);
        }
    }
}