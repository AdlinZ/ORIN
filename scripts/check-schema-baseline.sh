#!/bin/bash
# Read-only check for the Docker schema snapshot baseline.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SNAPSHOT="$ORIN_ROOT/docker/mysql/init/01-orin-schema.sql"
MIGRATION_DIR="$ORIN_ROOT/orin-backend/src/main/resources/db/migration"

fail() {
    echo "status: FAIL"
    echo "error: $*" >&2
    exit 1
}

echo "=== ORIN Schema Baseline Check ==="

if [ ! -f "$SNAPSHOT" ]; then
    fail "snapshot file missing: docker/mysql/init/01-orin-schema.sql"
fi
echo "snapshot: present"

if [ ! -d "$MIGRATION_DIR" ]; then
    fail "migration directory missing: orin-backend/src/main/resources/db/migration"
fi

COVERAGE_LINE="$(grep -E '^# Snapshot covers Flyway migrations V[0-9]+ through V[0-9]+' "$SNAPSHOT" | head -1 || true)"
if [ -z "$COVERAGE_LINE" ]; then
    fail "snapshot header does not declare covered Flyway version"
fi

SNAPSHOT_COVERED="$(echo "$COVERAGE_LINE" | sed -E 's/.* through V([0-9]+).*/\1/')"
if ! echo "$SNAPSHOT_COVERED" | grep -Eq '^[0-9]+$'; then
    fail "could not parse snapshot covered version from header"
fi
echo "snapshot-covered-through: V$SNAPSHOT_COVERED"

MIGRATION_VERSIONS="$(
    find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -exec basename {} \; \
        | sed -nE 's/^V([0-9]+)__.*\.sql$/\1/p' \
        | sort -n
)"
if [ -z "$MIGRATION_VERSIONS" ]; then
    fail "no Flyway migration files found"
fi

MIGRATION_LATEST="$(echo "$MIGRATION_VERSIONS" | tail -1)"
echo "migration-latest: V$MIGRATION_LATEST"

PENDING_AFTER_SNAPSHOT="$(
    echo "$MIGRATION_VERSIONS" \
        | awk -v covered="$SNAPSHOT_COVERED" '$1 > covered { printf "%sV%s", sep, $1; sep="," }'
)"

if [ "$MIGRATION_LATEST" -gt "$SNAPSHOT_COVERED" ]; then
    echo "pending-after-snapshot: $PENDING_AFTER_SNAPSHOT"
    echo "baseline-mode: import snapshot, then Flyway applies $PENDING_AFTER_SNAPSHOT"
else
    echo "pending-after-snapshot: none"
    echo "baseline-mode: snapshot already covers all current migrations"
fi

echo "next-schema-migration-starts-at: V$((MIGRATION_LATEST + 1))"
echo "status: PASS"
