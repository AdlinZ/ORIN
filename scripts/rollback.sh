#!/bin/bash

# ORIN 多智能体协作系统 - 回滚脚本
# 用法: ./rollback.sh [backend|frontend|ai-engine|all]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 配置
BACKEND_JAR="target/orin-backend-1.0.0.jar"
FRONTEND_DIR="/var/www/html"
AI_ENGINE_PORT=8000

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 回滚后端
rollback_backend() {
    log_info "开始回滚后端服务..."

    # 停止当前服务
    if systemctl is-active --quiet orin-backend; then
        log_warn "停止 orin-backend 服务..."
        systemctl stop orin-backend || true
    fi

    # 使用旧版本 JAR (如果存在)
    if [ -f "${BACKEND_JAR}.backup" ]; then
        log_info "恢复备份 JAR..."
        mv "${BACKEND_JAR}.backup" "${BACKEND_JAR}"
    fi

    # 启动服务
    if [ -f "${BACKEND_JAR}" ]; then
        log_info "启动后端服务..."
        nohup java -jar "${BACKEND_JAR}" --spring.profiles.active=prod > /var/log/orin-backend.log 2>&1 &
        sleep 10

        # 验证
        if curl -sf http://localhost:8080/actuator/health > /dev/null; then
            log_info "后端服务启动成功"
        else
            log_error "后端服务启动失败"
            exit 1
        fi
    else
        log_error "未找到 JAR 文件"
        exit 1
    fi
}

# 回滚前端
rollback_frontend() {
    log_info "开始回滚前端..."

    if [ -d "${FRONTEND_DIR}.backup" ]; then
        log_info "恢复前端备份..."
        rm -rf "${FRONTEND_DIR}"
        mv "${FRONTEND_DIR}.backup" "${FRONTEND_DIR}"

        # 重载 Nginx
        nginx -s reload
        log_info "前端回滚完成"
    else
        log_error "未找到前端备份"
        exit 1
    fi
}

# 回滚 AI 引擎
rollback_ai_engine() {
    log_info "开始回滚 AI 引擎..."

    # 停止当前进程
    if lsof -i:${AI_ENGINE_PORT} > /dev/null 2>&1; then
        log_warn "停止 AI 引擎进程..."
        pkill -f "uvicorn app.main:app" || true
    fi

    # 检查备份
    if [ -d "orin-ai-engine.backup" ]; then
        log_info "恢复 AI 引擎备份..."
        rm -rf orin-ai-engine
        mv orin-ai-engine.backup orin-ai-engine

        # 重新安装依赖
        cd orin-ai-engine
        pip install -r requirements.txt

        # 重启服务
        nohup uvicorn app.main:app --host 0.0.0.0 --port ${AI_ENGINE_PORT} > /var/log/orin-ai-engine.log 2>&1 &
        sleep 5

        log_info "AI 引擎回滚完成"
    else
        log_error "未找到 AI 引擎备份"
        exit 1
    fi
}

# 创建备份
create_backup() {
    local target=$1

    case $target in
        backend)
            if [ -f "${BACKEND_JAR}" ]; then
                log_info "备份后端 JAR..."
                cp "${BACKEND_JAR}" "${BACKEND_JAR}.backup"
            fi
            ;;
        frontend)
            if [ -d "${FRONTEND_DIR}" ]; then
                log_info "备份前端..."
                rm -rf "${FRONTEND_DIR}.backup"
                cp -r "${FRONTEND_DIR}" "${FRONTEND_DIR}.backup"
            fi
            ;;
        ai-engine)
            if [ -d "orin-ai-engine" ]; then
                log_info "备份 AI 引擎..."
                rm -rf orin-ai-engine.backup
                cp -r orin-ai-engine orin-ai-engine.backup
            fi
            ;;
    esac
}

# 主逻辑
case $1 in
    backend)
        create_backup backend
        rollback_backend
        ;;
    frontend)
        create_backup frontend
        rollback_frontend
        ;;
    ai-engine)
        create_backup ai-engine
        rollback_ai_engine
        ;;
    all)
        create_backup backend
        create_backup frontend
        create_backup ai-engine
        rollback_backend
        rollback_frontend
        rollback_ai_engine
        ;;
    *)
        echo "用法: $0 [backend|frontend|ai-engine|all]"
        echo ""
        echo "示例:"
        echo "  $0 backend    # 仅回滚后端"
        echo "  $0 frontend    # 仅回滚前端"
        echo "  $0 ai-engine   # 仅回滚 AI 引擎"
        echo "  $0 all         # 回滚所有组件"
        exit 1
        ;;
esac

log_info "回滚完成!"