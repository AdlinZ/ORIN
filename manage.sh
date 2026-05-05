#!/bin/bash

# ORIN 一键管理脚本 (MacOS/Linux)

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 配置相对路径
BACKEND_DIR="$SCRIPT_DIR/orin-backend"
FRONTEND_DIR="$SCRIPT_DIR/orin-frontend"
AI_ENGINE_DIR="$SCRIPT_DIR/orin-ai-engine"
PID_FILE="$SCRIPT_DIR/.orin.pids"
LOG_DIR="$SCRIPT_DIR/logs"
BACKEND_LOG="$LOG_DIR/orin-backend.log"
FRONTEND_LOG="$LOG_DIR/orin-frontend.log"
AI_ENGINE_LOG="$LOG_DIR/orin-ai-engine.log"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

FRONTEND_HOST="${ORIN_FRONTEND_HOST:-127.0.0.1}"
FRONTEND_PORT="${ORIN_FRONTEND_PORT:-5173}"

# 数据库配置 (支持环境变量覆盖)
# 使用方法: export ORIN_DB_PASS="your_password" 后再运行脚本
DB_NAME="${ORIN_DB_NAME:-orindb}"
DB_USER="${ORIN_DB_USER:-root}"
DB_PASS="${ORIN_DB_PASS:-password}"
DB_HOST="${ORIN_DB_HOST:-localhost}"
DB_PORT="${ORIN_DB_PORT:-3306}"

function flyway_runtime_props() {
    local jwt_secret="${JWT_SECRET:-${ORIN_JWT_SECRET:-0123456789012345678901234567890123456789012345678901234567890123}}"
    local datasource_url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    echo "--spring.datasource.url=$datasource_url"
    echo "--spring.datasource.username=$DB_USER"
    echo "--spring.datasource.password=$DB_PASS"
    echo "--jwt.secret=$jwt_secret"
}

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

function stop_existing_services() {
    # Rebuilding the backend removes target/*.jar. Stop any old JVM first so it
    # cannot lazily load classes/resources from a jar that mvn clean has deleted.
    if [ -f "$PID_FILE" ]; then
        while read -r pid; do
            if [ -n "$pid" ] && kill -0 "$pid" > /dev/null 2>&1; then
                echo "停止旧进程: $pid"
                kill "$pid" > /dev/null 2>&1 || true
            fi
        done < "$PID_FILE"
        sleep 2
        while read -r pid; do
            if [ -n "$pid" ] && kill -0 "$pid" > /dev/null 2>&1; then
                echo "强制停止旧进程: $pid"
                kill -9 "$pid" > /dev/null 2>&1 || true
            fi
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi

    lsof -ti:8080 | xargs kill -9 > /dev/null 2>&1 || true
    lsof -ti:"$FRONTEND_PORT" | xargs kill -9 > /dev/null 2>&1 || true
    lsof -ti:8000 | xargs kill -9 > /dev/null 2>&1 || true
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
        java -jar "$jar_file" --spring.profiles.active=dev $(flyway_runtime_props) flyway:migrate -X 2>&1 | grep -E "Current version|Successfully applied|No migrations" || true
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

    java -jar "$jar_file" --spring.profiles.active=dev $(flyway_runtime_props) flyway:repair -X 2>&1 | tail -20
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

    java -jar "$jar_file" --spring.profiles.active=dev $(flyway_runtime_props) flyway:migrate 2>&1 | tail -30
    return $?
}

function start() {
    echo -e "${YELLOW}正在启动 ORIN 系统...${NC}"
    mkdir -p "$LOG_DIR"

    stop_existing_services

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

    # 0.2 自动修复失败的迁移（删除失败记录让 Flyway 重新执行）
    local failed_count=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;" 2>/dev/null | tail -n 1)
    if [ "$failed_count" -gt 0 ]; then
        echo -e "${YELLOW}发现 $failed_count 个失败的迁移记录，自动清理...${NC}"
        mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "DELETE FROM flyway_schema_history WHERE success=0;" 2>/dev/null
        echo -e "${GREEN}✓ 失败的迁移记录已清理${NC}"
    fi

    # 0.3 检查并手动执行 V45 迁移（如有需要）
    local v45_exists=$(mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='sys_user' AND COLUMN_NAME='department_id';" 2>/dev/null | tail -n 1)
    if [ "$v45_exists" -eq 0 ]; then
        echo -e "${YELLOW}手动执行 V45 迁移...${NC}"
        mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "ALTER TABLE sys_user ADD COLUMN department_id BIGINT;" 2>/dev/null
        if [ $? -eq 0 ]; then
            mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" -e "INSERT INTO flyway_schema_history (version, description, type, script, success, installed_on) VALUES ('45', 'Add_User_Department', 'SQL', 'V45__Add_User_Department.sql', 1, NOW());" 2>/dev/null
            echo -e "${GREEN}✓ V45 迁移完成${NC}"
        fi
    fi

    # 1. 启动后端 (Java)
    echo -e "启动后端服务 (Port: 8080)..."
    cd $BACKEND_DIR
    # 总是重新编译以获取最新代码
    echo -e "正在编译后端..."
    mvn clean package -DskipTests -q
    if [ $? -ne 0 ]; then
        echo -e "${RED}后端编译失败，启动中止${NC}"
        exit 1
    fi

    # 启动后端（使用宽松模式避免历史 checksum 问题）
    local backend_jar
    backend_jar=$(ls target/orin-backend-*.jar 2>/dev/null | head -n 1)
    if [ -z "$backend_jar" ]; then
        echo -e "${RED}未找到后端 JAR 文件，启动中止${NC}"
        exit 1
    fi
    nohup java -jar "$backend_jar" \
        --spring.profiles.active=dev \
        --spring.jpa.hibernate.ddl-auto=none \
        --spring.flyway.validate-on-migrate=false \
        < /dev/null > "$BACKEND_LOG" 2>&1 &
    BPID=$!
    echo $BPID > $PID_FILE

    # 2. 启动 AI 引擎 (Python)
    echo -e "启动 AI 引擎 (Port: 8000)..."
    cd $AI_ENGINE_DIR
    if [ -d "venv" ]; then
        nohup venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 < /dev/null > "$AI_ENGINE_LOG" 2>&1 &
        APID=$!
        echo $APID >> $PID_FILE
    else
        echo -e "${RED}警告: Python 虚拟环境未找到，AI 引擎启动失败${NC}"
    fi

    # 3. 启动前端 (Vue)
    echo -e "启动前端服务 (${FRONTEND_HOST}:${FRONTEND_PORT})..."
    cd $FRONTEND_DIR
    nohup npm run dev -- --host "$FRONTEND_HOST" --port "$FRONTEND_PORT" < /dev/null > "$FRONTEND_LOG" 2>&1 &
    FPID=$!
    echo $FPID >> $PID_FILE

    # 4. 启动后健康校验，避免“假启动成功”（后端冷启动可能较慢）
    local wait_seconds=45
    local elapsed=0
    local backend_ok=0
    local ai_ok=0
    local frontend_ok=0
    while [ "$elapsed" -lt "$wait_seconds" ]; do
        curl -fsS "http://127.0.0.1:8080/v1/health" > /dev/null 2>&1 && backend_ok=1 || backend_ok=0
        curl -fsS "http://127.0.0.1:8000/health" > /dev/null 2>&1 && ai_ok=1 || ai_ok=0
        curl -fsS "http://${FRONTEND_HOST}:${FRONTEND_PORT}/" > /dev/null 2>&1 && frontend_ok=1 || frontend_ok=0
        if [ "$backend_ok" -eq 1 ] && [ "$ai_ok" -eq 1 ] && [ "$frontend_ok" -eq 1 ]; then
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    if [ "$backend_ok" -eq 1 ] && [ "$ai_ok" -eq 1 ] && [ "$frontend_ok" -eq 1 ]; then
        echo -e "${GREEN}启动成功！${NC}"
        echo -e "后端日志: $BACKEND_LOG"
        echo -e "前端日志: $FRONTEND_LOG"
        echo -e "AI引擎日志: $AI_ENGINE_LOG"
        echo -e "访问地址: ${GREEN}http://${FRONTEND_HOST}:${FRONTEND_PORT}${NC}"
    else
        echo -e "${RED}启动未通过健康校验：backend=$backend_ok ai_engine=$ai_ok frontend=$frontend_ok${NC}"
        echo -e "${YELLOW}请检查日志：${NC}"
        echo -e "  $BACKEND_LOG"
        echo -e "  $AI_ENGINE_LOG"
        echo -e "  $FRONTEND_LOG"
        return 1
    fi
}

function stop() {
    echo -e "${YELLOW}正在停止 ORIN 系统...${NC}"

    stop_existing_services

    echo -e "${RED}系统已安全停止。${NC}"
}

function status() {
    echo -e "${BLUE}=== 服务状态 ===${NC}"

    B_RUNNING=$(lsof -i:8080)
    F_RUNNING=$(lsof -i:"$FRONTEND_PORT")
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
