#!/bin/bash
# Verify that every shipped ORIN component reports the repository version.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSION_FILE="$ORIN_ROOT/VERSION"

fail() {
    echo "status: FAIL"
    echo "error: $*" >&2
    exit 1
}

if [ ! -f "$VERSION_FILE" ]; then
    fail "VERSION file is missing"
fi

ORIN_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
if ! echo "$ORIN_VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$'; then
    fail "VERSION is not a supported semantic version"
fi

check_literal() {
    local relative_path="$1"
    local expected="$2"
    if ! grep -Fq "$expected" "$ORIN_ROOT/$relative_path"; then
        fail "$relative_path does not declare $ORIN_VERSION"
    fi
    echo "$relative_path: OK"
}

check_value() {
    local label="$1"
    local actual="$2"
    if [ "$actual" != "$ORIN_VERSION" ]; then
        fail "$label declares '${actual:-missing}', expected '$ORIN_VERSION'"
    fi
    echo "$label: OK"
}

echo "=== ORIN Version Consistency Check ==="
echo "canonical-version: $ORIN_VERSION"

BACKEND_VERSION="$(sed -n '1,12p' "$ORIN_ROOT/orin-backend/pom.xml" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' | head -1)"
FRONTEND_VERSION="$(sed -n 's/^[[:space:]]*"version": "\([^"]*\)",/\1/p' "$ORIN_ROOT/orin-frontend/package.json" | head -1)"
FRONTEND_LOCK_VERSION="$(sed -n '1,6p' "$ORIN_ROOT/orin-frontend/package-lock.json" | sed -n 's/^[[:space:]]*"version": "\([^"]*\)",/\1/p' | head -1)"
AI_ENGINE_VERSION="$(sed -n 's/^version = "\([^"]*\)"/\1/p' "$ORIN_ROOT/orin-ai-engine/pyproject.toml" | head -1)"
MCP_BRIDGE_VERSION="$(sed -n 's/^version = "\([^"]*\)"/\1/p' "$ORIN_ROOT/orin-mcp-bridge/pyproject.toml" | head -1)"

check_value "orin-backend/pom.xml" "$BACKEND_VERSION"
check_value "orin-frontend/package.json" "$FRONTEND_VERSION"
check_value "orin-frontend/package-lock.json" "$FRONTEND_LOCK_VERSION"
check_value "orin-ai-engine/pyproject.toml" "$AI_ENGINE_VERSION"
check_value "orin-mcp-bridge/pyproject.toml" "$MCP_BRIDGE_VERSION"

check_literal "orin-backend/src/main/resources/application.properties" "info.app.version=$ORIN_VERSION"
check_literal "orin-backend/src/main/java/com/adlin/orin/modules/mcp/service/McpJsonRpcService.java" "\"version\", \"$ORIN_VERSION\""
check_literal "orin-ai-engine/app/main.py" "version=\"$ORIN_VERSION\""
check_literal "orin-mcp-bridge/orin_mcp_bridge/bridge.py" "VERSION = \"$ORIN_VERSION\""
check_literal "scripts/deploy-low-memory.sh" "orin-backend-$ORIN_VERSION.jar"
check_literal "deploy_ubuntu.sh" "orin-backend-$ORIN_VERSION.jar"
check_literal "docs/部署指南.md" "orin-backend-$ORIN_VERSION.jar"
check_literal "README.md" "当前产品线：\`$ORIN_VERSION\`"
check_literal "CHANGELOG.md" "Unreleased - v$ORIN_VERSION"

echo "status: PASS"
