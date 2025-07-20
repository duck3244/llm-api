// LlmServiceImpl.java
package com.yourcompany.llm.service.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import com.yourcompany.llm.service.LlmService;
import com.yourcompany.llm.service.vllm.VllmApiClient;
import com.yourcompany.llm.service.vllm.VllmLoadBalancer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j @Service @RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final VllmConfigProperties vllmConfig;
    private final VllmApiClient vllmApiClient;
    private final VllmLoadBalancer loadBalancer;
    private final Executor llmTaskExecutor;

    @Override @Retryable(value = { Exception.class }, maxAttempts = 3)
    public CompletableFuture<LlmResponse> generateText(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 요청 유효성 검증
                ValidationResult validation = validateRequest(request);
                if (!validation.isValid()) {
                    return LlmResponse.error("llama3.2", validation.getMessage());
                }

                // 최적의 vLLM 서버 선택
                return loadBalancer.selectServer("llama3.2", VllmLoadBalancer.LoadBalancingStrategy.HEALTH_BASED)
                        .map(serverName -> {
                            try {
                                LlmResponse response = vllmApiClient.chatCompletion(serverName, request).join();
                                loadBalancer.completeRequest(serverName);
                                return response;
                            } catch (Exception e) {
                                log.error("Failed to generate text with server: {}", serverName, e);
                                return LlmResponse.error("llama3.2", "Text generation failed: " + e.getMessage());
                            }
                        }).orElse(LlmResponse.error("llama3.2", "No available vLLM servers"));

            } catch (Exception e) {
                log.error("Error in generateText", e);
                return LlmResponse.error("llama3.2", "Service error: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }

    @Override
    public CompletableFuture<LlmResponse> chatCompletion(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 채팅 메시지 형태로 변환 (새로운 객체 생성)
                LlmRequest processedRequest = request;
                if (request.getMessages() == null && request.getMessage() != null) {
                    processedRequest = convertToChat(request);
                }

                return generateText(processedRequest).join();

            } catch (Exception e) {
                log.error("Error in chatCompletion", e);
                return LlmResponse.error("llama3.2", "Chat completion failed: " + e.getMessage());
            }
        }, llmTaskExecutor);
    }

    @Override
    public CompletableFuture<String> checkVllmHealth() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var enabledServers = vllmConfig.getEnabledServers();
                if (enabledServers.isEmpty()) {
                    return "DOWN - No servers configured";
                }

                long healthyCount = enabledServers.stream().mapToLong(server -> {
                    try {
                        // 간단한 포트 체크
                        java.net.Socket socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress(server.getHost(), server.getPort()), 2000);
                        socket.close();
                        return 1;
                    } catch (Exception e) {
                        return 0;
                    }
                }).sum();

                if (healthyCount == 0) {
                    return "DOWN";
                } else if (healthyCount < enabledServers.size()) {
                    return "DEGRADED";
                } else {
                    return "UP";
                }

            } catch (Exception e) {
                log.error("Health check failed", e);
                return "ERROR";
            }
        }, llmTaskExecutor);
    }

    @Override
    public ValidationResult validateRequest(LlmRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request cannot be null");
        }

        // 메시지 유효성 검증
        if ((request.getMessage() == null || request.getMessage().trim().isEmpty())
                && (request.getMessages() == null || request.getMessages().isEmpty())) {
            return ValidationResult.invalid("Either message or messages must be provided");
        }

        // 토큰 제한 확인 (Llama 3.2 기본 8K 컨텍스트)
        int estimatedTokens = estimateTokens(request);
        if (estimatedTokens > 8192) {
            return ValidationResult.invalid("Request exceeds Llama 3.2 context limit (8192 tokens)");
        }

        // 파라미터 범위 검증
        if (request.getTemperature() != null && (request.getTemperature() < 0.0 || request.getTemperature() > 2.0)) {
            return ValidationResult.invalid("Temperature must be between 0.0 and 2.0");
        }

        if (request.getMaxTokens() != null && request.getMaxTokens() <= 0) {
            return ValidationResult.invalid("Max tokens must be positive");
        }

        return ValidationResult.valid();
    }

    private LlmRequest convertToChat(LlmRequest originalRequest) {
        // 새로운 LlmRequest 객체 생성 (원본 수정 방지)
        LlmRequest chatRequest = LlmRequest.builder().model("llama3.2").temperature(originalRequest.getTemperature())
                .maxTokens(originalRequest.getMaxTokens()).requestId(originalRequest.getRequestId())
                .user(originalRequest.getUser()).build();

        // 단일 메시지를 채팅 형태로 변환
        LlmRequest.Message userMessage = LlmRequest.Message.builder().role("user").content(originalRequest.getMessage())
                .build();

        // 시스템 프롬프트가 있는 경우 추가
        if (originalRequest.getSystemPrompt() != null) {
            LlmRequest.Message systemMessage = LlmRequest.Message.builder().role("system")
                    .content(originalRequest.getSystemPrompt()).build();

            chatRequest.setMessages(java.util.List.of(systemMessage, userMessage));
        } else {
            chatRequest.setMessages(java.util.List.of(userMessage));
        }

        return chatRequest;
    }

    private int estimateTokens(LlmRequest request) {
        int totalTokens = 0;

        // 단일 메시지 토큰 추정
        if (request.getMessage() != null) {
            totalTokens += request.getMessage().length() / 4; // 대략 4글자당 1토큰
        }

        // 메시지 리스트 토큰 추정
        if (request.getMessages() != null) {
            for (LlmRequest.Message message : request.getMessages()) {
                if (message.getContent() != null) {
                    totalTokens += message.getContent().length() / 4;
                    totalTokens += 4; // 메시지 구조 토큰
                }
            }
        }

        // 시스템 프롬프트 토큰
        if (request.getSystemPrompt() != null) {
            totalTokens += request.getSystemPrompt().length() / 4;
            totalTokens += 4; // 시스템 메시지 구조 토큰
        }

        return Math.max(1, totalTokens);
    }
}