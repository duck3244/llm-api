// LlmApiApplication.java
package com.yourcompany.llm;

import com.yourcompany.llm.service.vllm.VllmMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
public class LlmApiApplication implements ApplicationRunner {

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private VllmMonitoringService vllmMonitoringService;

    private static final String APPLICATION_NAME = "vLLM Llama 3.2 API";
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        try {
            SpringApplication.run(LlmApiApplication.class, args);
            long duration = System.currentTimeMillis() - startTime;
            log.info("🎉 {} started successfully in {} ms", APPLICATION_NAME, duration);
        } catch (Exception e) {
            log.error("❌ Failed to start {}: {}", APPLICATION_NAME, e.getMessage(), e);
            System.exit(1);
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("🚀 Initializing {} v{}", APPLICATION_NAME, VERSION);
        
        printEnvironmentInfo();
        printServerInfo();
        
        if (vllmMonitoringService != null) {
            vllmMonitoringService.initializeDefaultAlertRules();
            log.info("✅ vLLM monitoring service initialized");
        }
        
        printStartupComplete();
    }

    @PreDestroy
    public void onShutdown() {
        log.info("🛑 Shutting down {} v{}", APPLICATION_NAME, VERSION);
        log.info("✅ {} shutdown completed gracefully", APPLICATION_NAME);
    }

    private void printEnvironmentInfo() {
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        
        log.info("🖥️ System Info:");
        log.info("   Java Version: {}", javaVersion);
        log.info("   OS: {}", osName);
        log.info("   Profile: {}", String.join(",", environment.getActiveProfiles()));
    }

    private void printServerInfo() {
        try {
            String port = environment.getProperty("server.port", "8080");
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            
            log.info("🌐 Server Info:");
            log.info("   Port: {}", port);
            log.info("   URL: http://{}:{}", hostAddress, port);
        } catch (Exception e) {
            log.warn("⚠️ Could not determine server info: {}", e.getMessage());
        }
    }

    private void printStartupComplete() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String port = environment.getProperty("server.port", "8080");
        
        log.info("");
        log.info("🎉 =====================================");
        log.info("🎉   {} v{}", APPLICATION_NAME, VERSION);
        log.info("🎉   Started at: {}", currentTime);
        log.info("🎉   Ready to serve Llama 3.2 requests!");
        log.info("🎉 =====================================");
        log.info("");
        
        log.info("🔗 API Endpoints:");
        log.info("   Health: http://localhost:{}/api/llm/health", port);
        log.info("   Generate: POST http://localhost:{}/api/llm/generate", port);
        log.info("   vLLM Status: http://localhost:{}/api/vllm/status", port);
        log.info("");
        
        log.info("💡 Quick Start:");
        log.info("   curl -X POST http://localhost:{}/api/llm/generate \\", port);
        log.info("        -H \"Content-Type: application/json\" \\");
        log.info("        -d '{{\"message\": \"Hello, Llama 3.2!\"}}'");
        log.info("");
    }

    public static String getVersion() {
        return VERSION;
    }
}