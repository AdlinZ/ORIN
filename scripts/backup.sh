#!/bin/bash
# ORIN 备份脚本
# 用法: ./backup.sh [--keep N]
#
# 备份内容：
#   - MySQL 数据库（orindb）完整 dump
#   - Redis 数据（可选，RDB snapshot）
#   - RabbitMQ 定义（vhost、exchange、queue 声明，不含消息）
#   - ORIN 配置文件（.env、docker-compose.override.yml 等）
#   - 各模块 JAR / dist 包（如已构建）
#
# 保留策略：默认保留最近 7 份，按 --keep N 覆盖
# 备份目录：./backups/orin-YYYYMMDD-HHMMSS/

set -e

# ========== 配置 ==========
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
BACKUP_BASE="${BACKUP_BASE:-$(cd "$(dirname "$0")/.." && pwd)/backups}"
KEEP_N="${KEEP_N:-7}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="${BACKUP_BASE}/orin-${TIMESTAMP}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-orin-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-orin-redis}"
RABBITMQ_CONTAINER="${RABBITMQ_CONTAINER:-orin-rabbitmq}"
ENV_FILE="${ENV_FILE:-.env}"

log()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ========== 参数解析 ==========
while [[ $# -gt 0 ]]; do
  case $1 in
    --keep) KEEP_N="$2"; shift 2 ;;
    --help)
      echo "用法: $0 [--keep N]"
      echo "  --keep N  保留最近 N 份备份（默认 7）"
      exit 0 ;;
    *) shift ;;
  esac
done

log "开始备份 ORIN..."
log "备份目录: ${BACKUP_DIR}"

mkdir -p "${BACKUP_DIR}/mysql" "${BACKUP_DIR}/redis" "${BACKUP_DIR}/rabbitmq" "${BACKUP_DIR}/config" "${BACKUP_DIR}/artifacts"

# ---- 1. MySQL ----
log "备份 MySQL..."
if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  docker exec "${MYSQL_CONTAINER}" mysqldump \
    -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" --single-transaction \
    --routines --events --triggers \
    orindb 2>/dev/null | gzip > "${BACKUP_DIR}/mysql/orindb.sql.gz"
  log "  MySQL dump 完成: mysql/orindb.sql.gz"
else
  warn "MySQL 容器未运行，跳过数据库备份"
fi

# ---- 2. Redis ----
log "备份 Redis..."
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
  docker exec "${REDIS_CONTAINER}" redis-cli BGSAVE > /dev/null 2>&1 || true
  sleep 2
  docker cp "${REDIS_CONTAINER}:/data/dump.rdb" "${BACKUP_DIR}/redis/dump.rdb" 2>/dev/null \
    && log "  Redis RDB 完成: redis/dump.rdb" \
    || warn "Redis RDB 不可用（可能无持久化）"
else
  warn "Redis 容器未运行，跳过"
fi

# ---- 3. RabbitMQ 定义 ----
log "备份 RabbitMQ..."
if docker ps --format '{{.Names}}' | grep -q "^${RABBITMQ_CONTAINER}$"; then
  docker exec "${RABBITMQ_CONTAINER}" sh -c \
    "rabbitmqctl list_exchanges -p / | grep -v '^Listing' | tail -n +2" \
    > "${BACKUP_DIR}/rabbitmq/exchanges.txt" 2>/dev/null || true
  docker exec "${RABBITMQ_CONTAINER}" sh -c \
    "rabbitmqctl list_queues -p / | grep -v '^Listing' | tail -n +2" \
    > "${BACKUP_DIR}/rabbitmq/queues.txt" 2>/dev/null || true
  docker exec "${RABBITMQ_CONTAINER}" sh -c \
    "rabbitmqctl list_bindings -p / | grep -v '^Listing' | tail -n +2" \
    > "${BACKUP_DIR}/rabbitmq/bindings.txt" 2>/dev/null || true
  log "  RabbitMQ 定义完成: rabbitmq/"
else
  warn "RabbitMQ 容器未运行，跳过"
fi

# ---- 4. 配置文件 ----
log "备份配置..."
if [[ -f "${ENV_FILE}" ]]; then
  cp "${ENV_FILE}" "${BACKUP_DIR}/config/.env.backup"
  log "  配置备份: config/.env.backup"
fi
if [[ -f "docker-compose.override.yml" ]]; then
  cp "docker-compose.override.yml" "${BACKUP_DIR}/config/docker-compose.override.yml"
fi
if [[ -f "docker-compose.yml" ]]; then
  cp "docker-compose.yml" "${BACKUP_DIR}/config/docker-compose.yml"
fi

# ---- 5. 已构建产物（可选）----
if [[ -f "orin-backend/target/orin-backend-1.0.0.jar" ]]; then
  cp "orin-backend/target/orin-backend-1.0.0.jar" "${BACKUP_DIR}/artifacts/" 2>/dev/null \
    && log "  后端 JAR: artifacts/orin-backend-1.0.0.jar"
fi
if [[ -d "orin-frontend/dist" ]]; then
  tar -czf "${BACKUP_DIR}/artifacts/frontend-dist.tar.gz" -C orin-frontend dist 2>/dev/null \
    && log "  前端 dist: artifacts/frontend-dist.tar.gz"
fi

# ---- 6. 元信息 ----
cat > "${BACKUP_DIR}/metadata.txt" <<EOF
ORIN Backup Metadata
====================
Backup time  : ${TIMESTAMP}
Hostname     : $(hostname)
ORIN version : $(git describe --tags 2>/dev/null || echo "unknown")
Git commit   : $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
MySQL        : ${MYSQL_CONTAINER}
Redis        : ${REDIS_CONTAINER}
RabbitMQ     : ${RABBITMQ_CONTAINER}
EOF

# ---- 7. SHA256 校验 ----
log "生成校验和..."
find "${BACKUP_DIR}" -type f -name "*.gz" -o -type f -name "*.sql" -o -type f -name "*.rdb" -o -type f -name "*.txt" -o -type f -name ".env*" | while read -r f; do
  sha256sum "$f" >> "${BACKUP_DIR}/SHA256SUMS.txt"
done
log "  校验和: SHA256SUMS.txt"

# ---- 8. 清理过期备份 ----
log "保留最近 ${KEEP_N} 份备份..."
ls -dt "${BACKUP_BASE}"/orin-*/ 2>/dev/null | tail -n +$((KEEP_N + 1)) | while read -r d; do
  warn "删除过期备份: $d"
  rm -rf "$d"
done

log "备份完成: ${BACKUP_DIR}"
echo ""
log "恢复命令: ./restore.sh ${BACKUP_DIR}"
