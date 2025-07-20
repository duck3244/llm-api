# Dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

# 애플리케이션 JAR 복사
COPY target/vllm-llama31-api-1.0.0.jar app.jar

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/llm/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]