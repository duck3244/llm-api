// VllmConfigProperties.java
package com.yourcompany.llm.config.vllm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "vllm")
public class VllmConfigProperties {
    
    @Valid
    private List<VllmServerConfig> servers;
    
    @Valid
    private VllmGlobalSettings globalSettings = new VllmGlobalSettings();
    
    @Data
    public static class VllmServerConfig {
        @NotBlank
        private String name;
        
        @NotBlank
        private String model; // llama3.2 모델 경로
        
        @NotBlank
        private String host = "localhost";
        
        @NotNull
        @Min(1024)
        private Integer port;
        
        @NotNull
        private Boolean enabled = true;
        
        @Valid
        private VllmModelSettings modelSettings = new VllmModelSettings();
        
        @Valid
        private VllmPerformanceSettings performanceSettings = new VllmPerformanceSettings();
    }
    
    @Data
    public static class VllmModelSettings {
        private Integer maxModelLen = 8192;
        private Integer maxNumSeqs = 256;
        private String dtype = "auto";
        private Boolean trustRemoteCode = false;
    }
    
    @Data
    public static class VllmPerformanceSettings {
        private Double gpuMemoryUtilization = 0.9;
        private Integer tensorParallelSize = 1;
        private Boolean disableLogStats = false;
    }
    
    @Data
    public static class VllmGlobalSettings {
        private Integer seed = 42;
        private String logLevel = "INFO";
        private Boolean enableMetrics = true;
    }
    
    // Helper methods
    public VllmServerConfig getServerByName(String serverName) {
        if (servers == null) return null;
        return servers.stream()
            .filter(server -> server.getName().equals(serverName))
            .findFirst()
            .orElse(null);
    }
    
    public List<VllmServerConfig> getEnabledServers() {
        if (servers == null) return List.of();
        return servers.stream()
            .filter(server -> server.getEnabled() != null && server.getEnabled())
            .toList();
    }
}