#!/bin/bash

# ORIN 一键管理脚本 (MacOS/Linux)

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 配置相对路径
BACKEND_DIR="$SCRIPT_DIR/orin-backend"
FRONTEND_DIR="$SCRIPT_DIR/orin-frontend"
AI_ENGINE_DIR="$SCRIPT_DIR/orin-ai-engine"
PID_FILE="$SCRIPT_DIR/.orin.pids"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 数据库配置 (支持环境变量覆盖)
# 使用方法: export ORIN_DB_PASS="your_password" 后再运行脚本
DB_NAME="${ORIN_DB_NAME:-orindb}"
DB_USER="${ORIN_DB_USER:-root}"
DB_PASS="${ORIN_DB_PASS:-password}"

function check_mysql() {
    echo -e "${BLUE}检查 MySQL 连接...${NC}"
    
    # 检查 MySQL 是否运行
    if ! command -v mysql &> /dev/null; then
        echo -e "${RED}错误: MySQL 未安装${NC}"
        echo -e "${YELLOW}请先安装 MySQL: brew install mysql${NC}"
        return 1
    fi
    
    # 检查 MySQL 服务是否运行
    if ! pgrep -x mysqld > /dev/null; then
        echo -e "${YELLOW}MySQL 服务未运行，正在启动...${NC}"
        brew services start mysql
        sleep 3
    fi
    
    # 检查数据库是否存在
    if mysql -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME" 2>/dev/null; then
        echo -e "${GREEN}✓ MySQL 连接成功，数据库 '$DB_NAME' 已就绪${NC}"
        return 0
    else
        echo -e "${YELLOW}数据库 '$DB_NAME' 不存在，正在创建...${NC}"
        mysql -u"$DB_USER" -p"$DB_PASS" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ 数据库创建成功${NC}"
            return 0
        else
            echo -e "${RED}✗ 数据库创建失败，请检查 MySQL 配置${NC}"
            echo -e "${YELLOW}提示: 请确保 application.properties 中的数据库密码正确${NC}"
            return 1
        fi
    fi
}

function db_status() {
    echo -e "${BLUE}=== 数据库状态 ===${NC}"
    
    if ! command -v mysql &> /dev/null; then
        echo -e "MySQL: ${RED}未安装${NC}"
        return
    fi
    
    if pgrep -x mysqld > /dev/null; then
        echo -e "MySQL 服务: ${GREEN}运行中${NC}"
        
        if mysql -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME" 2>/dev/null; then
            echo -e "数据库 '$DB_NAME': ${GREEN}存在${NC}"
            
            # 显示表信息
            TABLE_COUNT=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SHOW TABLES;" 2>/dev/null | wc -l)
            TABLE_COUNT=$((TABLE_COUNT - 1))
            echo -e "数据表数量: ${GREEN}$TABLE_COUNT${NC}"
        else
            echo -e "数据库 '$DB_NAME': ${RED}不存在${NC}"
        fi
    else
        echo -e "MySQL 服务: ${RED}未运行${NC}"
    fi
}


function start() {
    echo -e "${YELLOW}正在启动 ORIN 系统...${NC}"
    
    # 0. 检查 MySQL 数据库
    check_mysql
    if [ $? -ne 0 ]; then
        echo -e "${RED}数据库检查失败，无法启动系统${NC}"
        exit 1
    fi

    # 1. 启动后端 (Java)
    echo -e "启动后端服务 (Port: 8080)..."
    cd $BACKEND_DIR
    # 确保 JAR 包存在
    if [ ! -f target/orin-backend-*.jar ]; then
        echo -e "正在编译后端..."
        mvn clean package -DskipTests -q
    fi
    nohup java -jar target/orin-backend-*.jar --spring.profiles.active=dev < /dev/null > backend.log 2>&1 &
    BPID=$!
    echo $BPID > $PID_FILE

    # 2. 启动 AI 引擎 (Python)
    echo -e "启动 AI 引擎 (Port: 8000)..."
    cd $AI_ENGINE_DIR
    if [ -d "venv" ]; then
        nohup venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 < /dev/null > ai_engine.log 2>&1 &
        APID=$!
        echo $APID >> $PID_FILE
    else
        echo -e "${RED}警告: Python 虚拟环境未找到，AI 引擎启动失败${NC}"
    fi

    # 3. 启动前端 (Vue)
    echo -e "启动前端服务 (Port: 5173)..."
    cd $FRONTEND_DIR
    nohup npm run dev < /dev/null > frontend.log 2>&1 &
    FPID=$!
    echo $FPID >> $PID_FILE

    echo -e "${GREEN}启动成功！${NC}"
    echo -e "后端日志: $BACKEND_DIR/backend.log"
    echo -e "前端日志: $FRONTEND_DIR/frontend.log"
    echo -e "AI引擎日志: $AI_ENGINE_DIR/ai_engine.log"
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
    lsof -ti:8000 | xargs kill -9 > /dev/null 2>&1

    echo -e "${RED}系统已安全停止。${NC}"
}

function status() {
    echo -e "${BLUE}=== 服务状态 ===${NC}"
    
    B_RUNNING=$(lsof -i:8080)
    F_RUNNING=$(lsof -i:5173)
    A_RUNNING=$(lsof -i:8000)

    if [ ! -z "$B_RUNNING" ]; then
        echo -e "后端服务 (Java):   ${GREEN}运行中${NC}"
    else
        echo -e "后端服务 (Java):   ${RED}已停止${NC}"
    fi

    if [ ! -z "$A_RUNNING" ]; then
        echo -e "AI 引擎 (Python):  ${GREEN}运行中${NC}"
    else
        echo -e "AI 引擎 (Python):  ${RED}已停止${NC}"
    fi

    if [ ! -z "$F_RUNNING" ]; then
        echo -e "前端服务 (Vue):    ${GREEN}运行中${NC}"
    else
        echo -e "前端服务 (Vue):    ${RED}已停止${NC}"
    fi
    
    echo ""
    db_status
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
    db)
        db_status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|db}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动 ORIN 系统（包含数据库检查）"
        echo "  stop    - 停止 ORIN 系统"
        echo "  restart - 重启 ORIN 系统"
        echo "  status  - 查看服务和数据库状态"
        echo "  db      - 查看数据库状态"
        exit 1
esac
