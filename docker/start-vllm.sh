# start-vllm.sh (로컬 개발용 스크립트)
#!/bin/bash

echo "Starting vLLM Llama 3.1 Server..."

# 모델 경로 설정 (실제 경로로 수정 필요)
MODEL_PATH="meta-llama/Meta-Llama-3.1-8B-Instruct"

# vLLM 서버 시작
python -m vllm.entrypoints.openai.api_server \
  --model $MODEL_PATH \
  --host localhost \
  --port 8001 \
  --max-model-len 8192 \
  --max-num-seqs 256 \
  --gpu-memory-utilization 0.9 \
  --dtype auto \
  --disable-log-stats