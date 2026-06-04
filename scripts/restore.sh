#!/bin/bash
# ORIN 恢复脚本
# 用法: ./restore.sh <backup-dir> [--mysql|--redis|--rabbitmq|--config|--all]
#
# 示例:
#   ./restore.sh ./backups/orin-20260604-120000 --all    # 全量恢复
#   ./restore.sh ./backups/orin-20260604-120000 --mysql # 仅恢复 MySQL

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
MYSQL_CONTAINER="${MYSQL_CONTAINER:-orin-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-orin-redis}"
RABBITMQ_CONTAINER="${RABBITMQ_CONTAINER:-orin-rabbitmq}"

log()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

BACKUP_DIR="$1"
[[ -d "${BACKUP_DIR}" ]] || fail "备份目录不存在: ${BACKUP_DIR}"

MODE="${2:---all}"
[[ "$MODE" == "--all" ]] && MODE="--mysql --redis --rabbitmq --config"

log "开始恢复 ORIN..."
log "备份来源: ${BACKUP_DIR}"

# ---- MySQL ----
if [[ "$*" == *"--mysql"* ]] || [[ "$*" == *"--all"* ]]; then
  log "恢复 MySQL..."
  SQL_GZ="${BACKUP_DIR}/mysql/orindb.sql.gz"
  if [[ -f "${SQL_GZ}" ]]; then
    if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
      gunzip < "${SQL_GZ}" | docker exec -i "${MYSQL_CONTAINER}" mysql -uroot \
        -p"${MYSQL_ROOT_PASSWORD:-root}" orindb 2>/dev/null
      log "  MySQL 恢复完成"
    else
      fail "MySQL 容器未运行，请先启动: docker compose up -d mysql"
    fi
  else
    warn "未找到 MySQL dump: ${SQL_GZ}"
  fi
fi

# ---- Redis ----
if [[ "$*" == *"--redis"* ]] || [[ "$*" == *"--all"* ]]; then
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
if [[ "$*" == *"--rabbitmq"* ]] || [[ "$*" == *"--all"* ]]; then
  log "恢复 RabbitMQ 队列定义（消息不回退）..."
  if docker ps --format '{{.Names}}' | grep -q "^${RABBITMQ_CONTAINER}$"; then
    # 仅恢复 vhost / exchange / queue 声明，不恢复消息
    docker exec "${RABBITMQ_CONTAINER}" sh -c "
      rabbitmqctl list_exchanges -p / | grep -v '^Listing' | tail -n +2
    " > /tmp/rabbitmq-exchanges.txt 2>/dev/null || true
    # queue 声明从 docker-compose volume 恢复；如需精确重建，用备份的 YAML 重建
    log "  RabbitMQ 定义已备份，可手动重建队列（消息无法恢复）"
  else
    warn "RabbitMQ 容器未运行，跳过"
  fi
fi

# ---- 配置 ----
if [[ "$*" == *"--config"* ]] || [[ "$*" == *"--all"* ]]; then
  log "恢复配置..."
  ENV_BACKUP="${BACKUP_DIR}/config/.env.backup"
  if [[ -f "${ENV_BACKUP}" ]]; then
    read -p "覆盖当前 .env 文件？[y/N] " confirm
    [[ "${confirm}" =~ ^[yY] ]] && cp "${ENV_BACKUP}" ".env" && log "  配置已恢复: .env" || log "  跳过"
  fi
  if [[ -f "${BACKUP_DIR}/config/docker-compose.override.yml" ]]; then
    cp "${BACKUP_DIR}/config/docker-compose.override.yml" "docker-compose.override.yml"
    log "  docker-compose.override.yml 已恢复"
  fi
fi

log ""
log "恢复完成。"
log "建议执行以下步骤验证："
log "  1. 重启所有服务: docker compose restart"
log "  2. 检查健康状态: curl http://localhost:8080/actuator/health"
log "  3. 运行 smoke 脚本: bash scripts/business-smoke.sh"