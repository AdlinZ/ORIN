#!/bin/bash
# ORIN 恢复脚本
# 用法: ./restore.sh <backup-dir> [--mysql|--redis|--rabbitmq|--config|--all]
#
# 示例:
#   ./restore.sh ./backups/orin-20260604-120000 --all    # 全量恢复
#   ./restore.sh ./backups/orin-20260604-120000 --mysql # 仅恢复 MySQL

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
[[ "$ENV_FILE" = /* ]] || ENV_FILE="$ROOT_DIR/$ENV_FILE"

# Only parse required `KEY=value` entries. Sourcing a backup or .env file
# would execute arbitrary shell content during a destructive operation.
load_env_value() {
  local key="$1" value
  [[ -n "${!key+x}" || ! -f "$ENV_FILE" ]] && return
  value="$(awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$ENV_FILE")"
  [[ -z "$value" ]] && return
  printf -v "$key" '%s' "$value"
  export "$key"
}

for key in MYSQL_ROOT_PASSWORD MYSQL_DATABASE; do
  load_env_value "$key"
done

COMPOSE_PREFIX="${ORIN_COMPOSE_PREFIX:-orin}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-${COMPOSE_PREFIX}-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-${COMPOSE_PREFIX}-redis}"
RABBITMQ_CONTAINER="${RABBITMQ_CONTAINER:-${COMPOSE_PREFIX}-rabbitmq}"
MYSQL_DATABASE="${MYSQL_DATABASE:-orindb}"

log()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

[[ $# -ge 1 ]] || fail "用法: $0 <backup-dir> [--mysql|--redis|--rabbitmq|--config|--all]"
BACKUP_DIR="$1"
[[ -d "${BACKUP_DIR}" ]] || fail "备份目录不存在: ${BACKUP_DIR}"
[[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || fail "MYSQL_DATABASE 只能包含字母、数字和下划线"

RESTORE_MYSQL=false
RESTORE_REDIS=false
RESTORE_RABBITMQ=false
RESTORE_CONFIG=false
if [[ $# -eq 1 ]]; then
  RESTORE_MYSQL=true
  RESTORE_REDIS=true
  RESTORE_RABBITMQ=true
  RESTORE_CONFIG=true
else
  shift
  for option in "$@"; do
    case "$option" in
      --mysql) RESTORE_MYSQL=true ;;
      --redis) RESTORE_REDIS=true ;;
      --rabbitmq) RESTORE_RABBITMQ=true ;;
      --config) RESTORE_CONFIG=true ;;
      --all)
        RESTORE_MYSQL=true
        RESTORE_REDIS=true
        RESTORE_RABBITMQ=true
        RESTORE_CONFIG=true
        ;;
      *) fail "未知参数: $option" ;;
    esac
  done
fi

log "开始恢复 ORIN..."
log "备份来源: ${BACKUP_DIR}"

# ---- MySQL ----
if [[ "$RESTORE_MYSQL" == true ]]; then
  log "恢复 MySQL..."
  SQL_GZ="${BACKUP_DIR}/mysql/${MYSQL_DATABASE}.sql.gz"
  if [[ -f "${SQL_GZ}" ]]; then
    if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
      [[ -n "${MYSQL_ROOT_PASSWORD:-}" ]] || fail "MYSQL_ROOT_PASSWORD 未设置；请通过环境变量或 ${ENV_FILE} 提供后再恢复"
      [[ "${ORIN_RESTORE_CONFIRM:-}" == "DROP_DATABASE" ]] || fail "MySQL 恢复会替换 ${MYSQL_DATABASE}；确认后以 ORIN_RESTORE_CONFIRM=DROP_DATABASE 重试"
      docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "${MYSQL_CONTAINER}" mysql -uroot \
        -e "DROP DATABASE IF EXISTS \`${MYSQL_DATABASE}\`; CREATE DATABASE \`${MYSQL_DATABASE}\`;"
      gunzip -c "${SQL_GZ}" | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "${MYSQL_CONTAINER}" mysql -uroot "$MYSQL_DATABASE"
      log "  MySQL 恢复完成"
    else
      fail "MySQL 容器未运行，请先启动: docker compose up -d mysql"
    fi
  else
    warn "未找到 MySQL dump: ${SQL_GZ}"
  fi
fi

# ---- Redis ----
if [[ "$RESTORE_REDIS" == true ]]; then
  log "恢复 Redis..."
  RDB="${BACKUP_DIR}/redis/dump.rdb"
  if [[ -f "${RDB}" ]]; then
    if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
      docker stop "${REDIS_CONTAINER}" > /dev/null 2>&1 || true
      docker cp "${RDB}" "${REDIS_CONTAINER}:/data/dump.rdb"
      docker start "${REDIS_CONTAINER}" > /dev/null 2>&1
      log "  Redis 恢复完成（容器已重启）"
    else
      warn "Redis 容器未运行，跳过"
    fi
  else
    warn "未找到 Redis RDB: ${RDB}"
  fi
fi

# ---- RabbitMQ ----
if [[ "$RESTORE_RABBITMQ" == true ]]; then
  log "恢复 RabbitMQ 定义（消息不回退）..."
  DEFINITIONS="${BACKUP_DIR}/rabbitmq/definitions.json"
  if docker ps --format '{{.Names}}' | grep -q "^${RABBITMQ_CONTAINER}$"; then
    if [[ -f "${DEFINITIONS}" ]]; then
      docker cp "${DEFINITIONS}" "${RABBITMQ_CONTAINER}:/tmp/orin-restore-definitions.json"
      docker exec "${RABBITMQ_CONTAINER}" rabbitmqctl import_definitions /tmp/orin-restore-definitions.json
      docker exec "${RABBITMQ_CONTAINER}" rm -f /tmp/orin-restore-definitions.json >/dev/null 2>&1 || true
      log "  RabbitMQ definitions 已导入（不含消息）"
    else
      warn "未找到 RabbitMQ definitions.json；无法从旧文本清单自动重建"
    fi
  else
    warn "RabbitMQ 容器未运行，跳过"
  fi
fi

# ---- 配置 ----
if [[ "$RESTORE_CONFIG" == true ]]; then
  log "恢复配置..."
  ENV_BACKUP="${BACKUP_DIR}/config/.env.backup"
  if [[ -f "${ENV_BACKUP}" ]]; then
    read -r -p "覆盖当前 ${ENV_FILE} 文件？[y/N] " confirm
    [[ "${confirm}" =~ ^[yY] ]] && cp "${ENV_BACKUP}" "${ENV_FILE}" && chmod 600 "${ENV_FILE}" && log "  配置已恢复: ${ENV_FILE}" || log "  跳过"
  fi
  if [[ -f "${BACKUP_DIR}/config/docker-compose.override.yml" ]]; then
    cp "${BACKUP_DIR}/config/docker-compose.override.yml" "$ROOT_DIR/docker-compose.override.yml"
    log "  docker-compose.override.yml 已恢复"
  fi
fi

log ""
log "恢复完成。"
log "建议执行以下步骤验证："
log "  1. 重启所有服务: docker compose restart"
log "  2. 检查健康状态: curl http://localhost:8080/actuator/health"
log "  3. 运行 smoke 脚本: bash scripts/business-smoke.sh"
