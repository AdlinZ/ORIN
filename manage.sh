#!/bin/bash

# ORIN 一键管理脚本 (MacOS/Linux)

# 配置路径
BACKEND_DIR="/Users/adlin/Desktop/JAVA/orin-backend"
FRONTEND_DIR="/Users/adlin/Desktop/JAVA/orin-frontend"
PID_FILE="/Users/adlin/Desktop/JAVA/.orin.pids"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

function start() {
    echo -e "${YELLOW}正在启动 ORIN 系统...${NC}"

    # 1. 启动后端
    echo -e "启动后端服务 (Port: 8080)..."
    cd $BACKEND_DIR
    nohup mvn spring-boot:run < /dev/null > backend.log 2>&1 &
    BPID=$!
    echo $BPID > $PID_FILE

    # 2. 启动前端
    echo -e "启动前端服务 (Port: 5173)..."
    cd $FRONTEND_DIR
    nohup npm run dev < /dev/null > frontend.log 2>&1 &
    FPID=$!
    echo $FPID >> $PID_FILE

    echo -e "${GREEN}启动成功！${NC}"
    echo -e "后端日志: $BACKEND_DIR/backend.log"
    echo -e "前端日志: $FRONTEND_DIR/frontend.log"
    echo -e "访问地址: ${GREEN}http://localhost:5173${NC}"
}

function stop() {
    echo -e "${YELLOW}正在停止 ORIN 系统...${NC}"
    
    # 按照 PID 文件停止
    if [ -f $PID_FILE ]; then
        while read pid; do
            echo "正在杀死进程: $pid"
            kill -9 $pid > /dev/null 2>&1
        done < $PID_FILE
        rm $PID_FILE
    fi

    # 保险起见，按端口清理
    echo "清理端口占用..."
    lsof -ti:8080 | xargs kill -9 > /dev/null 2>&1
    lsof -ti:5173 | xargs kill -9 > /dev/null 2>&1

    echo -e "${RED}系统已安全停止。${NC}"
}

function status() {
    B_RUNNING=$(lsof -i:8080)
    F_RUNNING=$(lsof -i:5173)

    if [ ! -z "$B_RUNNING" ]; then
        echo -e "后端服务: ${GREEN}运行中${NC}"
    else
        echo -e "后端服务: ${RED}已停止${NC}"
    fi

    if [ ! -z "$F_RUNNING" ]; then
        echo -e "前端服务: ${GREEN}运行中${NC}"
    else
        echo -e "前端服务: ${RED}已停止${NC}"
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        sleep 2
        start
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        exit 1
esac
