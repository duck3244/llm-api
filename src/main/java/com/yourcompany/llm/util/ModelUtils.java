// ModelUtils.java
package com.yourcompany.llm.util;

import com.yourcompany.llm.config.LlmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModelUtils {
    
    // 모델별 토큰 계산 정규식 패턴
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    // 모델별 토큰 비율 (대략적인 추정치)
    private static final Map<String, Double> TOKEN_RATIO_MAP = Map.of(
        "gpt-4", 0.75,           // 1 토큰 ≈ 0.75 단어
        "gpt-3.5-turbo", 0.75,
        "claude-3", 0.8,
        "llama3", 0.7,
        "mistral", 0.7,
        "gemma", 0.8
    );
    
    // 모델별 특수 토큰
    private static final Map<String, Integer> SPECIAL_TOKENS_MAP = Map.of(
        "gpt-4", 5,              // 시스템 메시지 등에 사용되는 특수 토큰 수
        "gpt-3.5-turbo", 5,
        "claude-3", 3,
        "llama3", 4,
        "mistral", 4,
        "gemma", 3
    );
    
    /**
     * 모델명 정규화
     */
    public static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return null;
        }
        
        String normalized = modelName.toLowerCase().trim();
        
        // 공통 별칭 처리
        Map<String, String> aliases = Map.of(
            "gpt4", "gpt-4",
            "gpt35", "gpt-3.5-turbo",
            "gpt-35-turbo", "gpt-3.5-turbo",
            "claude3", "claude-3",
            "llama-3", "llama3",
            "llama-3-8b", "llama3",
            "mistral-7b", "mistral"
        );
        
        return aliases.getOrDefault(normalized, normalized);
    }
    
    /**
     * 텍스트의 토큰 수 추정
     */
    public static int estimateTokenCount(String text, String modelName) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        String normalizedModel = normalizeModelName(modelName);
        double tokenRatio = TOKEN_RATIO_MAP.getOrDefault(normalizedModel, 0.75);
        
        // 단어 수 기반 계산
        int wordCount = WORD_PATTERN.matcher(text).results().mapToInt(m -> 1).sum();
        
        // 특수 문자와 공백 고려
        int specialCharCount = text.length() - text.replaceAll("[^a-zA-Z0-9\\s]", "").length();
        
        // 토큰 수 추정
        int estimatedTokens = (int) Math.ceil(wordCount / tokenRatio) + (specialCharCount / 4);
        
        log.debug("Text token estimation - Model: {}, Words: {}, Special chars: {}, Estimated tokens: {}", 
            normalizedModel, wordCount, specialCharCount, estimatedTokens);
        
        return Math.max(1, estimatedTokens);
    }
    
    /**
     * 요청의 총 토큰 수 추정
     */
    public static int estimateRequestTokens(LlmRequest request, String modelName) {
        int totalTokens = 0;
        String normalizedModel = normalizeModelName(modelName);
        
        // 메시지 토큰 계산
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (LlmRequest.Message message : request.getMessages()) {
                totalTokens += estimateTokenCount(message.getContent(), modelName);
                totalTokens += 3; // role, content 등의 구조적 토큰
            }
        } else if (request.getMessage() != null) {
            totalTokens += estimateTokenCount(request.getMessage(), modelName);
        }
        
        // 시스템 프롬프트 토큰
        if (request.getSystemPrompt() != null) {
            totalTokens += estimateTokenCount(request.getSystemPrompt(), modelName);
            totalTokens += 4; // 시스템 메시지 구조 토큰
        }
        
        // 모델별 특수 토큰 추가
        totalTokens += SPECIAL_TOKENS_MAP.getOrDefault(normalizedModel, 4);
        
        log.debug("Request token estimation - Model: {}, Total tokens: {}", normalizedModel, totalTokens);
        
        return totalTokens;
    }
    
    /**
     * 모델별 최대 컨텍스트 길이 반환
     */
    public static int getMaxContextLength(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        
        Map<String, Integer> contextLengths = Map.of(
            "gpt-4", 8192,
            "gpt-4-32k", 32768,
            "gpt-3.5-turbo", 4096,
            "gpt-3.5-turbo-16k", 16384,
            "claude-3", 100000,
            "claude-3-haiku", 200000,
            "llama3", 8192,
            "llama3-70b", 8192,
            "mistral", 8192,
            "mistral-7b", 8192,
            "gemma", 8192,
            "gemma-7b", 8192
        );
        
        return contextLengths.getOrDefault(normalizedModel, 4096);
    }
    
    /**
     * 요청이 모델의 컨텍스트 길이를 초과하는지 확인
     */
    public static boolean exceedsContextLength(LlmRequest request, String modelName) {
        int requestTokens = estimateRequestTokens(request, modelName);
        int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 1000;
        int maxContext = getMaxContextLength(modelName);
        
        boolean exceeds = (requestTokens + maxTokens) > maxContext;
        
        if (exceeds) {
            log.warn("Request exceeds context length - Model: {}, Request tokens: {}, Max tokens: {}, Context limit: {}", 
                modelName, requestTokens, maxTokens, maxContext);
        }
        
        return exceeds;
    }
    
    /**
     * 모델별 지원 기능 확인
     */
    public static Set<String> getSupportedFeatures(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        
        Map<String, Set<String>> featureMap = Map.of(
            "gpt-4", Set.of("chat", "completion", "streaming", "function_calling", "system_prompt"),
            "gpt-3.5-turbo", Set.of("chat", "completion", "streaming", "function_calling", "system_prompt"),
            "claude-3", Set.of("chat", "completion", "streaming", "system_prompt", "long_context"),
            "llama3", Set.of("chat", "completion", "system_prompt", "open_source"),
            "mistral", Set.of("chat", "completion", "system_prompt", "open_source"),
            "gemma", Set.of("chat", "completion", "system_prompt", "open_source")
        );
        
        return featureMap.getOrDefault(normalizedModel, Set.of("chat", "completion"));
    }
    
    /**
     * 모델이 특정 기능을 지원하는지 확인
     */
    public static boolean supportsFeature(String modelName, String feature) {
        Set<String> supportedFeatures = getSupportedFeatures(modelName);
        return supportedFeatures.contains(feature.toLowerCase());
    }
    
    /**
     * 모델별 추천 파라미터 반환
     */
    public static Map<String, Object> getRecommendedParameters(String modelName, String taskType) {
        String normalizedModel = normalizeModelName(modelName);
        Map<String, Object> params = new HashMap<>();
        
        // 기본 파라미터
        params.put("temperature", 0.7);
        params.put("max_tokens", 1000);
        params.put("top_p", 1.0);
        params.put("frequency_penalty", 0.0);
        params.put("presence_penalty", 0.0);
        
        // 태스크별 최적화
        switch (taskType.toLowerCase()) {
            case "coding", "code" -> {
                params.put("temperature", 0.1);
                params.put("max_tokens", 2000);
            }
            case "creative", "writing" -> {
                params.put("temperature", 0.9);
                params.put("max_tokens", 2000);
                params.put("presence_penalty", 0.6);
            }
            case "analysis", "reasoning" -> {
                params.put("temperature", 0.3);
                params.put("max_tokens", 1500);
            }
            case "translation" -> {
                params.put("temperature", 0.2);
                params.put("max_tokens", 1000);
            }
            case "summarization" -> {
                params.put("temperature", 0.3);
                params.put("max_tokens", 500);
            }
        }
        
        // 모델별 조정
        if (normalizedModel.contains("claude")) {
            // Claude는 일반적으로 더 높은 max_tokens 허용
            params.put("max_tokens", (Integer) params.get("max_tokens") * 2);
        } else if (normalizedModel.contains("llama") || normalizedModel.contains("mistral")) {
            // 오픈소스 모델은 보수적인 설정
            params.put("temperature", Math.min(0.8, (Double) params.get("temperature")));
        }
        
        log.debug("Recommended parameters for model {} and task {}: {}", 
            normalizedModel, taskType, params);
        
        return params;
    }
    
    /**
     * 모델 성능 등급 반환 (1-5, 5가 최고)
     */
    public static int getModelPerformanceRating(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        
        Map<String, Integer> ratings = Map.of(
            "gpt-4", 5,
            "gpt-3.5-turbo", 4,
            "claude-3", 5,
            "claude-3-haiku", 3,
            "llama3-70b", 4,
            "llama3", 3,
            "mistral", 3,
            "gemma", 2
        );
        
        return ratings.getOrDefault(normalizedModel, 2);
    }
    
    /**
     * 모델별 예상 응답 시간 (초)
     */
    public static double getExpectedResponseTime(String modelName, int tokenCount) {
        String normalizedModel = normalizeModelName(modelName);
        
        // 모델별 기본 처리 속도 (tokens/second)
        Map<String, Double> speedMap = Map.of(
            "gpt-4", 20.0,
            "gpt-3.5-turbo", 50.0,
            "claude-3", 30.0,
            "llama3", 100.0,        // vLLM으로 서빙 시
            "mistral", 120.0,       // vLLM으로 서빙 시
            "gemma", 80.0           // vLLM으로 서빙 시
        );
        
        double tokensPerSecond = speedMap.getOrDefault(normalizedModel, 30.0);
        double responseTime = tokenCount / tokensPerSecond;
        
        // 최소 응답 시간 (네트워크 지연 등)
        responseTime = Math.max(responseTime, 0.5);
        
        log.debug("Expected response time for model {} with {} tokens: {:.2f}s", 
            normalizedModel, tokenCount, responseTime);
        
        return responseTime;
    }
    
    /**
     * 태스크에 최적한 모델 추천
     */
    public static List<String> recommendModelsForTask(String taskType, List<LlmConfigProperties.ModelConfig> availableModels) {
        Map<String, List<String>> taskModelMap = Map.of(
            "coding", List.of("gpt-4", "claude-3", "llama3", "mistral"),
            "creative", List.of("gpt-4", "claude-3", "llama3"),
            "analysis", List.of("gpt-4", "claude-3", "gpt-3.5-turbo"),
            "translation", List.of("gpt-4", "gpt-3.5-turbo", "claude-3"),
            "summarization", List.of("gpt-3.5-turbo", "claude-3", "llama3"),
            "conversation", List.of("gpt-3.5-turbo", "llama3", "mistral", "gemma")
        );
        
        List<String> recommendedModels = taskModelMap.getOrDefault(taskType.toLowerCase(), 
            List.of("gpt-3.5-turbo", "llama3"));
        
        // 사용 가능한 모델만 필터링
        Set<String> availableModelNames = availableModels.stream()
            .filter(LlmConfigProperties.ModelConfig::getEnabled)
            .map(model -> normalizeModelName(model.getName()))
            .collect(Collectors.toSet());
        
        List<String> filtered = recommendedModels.stream()
            .filter(availableModelNames::contains)
            .collect(Collectors.toList());
        
        log.debug("Recommended models for task '{}': {}", taskType, filtered);
        
        return filtered;
    }
    
    /**
     * 모델 이름에서 프로바이더 추출
     */
    public static String extractProvider(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        
        if (normalizedModel.startsWith("gpt")) {
            return "openai";
        } else if (normalizedModel.startsWith("claude")) {
            return "anthropic";
        } else if (normalizedModel.startsWith("llama")) {
            return "ollama"; // 또는 "vllm"
        } else if (normalizedModel.startsWith("mistral")) {
            return "ollama"; // 또는 "vllm"
        } else if (normalizedModel.startsWith("gemma")) {
            return "ollama"; // 또는 "vllm"
        } else if (normalizedModel.contains("gemini")) {
            return "google";
        }
        
        return "unknown";
    }
    
    /**
     * 텍스트 트런케이션 (토큰 제한 내에서)
     */
    public static String truncateToTokenLimit(String text, String modelName, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        int currentTokens = estimateTokenCount(text, modelName);
        if (currentTokens <= maxTokens) {
            return text;
        }
        
        // 대략적인 비율로 텍스트 자르기
        double ratio = (double) maxTokens / currentTokens;
        int targetLength = (int) (text.length() * ratio * 0.9); // 여유분 10%
        
        if (targetLength >= text.length()) {
            return text;
        }
        
        // 단어 경계에서 자르기
        String truncated = text.substring(0, targetLength);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > targetLength * 0.8) {
            truncated = truncated.substring(0, lastSpace);
        }
        
        log.debug("Text truncated from {} to {} characters for token limit", 
            text.length(), truncated.length());
        
        return truncated + "...";
    }
    
    /**
     * 모델별 비용 추정 (USD)
     */
    public static double estimateCost(String modelName, int inputTokens, int outputTokens) {
        String normalizedModel = normalizeModelName(modelName);
        
        // 1000 토큰당 비용 (USD)
        Map<String, Double[]> costMap = Map.of(
            "gpt-4", new Double[]{0.03, 0.06},                    // input, output
            "gpt-3.5-turbo", new Double[]{0.0015, 0.002},
            "claude-3", new Double[]{0.015, 0.075},
            "claude-3-haiku", new Double[]{0.00025, 0.00125}
            // 오픈소스 모델은 일반적으로 무료 (인프라 비용만)
        );
        
        Double[] costs = costMap.get(normalizedModel);
        if (costs == null) {
            return 0.0; // 오픈소스 모델은 직접 비용 없음
        }
        
        double inputCost = (inputTokens / 1000.0) * costs[0];
        double outputCost = (outputTokens / 1000.0) * costs[1];
        
        return inputCost + outputCost;
    }
}