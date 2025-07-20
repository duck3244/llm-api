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
    
    public CompletableFuture<LlmResponse> chatCompletion(String serverName, LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
            if (serverConfig == null) {
                return LlmResponse.error("llama3.2", "Server configuration not found: " + serverName);
            }
            
            try {
                String endpoint = String.format("http://%s:%d/v1/chat/completions", 
                    serverConfig.getHost(), serverConfig.getPort());
                
                Map<String, Object> requestBody = buildChatRequest(request);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, Map.class);
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return parseResponse(response.getBody(), responseTime);
                } else {
                    return LlmResponse.error("llama3.2", "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Error calling vLLM API for server: {}", serverName, e);
                return LlmResponse.error("llama3.1", "API call failed: " + e.getMessage());
            }
        });
    }
    
    private Map<String, Object> buildChatRequest(LlmRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        
        requestBody.put("model", "llama3.2");
        
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            requestBody.put("messages", request.getMessages());
        } else if (request.getMessage() != null) {
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", request.getMessage())
            ));
        }
        
        requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1000);
        requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
        requestBody.put("stream", false);
        
        return requestBody;
    }
    
    private LlmResponse parseResponse(Map<String, Object> responseBody, long responseTime) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmResponse.error("llama3.2", "No choices in response");
            }
            
            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = (String) message.get("content");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            Integer tokensUsed = usage != null ? (Integer) usage.get("total_tokens") : null;
            
            LlmResponse response = LlmResponse.success("llama3.2", content, tokensUsed, "vllm");
            response.setId((String) responseBody.get("id"));
            response.setResponseTimeMs(responseTime);
            response.setTimestamp(LocalDateTime.now());
            
            if (usage != null) {
                LlmResponse.Usage usageInfo = LlmResponse.Usage.builder()
                    .promptTokens((Integer) usage.get("prompt_tokens"))
                    .completionTokens((Integer) usage.get("completion_tokens"))
                    .totalTokens((Integer) usage.get("total_tokens"))
                    .build();
                response.setUsage(usageInfo);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error parsing vLLM response", e);
            return LlmResponse.error("llama3.2", "Failed to parse response: " + e.getMessage());
        }
    }
}