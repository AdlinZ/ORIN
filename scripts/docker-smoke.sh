#!/bin/bash
# Runtime Docker quickstart smoke for ORIN.
# Destructive by design: resets the compose stack and named volumes first.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${DOCKER_SMOKE_ENV_FILE:-$ORIN_ROOT/.env}"
if [[ "$ENV_FILE" != /* ]]; then
    ENV_FILE="$ORIN_ROOT/$ENV_FILE"
fi
COMPOSE_ARGS=(--env-file "$ENV_FILE")
CREATED_TEMP_ENV=0

if [ ! -f "$ENV_FILE" ]; then
    if [ "$ENV_FILE" = "$ORIN_ROOT/.env" ] && [ -f "$ORIN_ROOT/.env.example" ]; then
        cp "$ORIN_ROOT/.env.example" "$ORIN_ROOT/.env"
        CREATED_TEMP_ENV=1
        echo "env-file: generated temporary .env from .env.example"
    else
        echo "status: FAIL"
        echo "error: env file not found: ${ENV_FILE#$ORIN_ROOT/}" >&2
        exit 1
    fi
fi

if [ ! -f "$ORIN_ROOT/.env" ]; then
    cp "$ENV_FILE" "$ORIN_ROOT/.env"
    CREATED_TEMP_ENV=1
    echo "env-file: generated temporary .env for compose service env_file"
fi

cleanup() {
    local exit_code=$?
    if [ "$exit_code" -ne 0 ]; then
        echo "diagnostics: docker compose ps"
        docker compose "${COMPOSE_ARGS[@]}" ps || true
        echo "diagnostics: recent backend logs"
        docker logs --tail 160 orin-backend 2>/dev/null || true
    fi
    if [ "${DOCKER_SMOKE_CLEANUP:-0}" = "1" ]; then
        docker compose "${COMPOSE_ARGS[@]}" down -v || true
    fi
    if [ "$CREATED_TEMP_ENV" = "1" ]; then
        rm -f "$ORIN_ROOT/.env"
    fi
}
trap cleanup EXIT

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "status: FAIL"
        echo "error: required command not found: $1" >&2
        exit 1
    fi
}

wait_healthy() {
    local container="$1"
    local attempts="${2:-60}"
    local delay="${3:-5}"
    local status=""

    for _ in $(seq 1 "$attempts"); do
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)"
        echo "health.$container: $status"
        if [ "$status" = "healthy" ]; then
            return 0
        fi
        sleep "$delay"
    done

    echo "status: FAIL"
    echo "error: $container did not become healthy" >&2
    return 1
}

require_http() {
    local url="$1"
    local label="$2"
    local attempts="${3:-10}"
    local delay="${4:-2}"

    for _ in $(seq 1 "$attempts"); do
        if curl -fsS --noproxy "*" "$url" >/dev/null; then
            echo "$label: OK"
            return 0
        fi
        sleep "$delay"
    done

    echo "status: FAIL"
    echo "error: $label did not respond successfully at $url" >&2
    return 1
}

read_env_value() {
    local key="$1"
    grep -E "^${key}=" "$ENV_FILE" | tail -1 | cut -d= -f2- | sed "s/^['\"]//;s/['\"]$//"
}

require_command docker
require_command curl

cd "$ORIN_ROOT"

echo "=== ORIN Docker Runtime Smoke ==="
echo "env-file: ${ENV_FILE#$ORIN_ROOT/}"
echo "cleanup-on-exit: ${DOCKER_SMOKE_CLEANUP:-0}"

bash "$ORIN_ROOT/scripts/check-docker-quickstart.sh"
docker compose "${COMPOSE_ARGS[@]}" config --quiet

echo "compose.down: resetting containers and volumes"
docker compose "${COMPOSE_ARGS[@]}" down -v

echo "compose.up: building and starting"
docker compose "${COMPOSE_ARGS[@]}" up --build -d

for container in orin-mysql orin-redis orin-rabbitmq orin-ai-engine orin-backend orin-frontend; do
    wait_healthy "$container"
done

echo "compose.ps:"
docker compose "${COMPOSE_ARGS[@]}" ps

BACKEND_HTTP_PORT="${BACKEND_PORT:-$(read_env_value BACKEND_PORT)}"
AI_ENGINE_HTTP_PORT="${AI_ENGINE_PORT:-$(read_env_value AI_ENGINE_PORT)}"
FRONTEND_HTTP_PORT="${FRONTEND_PORT:-$(read_env_value FRONTEND_PORT)}"

require_http "http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}/v1/health" "backend.v1-health"
require_http "http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}/api/v1/health" "backend.api-health"
require_http "http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}/health" "ai-engine.health"
require_http "http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}/v1/health" "ai-engine.v1-health"
require_http "http://127.0.0.1:${FRONTEND_HTTP_PORT:-5173}/" "frontend.homepage"

FLYWAY_STATUS="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT CONCAT(version, \" \", success) FROM flyway_schema_history WHERE version = \"88\";"' 2>/dev/null)"
if [ "$FLYWAY_STATUS" != "88 1" ]; then
    echo "status: FAIL"
    echo "error: expected Flyway V88 success=1, got: $FLYWAY_STATUS" >&2
    exit 1
fi
echo "flyway.v88: success"

FLYWAY_STATUS="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT CONCAT(version, \" \", success) FROM flyway_schema_history WHERE version = \"89\";"' 2>/dev/null)"
if [ "$FLYWAY_STATUS" != "89 1" ]; then
    echo "status: FAIL"
    echo "error: expected Flyway V89 success=1, got: $FLYWAY_STATUS" >&2
    exit 1
fi
echo "flyway.v89: success"

FLYWAY_STATUS="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT CONCAT(version, \" \", success) FROM flyway_schema_history WHERE version = \"90\";"' 2>/dev/null)"
if [ "$FLYWAY_STATUS" != "90 1" ]; then
    echo "status: FAIL"
    echo "error: expected Flyway V90 success=1, got: $FLYWAY_STATUS" >&2
    exit 1
fi
echo "flyway.v90: success"

MCP_ROUTE_COUNT="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM gateway_routes WHERE name IN (\"v1-mcp-streamable-http\", \"v1-mcp-streamable-http-wildcard\");"' 2>/dev/null)"
if [ "$MCP_ROUTE_COUNT" != "2" ]; then
    echo "status: FAIL"
    echo "error: expected 2 MCP gateway routes, got: $MCP_ROUTE_COUNT" >&2
    exit 1
fi
echo "gateway.mcp-routes: 2"

ADMIN_PASSWORD_PREFIX="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT LEFT(password, 4) FROM sys_user WHERE username = \"admin\";"' 2>/dev/null)"
if [ "$ADMIN_PASSWORD_PREFIX" != "\$2a\$" ]; then
    echo "status: FAIL"
    echo "error: expected default admin password to be BCrypt encoded" >&2
    exit 1
fi
echo "admin.password: bcrypt"

ADMIN_ROLE_COUNT="$(docker exec orin-mysql sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.user_id JOIN sys_role r ON r.role_id = ur.role_id WHERE u.username = \"admin\" AND r.role_code IN (\"ROLE_ADMIN\", \"ROLE_SUPER_ADMIN\");"' 2>/dev/null)"
if ! [[ "$ADMIN_ROLE_COUNT" =~ ^[0-9]+$ ]] || [ "$ADMIN_ROLE_COUNT" -lt 2 ]; then
    echo "status: FAIL"
    echo "error: expected default admin to have ROLE_ADMIN and ROLE_SUPER_ADMIN" >&2
    exit 1
fi
echo "admin.roles: OK"

ORIN_BASE_URL="http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}" \
ORIN_AI_BASE_URL="http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}" \
ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED=true \
ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS="${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS:-120}" \
    bash "$ORIN_ROOT/scripts/business-smoke.sh"
echo "business-smoke: OK"

echo "status: PASS"
