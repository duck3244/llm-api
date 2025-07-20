// ValidationUtils.java
package com.yourcompany.llm.util;

import com.yourcompany.llm.dto.LlmRequest;
import com.yourcompany.llm.config.LlmConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ValidationUtils {
    
    // 정규식 패턴들
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._-]{20,}$"
    );
    
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._-]+$"
    );
    
    private static final Pattern SAFE_TEXT_PATTERN = Pattern.compile(
        "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\s]*$", Pattern.UNICODE_CHARACTER_CLASS
    );
    
    // 금지된 콘텐츠 패턴
    private static final List<Pattern> PROHIBITED_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(password|secret|token|key)\\s*[:=]\\s*[\\w-]+", Pattern.MULTILINE),
        Pattern.compile("(?i)\\bcredit\\s*card\\s*number", Pattern.MULTILINE),
        Pattern.compile("(?i)\\bssn\\s*[:=]\\s*\\d{3}-?\\d{2}-?\\d{4}", Pattern.MULTILINE),
        Pattern.compile("(?i)\\b(?:execute|exec|eval|system|shell)\\s*\\(", Pattern.MULTILINE)
    );
    
    // 스팸/악성 콘텐츠 패턴
    private static final List<Pattern> SPAM_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(buy now|click here|free money|get rich|urgent)\\b", Pattern.MULTILINE),
        Pattern.compile("(?i)\\b(viagra|casino|poker|lottery)\\b", Pattern.MULTILINE),
        Pattern.compile("(?i)\\b(hack|crack|illegal|pirate)\\b", Pattern.MULTILINE)
    );
    
    /**
     * LLM 요청 전체 검증
     */
    public static ValidationResult validateRequest(LlmRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (request == null) {
            errors.add("Request cannot be null");
            return ValidationResult.builder()
                .valid(false)
                .errors(errors)
                .warnings(warnings)
                .build();
        }
        
        // 기본 필드 검증
        validateBasicFields(request, errors, warnings);
        
        // 메시지 내용 검증
        validateMessageContent(request, errors, warnings);
        
        // 파라미터 범위 검증
        validateParameters(request, errors, warnings);
        
        // 보안 검증
        validateSecurity(request, errors, warnings);
        
        // 길이 제한 검증
        validateLengthLimits(request, errors, warnings);
        
        boolean isValid = errors.isEmpty();
        
        log.debug("Request validation completed - Valid: {}, Errors: {}, Warnings: {}", 
            isValid, errors.size(), warnings.size());
        
        return ValidationResult.builder()
            .valid(isValid)
            .errors(errors)
            .warnings(warnings)
            .build();
    }
    
    /**
     * 기본 필드 검증
     */
    private static void validateBasicFields(LlmRequest request, List<String> errors, List<String> warnings) {
        // 모델명 검증
        if (isBlank(request.getModel())) {
            warnings.add("Model name not specified, will use default model");
        } else if (!MODEL_NAME_PATTERN.matcher(request.getModel()).matches()) {
            errors.add("Invalid model name format: " + request.getModel());
        }
        
        // 메시지 또는 메시지 리스트 중 하나는 필수
        boolean hasMessage = !isBlank(request.getMessage());
        boolean hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        
        if (!hasMessage && !hasMessages) {
            errors.add("Either 'message' or 'messages' must be provided");
        } else if (hasMessage && hasMessages) {
            warnings.add("Both 'message' and 'messages' provided, 'messages' will take priority");
        }
    }
    
    /**
     * 메시지 내용 검증
     */
    private static void validateMessageContent(LlmRequest request, List<String> errors, List<String> warnings) {
        List<String> allMessages = new ArrayList<>();
        
        // 단일 메시지 수집
        if (!isBlank(request.getMessage())) {
            allMessages.add(request.getMessage());
        }
        
        // 메시지 리스트 수집 및 검증
        if (request.getMessages() != null) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                LlmRequest.Message msg = request.getMessages().get(i);
                
                if (msg == null) {
                    errors.add("Message at index " + i + " is null");
                    continue;
                }
                
                // Role 검증
                if (isBlank(msg.getRole())) {
                    errors.add("Message role is required at index " + i);
                } else if (!isValidRole(msg.getRole())) {
                    errors.add("Invalid message role '" + msg.getRole() + "' at index " + i);
                }
                
                // Content 검증
                if (isBlank(msg.getContent())) {
                    errors.add("Message content is required at index " + i);
                } else {
                    allMessages.add(msg.getContent());
                }
            }
        }
        
        // 시스템 프롬프트 검증
        if (!isBlank(request.getSystemPrompt())) {
            allMessages.add(request.getSystemPrompt());
        }
        
        // 모든 메시지 내용 검증
        for (String message : allMessages) {
            validateSingleMessage(message, errors, warnings);
        }
    }
    
    /**
     * 단일 메시지 검증
     */
    private static void validateSingleMessage(String message, List<String> errors, List<String> warnings) {
        if (isBlank(message)) {
            return;
        }
        
        // 안전한 텍스트 패턴 검증
        if (!SAFE_TEXT_PATTERN.matcher(message).matches()) {
            warnings.add("Message contains potentially unsafe characters");
        }
        
        // 금지된 콘텐츠 검사
        for (Pattern pattern : PROHIBITED_PATTERNS) {
            if (pattern.matcher(message).find()) {
                errors.add("Message contains prohibited content pattern");
                break;
            }
        }
        
        // 스팸 콘텐츠 검사
        int spamCount = 0;
        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(message).find()) {
                spamCount++;
            }
        }
        
        if (spamCount >= 2) {
            warnings.add("Message may contain spam-like content");
        }
        
        // 반복 문자 검사
        if (hasExcessiveRepetition(message)) {
            warnings.add("Message contains excessive character repetition");
        }
        
        // 대소문자 비율 검사
        if (hasImproperCaseRatio(message)) {
            warnings.add("Message has unusual uppercase/lowercase ratio");
        }
    }
    
    /**
     * 파라미터 범위 검증
     */
    private static void validateParameters(LlmRequest request, List<String> errors, List<String> warnings) {
        // Temperature 검증
        if (request.getTemperature() != null) {
            double temp = request.getTemperature();
            if (temp < 0.0 || temp > 2.0) {
                errors.add("Temperature must be between 0.0 and 2.0, got: " + temp);
            } else if (temp > 1.5) {
                warnings.add("High temperature (" + temp + ") may produce unpredictable results");
            }
        }
        
        // Max tokens 검증
        if (request.getMaxTokens() != null) {
            int maxTokens = request.getMaxTokens();
            if (maxTokens <= 0) {
                errors.add("Max tokens must be positive, got: " + maxTokens);
            } else if (maxTokens > 32768) {
                errors.add("Max tokens exceeds maximum limit (32768), got: " + maxTokens);
            } else if (maxTokens > 8192) {
                warnings.add("High max tokens (" + maxTokens + ") may result in slower responses");
            }
        }
    }
    
    /**
     * 보안 검증
     */
    private static void validateSecurity(LlmRequest request, List<String> errors, List<String> warnings) {
        List<String> allTexts = getAllTextFields(request);
        
        for (String text : allTexts) {
            if (isBlank(text)) continue;
            
            // SQL Injection 패턴 검사
            if (containsSqlInjection(text)) {
                errors.add("Text contains potential SQL injection patterns");
            }
            
            // Script injection 패턴 검사
            if (containsScriptInjection(text)) {
                errors.add("Text contains potential script injection patterns");
            }
            
            // Prompt injection 패턴 검사
            if (containsPromptInjection(text)) {
                warnings.add("Text contains potential prompt injection patterns");
            }
            
            // 개인정보 패턴 검사
            if (containsPersonalInfo(text)) {
                warnings.add("Text may contain personal information");
            }
        }
    }
    
    /**
     * 길이 제한 검증
     */
    private static void validateLengthLimits(LlmRequest request, List<String> errors, List<String> warnings) {
        // 단일 메시지 길이 제한
        if (!isBlank(request.getMessage())) {
            validateTextLength("message", request.getMessage(), errors, warnings);
        }
        
        // 메시지 리스트 길이 제한
        if (request.getMessages() != null) {
            if (request.getMessages().size() > 100) {
                errors.add("Too many messages in conversation (max: 100)");
            }
            
            for (int i = 0; i < request.getMessages().size(); i++) {
                LlmRequest.Message msg = request.getMessages().get(i);
                if (msg != null && !isBlank(msg.getContent())) {
                    validateTextLength("message[" + i + "]", msg.getContent(), errors, warnings);
                }
            }
        }
        
        // 시스템 프롬프트 길이 제한
        if (!isBlank(request.getSystemPrompt())) {
            validateTextLength("systemPrompt", request.getSystemPrompt(), errors, warnings);
        }
    }
    
    /**
     * 텍스트 길이 검증
     */
    private static void validateTextLength(String fieldName, String text, List<String> errors, List<String> warnings) {
        int length = text.length();
        
        if (length > 1_000_000) { // 1MB
            errors.add(fieldName + " exceeds maximum length (1MB)");
        } else if (length > 100_000) { // 100KB
            warnings.add(fieldName + " is very long (" + length + " chars), may cause performance issues");
        }
        
        // 토큰 수 추정 및 검증
        int estimatedTokens = ModelUtils.estimateTokenCount(text, "gpt-3.5-turbo");
        if (estimatedTokens > 16000) {
            warnings.add(fieldName + " contains approximately " + estimatedTokens + " tokens, may exceed model limits");
        }
    }
    
    /**
     * API 키 검증
     */
    public static boolean isValidApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return false;
        }
        
        return API_KEY_PATTERN.matcher(apiKey).matches();
    }
    
    /**
     * 이메일 주소 검증
     */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * URL 검증
     */
    public static boolean isValidUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
    
    /**
     * 모델 설정 검증
     */
    public static ValidationResult validateModelConfig(LlmConfigProperties.ModelConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config == null) {
            errors.add("Model configuration cannot be null");
            return ValidationResult.builder().valid(false).errors(errors).build();
        }
        
        // 이름 검증
        if (isBlank(config.getName())) {
            errors.add("Model name is required");
        } else if (!MODEL_NAME_PATTERN.matcher(config.getName()).matches()) {
            errors.add("Invalid model name format: " + config.getName());
        }
        
        // 프로바이더 검증
        if (isBlank(config.getProvider())) {
            errors.add("Provider is required");
        } else if (!isValidProvider(config.getProvider())) {
            errors.add("Invalid provider: " + config.getProvider());
        }
        
        // 엔드포인트 검증
        if (isBlank(config.getEndpoint())) {
            errors.add("Endpoint is required");
        } else if (!isValidUrl(config.getEndpoint())) {
            errors.add("Invalid endpoint URL: " + config.getEndpoint());
        }
        
        // API 키 검증 (선택적)
        if (!isBlank(config.getApiKey()) && !isValidApiKey(config.getApiKey())) {
            warnings.add("API key format may be invalid");
        }
        
        // 파라미터 검증
        if (config.getMaxTokens() != null && (config.getMaxTokens() <= 0 || config.getMaxTokens() > 32768)) {
            errors.add("Max tokens must be between 1 and 32768");
        }
        
        if (config.getTemperature() != null && (config.getTemperature() < 0.0 || config.getTemperature() > 2.0)) {
            errors.add("Temperature must be between 0.0 and 2.0");
        }
        
        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .build();
    }
    
    // Helper methods
    
    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    private static boolean isValidRole(String role) {
        return role != null && Set.of("system", "user", "assistant", "function").contains(role.toLowerCase());
    }
    
    private static boolean isValidProvider(String provider) {
        return provider != null && Set.of(
            "openai", "anthropic", "google", "huggingface", 
            "ollama", "vllm", "local"
        ).contains(provider.toLowerCase());
    }
    
    private static List<String> getAllTextFields(LlmRequest request) {
        List<String> texts = new ArrayList<>();
        
        if (!isBlank(request.getMessage())) {
            texts.add(request.getMessage());
        }
        
        if (request.getMessages() != null) {
            request.getMessages().stream()
                .filter(Objects::nonNull)
                .map(LlmRequest.Message::getContent)
                .filter(content -> !isBlank(content))
                .forEach(texts::add);
        }
        
        if (!isBlank(request.getSystemPrompt())) {
            texts.add(request.getSystemPrompt());
        }
        
        return texts;
    }
    
    private static boolean hasExcessiveRepetition(String text) {
        if (text.length() < 10) return false;
        
        // 같은 문자가 5번 이상 반복되는지 확인
        for (int i = 0; i <= text.length() - 5; i++) {
            char c = text.charAt(i);
            boolean isRepeated = true;
            
            for (int j = 1; j < 5; j++) {
                if (i + j >= text.length() || text.charAt(i + j) != c) {
                    isRepeated = false;
                    break;
                }
            }
            
            if (isRepeated) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean hasImproperCaseRatio(String text) {
        if (text.length() < 20) return false;
        
        long upperCount = text.chars().filter(Character::isUpperCase).count();
        long lowerCount = text.chars().filter(Character::isLowerCase).count();
        long totalLetters = upperCount + lowerCount;
        
        if (totalLetters == 0) return false;
        
        double upperRatio = (double) upperCount / totalLetters;
        
        // 80% 이상이 대문자이거나 95% 이상이 소문자인 경우
        return upperRatio > 0.8 || upperRatio < 0.05;
    }
    
    private static boolean containsSqlInjection(String text) {
        String[] sqlPatterns = {
            "(?i)\\b(select|insert|update|delete|drop|create|alter|exec|execute)\\s+",
            "(?i)\\bunion\\s+select",
            "(?i)\\bor\\s+1\\s*=\\s*1",
            "(?i)\\band\\s+1\\s*=\\s*1",
            "(?i)';\\s*--",
            "(?i)\\bxp_cmdshell\\b"
        };
        
        for (String pattern : sqlPatterns) {
            if (Pattern.compile(pattern).matcher(text).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean containsScriptInjection(String text) {
        String[] scriptPatterns = {
            "(?i)<script[^>]*>",
            "(?i)javascript:",
            "(?i)on(click|load|error|focus|blur)\\s*=",
            "(?i)\\beval\\s*\\(",
            "(?i)\\bdocument\\.(write|cookie|location)",
            "(?i)\\bwindow\\.(location|open)"
        };
        
        for (String pattern : scriptPatterns) {
            if (Pattern.compile(pattern).matcher(text).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean containsPromptInjection(String text) {
        String[] promptPatterns = {
            "(?i)ignore\\s+(previous|all)\\s+instructions?",
            "(?i)you\\s+are\\s+now\\s+",
            "(?i)forget\\s+everything",
            "(?i)act\\s+as\\s+if\\s+you\\s+are",
            "(?i)pretend\\s+to\\s+be",
            "(?i)new\\s+instructions?:",
            "(?i)system\\s*:\\s*you\\s+must"
        };
        
        for (String pattern : promptPatterns) {
            if (Pattern.compile(pattern).matcher(text).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean containsPersonalInfo(String text) {
        String[] personalInfoPatterns = {
            "\\b\\d{3}-?\\d{2}-?\\d{4}\\b",  // SSN
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b",  // Credit card
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",  // Email
            "\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b",  // Phone number
            "\\b(?:passport|license|id)\\s*#?\\s*:?\\s*[A-Z0-9]+\\b"  // ID numbers
        };
        
        for (String pattern : personalInfoPatterns) {
            if (Pattern.compile(pattern).matcher(text).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 검증 결과 클래스
     */
    @lombok.Builder
    @lombok.Data
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
        
        public String getErrorSummary() {
            if (!hasErrors()) {
                return null;
            }
            return String.join("; ", errors);
        }
        
        public String getWarningSummary() {
            if (!hasWarnings()) {
                return null;
            }
            return String.join("; ", warnings);
        }
    }
    
    /**
     * 텍스트 정제 (위험한 내용 제거)
     */
    public static String sanitizeText(String text) {
        if (isBlank(text)) {
            return text;
        }
        
        String sanitized = text;
        
        // HTML 태그 제거
        sanitized = sanitized.replaceAll("<[^>]+>", "");
        
        // 스크립트 관련 제거
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        
        // 과도한 공백 정리
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        // 앞뒤 공백 제거
        sanitized = sanitized.trim();
        
        return sanitized;
    }
    
    /**
     * 안전한 모델명 생성
     */
    public static String sanitizeModelName(String modelName) {
        if (isBlank(modelName)) {
            return null;
        }
        
        // 알파벳, 숫자, 하이픈, 언더스코어, 점만 허용
        String sanitized = modelName.replaceAll("[^a-zA-Z0-9._-]", "");
        
        // 연속된 특수문자 제거
        sanitized = sanitized.replaceAll("[._-]{2,}", "-");
        
        // 앞뒤 특수문자 제거
        sanitized = sanitized.replaceAll("^[._-]+|[._-]+$", "");
        
        return sanitized.toLowerCase();
    }
    
    /**
     * 요청 크기 검증
     */
    public static boolean isRequestSizeValid(LlmRequest request, int maxSizeKb) {
        if (request == null) {
            return false;
        }
        
        int totalSize = 0;
        
        if (request.getMessage() != null) {
            totalSize += request.getMessage().length();
        }
        
        if (request.getMessages() != null) {
            for (LlmRequest.Message msg : request.getMessages()) {
                if (msg != null && msg.getContent() != null) {
                    totalSize += msg.getContent().length();
                }
                if (msg != null && msg.getRole() != null) {
                    totalSize += msg.getRole().length();
                }
            }
        }
        
        if (request.getSystemPrompt() != null) {
            totalSize += request.getSystemPrompt().length();
        }
        
        if (request.getModel() != null) {
            totalSize += request.getModel().length();
        }
        
        int sizeInKb = totalSize / 1024;
        return sizeInKb <= maxSizeKb;
    }
}
    