# start-vllm.sh (로컬 개발용 스크립트)
#!/bin/bash

echo "Starting vLLM Llama 3.2 Server..."

# 모델 경로 설정 (실제 경로로 수정 필요)
MODEL_PATH="/home/duck/PycharmProjects/snapshot_model/huggingface/models/torchtorchkimtorch-Llama-3.2-Korean-GGACHI-1B-Instruct-v1"

# vLLM 서버 시작
python -m vllm.entrypoints.openai.api_server \
  --model $MODEL_PATH \
  --host localhost \
  --port 8080 \
  --max-model-len 4096 \
  --max-num-seqs 64 \
  --gpu-memory-utilization 0.6 \
  --dtype auto \
  --disable-log-stats
