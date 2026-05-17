#!/bin/bash
# ORIN Smoke Test Script
# 本机最小闭环 + 可选 live smoke；不是完整 E2E。
# 用于快速验证 ORIN 三端可运行性，并脱敏展示当前本机运行态。

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
FAILED=0

classify_host() {
    local host="$1"
    if [ -z "$host" ]; then
        echo "unset"
    elif [ "$host" = "localhost" ] || [ "$host" = "127.0.0.1" ] || [ "$host" = "::1" ]; then
        echo "local-loopback"
    elif [[ "$host" =~ ^10\. ]] || [[ "$host" =~ ^192\.168\. ]] || [[ "$host" =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ]]; then
        echo "private-lan-or-vpn"
    elif [[ "$host" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "public-ip-or-non-rfc1918"
    else
        echo "hostname-or-domain"
    fi
}

read_env_value() {
    local file="$1"
    local key="$2"
    if [ ! -f "$file" ]; then
        return 0
    fi
    grep -E "^${key}=" "$file" | tail -1 | cut -d= -f2- | sed "s/^['\"]//;s/['\"]$//"
}

env_set_state() {
    local value="$1"
    if [ -n "$value" ] && [[ "$value" != replace_with* ]]; then
        echo "set"
    else
        echo "unset/example"
    fi
}

port_state() {
    local value="$1"
    if [ -n "$value" ]; then
        echo "$value"
    else
        echo "unset"
    fi
}

endpoint_host_from_url() {
    python3 - "$1" <<'PY'
from urllib.parse import urlparse
import sys
value = sys.argv[1]
if "://" not in value:
    print(value)
else:
    print(urlparse(value).hostname or "")
PY
}

echo -e "${YELLOW}=== ORIN Smoke Test ===${NC}"
echo ""

# 0. 当前本机运行态摘要（不输出 .env 明文）
echo -e "${YELLOW}[0/6] Runtime Baseline (redacted)...${NC}"
if command -v lsof >/dev/null 2>&1; then
    LISTENING=$(lsof -nP -iTCP:5173 -iTCP:8080 -iTCP:8000 -iTCP:6379 -iTCP:3306 -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {print "  " $1 " pid=" $2 " " $9}' || true)
    if [ -n "$LISTENING" ]; then
        echo "$LISTENING"
    else
        echo "  No target ports currently listening"
    fi
else
    echo "  lsof not available, skipped process source check"
fi

BACKEND_ENV="$ORIN_ROOT/orin-backend/.env"
ROOT_ENV="$ORIN_ROOT/.env"
if [ -f "$BACKEND_ENV" ]; then
    DB_HOST_VALUE="$(read_env_value "$BACKEND_ENV" DB_HOST)"
    DB_PORT_VALUE="$(read_env_value "$BACKEND_ENV" DB_PORT)"
    REDIS_HOST_VALUE="$(read_env_value "$BACKEND_ENV" REDIS_HOST)"
    REDIS_PORT_VALUE="$(read_env_value "$BACKEND_ENV" REDIS_PORT)"
    RABBITMQ_HOST_VALUE="$(read_env_value "$BACKEND_ENV" RABBITMQ_HOST)"
    RABBITMQ_PORT_VALUE="$(read_env_value "$BACKEND_ENV" RABBITMQ_PORT)"
    MILVUS_HOST_VALUE="$(read_env_value "$BACKEND_ENV" MILVUS_HOST)"
    MILVUS_PORT_VALUE="$(read_env_value "$BACKEND_ENV" MILVUS_PORT)"
    DIFY_ENDPOINT_VALUE="$(read_env_value "$BACKEND_ENV" DIFY_DEFAULT_ENDPOINT)"
    RAGFLOW_ENDPOINT_VALUE="$(read_env_value "$BACKEND_ENV" RAGFLOW_DEFAULT_ENDPOINT)"
    SILICONFLOW_KEY_VALUE="$(read_env_value "$BACKEND_ENV" SILICONFLOW_API_KEY)"
    echo "  backend .env DB_HOST: $(classify_host "$DB_HOST_VALUE")"
    echo "  backend .env DB_PORT: $(port_state "$DB_PORT_VALUE")"
    echo "  backend .env REDIS_HOST: $(classify_host "$REDIS_HOST_VALUE")"
    echo "  backend .env REDIS_PORT: $(port_state "$REDIS_PORT_VALUE")"
    echo "  backend .env RABBITMQ_HOST: $(classify_host "$RABBITMQ_HOST_VALUE")"
    echo "  backend .env RABBITMQ_PORT: $(port_state "$RABBITMQ_PORT_VALUE")"
    echo "  backend .env MILVUS_HOST: $(classify_host "$MILVUS_HOST_VALUE")"
    echo "  backend .env MILVUS_PORT: $(port_state "$MILVUS_PORT_VALUE")"
    echo "  backend .env DIFY_DEFAULT_ENDPOINT: $(classify_host "$(endpoint_host_from_url "$DIFY_ENDPOINT_VALUE")")"
    echo "  backend .env RAGFLOW_DEFAULT_ENDPOINT: $(classify_host "$(endpoint_host_from_url "$RAGFLOW_ENDPOINT_VALUE")")"
    echo "  backend .env SILICONFLOW_API_KEY: $(env_set_state "$SILICONFLOW_KEY_VALUE")"
elif [ -f "$ROOT_ENV" ]; then
    MYSQL_HOST_VALUE="$(read_env_value "$ROOT_ENV" MYSQL_HOST)"
    MYSQL_PORT_VALUE="$(read_env_value "$ROOT_ENV" MYSQL_PORT)"
    REDIS_HOST_VALUE="$(read_env_value "$ROOT_ENV" REDIS_HOST)"
    REDIS_PORT_VALUE="$(read_env_value "$ROOT_ENV" REDIS_PORT)"
    MILVUS_HOST_VALUE="$(read_env_value "$ROOT_ENV" MILVUS_HOST)"
    MILVUS_PORT_VALUE="$(read_env_value "$ROOT_ENV" MILVUS_PORT)"
    SILICONFLOW_KEY_VALUE="$(read_env_value "$ROOT_ENV" SILICONFLOW_API_KEY)"
    echo "  root .env MYSQL_HOST: $(classify_host "$MYSQL_HOST_VALUE")"
    echo "  root .env MYSQL_PORT: $(port_state "$MYSQL_PORT_VALUE")"
    echo "  root .env REDIS_HOST: $(classify_host "$REDIS_HOST_VALUE")"
    echo "  root .env REDIS_PORT: $(port_state "$REDIS_PORT_VALUE")"
    echo "  root .env MILVUS_HOST: $(classify_host "$MILVUS_HOST_VALUE")"
    echo "  root .env MILVUS_PORT: $(port_state "$MILVUS_PORT_VALUE")"
    echo "  root .env SILICONFLOW_API_KEY: $(env_set_state "$SILICONFLOW_KEY_VALUE")"
else
    echo "  No .env file found; runtime services must come from shell environment or defaults"
fi

if command -v docker >/dev/null 2>&1; then
    echo "  docker: available"
else
    echo "  docker: unavailable"
fi

# 1. Java后端编译
echo -e "${YELLOW}[1/6] Testing Java Backend Compile...${NC}"
cd "$ORIN_ROOT/orin-backend"

if mvn -q -DskipTests compile -o 2>/dev/null; then
    echo -e "${GREEN}✓ Backend compile OK${NC}"
else
    if mvn -q -DskipTests compile; then
        echo -e "${GREEN}✓ Backend compile OK (with network)${NC}"
    else
        echo -e "${RED}✗ Backend compile failed${NC}"
        FAILED=1
    fi
fi

# 2. 前端构建
echo -e "${YELLOW}[2/6] Testing Frontend Build...${NC}"
cd "$ORIN_ROOT/orin-frontend"
if npm run build --silent 2>/dev/null; then
    echo -e "${GREEN}✓ Frontend build OK${NC}"
else
    if npm run build --silent; then
        echo -e "${GREEN}✓ Frontend build OK${NC}"
    else
        echo -e "${RED}✗ Frontend build failed${NC}"
        FAILED=1
    fi
fi

# 3. Python引擎依赖检查
echo -e "${YELLOW}[3/6] Testing Python Engine Dependencies...${NC}"
cd "$ORIN_ROOT/orin-ai-engine"
PYTHON_BIN="python3"
if [ -x "$ORIN_ROOT/orin-ai-engine/venv/bin/python" ]; then
    PYTHON_BIN="$ORIN_ROOT/orin-ai-engine/venv/bin/python"
fi
if "$PYTHON_BIN" -c "import fastapi; import httpx; import aio_pika" 2>/dev/null; then
    echo -e "${GREEN}✓ Python dependencies OK${NC}"
else
    echo -e "${YELLOW}⚠ Some Python dependencies missing in $PYTHON_BIN (install if needed)${NC}"
fi

# 4. 测试文件统计 + DifySync 单元测试
echo -e "${YELLOW}[4/6] Test Coverage Summary + DifySync Unit Tests...${NC}"
AGENT_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Agent*Test.java" | wc -l)
KNOWLEDGE_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Knowledge*Test.java" | wc -l)
WORKFLOW_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Workflow*Test.java" | wc -l)
COLLAB_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Collaboration*Test.java" | wc -l)

echo "  Agent tests: $AGENT_TESTS"
echo "  Knowledge tests: $KNOWLEDGE_TESTS"
echo "  Workflow tests: $WORKFLOW_TESTS"
echo "  Collaboration tests: $COLLAB_TESTS"

# DifyApiClient 单元测试
echo -e "${YELLOW}  Running DifyApiClient unit tests...${NC}"
cd "$ORIN_ROOT/orin-backend"
DIFY_TEST_OUTPUT=$(mvn test -Dtest=DifyApiClientTest 2>&1)
DIFY_TEST_RESULT=$(echo "$DIFY_TEST_OUTPUT" | grep -E "Tests run" | tail -1 || echo "")
if echo "$DIFY_TEST_RESULT" | grep -q "Failures: 0, Errors: 0"; then
    echo -e "${GREEN}  ✓ DifyApiClient unit tests PASS ($DIFY_TEST_RESULT)${NC}"
else
    echo -e "${RED}  ✗ DifyApiClient unit tests FAIL${NC}"
    echo "$DIFY_TEST_RESULT"
    FAILED=1
fi

# SideClientSyncService 单元测试
echo -e "${YELLOW}  Running SideClientSyncService unit tests...${NC}"
SIDE_TEST_OUTPUT=$(mvn test -Dtest=SideClientSyncServiceTest 2>&1)
SIDE_TEST_RESULT=$(echo "$SIDE_TEST_OUTPUT" | grep -E "Tests run" | tail -1 || echo "")
if echo "$SIDE_TEST_RESULT" | grep -q "Failures: 0, Errors: 0"; then
    echo -e "${GREEN}  ✓ SideClientSyncService unit tests PASS ($SIDE_TEST_RESULT)${NC}"
else
    echo -e "${RED}  ✗ SideClientSyncService unit tests FAIL${NC}"
    echo "$SIDE_TEST_RESULT"
    FAILED=1
fi

# 5. 已启动服务的最小 HTTP smoke（如果服务未启动则提示跳过）
echo -e "${YELLOW}[5/6] Runtime HTTP Smoke (optional)...${NC}"
if curl -fsS http://127.0.0.1:8080/v1/health >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Backend /v1/health OK${NC}"
else
    echo -e "${YELLOW}⚠ Backend /v1/health unavailable, skipped live backend smoke${NC}"
    echo "  Start backend: cd orin-backend && mvn spring-boot:run"
fi

if curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Backend /api/v1/health OK${NC}"
else
    echo -e "${YELLOW}⚠ Backend /api/v1/health unavailable, skipped legacy health smoke${NC}"
    echo "  Database connectivity is judged by /api/v1/health when backend is running"
fi

if curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1 \
    && curl -fsS http://127.0.0.1:8000/v1/health >/dev/null 2>&1; then
    echo -e "${GREEN}✓ AI Engine /health and /v1/health reachable${NC}"
    echo "  AI Engine may report degraded optional dependencies such as RabbitMQ"
else
    echo -e "${YELLOW}⚠ AI Engine health endpoints unavailable, skipped live AI smoke${NC}"
    echo "  Start AI Engine: cd orin-ai-engine && venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000"
fi

if curl -fsS http://127.0.0.1:5173/ >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Frontend homepage OK${NC}"
else
    echo -e "${YELLOW}⚠ Frontend homepage unavailable, skipped live frontend smoke${NC}"
    echo "  Start frontend: cd orin-frontend && npm run dev"
fi

# 6. 本轮基线说明
echo -e "${YELLOW}[6/6] Baseline Notes...${NC}"
echo "  Required local runtime: frontend, backend, ai-engine, Redis, MySQL"
echo "  Optional local infra: Milvus, RabbitMQ, Langfuse, Prometheus/Grafana, Neo4j, MinIO"
echo "  External dependencies: model providers, Dify/RAGFlow endpoints, remote MCP servers, production databases"
echo "  DB connectivity source: backend /api/v1/health database status; local :3306 listening is only a hint"
echo "  Schema baseline: run bash scripts/check-schema-baseline.sh to verify snapshot coverage and V88+ boundary"

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}=== Smoke Test Complete (PASS) ===${NC}"
else
    echo -e "${RED}=== Smoke Test Complete (FAIL) ===${NC}"
    exit 1
fi
