#!/bin/bash

# ORIN 协作系统 - 数据库验证脚本

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 配置
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-orin}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# 执行 SQL
exec_sql() {
    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" -e "$1" 2>/dev/null
}

echo "========================================"
echo "  ORIN 协作系统 - 数据库验证"
echo "========================================"
echo ""

# 1. 检查表是否存在
echo "1. 检查协作表是否存在..."
TABLES=("collab_package" "collab_subtask" "collab_participant" "collab_event_log")
ALL_EXIST=true

for table in "${TABLES[@]}"; do
    result=$(exec_sql "SHOW TABLES LIKE '$table'" | tail -n 1)
    if [ "$result" = "$table" ]; then
        log_pass "$table 存在"
    else
        log_fail "$table 不存在"
        ALL_EXIST=false
    fi
done

echo ""

# 2. 检查表结构
echo "2. 检查表结构..."
if [ "$ALL_EXIST" = true ]; then
    # 检查 collab_package 字段
    FIELDS=("package_id" "intent" "status" "collaboration_mode" "trace_id" "strategy")
    for field in "${FIELDS[@]}"; do
        result=$(exec_sql "SHOW COLUMNS FROM collab_package LIKE '$field'" | wc -l)
        if [ "$result" -gt 0 ]; then
            log_pass "collab_package.$field"
        else
            log_fail "collab_package.$field 缺失"
        fi
    done
fi

echo ""

# 3. 检查索引
echo "3. 检查索引..."
INDEXES=("idx_package_id" "idx_status" "idx_trace_id" "idx_created_by")
for index in "${INDEXES[@]}"; do
    result=$(exec_sql "SHOW INDEX FROM collab_package WHERE Key_name='$index'" | wc -l)
    if [ "$result" -gt 0 ]; then
        log_pass "索引 $index 存在"
    else
        log_fail "索引 $index 不存在"
    fi
done

echo ""

# 4. 检查数据（如果有）
echo "4. 检查数据..."
PACKAGE_COUNT=$(exec_sql "SELECT COUNT(*) FROM collab_package" 2>/dev/null | tail -n 1)
SUBTASK_COUNT=$(exec_sql "SELECT COUNT(*) FROM collab_subtask" 2>/dev/null | tail -n 1)
EVENT_COUNT=$(exec_sql "SELECT COUNT(*) FROM collab_event_log" 2>/dev/null | tail -n 1)

log_info "collab_package: $PACKAGE_COUNT 条记录"
log_info "collab_subtask: $SUBTASK_COUNT 条记录"
log_info "collab_event_log: $EVENT_COUNT 条记录"

echo ""

# 5. 检查外键
echo "5. 检查外键..."
FOREIGN_KEYS=("collab_task_agents" "collab_subtask_ibfk_1")
for fk in "${FOREIGN_KEYS[@]}"; do
    result=$(exec_sql "SHOW CREATE TABLE collab_task" 2>/dev/null | grep -c "$fk" || echo 0)
    if [ "$result" -gt 0 ]; then
        log_pass "外键 $fk 存在"
    else
        log_info "外键 $fk (optional)"
    fi
done

echo ""
echo "========================================"
echo "  验证完成"
echo "========================================"