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
    
    /**
     * vLLM 서버 프로세스 시작
     */
    public CompletableFuture<Boolean> startServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                VllmConfigProperties.VllmServerConfig serverConfig = vllmConfig.getServerByName(serverName);
                if (serverConfig == null) {
                    log.error("Server configuration not found: {}", serverName);
                    return false;
                }
                
                if (isProcessRunning(serverName)) {
                    log.info("Server {} is already running", serverName);
                    return true;
                }
                
                List<String> command = buildCommand(serverConfig);
                log.info("Starting vLLM server: {} with command: {}", serverName, String.join(" ", command));
                
                ProcessBuilder pb = new ProcessBuilder(command);
                setupProcessEnvironment(pb, serverConfig);
                
                Process process = pb.start();
                runningProcesses.put(serverName, process);
                
                // 로그 리더 시작
                startLogReader(serverName, process);
                
                // 프로세스 종료 모니터링
                monitorProcess(serverName, process);
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to start vLLM server: {}", serverName, e);
                return false;
            }
        });
    }
    
    /**
     * vLLM 서버 프로세스 중지
     */
    public CompletableFuture<Boolean> stopServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process process = runningProcesses.remove(serverName);
                if (process == null) {
                    log.warn("No running process found for server: {}", serverName);
                    return true;
                }
                
                // Graceful shutdown 시도
                process.destroy();
                boolean terminated = process.waitFor(10, TimeUnit.SECONDS);
                
                if (!terminated) {
                    log.warn("Process did not terminate gracefully, forcing shutdown: {}", serverName);
                    process.destroyForcibly();
                    terminated = process.waitFor(5, TimeUnit.SECONDS);
                }
                
                // 로그 리더 중지
                CompletableFuture<Void> logReader = logReaders.remove(serverName);
                if (logReader != null) {
                    logReader.cancel(true);
                }
                
                if (terminated) {
                    log.info("vLLM server {} stopped successfully", serverName);
                } else {
                    log.error("Failed to stop vLLM server: {}", serverName);
                }
                
                return terminated;
                
            } catch (Exception e) {
                log.error("Error stopping vLLM server: {}", serverName, e);
                return false;
            }
        });
    }
    
    /**
     * 모든 서버 중지
     */
    public CompletableFuture<Boolean> stopAllServers() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        runningProcesses.keySet().forEach(serverName -> 
            futures.add(stopServer(serverName))
        );
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }
    
    /**
     * 프로세스 실행 상태 확인
     */
    public boolean isProcessRunning(String serverName) {
        Process process = runningProcesses.get(serverName);
        return process != null && process.isAlive();
    }
    
    /**
     * 실행 중인 서버 목록 반환
     */
    public List<String> getRunningServers() {
        return runningProcesses.entrySet().stream()
            .filter(entry -> entry.getValue().isAlive())
            .map(Map.Entry::getKey)
            .toList();
    }
    
    /**
     * 서버 프로세스 정보 반환
     */
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
    
    private List<String> buildCommand(VllmConfigProperties.VllmServerConfig serverConfig) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add("-m");
        command.add("vllm.entrypoints.openai.api_server");
        
        // 기본 파라미터
        command.add("--model");
        command.add(serverConfig.getModel());
        command.add("--host");
        command.add(serverConfig.getHost());
        command.add("--port");
        command.add(String.valueOf(serverConfig.getPort()));
        
        // 모델 설정
        addModelSettings(command, serverConfig.getModelSettings());
        
        // 성능 설정
        addPerformanceSettings(command, serverConfig.getPerformanceSettings());
        
        // 양자화 설정
        addQuantizationSettings(command, serverConfig.getQuantizationSettings());
        
        // 글로벌 설정
        addGlobalSettings(command, vllmConfig.getGlobalSettings());
        
        // 리소스 설정
        addResourceSettings(command, vllmConfig.getResourceSettings());
        
        return command;
    }
    
    private void addModelSettings(List<String> command, VllmConfigProperties.VllmModelSettings settings) {
        if (settings == null) return;
        
        if (settings.getMaxModelLen() != null) {
            command.add("--max-model-len");
            command.add(String.valueOf(settings.getMaxModelLen()));
        }
        
        if (settings.getMaxNumSeqs() != null) {
            command.add("--max-num-seqs");
            command.add(String.valueOf(settings.getMaxNumSeqs()));
        }
        
        if (settings.getDtype() != null) {
            command.add("--dtype");
            command.add(settings.getDtype());
        }
        
        if (settings.getTrustRemoteCode() != null && settings.getTrustRemoteCode()) {
            command.add("--trust-remote-code");
        }
        
        if (settings.getRevision() != null) {
            command.add("--revision");
            command.add(settings.getRevision());
        }
        
        if (settings.getTokenizer() != null) {
            command.add("--tokenizer");
            command.add(settings.getTokenizer());
        }
    }
    
    private void addPerformanceSettings(List<String> command, VllmConfigProperties.VllmPerformanceSettings settings) {
        if (settings == null) return;
        
        if (settings.getGpuMemoryUtilization() != null) {
            command.add("--gpu-memory-utilization");
            command.add(String.valueOf(settings.getGpuMemoryUtilization()));
        }
        
        if (settings.getTensorParallelSize() != null) {
            command.add("--tensor-parallel-size");
            command.add(String.valueOf(settings.getTensorParallelSize()));
        }
        
        if (settings.getPipelineParallelSize() != null) {
            command.add("--pipeline-parallel-size");
            command.add(String.valueOf(settings.getPipelineParallelSize()));
        }
        
        if (settings.getBlockSize() != null) {
            command.add("--block-size");
            command.add(String.valueOf(settings.getBlockSize()));
        }
        
        if (settings.getDisableLogStats() != null && settings.getDisableLogStats()) {
            command.add("--disable-log-stats");
        }
    }
    
    private void addQuantizationSettings(List<String> command, VllmConfigProperties.VllmQuantizationSettings settings) {
        if (settings == null) return;
        
        if (settings.getQuantization() != null) {
            command.add("--quantization");
            command.add(settings.getQuantization());
        }
        
        if (settings.getLoadFormat() != null) {
            command.add("--load-format");
            command.add(settings.getLoadFormat());
        }
        
        if (settings.getEnforceEager() != null && settings.getEnforceEager()) {
            command.add("--enforce-eager");
        }
    }
    
    private void addGlobalSettings(List<String> command, VllmConfigProperties.VllmGlobalSettings settings) {
        if (settings == null) return;
        
        if (settings.getSeed() != null) {
            command.add("--seed");
            command.add(String.valueOf(settings.getSeed()));
        }
        
        if (settings.getDisableLogRequests() != null && settings.getDisableLogRequests()) {
            command.add("--disable-log-requests");
        }
    }
    
    private void addResourceSettings(List<String> command, VllmConfigProperties.VllmResourceSettings settings) {
        if (settings == null) return;
        
        if (settings.getDevice() != null) {
            command.add("--device");
            command.add(settings.getDevice());
        }
    }
    
    private void setupProcessEnvironment(ProcessBuilder pb, VllmConfigProperties.VllmServerConfig serverConfig) {
        Map<String, String> env = pb.environment();
        
        // CUDA 설정
        if (vllmConfig.getResourceSettings() != null) {
            if (vllmConfig.getResourceSettings().getGpuIds() != null) {
                String gpuIds = vllmConfig.getResourceSettings().getGpuIds().stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
                env.put("CUDA_VISIBLE_DEVICES", gpuIds);
            }
        }
        
        // 로깅 레벨 설정
        if (vllmConfig.getGlobalSettings() != null && vllmConfig.getGlobalSettings().getLogLevel() != null) {
            env.put("VLLM_LOG_LEVEL", vllmConfig.getGlobalSettings().getLogLevel());
        }
        
        pb.redirectErrorStream(true);
    }
    
    private void startLogReader(String serverName, Process process) {
        CompletableFuture<Void> logReader = CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    log.info("[{}] {}", serverName, line);
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("Error reading logs for server: {}", serverName, e);
                }
            }
        });
        
        logReaders.put(serverName, logReader);
    }
    
    private void monitorProcess(String serverName, Process process) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                log.warn("vLLM server {} terminated with exit code: {}", serverName, exitCode);
                runningProcesses.remove(serverName);
                
                CompletableFuture<Void> logReader = logReaders.remove(serverName);
                if (logReader != null) {
                    logReader.cancel(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Process monitoring interrupted for server: {}", serverName);
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