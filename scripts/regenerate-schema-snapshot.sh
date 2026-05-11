#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_FILE="$ROOT_DIR/docker/mysql/init/01-orin-schema.sql"

HOST="${SCHEMA_SNAPSHOT_HOST:-localhost}"
PORT="${SCHEMA_SNAPSHOT_PORT:-3306}"
USER="${SCHEMA_SNAPSHOT_USER:-root}"
PASS="${SCHEMA_SNAPSHOT_PASS:-password}"
DB="${SCHEMA_SNAPSHOT_DB:-orindb}"

MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

echo "This will dump schema from your local DB at ${HOST}:${PORT}/${DB} as user '${USER}'."
read -r -p "Continue? [y/N] " confirm
case "$confirm" in
  y|Y|yes|YES) ;;
  *)
    echo "Aborted."
    exit 1
    ;;
esac

tmp_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

export MYSQL_PWD="$PASS"

"$MYSQL_BIN" \
  -h "$HOST" \
  -P "$PORT" \
  -u "$USER" \
  -D "$DB" \
  -e "SELECT 1 FROM flyway_schema_history LIMIT 1" >/dev/null

last_version="$("$MYSQL_BIN" \
  -N \
  -h "$HOST" \
  -P "$PORT" \
  -u "$USER" \
  -D "$DB" \
  -e "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL AND success = 1 ORDER BY installed_rank DESC LIMIT 1")"

git_hash="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
generated_at="$(date '+%Y-%m-%d %H:%M:%S %z')"

mkdir -p "$(dirname "$OUT_FILE")"

"$MYSQLDUMP_BIN" \
  -h "$HOST" \
  -P "$PORT" \
  -u "$USER" \
  --single-transaction \
  --routines \
  --triggers \
  --no-data \
  --set-gtid-purged=OFF \
  "$DB" > "$tmp_dir/schema.sql"

"$MYSQLDUMP_BIN" \
  -h "$HOST" \
  -P "$PORT" \
  -u "$USER" \
  --single-transaction \
  --no-create-info \
  --skip-triggers \
  --set-gtid-purged=OFF \
  "$DB" flyway_schema_history > "$tmp_dir/flyway-data.sql"

{
  echo "# ORIN schema snapshot for Docker quickstart"
  echo "# Generated from: ${git_hash} at ${generated_at}"
  echo "# Source: developer local MySQL ${HOST}:${PORT}/${DB}"
  echo "# Future note: once historical Flyway migrations run cleanly on an empty DB, this script can be changed to dump from a fresh temporary container."
  echo "# DO NOT EDIT MANUALLY. Regenerate via scripts/regenerate-schema-snapshot.sh"
  echo "# Snapshot covers Flyway migrations V1 through V${last_version}"
  echo
  cat "$tmp_dir/schema.sql"
  echo
  echo "-- flyway_schema_history data only; no application table data is included."
  cat "$tmp_dir/flyway-data.sql"
} > "$OUT_FILE"

if grep -E '^INSERT INTO `?(sys_user|agent_metadata|knowledge_bases|kb_documents|conversation_logs|audit_logs|task_info|system_message)`?' "$OUT_FILE" >/dev/null; then
  echo "Snapshot sanity check failed: found application data INSERT statements." >&2
  exit 1
fi

echo "Wrote $OUT_FILE"
echo "Latest Flyway version in snapshot: V${last_version}"
