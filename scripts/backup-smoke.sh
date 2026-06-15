#!/usr/bin/env bash
# ORIN 备份静态完整性验证（Phase 3 起步：小刀 7）。
#
# 用途：每次 backup 后（或 cron 中）跑，验证备份目录结构 + 关键文件
# 完整 + 脚本本身语法 + 关键模式。不**真**恢复（避免误覆盖线上）。
#
# 用法：
#   ./scripts/backup-smoke.sh <backup-dir>
#
# 退出码：
#   0 = OK
#   1 = 关键文件缺失 / 不可读
#   2 = 参数错 / 脚本本身语法错
#   3 = mysql/redis/rabbitmq 子目录缺关键产物

set -euo pipefail

# ---- env defaults ----

BACKUP_DIR="${1:-}"
SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPTS_DIR}/.." && pwd)"

WARN_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

step()  { printf '\n=== %s ===\n' "$*"; }
warn()  { printf '  WARN: %s\n' "$*" >&2; WARN_COUNT=$((WARN_COUNT+1)); }
fail()  { printf '  FAIL: %s\n' "$*" >&2; FAIL_COUNT=$((FAIL_COUNT+1)); }
skip()  { printf '  SKIP: %s\n' "$*" >&2; SKIP_COUNT=$((SKIP_COUNT+1)); }
ok()    { printf '  OK:   %s\n' "$*"; }

# ---- 1) 参数校验 ----

step "Pre-check: backup dir + scripts exist"
if [[ -z "${BACKUP_DIR}" ]]; then
  printf 'usage: %s <backup-dir>\n' "$0" >&2
  exit 2
fi
if [[ ! -d "${BACKUP_DIR}" ]]; then
  fail "备份目录不存在: ${BACKUP_DIR}"
  exit 2
fi
ok "backup dir: ${BACKUP_DIR}"

for script in backup.sh restore.sh rollback.sh; do
  if [[ ! -f "${SCRIPTS_DIR}/${script}" ]]; then
    fail "缺失脚本: ${SCRIPTS_DIR}/${script}"
  else
    ok "script exists: ${script}"
  fi
done

# ---- 2) 三个脚本的 bash 语法 ----

step "Bash syntax check on 3 backup scripts"
for script in backup.sh restore.sh rollback.sh; do
  if bash -n "${SCRIPTS_DIR}/${script}" 2>/dev/null; then
    ok "bash -n ${script}"
  else
    fail "bash -n ${script} FAILED"
  fi
done

# ---- 3) 关键模式 grep（防脚本被无意改坏）----

step "Critical pattern check (防 backup.sh 关键命令被无意改坏)"
# 关键模式拆为**独立子串**（grep 不跨行，多行 sh -c 包装的 `docker exec ... X`
# 形式不在同一行；拆为多个独立 grep 校验）
REQUIRED_PATTERNS=(
  "set -e"
  "docker exec"
  "mysqldump"
  "orindb"
  "BGSAVE"
  "list_exchanges"
  "list_queues"
  "list_bindings"
  "rabbitmqctl"
)
for pat in "${REQUIRED_PATTERNS[@]}"; do
  if grep -qF "${pat}" "${SCRIPTS_DIR}/backup.sh"; then
    ok "backup.sh 包含: ${pat}"
  else
    fail "backup.sh 缺: ${pat}"
  fi
done

step "Critical pattern check (restore.sh)"
RESTORE_PATTERNS=(
  "docker exec"
  "mysql"
  "redis"
  "rabbitmqctl"
)
for pat in "${RESTORE_PATTERNS[@]}"; do
  if grep -qF "${pat}" "${SCRIPTS_DIR}/restore.sh"; then
    ok "restore.sh 包含: ${pat}"
  else
    fail "restore.sh 缺: ${pat}"
  fi
done

# ---- 4) 备份目录结构 ----

step "Backup directory structure: ${BACKUP_DIR}"
REQUIRED_SUBDIRS=(mysql redis rabbitmq config)
for sub in "${REQUIRED_SUBDIRS[@]}"; do
  if [[ -d "${BACKUP_DIR}/${sub}" ]]; then
    ok "subdir exists: ${sub}/"
  else
    fail "subdir missing: ${sub}/"
  fi
done

if [[ ! -f "${BACKUP_DIR}/metadata.txt" ]]; then
  fail "metadata.txt 缺失"
else
  ok "metadata.txt 存在"
fi

# ---- 5) MySQL dump 完整性 ----

step "MySQL dump integrity"
MYSQL_DUMP="${BACKUP_DIR}/mysql/orindb.sql.gz"
if [[ ! -f "${MYSQL_DUMP}" ]]; then
  skip "MySQL dump 缺失（容器未运行？或备份跳过？）"
elif ! file "${MYSQL_DUMP}" 2>/dev/null | grep -qi "gzip"; then
  fail "MySQL dump 不是 gzip 格式: ${MYSQL_DUMP}"
else
  # 试解 gzip 头（前 8 字节是 gzip magic + 一些 metadata）
  if gunzip -t "${MYSQL_DUMP}" 2>/dev/null; then
    ok "MySQL dump gzip 完整可解压"
    # 检查文件大小（空 dump 是异常）
    SIZE=$(stat -f%z "${MYSQL_DUMP}" 2>/dev/null || stat -c%s "${MYSQL_DUMP}" 2>/dev/null)
    if [[ "${SIZE}" -lt 200 ]]; then
      fail "MySQL dump 异常小（${SIZE} 字节），可能是空 dump"
    else
      ok "MySQL dump 大小合理: ${SIZE} 字节"
    fi
    # 检 mysqldump header
    FIRST_LINE=$(gunzip -c "${MYSQL_DUMP}" 2>/dev/null | head -1)
    if echo "${FIRST_LINE}" | grep -qi "mysqldump"; then
      ok "MySQL dump 头部合法: '${FIRST_LINE:0:60}...'"
    else
      fail "MySQL dump 头部不像 mysqldump: '${FIRST_LINE:0:60}'"
    fi
  else
    fail "MySQL dump gzip 损坏（gunzip -t 失败）"
  fi
fi

# ---- 6) Redis RDB 完整性 ----

step "Redis RDB integrity"
REDIS_RDB="${BACKUP_DIR}/redis/dump.rdb"
if [[ ! -f "${REDIS_RDB}" ]]; then
  skip "Redis RDB 缺失（容器未运行？或无持久化？）"
elif [[ ! -s "${REDIS_RDB}" ]]; then
  fail "Redis RDB 空文件"
else
  # Redis RDB 头部 magic: "REDIS" + 4 字节版本号
  HEADER=$(head -c 5 "${REDIS_RDB}" 2>/dev/null)
  if [[ "${HEADER}" == "REDIS" ]]; then
    ok "Redis RDB 头部合法: ${HEADER}"
  else
    fail "Redis RDB 头部不像 Redis: '${HEADER}'"
  fi
fi

# ---- 7) RabbitMQ 定义完整性 ----

step "RabbitMQ definitions integrity"
for f in exchanges.txt queues.txt bindings.txt; do
  PATH_F="${BACKUP_DIR}/rabbitmq/${f}"
  if [[ ! -f "${PATH_F}" ]]; then
    fail "RabbitMQ 定义文件缺失: rabbitmq/${f}"
  elif [[ ! -s "${PATH_F}" ]]; then
    warn "RabbitMQ ${f} 空（vhost 也许没声明任何 exchange/queue/binding）"
  else
    LINES=$(wc -l <"${PATH_F}")
    ok "rabbitmq/${f}: ${LINES} 行"
  fi
done

# ---- 8) 配置完整性 ----

step "Config integrity"
if [[ ! -f "${BACKUP_DIR}/config/.env.backup" ]]; then
  warn "config/.env.backup 缺失（备份时 .env 不存在？）"
else
  ok ".env.backup 存在"
  # 关键变量抽检
  for key in MYSQL_ROOT_PASSWORD JWT_SECRET; do
    if grep -q "^${key}=" "${BACKUP_DIR}/config/.env.backup"; then
      ok ".env.backup 包含 ${key}"
    else
      warn ".env.backup 缺 ${key}"
    fi
  done
fi

# ---- 9) metadata.txt 字段 ----

step "Metadata.txt fields"
if [[ -f "${BACKUP_DIR}/metadata.txt" ]]; then
  for field in timestamp commit container; do
    if grep -qi "${field}" "${BACKUP_DIR}/metadata.txt"; then
      ok "metadata.txt 含 ${field}"
    else
      warn "metadata.txt 缺 ${field}（非阻断）"
    fi
  done
fi

# ---- 汇总 ----

step "Summary"
printf '  backup dir : %s\n' "${BACKUP_DIR}"
printf '  warns      : %d\n' "${WARN_COUNT}"
printf '  fails      : %d\n' "${FAIL_COUNT}"
printf '  skips      : %d\n' "${SKIP_COUNT}"

if [[ "${FAIL_COUNT}" -gt 0 ]]; then
  printf '\nbackup-smoke FAIL: %d critical issue(s)\n' "${FAIL_COUNT}"
  exit 1
fi
printf '\nbackup-smoke OK\n'
exit 0
