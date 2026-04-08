#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/docker/local-infra"
BACKEND_DIR="$ROOT_DIR/orin-backend"
AI_ENGINE_DIR="$ROOT_DIR/orin-ai-engine"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker-compose)
else
  echo "docker compose is required"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not running. Start Docker Desktop (or dockerd) and rerun this script."
  exit 1
fi

echo "[1/5] Ensure local infra env exists"
if [[ ! -f "$INFRA_DIR/.env" ]]; then
  cp "$INFRA_DIR/.env.example" "$INFRA_DIR/.env"
  echo "Created $INFRA_DIR/.env from example"
fi

echo "[2/5] Start RabbitMQ queue profile"
(
  cd "$INFRA_DIR"
  "${DOCKER_COMPOSE[@]}" --profile queue up -d rabbitmq
)

echo "[3/5] Verify backend collaboration tests"
(
  cd "$BACKEND_DIR"
  mvn -q -DskipTests compile
  mvn -q -Dtest=CollaborationOrchestratorTest,CollaborationMqIsolationTest,CollaborationOrchestratorControllerTest test
)

echo "[4/5] Run AI engine MQ worker smoke in a disposable Python 3.11 container"
docker run --rm \
  -v "$AI_ENGINE_DIR:/app" \
  -w /app \
  python:3.11-slim \
  sh -lc '
    pip install -q pytest pytest-asyncio fastapi uvicorn httpx aio-pika redis pydantic-settings &&
    PYTHONPATH=/app python -m pytest tests/test_mq_worker.py -q
  '

echo "[5/5] Smoke finished"
echo "RabbitMQ management UI: http://localhost:15672"
echo "Backend env: copy $BACKEND_DIR/.env.example -> $BACKEND_DIR/.env"
echo "AI engine env: copy $AI_ENGINE_DIR/.env.example -> $AI_ENGINE_DIR/.env"
