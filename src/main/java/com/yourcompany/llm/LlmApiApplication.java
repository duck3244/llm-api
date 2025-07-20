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
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAsync
@EnableScheduling
public class LlmApiApplication implements ApplicationRunner, ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private VllmMonitoringService vllmMonitoringService;

    private static final String APPLICATION_NAME = "LLM REST API";
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        // JVM 시스템 프로퍼티 설정
        setSystemProperties();
        
        // 애플리케이션 시작 시간 기록
        long startTime = System.currentTimeMillis();
        
        try {
            // Spring Boot 애플리케이션 실행
            ApplicationContext context = SpringApplication.run(LlmApiApplication.class, args);
            
            // 시작 완료 로그
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
        
        // 환경 정보 출력
        printEnvironmentInfo();
        
        // 활성 프로파일 출력
        printActiveProfiles();
        
        // 서버 정보 출력
        printServerInfo();
        
        // vLLM 모니터링 초기화
        initializeVllmMonitoring();
        
        // 헬스 체크 URL 출력
        printHealthCheckUrls();
        
        // 시작 완료 메시지
        printStartupComplete();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("📋 Application context refreshed - {} beans loaded", 
                event.getApplicationContext().getBeanDefinitionCount());
    }

    @PreDestroy
    public void onShutdown() {
        log.info("🛑 Shutting down {} v{}", APPLICATION_NAME, VERSION);
        
        // vLLM 서버들 정리
        if (vllmMonitoringService != null) {
            try {
                // 모니터링 서비스 정리 작업
                log.info("🧹 Cleaning up vLLM monitoring service...");
                // 필요한 정리 작업 수행
            } catch (Exception e) {
                log.warn("⚠️ Error during vLLM cleanup: {}", e.getMessage());
            }
        }
        
        log.info("✅ {} shutdown completed gracefully", APPLICATION_NAME);
    }

    /**
     * JVM 시스템 프로퍼티 설정
     */
    private static void setSystemProperties() {
        // 파일 인코딩 설정
        System.setProperty("file.encoding", "UTF-8");
        
        // 네트워크 타임아웃 설정
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "60000");
        
        // HTTP 연결 풀 설정
        System.setProperty("http.maxConnections", "50");
        System.setProperty("http.keepAlive", "true");
        
        // Java 보안 설정 (HTTPS 관련)
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        
        // 메모리 설정 권장사항 출력
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        log.info("💾 JVM Memory - Max: {}MB, Total: {}MB, Free: {}MB", 
                maxMemory / 1024 / 1024, 
                totalMemory / 1024 / 1024, 
                freeMemory / 1024 / 1024);
        
        // 메모리 부족 경고
        if (maxMemory < 2L * 1024 * 1024 * 1024) { // 2GB 미만
            log.warn("⚠️ Low memory detected. Consider increasing JVM heap size with -Xmx4g or higher for optimal performance");
        }
    }

    /**
     * 환경 정보 출력
     */
    private void printEnvironmentInfo() {
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        
        log.info("🖥️ System Info:");
        log.info("   Java Version: {}", javaVersion);
        log.info("   OS: {} {} ({})", osName, osVersion, osArch);
        log.info("   User: {}", System.getProperty("user.name"));
        log.info("   Working Directory: {}", System.getProperty("user.dir"));
        log.info("   Timezone: {}", System.getProperty("user.timezone"));
    }

    /**
     * 활성 프로파일 출력
     */
    private void printActiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] defaultProfiles = environment.getDefaultProfiles();
        
        log.info("📝 Spring Profiles:");
        log.info("   Active: {}", activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "none");
        log.info("   Default: {}", Arrays.toString(defaultProfiles));
        
        // 프로파일별 특별 설정 안내
        if (Arrays.asList(activeProfiles).contains("prod")) {
            log.info("🏭 Production profile detected - Performance optimizations enabled");
        } else if (Arrays.asList(activeProfiles).contains("dev")) {
            log.info("🛠️ Development profile detected - Debug features enabled");
        } else if (Arrays.asList(activeProfiles).contains("test")) {
            log.info("🧪 Test profile detected - Test configurations loaded");
        }
    }

    /**
     * 서버 정보 출력
     */
    private void printServerInfo() {
        try {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String hostName = InetAddress.getLocalHost().getHostName();
            
            log.info("🌐 Server Info:");
            log.info("   Port: {}", port);
            log.info("   Context Path: {}", contextPath.isEmpty() ? "/" : contextPath);
            log.info("   Host: {} ({})", hostName, hostAddress);
            log.info("   Local URL: http://localhost:{}{}", port, contextPath);
            log.info("   Network URL: http://{}:{}{}", hostAddress, port, contextPath);
            
        } catch (Exception e) {
            log.warn("⚠️ Could not determine server info: {}", e.getMessage());
        }
    }

    /**
     * vLLM 모니터링 초기화
     */
    private void initializeVllmMonitoring() {
        if (vllmMonitoringService != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("🔧 Initializing vLLM monitoring service...");
                    
                    // 기본 알럿 규칙 초기화
                    vllmMonitoringService.initializeDefaultAlertRules();
                    
                    log.info("✅ vLLM monitoring service initialized successfully");
                    
                } catch (Exception e) {
                    log.error("❌ Failed to initialize vLLM monitoring: {}", e.getMessage(), e);
                }
            });
        } else {
            log.info("ℹ️ vLLM monitoring service not available (vLLM profile not active)");
        }
    }

    /**
     * 헬스 체크 URL 출력
     */
    private void printHealthCheckUrls() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;
        
        log.info("🏥 Health Check URLs:");
        log.info("   Main Health: {}/actuator/health", baseUrl);
        log.info("   LLM Health: {}/api/llm/health", baseUrl);
        
        if (vllmMonitoringService != null) {
            log.info("   vLLM Status: {}/api/vllm/status", baseUrl);
            log.info("   vLLM Dashboard: {}/api/vllm/dashboard", baseUrl);
        }
        
        log.info("   Metrics: {}/actuator/metrics", baseUrl);
        log.info("   Prometheus: {}/actuator/prometheus", baseUrl);
    }

    /**
     * API 엔드포인트 정보 출력
     */
    private void printApiEndpoints() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;
        
        log.info("🔗 Main API Endpoints:");
        log.info("   Generate Text: POST {}/api/llm/generate", baseUrl);
        log.info("   List Models: GET {}/api/llm/models", baseUrl);
        log.info("   Usage Stats: GET {}/api/stats/daily", baseUrl);
        
        if (vllmMonitoringService != null) {
            log.info("🚀 vLLM API Endpoints:");
            log.info("   Start Server: POST {}/api/vllm/servers/{{name}}/start", baseUrl);
            log.info("   Stop Server: POST {}/api/vllm/servers/{{name}}/stop", baseUrl);
            log.info("   Server Health: GET {}/api/vllm/servers/{{name}}/health", baseUrl);
            log.info("   Load Balancer: GET {}/api/vllm/load-balancer/status", baseUrl);
        }
    }

    /**
     * 시작 완료 메시지 출력
     */
    private void printStartupComplete() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        log.info("");
        log.info("🎉 =====================================");
        log.info("🎉   {} v{}", APPLICATION_NAME, VERSION);
        log.info("🎉   Started at: {}", currentTime);
        log.info("🎉   Ready to serve LLM requests!");
        log.info("🎉 =====================================");
        log.info("");
        
        // API 엔드포인트 정보 출력
        printApiEndpoints();
        
        // 추가 정보
        log.info("📚 Documentation:");
        log.info("   API Docs: Check /docs/API.md");
        log.info("   Setup Guide: Check /docs/SETUP.md");
        log.info("   Troubleshooting: Check /docs/TROUBLESHOOTING.md");
        
        log.info("");
        log.info("💡 Quick Start:");
        log.info("   1. Check health: curl http://localhost:{}/api/llm/health", 
                environment.getProperty("server.port", "8080"));
        log.info("   2. List models: curl http://localhost:{}/api/llm/models", 
                environment.getProperty("server.port", "8080"));
        log.info("   3. Generate text: curl -X POST http://localhost:{}/api/llm/generate \\", 
                environment.getProperty("server.port", "8080"));
        log.info("      -H \"Content-Type: application/json\" \\");
        log.info("      -d '{{\"model\": \"llama3\", \"message\": \"Hello!\"}}'");
        log.info("");
    }

    /**
     * 애플리케이션 정보 반환 (다른 컴포넌트에서 사용)
     */
    public static String getApplicationInfo() {
        return String.format("%s v%s", APPLICATION_NAME, VERSION);
    }

    /**
     * 버전 정보 반환
     */
    public static String getVersion() {
        return VERSION;
    }
}