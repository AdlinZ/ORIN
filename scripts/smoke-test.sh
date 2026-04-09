#!/bin/bash
# ORIN Smoke Test Script
# 用于快速验证ORIN三端可运行性

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
FAILED=0

echo -e "${YELLOW}=== ORIN Smoke Test ===${NC}"
echo ""

# 1. Java后端编译
echo -e "${YELLOW}[1/4] Testing Java Backend Compile...${NC}"
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
echo -e "${YELLOW}[2/4] Testing Frontend Build...${NC}"
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
echo -e "${YELLOW}[3/4] Testing Python Engine Dependencies...${NC}"
cd "$ORIN_ROOT/orin-ai-engine"
if python3 -c "import fastapi; import httpx; import pika" 2>/dev/null; then
    echo -e "${GREEN}✓ Python dependencies OK${NC}"
else
    echo -e "${YELLOW}⚠ Some Python dependencies missing (install if needed)${NC}"
fi

# 4. 测试文件统计
echo -e "${YELLOW}[4/4] Test Coverage Summary...${NC}"
AGENT_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Agent*Test.java" | wc -l)
KNOWLEDGE_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Knowledge*Test.java" | wc -l)
WORKFLOW_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Workflow*Test.java" | wc -l)
COLLAB_TESTS=$(find "$ORIN_ROOT/orin-backend/src/test" -name "*Collaboration*Test.java" | wc -l)

echo "  Agent tests: $AGENT_TESTS"
echo "  Knowledge tests: $KNOWLEDGE_TESTS"
echo "  Workflow tests: $WORKFLOW_TESTS"
echo "  Collaboration tests: $COLLAB_TESTS"

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}=== Smoke Test Complete (PASS) ===${NC}"
else
    echo -e "${RED}=== Smoke Test Complete (FAIL) ===${NC}"
    exit 1
fi
