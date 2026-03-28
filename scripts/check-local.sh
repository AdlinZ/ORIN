#!/bin/bash
# ORIN 本地最小检查脚本
# 用于 PR 提交前在本地运行三端最小检查组合
# 依赖：Java 17, Maven, Node.js 18+, Python 3.11+
#
# 用法：
#   bash scripts/check-local.sh          # 运行全部检查
#   bash scripts/check-local.sh -b      # 仅后端
#   bash scripts/check-local.sh -f      # 仅前端
#   bash scripts/check-local.sh -a      # 仅 AI 引擎
#   bash scripts/check-local.sh -q      # 快速检查（无测试，仅 lint/build）

set -e

ORIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$ORIN_ROOT/orin-backend"
FRONTEND_DIR="$ORIN_ROOT/orin-frontend"
AI_ENGINE_DIR="$ORIN_ROOT/orin-ai-engine"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 默认执行全部检查
RUN_BACKEND=true
RUN_FRONTEND=true
RUN_AI_ENGINE=true
QUICK_MODE=false

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -b) RUN_AI_ENGINE=false; RUN_FRONTEND=false; shift ;;
        -f) RUN_BACKEND=false; RUN_AI_ENGINE=false; shift ;;
        -a) RUN_BACKEND=false; RUN_FRONTEND=false; shift ;;
        -q) QUICK_MODE=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }
section() { echo ""; echo "========================================"; echo " $1"; echo "========================================"; }

# ==================== 前置检查 ====================
section "环境前置检查"

check_cmd() {
    if command -v "$1" &> /dev/null; then
        pass "$1 已安装: $(command -v $1)"
        return 0
    else
        fail "$1 未安装"
        return 1
    fi
}

MISSING=0
check_cmd java || MISSING=1
check_cmd mvn || MISSING=1
check_cmd node || MISSING=1
check_cmd npm || MISSING=1
check_cmd python3 || MISSING=1

if [ $MISSING -eq 1 ]; then
    fail "环境不完整，请先安装缺失工具"
    exit 1
fi

# Java 版本检查
JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
info "Java 版本: $JAVA_VERSION"

# Node 版本检查
NODE_VERSION=$(node --version)
info "Node 版本: $NODE_VERSION"

pass "环境检查完成"
echo ""

# ==================== 后端检查 ====================
if [ "$RUN_BACKEND" = true ]; then
    section "后端检查 (orin-backend)"

    if [ ! -d "$BACKEND_DIR" ]; then
        fail "orin-backend 目录不存在，跳过后端检查"
    else
        cd "$BACKEND_DIR"

        # 编译检查
        info "mvn compile..."
        if mvn compile -q; then
            pass "编译通过"
        else
            fail "编译失败"
            exit 1
        fi

        # 测试检查（快速模式跳过）
        if [ "$QUICK_MODE" = false ]; then
            info "mvn test（仅运行 F1.x 核心测试）..."
            if mvn test -q -Dtest="ErrorCodeTest,ResultTest,BusinessExceptionTest,GlobalExceptionHandlerTest,KnowledgeGraphServiceTest,CollaborationServiceUnitTest,WorkflowServiceTest,AgentSmokeTest"; then
                pass "核心测试全部通过"
            else
                fail "核心测试有失败用例"
                exit 1
            fi
        else
            info "快速模式：跳过测试"
        fi

        cd "$ORIN_ROOT"
        pass "后端检查完成"
    fi
fi

# ==================== 前端检查 ====================
if [ "$RUN_FRONTEND" = true ]; then
    section "前端检查 (orin-frontend)"

    if [ ! -d "$FRONTEND_DIR" ]; then
        fail "orin-frontend 目录不存在，跳过前端检查"
    else
        cd "$FRONTEND_DIR"

        # 依赖安装检查
        if [ ! -d "node_modules" ]; then
            info "node_modules 不存在，执行 npm install..."
            npm install
        fi

        # Lint 检查
        info "npm run lint..."
        if npm run lint --silent 2>&1 | tee /tmp/lint-output.tmp; then
            pass "Lint 检查通过"
        else
            fail "Lint 检查失败，请查看上方输出"
            if [ -s /tmp/lint-output.tmp ]; then
                info "常见问题：运行 npm run lint:fix 自动修复格式问题"
            fi
            exit 1
        fi

        # 快速模式跳过构建
        if [ "$QUICK_MODE" = false ]; then
            info "npm run build..."
            if npm run build --silent 2>&1 | tail -5; then
                pass "构建通过"
            else
                fail "构建失败"
                exit 1
            fi
        else
            info "快速模式：跳过构建"
        fi

        cd "$ORIN_ROOT"
        pass "前端检查完成"
    fi
fi

# ==================== AI 引擎检查 ====================
if [ "$RUN_AI_ENGINE" = true ]; then
    section "AI 引擎检查 (orin-ai-engine)"

    if [ ! -d "$AI_ENGINE_DIR" ]; then
        fail "orin-ai-engine 目录不存在，跳过 AI 引擎检查"
    else
        cd "$AI_ENGINE_DIR"

        # 确定 Python 命令（优先使用 venv）
        if [ -f "$AI_ENGINE_DIR/venv/bin/python" ]; then
            PYTHON_CMD="$AI_ENGINE_DIR/venv/bin/python"
        else
            PYTHON_CMD="python3"
        fi

        # Python 语法检查
        info "Python 语法检查..."
        if "$PYTHON_CMD" -m py_compile app/main.py 2>/dev/null; then
            pass "语法检查通过"
        else
            fail "Python 语法错误"
            exit 1
        fi

        # 测试检查（快速模式跳过）
        if [ "$QUICK_MODE" = false ]; then
            # 检查是否有 pytest
            if "$PYTHON_CMD" -m pytest --version &> /dev/null; then
                info "$PYTHON_CMD -m pytest tests/..."
                if "$PYTHON_CMD" -m pytest tests/ -v --tb=short 2>&1 | tail -10; then
                    pass "AI 引擎测试通过"
                else
                    fail "AI 引擎测试有失败用例"
                    exit 1
                fi
            else
                info "pytest 未安装，跳过 AI 引擎测试"
            fi
        else
            info "快速模式：跳过测试"
        fi

        cd "$ORIN_ROOT"
        pass "AI 引擎检查完成"
    fi
fi

# ==================== 汇总 ====================
section "检查汇总"
echo ""
echo -e "${GREEN}========================================"
echo -e " 全部检查通过 ✅"
echo -e "========================================${NC}"
echo ""
echo "提示："
echo "  - 完整检查（编译+测试）: bash scripts/check-local.sh"
echo "  - 快速检查（lint+build）: bash scripts/check-local.sh -q"
echo "  - 仅后端: bash scripts/check-local.sh -b"
echo "  - 仅前端: bash scripts/check-local.sh -f"
echo "  - 仅 AI 引擎: bash scripts/check-local.sh -a"
