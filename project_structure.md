vllm-llama31-api/
├── src/main/java/com/yourcompany/llm/
│   ├── LlmApiApplication.java                    # 메인 애플리케이션 클래스
│   │
│   ├── config/
│   │   └── vllm/
│   │       └── VllmConfigProperties.java         # vLLM 설정 프로퍼티
│   │
│   ├── controller/
│   │   ├── LlmController.java                    # 기본 LLM API 컨트롤러
│   │   └── VllmController.java                   # vLLM 관리 API 컨트롤러
│   │
│   ├── dto/
│   │   ├── LlmRequest.java                       # 요청 DTO
│   │   └── LlmResponse.java                      # 응답 DTO
│   │
│   ├── service/
│   │   ├── LlmService.java                       # LLM 서비스 인터페이스
│   │   ├── impl/
│   │   │   └── LlmServiceImpl.java               # LLM 서비스 구현체
│   │   └── vllm/
│   │       ├── VllmApiClient.java                # vLLM API 클라이언트
│   │       ├── VllmHealthChecker.java            # vLLM 헬스 체커
│   │       ├── VllmLoadBalancer.java             # vLLM 로드 밸런서
│   │       ├── VllmMonitoringService.java        # vLLM 모니터링 서비스
│   │       └── VllmProcessManager.java           # vLLM 프로세스 매니저
│   │
│   └── [제거된 패키지들]
│       ├── entity/          # 데이터베이스 엔티티 (제거)
│       ├── repository/      # JPA 리포지토리 (제거)
│       └── util/           # 유틸리티 클래스들 (제거)
│
├── src/main/resources/
│   ├── application.yml                           # 메인 설정 파일
│   ├── application-dev.yml                       # 개발 환경 설정
│   └── application-prod.yml                      # 운영 환경 설정
│
├── docker/
│   ├── docker-compose.yml                        # Docker Compose 설정
│   ├── Dockerfile                                # 애플리케이션 Docker 이미지
│   └── start-vllm.sh                            # vLLM 서버 시작 스크립트
│
├── docs/
│   └── README.md                                 # 사용 가이드 및 API 문서
│
├── logs/                                         # 로그 디렉토리
├── models/                                       # 모델 파일 디렉토리
├── pom.xml                                       # Maven 설정
└── README.md                                     # 프로젝트 README