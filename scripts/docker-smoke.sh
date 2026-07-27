#!/bin/bash
# Runtime Docker quickstart smoke for ORIN.
# The selected compose prefix is reset on purpose; use docker-smoke-isolated.sh
# for a disposable prefix that never touches a developer's normal stack.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${DOCKER_SMOKE_ENV_FILE:-$ORIN_ROOT/.env}"
if [[ "$ENV_FILE" != /* ]]; then
    ENV_FILE="$ORIN_ROOT/$ENV_FILE"
fi
CREATED_TEMP_ENV=0

if [ ! -f "$ENV_FILE" ]; then
    if [ "$ENV_FILE" = "$ORIN_ROOT/.env" ] && [ -f "$ORIN_ROOT/.env.example" ]; then
        ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/orin-docker-smoke.XXXXXX")"
        cp "$ORIN_ROOT/.env.example" "$ENV_FILE"
        CREATED_TEMP_ENV=1
        echo "env-file: generated temporary environment from .env.example"
    else
        echo "status: FAIL"
        echo "error: env file not found: ${ENV_FILE#$ORIN_ROOT/}" >&2
        exit 1
    fi
fi

COMPOSE_PREFIX="${ORIN_COMPOSE_PREFIX:-$({ grep -E '^ORIN_COMPOSE_PREFIX=' "$ENV_FILE" || true; } | tail -1 | cut -d= -f2-)}"
COMPOSE_PREFIX="${COMPOSE_PREFIX:-orin}"
COMPOSE_PROJECT="${ORIN_COMPOSE_PROJECT:-$COMPOSE_PREFIX}"
COMPOSE_PARALLEL_LIMIT="${ORIN_DOCKER_SMOKE_PARALLEL_LIMIT:-1}"
DOCKER_PULL_RETRIES="${ORIN_DOCKER_SMOKE_PULL_RETRIES:-5}"
DOCKER_SMOKE_FORCE_PULL="${ORIN_DOCKER_SMOKE_FORCE_PULL:-0}"
COMPOSE_ARGS=(--env-file "$ENV_FILE")
MYSQL_CONTAINER="${COMPOSE_PREFIX}-mysql"
REDIS_CONTAINER="${COMPOSE_PREFIX}-redis"
RABBITMQ_CONTAINER="${COMPOSE_PREFIX}-rabbitmq"
AI_ENGINE_CONTAINER="${COMPOSE_PREFIX}-ai-engine"
BACKEND_CONTAINER="${COMPOSE_PREFIX}-backend"
FRONTEND_CONTAINER="${COMPOSE_PREFIX}-frontend"

compose() {
    ORIN_ENV_FILE="$ENV_FILE" ORIN_COMPOSE_PREFIX="$COMPOSE_PREFIX" \
        COMPOSE_PARALLEL_LIMIT="$COMPOSE_PARALLEL_LIMIT" \
        docker compose --project-name "$COMPOSE_PROJECT" "${COMPOSE_ARGS[@]}" "$@"
}

cleanup() {
    local exit_code=$?
    if [ "$exit_code" -ne 0 ]; then
        echo "diagnostics: docker compose ps"
        compose ps || true
        echo "diagnostics: recent backend logs"
        docker logs --tail 160 "$BACKEND_CONTAINER" 2>/dev/null || true
    fi
    if [ "${DOCKER_SMOKE_CLEANUP:-0}" = "1" ]; then
        compose down -v || true
    fi
    if [ "$CREATED_TEMP_ENV" = "1" ]; then
        rm -- "$ENV_FILE"
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

pull_image_with_retry() {
    local image="$1"
    local attempt

    # A local isolated smoke is intended to cold-build ORIN application images,
    # not to make a healthy Docker cache depend on a fresh Docker Hub manifest
    # request. CI has no such cache and will still exercise the real pull path.
    # Set ORIN_DOCKER_SMOKE_FORCE_PULL=1 to explicitly refresh base images.
    if [ "$DOCKER_SMOKE_FORCE_PULL" != "1" ] && docker image inspect "$image" >/dev/null 2>&1; then
        echo "image.pull.${image}: cached"
        return 0
    fi

    for attempt in $(seq 1 "$DOCKER_PULL_RETRIES"); do
        echo "image.pull.${image}: attempt ${attempt}/${DOCKER_PULL_RETRIES}"
        if docker pull "$image"; then
            return 0
        fi
        if [ "$attempt" -lt "$DOCKER_PULL_RETRIES" ]; then
            sleep $((attempt * 5))
        fi
    done
    echo "status: FAIL"
    echo "error: unable to pull required base image after ${DOCKER_PULL_RETRIES} attempts: $image" >&2
    return 1
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
echo "compose-prefix: $COMPOSE_PREFIX"
echo "compose-project: $COMPOSE_PROJECT"
echo "compose-parallel-limit: $COMPOSE_PARALLEL_LIMIT"
echo "image-pull-retries: $DOCKER_PULL_RETRIES"
echo "force-base-image-pull: $DOCKER_SMOKE_FORCE_PULL"
echo "cleanup-on-exit: ${DOCKER_SMOKE_CLEANUP:-0}"

bash "$ORIN_ROOT/scripts/check-docker-quickstart.sh"
compose config --quiet

echo "compose.down: resetting containers and volumes"
compose down -v

echo "image.pull: serial prefetch for reproducible cold build"
for image in \
    mysql:8.0.40 \
    redis:7.4-alpine \
    rabbitmq:3.13-management \
    jaegertracing/all-in-one:1.62.0 \
    maven:3.9.9-eclipse-temurin-17 \
    amazoncorretto:17.0.13-alpine3.20 \
    alpine:3.20.3 \
    python:3.11.10-slim-bookworm \
    node:22.14.0-alpine \
    nginx:1.27.3-alpine-slim; do
    pull_image_with_retry "$image"
done

echo "compose.build: serial application image builds"
for service in orin-backend orin-ai-engine orin-frontend; do
    compose build "$service"
done

echo "compose.up: starting prebuilt services"
compose up -d --no-build

for container in "$MYSQL_CONTAINER" "$REDIS_CONTAINER" "$RABBITMQ_CONTAINER" "$AI_ENGINE_CONTAINER" "$BACKEND_CONTAINER" "$FRONTEND_CONTAINER"; do
    wait_healthy "$container"
done

echo "compose.ps:"
compose ps

BACKEND_HTTP_PORT="${BACKEND_PORT:-$(read_env_value BACKEND_PORT)}"
AI_ENGINE_HTTP_PORT="${AI_ENGINE_PORT:-$(read_env_value AI_ENGINE_PORT)}"
FRONTEND_HTTP_PORT="${FRONTEND_PORT:-$(read_env_value FRONTEND_PORT)}"

require_http "http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}/v1/health" "backend.v1-health"
require_http "http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}/api/v1/health" "backend.api-health"
require_http "http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}/health" "ai-engine.health"
require_http "http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}/v1/health" "ai-engine.v1-health"
require_http "http://127.0.0.1:${FRONTEND_HTTP_PORT:-5173}/" "frontend.homepage"

while IFS= read -r migration_version; do
    FLYWAY_STATUS="$(docker exec "$MYSQL_CONTAINER" sh -lc "mysql -N -B -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \"\$MYSQL_DATABASE\" -e \"SELECT CONCAT(version, ' ', success) FROM flyway_schema_history WHERE version = '$migration_version';\"" 2>/dev/null)"
    if [ "$FLYWAY_STATUS" != "$migration_version 1" ]; then
        echo "status: FAIL"
        echo "error: expected Flyway V${migration_version} success=1, got: $FLYWAY_STATUS" >&2
        exit 1
    fi
done < <(find "$ORIN_ROOT/orin-backend/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*.sql' -print \
    | sed -E 's#.*/V([0-9]+)__.*#\1#' \
    | awk '$1 > 87' | sort -V)
echo "flyway.migrations: all migrations after snapshot V87 applied"

MCP_ROUTE_COUNT="$(docker exec "$MYSQL_CONTAINER" sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM gateway_routes WHERE name IN (\"v1-mcp-streamable-http\", \"v1-mcp-streamable-http-wildcard\");"' 2>/dev/null)"
if [ "$MCP_ROUTE_COUNT" != "2" ]; then
    echo "status: FAIL"
    echo "error: expected 2 MCP gateway routes, got: $MCP_ROUTE_COUNT" >&2
    exit 1
fi
echo "gateway.mcp-routes: 2"

ADMIN_PASSWORD_PREFIX="$(docker exec "$MYSQL_CONTAINER" sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT LEFT(password, 4) FROM sys_user WHERE username = \"admin\";"' 2>/dev/null)"
if [ "$ADMIN_PASSWORD_PREFIX" != "\$2a\$" ]; then
    echo "status: FAIL"
    echo "error: expected default admin password to be BCrypt encoded" >&2
    exit 1
fi
echo "admin.password: bcrypt"

ADMIN_ROLE_COUNT="$(docker exec "$MYSQL_CONTAINER" sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.user_id JOIN sys_role r ON r.role_id = ur.role_id WHERE u.username = \"admin\" AND r.role_code IN (\"ROLE_ADMIN\", \"ROLE_SUPER_ADMIN\");"' 2>/dev/null)"
if ! [[ "$ADMIN_ROLE_COUNT" =~ ^[0-9]+$ ]] || [ "$ADMIN_ROLE_COUNT" -lt 2 ]; then
    echo "status: FAIL"
    echo "error: expected default admin to have ROLE_ADMIN and ROLE_SUPER_ADMIN" >&2
    exit 1
fi
echo "admin.roles: OK"

ORIN_BASE_URL="http://127.0.0.1:${BACKEND_HTTP_PORT:-8080}" \
ORIN_AI_BASE_URL="http://127.0.0.1:${AI_ENGINE_HTTP_PORT:-8000}" \
ORIN_ADMIN_PASSWORD="$(read_env_value ORIN_DEFAULT_ADMIN_PASSWORD)" \
ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED=true \
ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS="${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS:-120}" \
    bash "$ORIN_ROOT/scripts/business-smoke.sh"
echo "business-smoke: OK"

echo "status: PASS"
