# vLLM Llama 3.2 API

🚀 **Meta Llama 3.2 모델을 위한 간소화된 vLLM API 서비스**

이 프로젝트는 vLLM을 사용하여 Meta Llama 3.2 모델을 효율적으로 서빙하는 REST API입니다.

## ✨ 주요 기능

- 🤖 **Llama 3.2 텍스트 생성** - 고품질 텍스트 생성 API
- 💬 **채팅 완성** - OpenAI 호환 채팅 API
- ⚖️ **로드 밸런싱** - 다중 vLLM 서버 지원
- 📊 **실시간 모니터링** - 서버 상태 및 성능 추적
- 🔄 **프로세스 관리** - vLLM 서버 자동 시작/중지
- 🚨 **알럿 시스템** - 장애 및 이상 상황 감지
- ❤️ **헬스 체크** - 서비스 가용성 모니터링

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │    │   Load Balancer │    │  vLLM Server 1  │
│                 │───▶│                 │───▶│  (Llama 3.2)    │
└─────────────────┘    │                 │    └─────────────────┘
                       │                 │    ┌─────────────────┐
┌─────────────────┐    │                 │───▶│  vLLM Server 2  │
│  Spring Boot    │───▶│                 │    │  (Llama 3.2)    │
│     API         │    └─────────────────┘    └─────────────────┘
└─────────────────┘             │
                                 ▼
                       ┌─────────────────┐
                       │   Monitoring    │
                       │   & Alerting    │
                       └─────────────────┘
```

## 🚀 빠른 시작

### 📋 사전 요구사항

- **Java 17+**
- **CUDA 지원 GPU** (8GB+ VRAM 권장)
- **Python 3.8+** 및 vLLM
- **Git LFS** (모델 다운로드용)
- **Hugging Face 계정** (Llama 3.2 액세스용)

### 2️⃣ 환경 설정

```bash
# CUDA 환경 확인
nvidia-smi

# vLLM 설치
pip install vllm

# Hugging Face 로그인 (Llama 3.2 액세스용)
huggingface-cli login
```

```bash
# 8B 모델 (권장)
huggingface-cli download torchtorchkimtorch/Llama-3.2-Korean-GGACHI-1B-Instruct-v1

### 4️⃣ 설정 파일 수정

```yaml
# src/main/resources/application.yml
vllm:
  servers:
    - name: "llama32-primary"
      model: "torchtorchkimtorch/Llama-3.2-Korean-GGACHI-1B-Instruct-v1"  # 실제 모델 경로로 수정
      host: "localhost"
      port: 8001
      enabled: true
```

### 5️⃣ 애플리케이션 실행

#### 방법 1: Maven으로 실행
```bash
# vLLM 서버 시작 (별도 터미널)
./docker/start-vllm.sh

# Spring Boot API 시작
mvn spring-boot:run
```

#### 방법 2: Docker Compose로 실행
```bash
# 전체 스택 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

### 6️⃣ API 테스트

```bash
# 헬스 체크
curl http://localhost:8080/health

# 텍스트 생성 테스트
curl -X POST http://localhost:8080/api/llm/generate \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello, Llama 3.2! Explain quantum computing.",
    "temperature": 0.7,
    "maxTokens": 200
  }'
```

## 📖 API 사용 가이드

### 🔥 기본 텍스트 생성

```bash
curl -X POST http://localhost:8080/api/llm/generate \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Write a Python function to sort a list",
    "temperature": 0.3,
    "maxTokens": 500
  }'
```

### 💬 채팅 완성 (OpenAI 호환)

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "You are a helpful coding assistant."},
      {"role": "user", "content": "How do I implement a binary search in Python?"}
    ],
    "temperature": 0.2,
    "maxTokens": 800
  }'
```

### 🎯 시스템 프롬프트 활용

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explain machine learning",
    "systemPrompt": "You are an expert data scientist. Explain concepts clearly with examples.",
    "temperature": 0.5,
    "maxTokens": 600
  }'
```

```

## ⚙️ 설정 가이드

### 🎛️ 모델 설정

```yaml
vllm:
  servers:
    - name: "llama32-1b"
      model: "torchtorchkimtorch/Llama-3.2-Korean-GGACHI-1B-Instruct-v1"
      host: "localhost"
      port: 8001
      enabled: true
      model-settings:
        max-model-len: 8192        # 컨텍스트 길이 (토큰)
        max-num-seqs: 256          # 동시 처리 시퀀스 수
        dtype: "auto"              # 자동 정밀도 선택
        trust-remote-code: false   # 보안을 위해 false 권장
      performance-settings:
        gpu-memory-utilization: 0.9    # GPU 메모리 90% 사용
        tensor-parallel-size: 1        # 단일 GPU용
        disable-log-stats: false       # 통계 로깅 활성화
```

### 🔧 성능 최적화

#### 단일 GPU (RTX 4090, A100 등)
```yaml
performance-settings:
  gpu-memory-utilization: 0.9
  tensor-parallel-size: 1
  max-num-seqs: 256
```

#### 멀티 GPU (2x A100 등)
```yaml
performance-settings:
  gpu-memory-utilization: 0.95
  tensor-parallel-size: 2
  max-num-seqs: 512
```

#### 메모리 제약 환경 (RTX 3080 등)
```yaml
performance-settings:
  gpu-memory-utilization: 0.7
  tensor-parallel-size: 1
  max-num-seqs: 128
model-settings:
  max-model-len: 4096  # 컨텍스트 길이 줄임
```

### 🚨 알럿 설정

```yaml
# 기본 알럿 규칙 (자동 설정됨)
- server_down: vLLM 서버 다운 감지
- high_load: 동시 요청 50개 초과
- memory_usage: GPU 메모리 95% 초과
```

## 📊 모니터링

### 📈 메트릭 수집

Spring Boot Actuator를 통해 다음 메트릭들을 제공합니다:

```bash
# 모든 메트릭 조회
curl http://localhost:8080/actuator/metrics

# HTTP 요청 메트릭
curl http://localhost:8080/actuator/metrics/http.server.requests

# JVM 메모리 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### 🔍 로그 모니터링

```bash
# 애플리케이션 로그
tail -f logs/vllm-llama32-api.log

# vLLM 서버 로그 (Docker)
docker logs -f vllm-server

# 실시간 GPU 모니터링
nvidia-smi -l 1
```

## 🐳 Docker 배포

### 전체 스택 배포
```bash
# 프로덕션 환경
docker-compose -f docker-compose.prod.yml up -d

# 개발 환경
docker-compose up -d
```

### 개별 서비스 배포
```bash
# API 서버만
docker build -t vllm-llama32-api .
docker run -p 8080:8080 vllm-llama32-api

# vLLM 서버만
docker run --gpus all -p 8001:8001 \
  -v ./models:/models \
  vllm/vllm-openai:latest \
  --model meta-llama/Meta-Llama-3.2-1B-Instruct \
  --host 0.0.0.0 --port 8001
```

## 🛠️ 개발 가이드

### 📁 프로젝트 구조

```
src/main/java/com/yourcompany/llm/
├── LlmApiApplication.java              # 메인 애플리케이션
├── config/vllm/
│   └── VllmConfigProperties.java       # vLLM 설정
├── controller/
│   ├── LlmController.java              # 기본 LLM API
│   └── VllmController.java             # vLLM 관리 API
├── dto/
│   ├── LlmRequest.java                 # 요청 DTO
│   └── LlmResponse.java                # 응답 DTO
├── service/
│   ├── LlmService.java                 # 서비스 인터페이스
│   ├── impl/LlmServiceImpl.java        # 서비스 구현
│   └── vllm/                           # vLLM 전용 서비스들
│       ├── VllmApiClient.java          # vLLM HTTP 클라이언트
│       ├── VllmHealthChecker.java      # 헬스 체크
│       ├── VllmLoadBalancer.java       # 로드 밸런싱
│       ├── VllmMonitoringService.java  # 모니터링
│       └── VllmProcessManager.java     # 프로세스 관리
```

### 🔨 빌드 및 테스트

```bash
# 프로젝트 빌드
mvn clean package

# 테스트 실행
mvn test

# 통합 테스트 (vLLM 서버 필요)
mvn integration-test
```

### 🐛 디버깅

```bash
# 디버그 모드로 실행
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# 개발 프로파일로 실행
mvn spring-boot:run -Dspring.profiles.active=dev
```

## 🚨 트러블슈팅

### ❓ 자주 발생하는 문제들

#### 1. vLLM 서버가 시작되지 않음
```bash
# 원인 확인
nvidia-smi                    # GPU 상태 확인
docker logs vllm-server      # vLLM 로그 확인
df -h                        # 디스크 공간 확인

# 해결방법
# GPU 메모리 사용률 줄이기
gpu-memory-utilization: 0.7  # 0.9 → 0.7
```

#### 2. 모델 로딩 실패
```bash
# Hugging Face 인증 확인
huggingface-cli whoami

# 모델 경로 확인
ls -la ~/.cache/huggingface/hub/

# 수동 다운로드
huggingface-cli download torchtorchkimtorch/Llama-3.2-Korean-GGACHI-1B-Instruct-v1
```

#### 3. 메모리 부족 (CUDA OOM)
```yaml
# application.yml 수정
performance-settings:
  gpu-memory-utilization: 0.6  # 낮춤
  max-num-seqs: 64             # 배치 크기 줄임
model-settings:
  max-model-len: 4096          # 컨텍스트 길이 줄임
```

#### 4. API 응답 느림
```bash
# GPU 사용률 확인
nvidia-smi

# 네트워크 지연 확인
curl -w "@curl-format.txt" http://localhost:8080/api/llm/health

# 스레드 풀 크기 조정 (application.yml)
server:
  tomcat:
    threads:
      max: 200
```