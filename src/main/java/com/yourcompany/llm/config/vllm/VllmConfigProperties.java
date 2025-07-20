// VllmConfigProperties.java
package com.yourcompany.llm.config.vllm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "vllm")
public class VllmConfigProperties {
    
    private List<VllmServerConfig> servers;
    private VllmGlobalSettings globalSettings;
    private VllmResourceSettings resourceSettings;
    private VllmSecuritySettings securitySettings;
    
    @Data
    public static class VllmServerConfig {
        private String name;
        private String model;
        private String modelPath;
        private Integer port;
        private String host;
        private Boolean enabled;
        private VllmModelSettings modelSettings;
        private VllmPerformanceSettings performanceSettings;
        private VllmQuantizationSettings quantizationSettings;
    }
    
    @Data
    public static class VllmModelSettings {
        private Integer maxModelLen;
        private Integer maxNumSeqs;
        private String dtype;
        private Boolean trustRemoteCode;
        private Integer maxLogProbs;
        private String revision;
        private String tokenizer;
        private String tokenizerRevision;
        private Boolean skipTokenizerInit;
    }
    
    @Data
    public static class VllmPerformanceSettings {
        private Double gpuMemoryUtilization;
        private Integer tensorParallelSize;
        private Integer pipelineParallelSize;
        private Integer maxPaddings;
        private Integer blockSize;
        private String swapSpace;
        private Boolean disableLogStats;
    }
    
    @Data
    public static class VllmQuantizationSettings {
        private String quantization; // "awq", "gptq", "squeezellm", "fp8"
        private String loadFormat; // "auto", "pt", "safetensors", "npcache", "dummy"
        private Boolean enforceEager;
        private Integer maxContextLenToCapture;
    }
    
    @Data
    public static class VllmGlobalSettings {
        private Integer seed;
        private String workerUseRay;
        private Integer engineUseRay;
        private Boolean disableLogRequests;
        private String logLevel; // "DEBUG", "INFO", "WARNING", "ERROR"
        private Integer requestTimeout;
        private Boolean enableMetrics;
    }
    
    @Data
    public static class VllmResourceSettings {
        private String device; // "auto", "cuda", "cpu"
        private List<Integer> gpuIds;
        private Integer numGpus;
        private String cpuOffloadGb;
        private String diskOffloadGb;
        private Integer maxCpuThreads;
    }
    
    @Data
    public static class VllmSecuritySettings {
        private Boolean sslEnabled;
        private String sslKeyfile;
        private String sslCertfile;
        private String apiKey;
        private Boolean corsEnabled;
        private List<String> allowedOrigins;
        private List<String> allowedMethods;
        private List<String> allowedHeaders;
    }
    
    // Helper methods
    public VllmServerConfig getServerByName(String serverName) {
        return servers.stream()
            .filter(server -> server.getName().equals(serverName))
            .findFirst()
            .orElse(null);
    }
    
    public List<VllmServerConfig> getEnabledServers() {
        return servers.stream()
            .filter(server -> server.getEnabled() != null && server.getEnabled())
            .toList();
    }
    
    public VllmServerConfig getServerByPort(Integer port) {
        return servers.stream()
            .filter(server -> server.getPort().equals(port))
            .findFirst()
            .orElse(null);
    }
    
    public List<VllmServerConfig> getServersByModel(String model) {
        return servers.stream()
            .filter(server -> server.getModel().contains(model))
            .toList();
    }
}