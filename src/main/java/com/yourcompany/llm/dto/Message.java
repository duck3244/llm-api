// Message.java
package com.yourcompany.llm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_conversation_id", columnList = "conversationId"),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_model", columnList = "model"),
    @Index(name = "idx_provider", columnList = "provider")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 메시지 고유 ID (UUID)
     */
    @Column(unique = true, nullable = false, length = 36)
    private String messageId;
    
    /**
     * 대화 ID (여러 메시지를 하나의 대화로 그룹핑)
     */
    @Column(length = 36)
    private String conversationId;
    
    /**
     * 사용자 ID
     */
    @Column(length = 100)
    private String userId;
    
    /**
     * API 키 (해시된 값)
     */
    @Column(length = 64)
    private String apiKeyHash;
    
    /**
     * 메시지 역할 (system, user, assistant, function)
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MessageRole role;
    
    /**
     * 메시지 내용
     */
    @Column(columnDefinition = "TEXT")
    private String content;
    
    /**
     * 원본 프롬프트 (사용자 입력)
     */
    @Column(columnDefinition = "TEXT")
    private String originalPrompt;
    
    /**
     * 시스템 프롬프트
     */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;
    
    /**
     * 사용된 모델명
     */
    @Column(length = 100)
    private String model;
    
    /**
     * 모델 프로바이더
     */
    @Column(length = 50)
    private String provider;
    
    /**
     * 메시지 타입 (text, function_call, function_result)
     */
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private MessageType messageType;
    
    /**
     * 함수 호출 정보 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String functionCall;
    
    /**
     * 요청 파라미터 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String requestParameters;
    
    /**
     * 응답 메타데이터 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String responseMetadata;
    
    /**
     * 입력 토큰 수
     */
    private Integer inputTokens;
    
    /**
     * 출력 토큰 수
     */
    private Integer outputTokens;
    
    /**
     * 총 토큰 수
     */
    private Integer totalTokens;
    
    /**
     * 응답 시간 (밀리초)
     */
    private Long responseTimeMs;
    
    /**
     * 예상 비용
     */
    @Column(precision = 10, scale = 6)
    private Double estimatedCost;
    
    /**
     * 통화
     */
    @Column(length = 3)
    private String currency;
    
    /**
     * 완료 이유 (stop, length, function_call, content_filter)
     */
    @Column(length = 20)
    private String finishReason;
    
    /**
     * 성공 여부
     */
    @Column(nullable = false)
    private Boolean success;
    
    /**
     * 오류 메시지
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 오류 코드
     */
    @Column(length = 50)
    private String errorCode;
    
    /**
     * 캐시에서 가져온 응답인지 여부
     */
    @Column(nullable = false)
    private Boolean fromCache;
    
    /**
     * 스트리밍 여부
     */
    @Column(nullable = false)
    private Boolean streaming;
    
    /**
     * 온도 설정
     */
    @Column(precision = 3, scale = 2)
    private Double temperature;
    
    /**
     * 최대 토큰 설정
     */
    private Integer maxTokens;
    
    /**
     * Top-p 설정
     */
    @Column(precision = 3, 2)
    private Double topP;
    
    /**
     * 존재 페널티
     */
    @Column(precision = 3, scale = 2)
    private Double presencePenalty;
    
    /**
     * 빈도 페널티
     */
    @Column(precision = 3, scale = 2)
    private Double frequencyPenalty;
    
    /**
     * 클라이언트 IP 주소
     */
    @Column(length = 45)
    private String clientIp;
    
    /**
     * 사용자 에이전트
     */
    @Column(length = 500)
    private String userAgent;
    
    /**
     * 지역 정보
     */
    @Column(length = 10)
    private String region;
    
    /**
     * 국가 코드
     */
    @Column(length = 2)
    private String countryCode;
    
    /**
     * 품질 점수 (0.0 ~ 1.0)
     */
    @Column(precision = 3, scale = 2)
    private Double qualityScore;
    
    /**
     * 만족도 점수 (사용자 피드백)
     */
    private Integer satisfactionScore;
    
    /**
     * 사용자 피드백
     */
    @Column(columnDefinition = "TEXT")
    private String userFeedback;
    
    /**
     * 태그 (콤마로 구분)
     */
    @Column(length = 500)
    private String tags;
    
    /**
     * 커스텀 메타데이터 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String customMetadata;
    
    /**
     * 생성 시간
     */
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 수정 시간
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    /**
     * 삭제 시간 (소프트 삭제)
     */
    private LocalDateTime deletedAt;
    
    /**
     * 메시지 역할 열거형
     */
    public enum MessageRole {
        SYSTEM, USER, ASSISTANT, FUNCTION
    }
    
    /**
     * 메시지 타입 열거형
     */
    public enum MessageType {
        TEXT, FUNCTION_CALL, FUNCTION_RESULT, IMAGE, AUDIO, SYSTEM
    }
    
    // 유틸리티 메서드들
    
    /**
     * 메시지가 성공했는지 확인
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 메시지가 캐시된 응답인지 확인
     */
    public boolean isCached() {
        return Boolean.TRUE.equals(fromCache);
    }
    
    /**
     * 메시지가 스트리밍 응답인지 확인
     */
    public boolean isStreaming() {
        return Boolean.TRUE.equals(streaming);
    }
    
    /**
     * 함수 호출 메시지인지 확인
     */
    public boolean isFunctionCall() {
        return MessageType.FUNCTION_CALL.equals(messageType) || 
               (functionCall != null && !functionCall.trim().isEmpty());
    }
    
    /**
     * 사용자 메시지인지 확인
     */
    public boolean isUserMessage() {
        return MessageRole.USER.equals(role);
    }
    
    /**
     * 어시스턴트 메시지인지 확인
     */
    public boolean isAssistantMessage() {
        return MessageRole.ASSISTANT.equals(role);
    }
    
    /**
     * 시스템 메시지인지 확인
     */
    public boolean isSystemMessage() {
        return MessageRole.SYSTEM.equals(role);
    }
    
    /**
     * 삭제된 메시지인지 확인
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
    
    /**
     * 토큰 효율성 계산 (출력토큰/입력토큰 비율)
     */
    public Double getTokenEfficiency() {
        if (inputTokens == null || inputTokens == 0 || outputTokens == null) {
            return null;
        }
        return (double) outputTokens / inputTokens;
    }
    
    /**
     * 토큰당 비용 계산
     */
    public Double getCostPerToken() {
        if (totalTokens == null || totalTokens == 0 || estimatedCost == null) {
            return null;
        }
        return estimatedCost / totalTokens;
    }
    
    /**
     * 응답 속도 계산 (토큰/초)
     */
    public Double getTokensPerSecond() {
        if (responseTimeMs == null || responseTimeMs == 0 || outputTokens == null) {
            return null;
        }
        return (double) outputTokens / (responseTimeMs / 1000.0);
    }
    
    /**
     * 내용 길이 반환
     */
    public Integer getContentLength() {
        return content != null ? content.length() : 0;
    }
    
    /**
     * 프롬프트 길이 반환
     */
    public Integer getPromptLength() {
        return originalPrompt != null ? originalPrompt.length() : 0;
    }
    
    /**
     * 태그 리스트로 반환
     */
    public java.util.List<String> getTagsList() {
        if (tags == null || tags.trim().isEmpty()) {
            return java.util.List.of();
        }
        return java.util.Arrays.asList(tags.split(","))
            .stream()
            .map(String::trim)
            .filter(tag -> !tag.isEmpty())
            .toList();
    }
    
    /**
     * 태그 설정 (리스트에서)
     */
    public void setTagsList(java.util.List<String> tagsList) {
        if (tagsList == null || tagsList.isEmpty()) {
            this.tags = null;
        } else {
            this.tags = String.join(",", tagsList);
        }
    }
    
    /**
     * 태그 추가
     */
    public void addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        
        java.util.List<String> currentTags = new java.util.ArrayList<>(getTagsList());
        if (!currentTags.contains(tag.trim())) {
            currentTags.add(tag.trim());
            setTagsList(currentTags);
        }
    }
    
    /**
     * 태그 제거
     */
    public void removeTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        
        java.util.List<String> currentTags = new java.util.ArrayList<>(getTagsList());
        currentTags.remove(tag.trim());
        setTagsList(currentTags);
    }
    
    /**
     * 메시지 요약 정보 반환
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", id);
        summary.put("messageId", messageId);
        summary.put("role", role);
        summary.put("model", model);
        summary.put("provider", provider);
        summary.put("success", success);
        summary.put("tokens", totalTokens);
        summary.put("cost", estimatedCost);
        summary.put("responseTime", responseTimeMs);
        summary.put("fromCache", fromCache);
        summary.put("createdAt", createdAt);
        
        return summary;
    }
    
    /**
     * 성능 메트릭 반환
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("responseTimeMs", responseTimeMs);
        metrics.put("tokensPerSecond", getTokensPerSecond());
        metrics.put("tokenEfficiency", getTokenEfficiency());
        metrics.put("costPerToken", getCostPerToken());
        metrics.put("qualityScore", qualityScore);
        metrics.put("satisfactionScore", satisfactionScore);
        
        return metrics;
    }
    
    /**
     * 소프트 삭제
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
    
    /**
     * 삭제 복원
     */
    public void restore() {
        this.deletedAt = null;
    }
    
    /**
     * 메시지 복사 (새 ID로)
     */
    public Message copy() {
        return Message.builder()
            .messageId(java.util.UUID.randomUUID().toString())
            .conversationId(this.conversationId)
            .userId(this.userId)
            .apiKeyHash(this.apiKeyHash)
            .role(this.role)
            .content(this.content)
            .originalPrompt(this.originalPrompt)
            .systemPrompt(this.systemPrompt)
            .model(this.model)
            .provider(this.provider)
            .messageType(this.messageType)
            .functionCall(this.functionCall)
            .requestParameters(this.requestParameters)
            .responseMetadata(this.responseMetadata)
            .inputTokens(this.inputTokens)
            .outputTokens(this.outputTokens)
            .totalTokens(this.totalTokens)
            .responseTimeMs(this.responseTimeMs)
            .estimatedCost(this.estimatedCost)
            .currency(this.currency)
            .finishReason(this.finishReason)
            .success(this.success)
            .errorMessage(this.errorMessage)
            .errorCode(this.errorCode)
            .fromCache(false) // 복사본은 캐시에서 온 것이 아님
            .streaming(this.streaming)
            .temperature(this.temperature)
            .maxTokens(this.maxTokens)
            .topP(this.topP)
            .presencePenalty(this.presencePenalty)
            .frequencyPenalty(this.frequencyPenalty)
            .clientIp(this.clientIp)
            .userAgent(this.userAgent)
            .region(this.region)
            .countryCode(this.countryCode)
            .qualityScore(this.qualityScore)
            .satisfactionScore(this.satisfactionScore)
            .userFeedback(this.userFeedback)
            .tags(this.tags)
            .customMetadata(this.customMetadata)
            .build();
    }
    
    @Override
    public String toString() {
        return String.format("Message{id=%d, messageId='%s', role=%s, model='%s', success=%s, tokens=%d}", 
            id, messageId, role, model, success, totalTokens);
    }
}