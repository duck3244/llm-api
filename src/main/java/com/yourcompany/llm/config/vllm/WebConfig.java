// WebConfig.java
package com.yourcompany.llm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /**
     * vLLM API 호출을 위한 RestTemplate 설정
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.info("✅ RestTemplate configured - Connect timeout: 10s, Read timeout: 60s");
        
        return restTemplate;
    }
    
    /**
     * CORS 설정
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
        
        log.info("✅ CORS configuration applied for /api/** endpoints");
    }
}