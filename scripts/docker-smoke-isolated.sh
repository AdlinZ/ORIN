#!/bin/bash
# Runs the Docker smoke test in a disposable compose namespace and with fresh
# local-only credentials. It never writes the repository .env or touches the
# normal `orin-*` containers, network, or volumes.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_ENV="$(mktemp "${TMPDIR:-/tmp}/orin-docker-smoke-isolated.XXXXXX")"
KEEP_ISOLATED_ENV="${ORIN_ISOLATED_KEEP:-0}"

cleanup() {
    if [ "$KEEP_ISOLATED_ENV" != "1" ]; then
        rm -- "$TMP_ENV"
    fi
}
trap cleanup EXIT

require_command() {
    command -v "$1" >/dev/null 2>&1 || { echo "error: required command not found: $1" >&2; exit 1; }
}

find_free_port() {
    python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

require_command docker
require_command openssl
require_command python3

ORIN_ISO_MYSQL_ROOT="$(openssl rand -hex 24)"
ORIN_ISO_MYSQL_PASSWORD="$(openssl rand -hex 24)"
ORIN_ISO_REDIS_PASSWORD="$(openssl rand -hex 24)"
ORIN_ISO_RABBITMQ_PASSWORD="$(openssl rand -hex 24)"
ORIN_ISO_JWT_SECRET="$(openssl rand -hex 64)"
ORIN_ISO_ADMIN_PASSWORD="$(openssl rand -hex 24)"
ORIN_ISO_PREFIX="orin-smoke-$(date +%s)-$RANDOM"
ORIN_ISO_BACKEND_PORT="$(find_free_port)"
ORIN_ISO_AI_PORT="$(find_free_port)"
ORIN_ISO_FRONTEND_PORT="$(find_free_port)"
ORIN_ISO_MYSQL_PORT="$(find_free_port)"
ORIN_ISO_REDIS_PORT="$(find_free_port)"
ORIN_ISO_RABBITMQ_PORT="$(find_free_port)"
ORIN_ISO_RABBITMQ_MANAGEMENT_PORT="$(find_free_port)"
ORIN_ISO_JAEGER_UI_PORT="$(find_free_port)"
ORIN_ISO_JAEGER_GRPC_PORT="$(find_free_port)"
ORIN_ISO_JAEGER_HTTP_PORT="$(find_free_port)"

export ORIN_ISO_MYSQL_ROOT ORIN_ISO_MYSQL_PASSWORD ORIN_ISO_REDIS_PASSWORD
export ORIN_ISO_RABBITMQ_PASSWORD ORIN_ISO_JWT_SECRET ORIN_ISO_ADMIN_PASSWORD
export ORIN_ISO_PREFIX ORIN_ISO_BACKEND_PORT ORIN_ISO_AI_PORT ORIN_ISO_FRONTEND_PORT
export ORIN_ISO_MYSQL_PORT ORIN_ISO_REDIS_PORT
export ORIN_ISO_RABBITMQ_PORT ORIN_ISO_RABBITMQ_MANAGEMENT_PORT ORIN_ISO_JAEGER_UI_PORT
export ORIN_ISO_JAEGER_GRPC_PORT ORIN_ISO_JAEGER_HTTP_PORT

python3 - "$ORIN_ROOT/.env.example" "$TMP_ENV" <<'PY'
import os
import sys

source, destination = sys.argv[1:]
values = {
    "ORIN_COMPOSE_PREFIX": os.environ["ORIN_ISO_PREFIX"],
    "FRONTEND_PORT": os.environ["ORIN_ISO_FRONTEND_PORT"],
    "BACKEND_PORT": os.environ["ORIN_ISO_BACKEND_PORT"],
    "AI_ENGINE_PORT": os.environ["ORIN_ISO_AI_PORT"],
    "MYSQL_PORT": os.environ["ORIN_ISO_MYSQL_PORT"],
    "REDIS_PORT": os.environ["ORIN_ISO_REDIS_PORT"],
    "RABBITMQ_PORT": os.environ["ORIN_ISO_RABBITMQ_PORT"],
    "RABBITMQ_MANAGEMENT_PORT": os.environ["ORIN_ISO_RABBITMQ_MANAGEMENT_PORT"],
    "JAEGER_UI_PORT": os.environ["ORIN_ISO_JAEGER_UI_PORT"],
    "JAEGER_OTLP_GRPC_PORT": os.environ["ORIN_ISO_JAEGER_GRPC_PORT"],
    "JAEGER_OTLP_HTTP_PORT": os.environ["ORIN_ISO_JAEGER_HTTP_PORT"],
    "MYSQL_ROOT_PASSWORD": os.environ["ORIN_ISO_MYSQL_ROOT"],
    "MYSQL_PASSWORD": os.environ["ORIN_ISO_MYSQL_PASSWORD"],
    "DB_PASSWORD": os.environ["ORIN_ISO_MYSQL_PASSWORD"],
    "REDIS_PASSWORD": os.environ["ORIN_ISO_REDIS_PASSWORD"],
    "RABBITMQ_PASSWORD": os.environ["ORIN_ISO_RABBITMQ_PASSWORD"],
    "ORIN_RABBITMQ_URL": "amqp://orin:%s@rabbitmq:5672/%%2Forin" % os.environ["ORIN_ISO_RABBITMQ_PASSWORD"],
    "JWT_SECRET": os.environ["ORIN_ISO_JWT_SECRET"],
    "ORIN_DEFAULT_ADMIN_PASSWORD": os.environ["ORIN_ISO_ADMIN_PASSWORD"],
    "CORS_ALLOWED_ORIGINS": "http://127.0.0.1:%s" % os.environ["ORIN_ISO_FRONTEND_PORT"],
}
with open(source, encoding="utf-8") as input_file, open(destination, "w", encoding="utf-8") as output_file:
    for line in input_file:
        key = line.split("=", 1)[0].strip()
        output_file.write(f"{key}={values[key]}\n" if key in values else line)
    output_file.write("\nORIN_COMPOSE_PREFIX=%s\n" % values["ORIN_COMPOSE_PREFIX"])
PY

echo "=== ORIN isolated Docker smoke ==="
echo "compose-prefix: $ORIN_ISO_PREFIX"
echo "ports: frontend=$ORIN_ISO_FRONTEND_PORT backend=$ORIN_ISO_BACKEND_PORT ai-engine=$ORIN_ISO_AI_PORT mysql=$ORIN_ISO_MYSQL_PORT redis=$ORIN_ISO_REDIS_PORT"
if [ "$KEEP_ISOLATED_ENV" = "1" ]; then
    echo "isolated environment retained for follow-up verification"
    echo "environment file: $TMP_ENV"
fi
DOCKER_SMOKE_ENV_FILE="$TMP_ENV" DOCKER_SMOKE_CLEANUP="$([ "$KEEP_ISOLATED_ENV" = "1" ] && echo 0 || echo 1)" \
    ORIN_COMPOSE_PREFIX="$ORIN_ISO_PREFIX" \
    ORIN_COMPOSE_PROJECT="$ORIN_ISO_PREFIX" \
    bash "$SCRIPT_DIR/docker-smoke.sh"
