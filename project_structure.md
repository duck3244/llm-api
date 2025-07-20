# LLM API 프로젝트 구조

```
llm-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── yourcompany/
│   │   │           └── llm/
│   │   │               ├── LlmApiApplication.java                 # 메인 애플리케이션 클래스
│   │   │               │
│   │   │               ├── config/                              # 설정 클래스들
│   │   │               │   ├── LlmConfiguration.java            # LLM 기본 설정
│   │   │               │   ├── RedisConfig.java                 # Redis 설정
│   │   │               │   ├── LlmConfigProperties.java         # LLM 설정 프로퍼티
│   │   │               │   └── vllm/                           # vLLM 전용 설정
│   │   │               │       └── VllmConfigProperties.java    # vLLM 상세 설정
│   │   │               │
│   │   │               ├── controller/                         # REST 컨트롤러들
│   │   │               │   ├── LlmController.java              # 메인 LLM API 컨트롤러
│   │   │               │   ├── VllmController.java             # vLLM 전용 컨트롤러
│   │   │               │   └── StatsController.java            # 통계 API 컨트롤러
│   │   │               │
│   │   │               ├── dto/                                # 데이터 전송 객체들
│   │   │               │   ├── LlmRequest.java                 # LLM 요청 DTO
│   │   │               │   ├── LlmResponse.java                # LLM 응답 DTO
│   │   │               │   └── Message.java                    # 메시지 DTO
│   │   │               │
│   │   │               ├── entity/                             # JPA 엔티티들
│   │   │               │   ├── UsageEntity.java                # 사용량 엔티티
│   │   │               │   └── LogEntity.java                  # 로그 엔티티
│   │   │               │
│   │   │               ├── repository/                         # JPA 리포지토리들
│   │   │               │   ├── UsageRepository.java            # 사용량 리포지토리
│   │   │               │   └── LogRepository.java              # 로그 리포지토리
│   │   │               │
│   │   │               ├── service/                            # 비즈니스 로직 서비스들
│   │   │               │   ├── LlmService.java                 # 메인 LLM 서비스
│   │   │               │   ├── EnhancedLlmService.java         # 캐싱 및 사용량 추적 포함 서비스
│   │   │               │   ├── CacheService.java               # 캐싱 서비스
│   │   │               │   ├── LlmUsageService.java            # 사용량 추적 서비스
│   │   │               │   └── vllm/                          # vLLM 전용 서비스들
│   │   │               │       ├── VllmProcessManager.java     # vLLM 프로세스 관리
│   │   │               │       ├── VllmHealthChecker.java      # vLLM 헬스 체크
│   │   │               │       ├── VllmApiClient.java          # vLLM API 클라이언트
│   │   │               │       ├── VllmLoadBalancer.java       # vLLM 로드 밸런서
│   │   │               │       └── VllmMonitoringService.java  # vLLM 모니터링
│   │   │               │
│   │   │               ├── exception/                          # 예외 처리
│   │   │               │   ├── LlmException.java               # LLM 관련 예외
│   │   │               │   ├── VllmException.java              # vLLM 관련 예외
│   │   │               │   └── GlobalExceptionHandler.java     # 글로벌 예외 핸들러
│   │   │               │
│   │   │               └── util/                               # 유틸리티 클래스들
│   │   │                   ├── ModelUtils.java                # 모델 관련 유틸
│   │   │                   ├── ValidationUtils.java           # 검증 유틸
│   │   │                   └── CacheUtils.java                # 캐시 유틸
│   │   │
│   │   └── resources/                                          # 리소스 파일들
│   │       ├── application.yml                                 # 메인 설정 파일
│   │       ├── application-dev.yml                             # 개발 환경 설정
│   │       ├── application-prod.yml                            # 프로덕션 환경 설정
│   │       ├── application-test.yml                            # 테스트 환경 설정
│   │       ├── application-vllm.yml                            # vLLM 전용 설정
│   │       ├── logback-spring.xml                              # 로깅 설정
│   │       └── static/                                         # 정적 파일들
│   │           └── dashboard/                                  # 대시보드 웹 파일들
│   │               ├── index.html                              # 대시보드 메인 페이지
│   │               ├── css/
│   │               │   └── dashboard.css                       # 대시보드 스타일
│   │               └── js/
│   │                   └── dashboard.js                        # 대시보드 JavaScript
│   │
│   └── test/                                                   # 테스트 코드
│       └── java/
│           └── com/
│               └── yourcompany/
│                   └── llm/
│                       ├── LlmApiApplicationTests.java         # 애플리케이션 테스트
│                       ├── controller/                         # 컨트롤러 테스트
│                       │   ├── LlmControllerTest.java          # LLM 컨트롤러 테스트
│                       │   └── VllmControllerTest.java         # vLLM 컨트롤러 테스트
│                       ├── service/                            # 서비스 테스트
│                       │   ├── LlmServiceTest.java             # LLM 서비스 테스트
│                       │   └── vllm/                          # vLLM 서비스 테스트들
│                       │       ├── VllmProcessManagerTest.java
│                       │       ├── VllmHealthCheckerTest.java
│                       │       └── VllmLoadBalancerTest.java
│                       └── integration/                        # 통합 테스트
│                           ├── LlmApiIntegrationTest.java      # API 통합 테스트
│                           └── VllmIntegrationTest.java        # vLLM 통합 테스트
│
├── docker/                                                     # Docker 관련 파일들
│   ├── Dockerfile                                              # Spring Boot 애플리케이션 도커파일
│   ├── docker-compose.yml                                      # 전체 스택 Docker Compose
│   ├── docker-compose.dev.yml                                  # 개발용 Docker Compose
│   ├── docker-compose.prod.yml                                 # 프로덕션용 Docker Compose
│   ├── nginx/                                                  # Nginx 설정
│   │   ├── nginx.conf                                          # Nginx 메인 설정
│   │   └── ssl/                                               # SSL 인증서
│   │       ├── cert.pem
│   │       └── key.pem
│   ├── grafana/                                               # Grafana 설정
│   │   ├── dashboards/                                        # 대시보드 정의
│   │   │   ├── llm-overview.json                             # LLM 개요 대시보드
│   │   │   └── vllm-performance.json                         # vLLM 성능 대시보드
│   │   └── datasources/                                       # 데이터 소스 설정
│   │       └── prometheus.yml                                # Prometheus 데이터 소스
│   └── prometheus/                                            # Prometheus 설정
│       └── prometheus.yml                                     # Prometheus 설정 파일
│
├── scripts/                                                   # 스크립트 파일들
│   ├── setup-llm-api.sh                                      # 전체 환경 설정 스크립트
│   ├── vllm-setup.sh                                         # vLLM 설치 및 설정
│   ├── development-setup.sh                                  # 개발 환경 설정
│   ├── start_llama3.sh                                       # Llama3 서버 시작
│   ├── start_mistral.sh                                      # Mistral 서버 시작
│   ├── start_codellama.sh                                    # CodeLlama 서버 시작
│   ├── stop_servers.sh                                       # 모든 서버 중지
│   ├── check_servers.sh                                      # 서버 상태 확인
│   ├── test_vllm.sh                                          # vLLM API 테스트
│   └── backup.sh                                             # 데이터 백업 스크립트
│
├── docs/                                                      # 문서
│   ├── README.md                                              # 프로젝트 개요
│   ├── API.md                                                 # API 문서
│   ├── SETUP.md                                               # 설치 가이드
│   ├── DEPLOYMENT.md                                          # 배포 가이드
│   ├── TROUBLESHOOTING.md                                     # 문제 해결 가이드
│   ├── CONFIGURATION.md                                       # 설정 가이드
│   └── architecture/                                          # 아키텍처 문서
│       ├── overview.md                                        # 전체 개요
│       ├── vllm-architecture.md                             # vLLM 아키텍처
│       └── monitoring.md                                     # 모니터링 가이드
│
├── configs/                                                   # 환경별 설정 파일들
│   ├── .env.example                                          # 환경 변수 예시
│   ├── application-local.yml                                 # 로컬 개발용 설정
│   ├── application-docker.yml                                # Docker 환경 설정
│   └── systemd/                                              # systemd 서비스 파일들
│       ├── vllm-llama3.service                              # Llama3 서비스
│       ├── vllm-mistral.service                             # Mistral 서비스
│       └── llm-api.service                                   # LLM API 서비스
│
├── monitoring/                                               # 모니터링 관련 파일들
│   ├── alerts/                                               # 알럿 규칙
│   │   ├── llm-alerts.yml                                    # LLM 알럿 규칙
│   │   └── vllm-alerts.yml                                   # vLLM 알럿 규칙
│   ├── dashboards/                                           # 추가 대시보드
│   │   ├── system-overview.json                             # 시스템 개요
│   │   └── api-performance.json                             # API 성능
│   └── logs/                                                 # 로그 설정
│       └── logstash.conf                                     # Logstash 설정
│
├── tests/                                                     # 추가 테스트 파일들
│   ├── api/                                                  # API 테스트
│   │   ├── test-requests.http                                # HTTP 요청 테스트
│   │   └── postman/                                          # Postman 컬렉션
│   │       ├── LLM-API.postman_collection.json              # API 테스트 컬렉션
│   │       └── environments/                                 # 환경 설정
│   │           ├── dev.postman_environment.json             # 개발 환경
│   │           └── prod.postman_environment.json            # 프로덕션 환경
│   ├── performance/                                          # 성능 테스트
│   │   ├── load-test.js                                      # 부하 테스트 (K6)
│   │   └── stress-test.py                                    # 스트레스 테스트 (Python)
│   └── integration/                                          # 통합 테스트
│       ├── docker-test.sh                                    # Docker 통합 테스트
│       └── e2e-test.py                                       # End-to-End 테스트
│
├── .github/                                                  # GitHub Actions
│   └── workflows/                                            # CI/CD 워크플로우
│       ├── ci.yml                                            # 지속적 통합
│       ├── cd.yml                                            # 지속적 배포
│       └── security.yml                                      # 보안 스캔
│
├── .vscode/                                                  # VSCode 설정
│   ├── settings.json                                         # VSCode 설정
│   ├── launch.json                                           # 디버그 설정
│   ├── tasks.json                                            # 작업 설정
│   └── extensions.json                                       # 권장 확장 프로그램
│
├── .idea/                                                    # IntelliJ IDEA 설정 (선택사항)
│   ├── runConfigurations/                                    # 실행 설정
│   └── codeStyles/                                           # 코드 스타일
│
├── target/                                                   # Maven 빌드 결과 (gitignore)
├── logs/                                                     # 애플리케이션 로그 (gitignore)
├── vllm-env/                                                # vLLM Python 가상환경 (gitignore)
├── .env                                                      # 환경 변수 (gitignore)
├── pom.xml                                                   # Maven 프로젝트 설정
├── .gitignore                                               # Git 무시 파일
├── README.md                                                # 프로젝트 README
├── LICENSE                                                  # 라이센스
└── CHANGELOG.md                                             # 변경 로그
```

## 📋 주요 디렉토리 설명

### **🔧 핵심 소스 코드**
- **`src/main/java/`**: 메인 애플리케이션 코드
  - **`controller/`**: REST API 엔드포인트
  - **`service/`**: 비즈니스 로직 및 외부 API 호출
  - **`config/`**: Spring 설정 및 프로퍼티
  - **`dto/`**: 데이터 전송 객체
  - **`entity/`**: JPA 엔티티

### **🐳 인프라 및 배포**
- **`docker/`**: 컨테이너화 관련 파일들
- **`scripts/`**: 자동화 스크립트들
- **`configs/`**: 환경별 설정 파일들

### **📊 모니터링 및 관측성**
- **`monitoring/`**: Grafana, Prometheus 설정
- **`logs/`**: 로그 파일들 (런타임 생성)

### **🧪 테스트**
- **`src/test/`**: 단위 테스트 및 통합 테스트
- **`tests/`**: API 테스트, 성능 테스트

### **📚 문서화**
- **`docs/`**: 프로젝트 문서들
- **API 문서**: REST API 명세서
- **아키텍처 문서**: 시스템 설계 문서

### **⚙️ IDE 설정**
- **`.vscode/`**: VSCode 프로젝트 설정
- **`.idea/`**: IntelliJ IDEA 설정

### **🔄 CI/CD**
- **`.github/workflows/`**: GitHub Actions 워크플로우

## 🎯 **프로젝트 구조의 특징**

### **1. 계층화된 아키텍처**
```
Controller → Service → Repository → Database
          ↓
       External APIs (vLLM, Ollama, etc.)
```

### **2. 기능별 모듈화**
- **vLLM 모듈**: `service/vllm/` 패키지에 독립적으로 구성
- **캐싱 모듈**: Redis 기반 캐싱 서비스
- **모니터링 모듈**: 메트릭 수집 및 알럿

### **3. 환경별 설정 분리**
- **개발**: `application-dev.yml`
- **테스트**: `application-test.yml`
- **프로덕션**: `application-prod.yml`
- **vLLM 전용**: `application-vllm.yml`

### **4. 완전한 DevOps 지원**
- **Docker**: 전체 스택 컨테이너화
- **모니터링**: Grafana + Prometheus
- **CI/CD**: GitHub Actions
- **테스트**: 단위/통합/성능 테스트

이 구조는 **확장성**, **유지보수성**, **테스트 가능성**을 모두 고려하여 설계된 엔터프라이즈급 프로젝트 구조입니다! 🚀