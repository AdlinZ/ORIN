#!/usr/bin/env bash
# Build reviewable release-candidate artifacts from one clean commit.
# Publishing, tagging and image registry credentials remain deliberate manual
# actions after this script and the release checklist are green.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${ORIN_RELEASE_PYTHON:-python3}"
VERSION="$(tr -d '[:space:]' < "$ROOT_DIR/VERSION")"
OUTPUT_DIR="$ROOT_DIR/artifacts/release/$VERSION"
STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/orin-release-${VERSION}.XXXXXX")"

cleanup() {
  [[ -d "$STAGING_DIR" ]] && rm -r -- "$STAGING_DIR"
}
trap cleanup EXIT

fail() {
  echo "release-package: FAIL — $*" >&2
  exit 1
}

for command in git mvn npm node "$PYTHON_BIN" tar shasum docker gzip; do
  command -v "$command" >/dev/null 2>&1 || fail "required command is missing: $command"
done
docker buildx version >/dev/null 2>&1 || fail "Docker Buildx is required; install or enable the buildx plugin before packaging a release candidate"
export DOCKER_BUILDKIT=1

cd "$ROOT_DIR"
ORIN_RELEASE_REQUIRE_CLEAN=1 bash scripts/check-release-version.sh
[[ ! -e "$OUTPUT_DIR" ]] || fail "output already exists: ${OUTPUT_DIR#$ROOT_DIR/}"

echo "release-package: backend"
(
  cd orin-backend
  mvn -B clean verify
  "$PYTHON_BIN" "$ROOT_DIR/scripts/coverage-gate.py" backend \
    target/site/jacoco/jacoco.xml
  cp target/orin-backend.jar "$STAGING_DIR/orin-backend-$VERSION.jar"
)

echo "release-package: frontend"
(
  cd orin-frontend
  npm ci
  npm run lint -- --max-warnings=0
  npx vitest run --coverage --coverage.reporter=text --coverage.reporter=json-summary \
    --coverage.reportsDirectory="$STAGING_DIR/frontend-coverage"
  "$PYTHON_BIN" "$ROOT_DIR/scripts/coverage-gate.py" frontend \
    "$STAGING_DIR/frontend-coverage/coverage-summary.json"
  npm run build
  tar -C dist -czf "$STAGING_DIR/orin-frontend-$VERSION.tar.gz" .
)

echo "release-package: AI Engine"
(
  cd orin-ai-engine
  "$PYTHON_BIN" -m pip install -e . pytest pytest-asyncio pytest-cov
  "$PYTHON_BIN" -m compileall -q app tests
  "$PYTHON_BIN" -m pytest --cov=app --cov-report=xml:coverage.xml
  "$PYTHON_BIN" "$ROOT_DIR/scripts/coverage-gate.py" ai-engine coverage.xml
  "$PYTHON_BIN" -m pip wheel --no-deps . --wheel-dir "$STAGING_DIR"
)

echo "release-package: MCP Bridge"
(
  cd orin-mcp-bridge
  "$PYTHON_BIN" -m pip install -e . pytest
  "$PYTHON_BIN" -m pytest
  "$PYTHON_BIN" -m pip wheel --no-deps . --wheel-dir "$STAGING_DIR"
)

RUNNER_IMAGE="orin-runner:${VERSION}"
echo "release-package: Runner image (${RUNNER_IMAGE})"
docker buildx build --load --pull=false --tag "$RUNNER_IMAGE" \
  --file "$ROOT_DIR/orin-ai-engine/Dockerfile.runner" \
  "$ROOT_DIR/orin-ai-engine"
docker run --rm "$RUNNER_IMAGE" --help >/dev/null
docker image save "$RUNNER_IMAGE" | gzip -n > "$STAGING_DIR/orin-runner-${VERSION}.tar.gz"
RUNNER_IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$RUNNER_IMAGE")"

cat > "$STAGING_DIR/manifest.txt" <<EOF
ORIN release candidate
version: $VERSION
commit: $(git rev-parse HEAD)
runner_image: $RUNNER_IMAGE
runner_image_id: $RUNNER_IMAGE_ID
EOF
(
  cd "$STAGING_DIR"
  shasum -a 256 ./* > SHA256SUMS
)

mkdir -p "$(dirname "$OUTPUT_DIR")"
mv "$STAGING_DIR" "$OUTPUT_DIR"
trap - EXIT
echo "release-package: PASS — ${OUTPUT_DIR#$ROOT_DIR/}"
