// VllmApiClient.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VllmApiClient {
    
    private final VllmConfigProperties vllmConfig;
    private final RestTemplate restTemplate;
    
    /**
     * vLLM 서버로 채팅 완성 요청
     */
    public CompletableFuture<LlmResponse> chatCompletion(String serverName, LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return LlmResponse.error(serverName, "Server configuration not found");
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/chat/completions", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                Map<String, Object> requestBody = buildChatCompletionRequest(request, serverConfig);
                HttpHeaders headers = buildHeaders(serverConfig);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, Map.class);
                long endTime = System.currentTimeMillis();
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseResponse(serverName, response.getBody(), endTime - startTime);
                } else {
                    return LlmResponse.error(serverName, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Error calling vLLM API for server: {}", serverName, e);
                return LlmResponse.error(serverName, "API call failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * vLLM 서버로 완성 요청 (legacy)
     */
    public CompletableFuture<LlmResponse> completion(String serverName, LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return LlmResponse.error(serverName, "Server configuration not found");
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/completions", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                Map<String, Object> requestBody = buildCompletionRequest(request, serverConfig);
                HttpHeaders headers = buildHeaders(serverConfig);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, Map.class);
                long endTime = System.currentTimeMillis();
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseCompletionResponse(serverName, response.getBody(), endTime - startTime);
                } else {
                    return LlmResponse.error(serverName, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Error calling vLLM completion API for server: {}", serverName, e);
                return LlmResponse.error(serverName, "API call failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * 스트리밍 채팅 완성 요청
     */
    public CompletableFuture<Void> streamChatCompletion(String serverName, LlmRequest request, 
                                                        StreamingResponseHandler handler) {
        return CompletableFuture.runAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                handler.onError("Server configuration not found");
                return;
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/chat/completions", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                Map<String, Object> requestBody = buildChatCompletionRequest(request, serverConfig);
                requestBody.put("stream", true);
                
                // 스트리밍 구현은 WebClient나 OkHttp 등이 필요
                // 여기서는 기본 구조만 제공
                handler.onError("Streaming not implemented with RestTemplate");
                
            } catch (Exception e) {
                log.error("Error in streaming chat completion for server: {}", serverName, e);
                handler.onError("Streaming failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * 모델 목록 조회
     */
    public CompletableFuture<List<ModelResponse>> getModels(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return List.of();
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/models", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseModelsResponse(response.getBody());
                } else {
                    log.warn("Failed to get models from server: {} - HTTP {}", 
                        serverName, response.getStatusCode());
                    return List.of();
                }
                
            } catch (Exception e) {
                log.error("Error getting models from server: {}", serverName, e);
                return List.of();
            }
        });
    }
    
    /**
     * 임베딩 생성 요청
     */
    public CompletableFuture<EmbeddingResponse> createEmbeddings(String serverName, EmbeddingRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return EmbeddingResponse.error("Server configuration not found");
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/embeddings", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                Map<String, Object> requestBody = buildEmbeddingRequest(request, serverConfig);
                HttpHeaders headers = buildHeaders(serverConfig);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseEmbeddingResponse(response.getBody());
                } else {
                    return EmbeddingResponse.error("HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Error creating embeddings for server: {}", serverName, e);
                return EmbeddingResponse.error("API call failed: " + e.getMessage());
            }
        });
    }
    
    private Map<String, Object> buildChatCompletionRequest(LlmRequest request, 
                                                           VllmConfigProperties.VllmServerConfig serverConfig) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // 모델 설정
        requestBody.put("model", serverConfig.getModel());
        
        // 메시지 설정
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            requestBody.put("messages", request.getMessages());
        } else {
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", request.getMessage())
            ));
        }
        
        // 생성 파라미터
        requestBody.put("max_tokens", 
            request.getMaxTokens() != null ? request.getMaxTokens() : 
            (serverConfig.getModelSettings() != null ? serverConfig.getModelSettings().getMaxModelLen() : 4096));
        
        requestBody.put("temperature", 
            request.getTemperature() != null ? request.getTemperature() : 0.7);
        
        requestBody.put("stream", false);
        
        // 추가 파라미터
        if (request.getSystemPrompt() != null) {
            // 시스템 메시지를 messages 앞에 추가
            List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
            messages.add(0, Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        
        return requestBody;
    }
    
    private Map<String, Object> buildCompletionRequest(LlmRequest request, 
                                                       VllmConfigProperties.VllmServerConfig serverConfig) {
        Map<String, Object> requestBody = new HashMap<>();
        
        requestBody.put("model", serverConfig.getModel());
        requestBody.put("prompt", request.getMessage());
        requestBody.put("max_tokens", 
            request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        requestBody.put("temperature", 
            request.getTemperature() != null ? request.getTemperature() : 0.7);
        requestBody.put("stream", false);
        
        return requestBody;
    }
    
    private Map<String, Object> buildEmbeddingRequest(EmbeddingRequest request, 
                                                      VllmConfigProperties.VllmServerConfig serverConfig) {
        Map<String, Object> requestBody = new HashMap<>();
        
        requestBody.put("model", serverConfig.getModel());
        requestBody.put("input", request.getInput());
        
        if (request.getEncodingFormat() != null) {
            requestBody.put("encoding_format", request.getEncodingFormat());
        }
        
        return requestBody;
    }
    
    private HttpHeaders buildHeaders(VllmConfigProperties.VllmServerConfig serverConfig) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // API 키가 설정된 경우 추가
        if (vllmConfig.getSecuritySettings() != null && 
            vllmConfig.getSecuritySettings().getApiKey() != null) {
            headers.setBearerAuth(vllmConfig.getSecuritySettings().getApiKey());
        }
        
        return headers;
    }
    
    private LlmResponse parseResponse(String serverName, Map<String, Object> responseBody, long responseTime) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmResponse.error(serverName, "No choices in response");
            }
            
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = (String) message.get("content");
            
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            Integer tokensUsed = usage != null ? (Integer) usage.get("total_tokens") : 0;
            
            LlmResponse response = LlmResponse.success(serverName, content, tokensUsed, "vllm");
            response.setId((String) responseBody.get("id"));
            response.setTimestamp(LocalDateTime.now());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error parsing vLLM response", e);
            return LlmResponse.error(serverName, "Failed to parse response: " + e.getMessage());
        }
    }
    
    private LlmResponse parseCompletionResponse(String serverName, Map<String, Object> responseBody, long responseTime) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmResponse.error(serverName, "No choices in response");
            }
            
            Map<String, Object> choice = choices.get(0);
            String content = (String) choice.get("text");
            
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            Integer tokensUsed = usage != null ? (Integer) usage.get("total_tokens") : 0;
            
            LlmResponse response = LlmResponse.success(serverName, content, tokensUsed, "vllm");
            response.setId((String) responseBody.get("id"));
            response.setTimestamp(LocalDateTime.now());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error parsing vLLM completion response", e);
            return LlmResponse.error(serverName, "Failed to parse response: " + e.getMessage());
        }
    }
    
    private List<ModelResponse> parseModelsResponse(Map<String, Object> responseBody) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
        
        if (data == null) {
            return List.of();
        }
        
        return data.stream()
            .map(model -> ModelResponse.builder()
                .id((String) model.get("id"))
                .object((String) model.get("object"))
                .created(model.get("created") != null ? ((Number) model.get("created")).longValue() : null)
                .ownedBy((String) model.get("owned_by"))
                .build())
            .toList();
    }
    
    private EmbeddingResponse parseEmbeddingResponse(Map<String, Object> responseBody) {
        try {
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            
            return EmbeddingResponse.builder()
                .success(true)
                .data(data)
                .usage(usage)
                .build();
            
        } catch (Exception e) {
            return EmbeddingResponse.error("Failed to parse embedding response: " + e.getMessage());
        }
    }
    
    // Response 클래스들
    @lombok.Builder
    @lombok.Data
    public static class ModelResponse {
        private String id;
        private String object;
        private Long created;
        private String ownedBy;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class EmbeddingRequest {
        private String input;
        private String model;
        private String encodingFormat; // "float", "base64"
    }
    
    @lombok.Builder
    @lombok.Data
    public static class EmbeddingResponse {
        private Boolean success;
        private List<Map<String, Object>> data;
        private Map<String, Object> usage;
        private String error;
        
        public static EmbeddingResponse error(String error) {
            return EmbeddingResponse.builder()
                .success(false)
                .error(error)
                .build();
        }
    }
    
    public interface StreamingResponseHandler {
        void onData(String data);
        void onComplete();
        void onError(String error);
    }
}