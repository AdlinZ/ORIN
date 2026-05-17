#!/bin/bash
# Read-only static preflight for Docker quickstart wiring.
# This does not require Docker and does not prove containers can start.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE="$ORIN_ROOT/docker-compose.yml"
DEV_COMPOSE="$ORIN_ROOT/docker-compose.dev.yml"
SNAPSHOT="$ORIN_ROOT/docker/mysql/init/01-orin-schema.sql"
SCHEMA_CHECK="$ORIN_ROOT/scripts/check-schema-baseline.sh"

fail() {
    echo "status: FAIL"
    echo "error: $*" >&2
    exit 1
}

require_file() {
    local path="$1"
    local label="$2"
    if [ ! -f "$path" ]; then
        fail "$label missing: ${path#$ORIN_ROOT/}"
    fi
    echo "$label: present"
}

require_text() {
    local file="$1"
    local pattern="$2"
    local label="$3"
    if ! grep -Fq -- "$pattern" "$file"; then
        fail "$label missing: $pattern"
    fi
    echo "$label: OK"
}

require_schema_line() {
    local output="$1"
    local expected="$2"
    local label="$3"
    if ! printf '%s\n' "$output" | grep -Fq -- "$expected"; then
        fail "$label mismatch: expected '$expected'"
    fi
    echo "$label: OK"
}

echo "=== ORIN Docker Quickstart Static Preflight ==="

require_file "$COMPOSE" "docker-compose.yml"
require_file "$DEV_COMPOSE" "docker-compose.dev.yml"
require_file "$SNAPSHOT" "schema-snapshot"
require_file "$SCHEMA_CHECK" "schema-baseline-check"

for service in mysql redis orin-backend orin-ai-engine orin-frontend; do
    require_text "$COMPOSE" "  $service:" "service.$service"
done

require_text "$COMPOSE" "./docker/mysql/init:/docker-entrypoint-initdb.d:ro" "mysql.init-mount"
require_text "$COMPOSE" "      DB_HOST: mysql" "backend.DB_HOST"
require_text "$COMPOSE" "      REDIS_HOST: redis" "backend.REDIS_HOST"
require_text "$COMPOSE" "      ORIN_AI_ENGINE_URL: http://orin-ai-engine:8000" "backend.ORIN_AI_ENGINE_URL"
require_text "$COMPOSE" "      ORIN_BACKEND_URL: http://orin-backend:8080" "ai-engine.ORIN_BACKEND_URL"
require_text "$COMPOSE" "@redis:6379/0" "ai-engine.ORIN_REDIS_URL"
require_text "$COMPOSE" "      orin-backend:" "frontend.depends_on.backend"

for service in orin-backend orin-ai-engine orin-frontend; do
    require_text "$DEV_COMPOSE" "  $service:" "dev-override.service.$service"
done
require_text "$DEV_COMPOSE" "command: [\"mvn\", \"spring-boot:run\"]" "dev-override.backend-command"
require_text "$DEV_COMPOSE" "command: [\"uvicorn\", \"app.main:app\", \"--host\", \"0.0.0.0\", \"--port\", \"8000\", \"--reload\"]" "dev-override.ai-command"
require_text "$DEV_COMPOSE" "command: [\"npm\", \"run\", \"dev\", \"--\", \"--host\", \"0.0.0.0\", \"--port\", \"80\"]" "dev-override.frontend-command"

SCHEMA_OUTPUT="$(bash "$SCHEMA_CHECK")"
require_schema_line "$SCHEMA_OUTPUT" "snapshot-covered-through: V83" "schema.snapshot-covered-through"
require_schema_line "$SCHEMA_OUTPUT" "migration-latest: V87" "schema.migration-latest"
require_schema_line "$SCHEMA_OUTPUT" "pending-after-snapshot: V84,V85,V86,V87" "schema.pending-after-snapshot"
require_schema_line "$SCHEMA_OUTPUT" "next-schema-migration-starts-at: V88" "schema.next-schema-migration-starts-at"
require_schema_line "$SCHEMA_OUTPUT" "status: PASS" "schema.status"

echo "preflight-mode: static-only"
echo "docker-runtime-required-for-real-smoke: yes"
echo "status: PASS"
