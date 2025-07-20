// VllmProcessManager.java
package com.yourcompany.llm.service.vllm;

import com.yourcompany.llm.config.vllm.VllmConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class VllmProcessManager {
    
    private final VllmConfigProperties vllmConfig;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> logReaders = new ConcurrentHashMap<>();
    
    public CompletableFuture<Boolean> startServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
                if (serverConfig == null) {
                    log.error("Llama 3.2 server configuration not found: {}", serverName);
                    return false;
                }
                
                if (isProcessRunning(serverName)) {
                    log.info("Llama 3.2 server {} is already running", serverName);
                    return true;
                }
                
                List<String> command = buildLlamaCommand(serverConfig);
                log.info("Starting Llama 3.2 vLLM server: {} with command: {}", 
                    serverName, String.join(" ", command));
                
                ProcessBuilder pb = new ProcessBuilder(command);
                setupProcessEnvironment(pb, serverConfig);
                
                Process process = pb.start();
                runningProcesses.put(serverName, process);
                
                startLogReader(serverName, process);
                monitorProcess(serverName, process);
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to start Llama 3.2 vLLM server: {}", serverName, e);
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> stopServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process process = runningProcesses.remove(serverName);
                if (process == null) {
                    log.warn("No running Llama 3.2 process found for server: {}", serverName);
                    return true;
                }
                
                process.destroy();
                boolean terminated = process.waitFor(10, TimeUnit.SECONDS);
                
                if (!terminated) {
                    log.warn("Llama 3.2 process did not terminate gracefully, forcing shutdown: {}", serverName);
                    process.destroyForcibly();
                    terminated = process.waitFor(5, TimeUnit.SECONDS);
                }
                
                CompletableFuture<Void> logReader = logReaders.remove(serverName);
                if (logReader != null) {
                    logReader.cancel(true);
                }
                
                if (terminated) {
                    log.info("Llama 3.2 vLLM server {} stopped successfully", serverName);
                } else {
                    log.error("Failed to stop Llama 3.2 vLLM server: {}", serverName);
                }
                
                return terminated;
                
            } catch (Exception e) {
                log.error("Error stopping Llama 3.2 vLLM server: {}", serverName, e);
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> stopAllServers() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        runningProcesses.keySet().forEach(serverName -> 
            futures.add(stopServer(serverName))
        );
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }
    
    public boolean isProcessRunning(String serverName) {
        Process process = runningProcesses.get(serverName);
        return process != null && process.isAlive();
    }
    
    public List<String> getRunningServers() {
        return runningProcesses.entrySet().stream()
            .filter(entry -> entry.getValue().isAlive())
            .map(Map.Entry::getKey)
            .toList();
    }
    
    public ProcessInfo getProcessInfo(String serverName) {
        Process process = runningProcesses.get(serverName);
        if (process == null) {
            return ProcessInfo.notRunning();
        }
        
        return ProcessInfo.builder()
            .serverName(serverName)
            .pid(process.pid())
            .isAlive(process.isAlive())
            .exitCode(process.isAlive() ? null : process.exitValue())
            .build();
    }
    
    private List<String> buildLlamaCommand(VllmConfigProperties.VllmServerConfig serverConfig) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add("-m");
        command.add("vllm.entrypoints.openai.api_server");
        
        // Llama 3.2 모델 설정
        command.add("--model");
        command.add(serverConfig.getModel()); // Llama 3.2 모델 경로
        command.add("--host");
        command.add(serverConfig.getHost());
        command.add("--port");
        command.add(String.valueOf(serverConfig.getPort()));
        
        // Llama 3.2 최적화 설정
        if (serverConfig.getModelSettings() != null) {
            var modelSettings = serverConfig.getModelSettings();
            
            if (modelSettings.getMaxModelLen() != null) {
                command.add("--max-model-len");
                command.add(String.valueOf(modelSettings.getMaxModelLen()));
            }
            
            if (modelSettings.getMaxNumSeqs() != null) {
                command.add("--max-num-seqs");
                command.add(String.valueOf(modelSettings.getMaxNumSeqs()));
            }
            
            if (modelSettings.getDtype() != null) {
                command.add("--dtype");
                command.add(modelSettings.getDtype());
            }
            
            if (Boolean.TRUE.equals(modelSettings.getTrustRemoteCode())) {
                command.add("--trust-remote-code");
            }
        }
        
        // 성능 설정
        if (serverConfig.getPerformanceSettings() != null) {
            var perfSettings = serverConfig.getPerformanceSettings();
            
            if (perfSettings.getGpuMemoryUtilization() != null) {
                command.add("--gpu-memory-utilization");
                command.add(String.valueOf(perfSettings.getGpuMemoryUtilization()));
            }
            
            if (perfSettings.getTensorParallelSize() != null) {
                command.add("--tensor-parallel-size");
                command.add(String.valueOf(perfSettings.getTensorParallelSize()));
            }
            
            if (Boolean.TRUE.equals(perfSettings.getDisableLogStats())) {
                command.add("--disable-log-stats");
            }
        }
        
        return command;
    }
    
    private void setupProcessEnvironment(ProcessBuilder pb, VllmConfigProperties.VllmServerConfig serverConfig) {
        Map<String, String> env = pb.environment();
        
        // Llama 3.2 최적화를 위한 환경 변수
        env.put("VLLM_WORKER_MULTIPROC_METHOD", "spawn");
        env.put("VLLM_LOG_LEVEL", vllmConfig.getGlobalSettings().getLogLevel());
        
        pb.redirectErrorStream(true);
    }
    
    private void startLogReader(String serverName, Process process) {
        CompletableFuture<Void> logReader = CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    log.info("[Llama3.2-{}] {}", serverName, line);
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("Error reading logs for Llama 3.2 server: {}", serverName, e);
                }
            }
        });
        
        logReaders.put(serverName, logReader);
    }
    
    private void monitorProcess(String serverName, Process process) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                log.warn("Llama 3.2 vLLM server {} terminated with exit code: {}", serverName, exitCode);
                runningProcesses.remove(serverName);
                
                CompletableFuture<Void> logReader = logReaders.remove(serverName);
                if (logReader != null) {
                    logReader.cancel(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Process monitoring interrupted for Llama 3.2 server: {}", serverName);
            }
        });
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ProcessInfo {
        private String serverName;
        private Long pid;
        private Boolean isAlive;
        private Integer exitCode;
        
        public static ProcessInfo notRunning() {
            return ProcessInfo.builder()
                .isAlive(false)
                .build();
        }
    }
}