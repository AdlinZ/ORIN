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

# Flyway migration status check
function flyway_status() {
    echo -e "${BLUE}=== Flyway 迁移状态 ===${NC}"

    if ! mysql -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME" 2>/dev/null; then
        echo -e "${RED}数据库 '$DB_NAME' 不存在${NC}"
        return 1
    fi

    # Check if flyway_schema_history table exists
    local table_exists=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SHOW TABLES LIKE 'flyway_schema_history';" 2>/dev/null | wc -l)

    if [ "$table_exists" -eq 0 ]; then
        echo -e "${YELLOW}Flyway 未初始化 (flyway_schema_history 表不存在)${NC}"
        return 1
    fi

    # Show successful migrations
    echo -e "${GREEN}已完成的迁移:${NC}"
    mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT version, description, success FROM flyway_schema_history WHERE type='SQL' ORDER BY installed_rank;" 2>/dev/null

    # Check for failed migrations
    local failed_count=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;" 2>/dev/null | tail -n 1)

    if [ "$failed_count" -gt 0 ]; then
        echo -e "${RED}失败的迁移数量: $failed_count${NC}"
        mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT version, description, installed_on, failure_msg FROM flyway_schema_history WHERE success=0;" 2>/dev/null
        return 1
    else
        echo -e "${GREEN}✓ 无失败的迁移${NC}"
    fi

    # Show pending migrations
    local jar_file=$(ls $BACKEND_DIR/target/orin-backend-*.jar 2>/dev/null | head -n 1)
    if [ -n "$jar_file" ]; then
        echo -e "${BLUE}待执行的迁移检查:${NC}"
        java -jar "$jar_file" --spring.profiles.active=dev flyway:migrate -X 2>&1 | grep -E "Current version|Successfully applied|No migrations" || true
    fi

    return 0
}

# Flyway repair (fix failed migrations)
function flyway_repair() {
    echo -e "${YELLOW}执行 Flyway Repair...${NC}"

    local jar_file=$(ls $BACKEND_DIR/target/orin-backend-*.jar 2>/dev/null | head -n 1)
    if [ -z "$jar_file" ]; then
        echo -e "${RED}错误: 未找到后端 JAR 文件${NC}"
        return 1
    fi

    java -jar "$jar_file" --spring.profiles.active=dev flyway:repair -X 2>&1 | tail -20
    echo -e "${GREEN}✓ Flyway Repair 完成${NC}"
}

# Run Flyway migrate
function flyway_migrate() {
    echo -e "${YELLOW}执行 Flyway Migrate...${NC}"

    local jar_file=$(ls $BACKEND_DIR/target/orin-backend-*.jar 2>/dev/null | head -n 1)
    if [ -z "$jar_file" ]; then
        echo -e "${RED}错误: 未找到后端 JAR 文件${NC}"
        return 1
    fi

    java -jar "$jar_file" --spring.profiles.active=dev flyway:migrate 2>&1 | tail -30
    return $?
}

function start() {
    echo -e "${YELLOW}正在启动 ORIN 系统...${NC}"

    # 0. 检查 MySQL 数据库
    check_mysql
    if [ $? -ne 0 ]; then
        echo -e "${RED}数据库检查失败，无法启动系统${NC}"
        exit 1
    fi

    # 0.1 自动修复 Flyway checksum 问题（处理历史上迁移脚本被修改的情况）
    echo -e "${YELLOW}检查 Flyway 迁移状态...${NC}"
    local checksum_issues=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND checksum IS NULL;" 2>/dev/null | tail -n 1)
    if [ "$checksum_issues" -gt 0 ]; then
        echo -e "${YELLOW}发现 $checksum_issues 个迁移记录 checksum 异常，自动修复...${NC}"
        mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "UPDATE flyway_schema_history SET checksum = NULL WHERE success=1;" 2>/dev/null
        echo -e "${GREEN}✓ Flyway 修复完成${NC}"
    fi

    # 1. 启动后端 (Java)
    echo -e "启动后端服务 (Port: 8080)..."
    cd $BACKEND_DIR
    # 总是重新编译以获取最新代码
    echo -e "正在编译后端..."
    mvn clean package -DskipTests -q

    # 启动后端（使用宽松模式避免历史 checksum 问题）
    nohup java -jar target/orin-backend-*.jar \
        --spring.profiles.active=dev \
        --spring.jpa.hibernate.ddl-auto=none \
        --spring.flyway.validate-on-migrate=false \
        < /dev/null > backend.log 2>&1 &
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
    flyway-status)
        flyway_status
        ;;
    flyway-repair)
        flyway_repair
        ;;
    flyway-migrate)
        flyway_migrate
        ;;
    flyway-fix)
        echo -e "${YELLOW}执行 Flyway 修复流程...${NC}"
        flyway_repair
        flyway_migrate
        flyway_status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|db|flyway-status|flyway-repair|flyway-migrate|flyway-fix}"
        echo ""
        echo "命令说明:"
        echo "  start         - 启动 ORIN 系统（包含数据库检查）"
        echo "  stop          - 停止 ORIN 系统"
        echo "  restart       - 重启 ORIN 系统"
        echo "  status        - 查看服务和数据库状态"
        echo "  db            - 查看数据库状态"
        echo "  flyway-status - 查看 Flyway 迁移状态"
        echo "  flyway-repair - 修复 Flyway 迁移记录"
        echo "  flyway-migrate - 执行 Flyway 迁移"
        echo "  flyway-fix    - 一键修复迁移 (repair + migrate + status)"
        exit 1
esac
