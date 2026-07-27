#!/usr/bin/env bash
# Verify that every distributable ORIN component carries the release version.
# This script deliberately never reads .env or prints environment variables.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/VERSION"

fail() {
  echo "release-version: FAIL — $*" >&2
  exit 1
}

[[ -f "$VERSION_FILE" ]] || fail "VERSION file is missing"
VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || fail "VERSION must be a SemVer-compatible value"

require_literal() {
  local file="$1"
  local expected="$2"
  grep -Fqx "$expected" "$file" >/dev/null || fail "expected version declaration not found in ${file#$ROOT_DIR/}"
}

require_literal "$ROOT_DIR/orin-backend/pom.xml" "    <version>$VERSION</version>"
require_literal "$ROOT_DIR/orin-frontend/package.json" "  \"version\": \"$VERSION\","
require_literal "$ROOT_DIR/orin-frontend/package-lock.json" "  \"version\": \"$VERSION\","
require_literal "$ROOT_DIR/orin-ai-engine/pyproject.toml" "version = \"$VERSION\""
require_literal "$ROOT_DIR/orin-mcp-bridge/pyproject.toml" "version = \"$VERSION\""

grep -Fq "version=\"$VERSION\"" "$ROOT_DIR/orin-ai-engine/app/main.py" || fail "AI Engine runtime version differs from VERSION"
grep -Fq "RUNNER_VERSION = \"$VERSION\"" "$ROOT_DIR/orin-ai-engine/app/runner/enrollment.py" || fail "Runner runtime version differs from VERSION"
grep -Fq "VERSION = \"$VERSION\"" "$ROOT_DIR/orin-mcp-bridge/orin_mcp_bridge/bridge.py" || fail "MCP bridge runtime version differs from VERSION"
grep -Fq "\"version\", \"$VERSION\"" "$ROOT_DIR/orin-backend/src/main/java/com/adlin/orin/modules/mcp/service/McpJsonRpcService.java" || fail "MCP serverInfo version differs from VERSION"
grep -Fq "info.app.version=$VERSION" "$ROOT_DIR/orin-backend/src/main/resources/application.properties" || fail "Backend actuator version differs from VERSION"

if [[ "${ORIN_RELEASE_REQUIRE_CLEAN:-0}" == "1" ]] && [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
  fail "working tree has tracked changes; commit or stash them before a release"
fi

echo "release-version: PASS ($VERSION)"
