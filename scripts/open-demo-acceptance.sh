#!/usr/bin/env bash
# ORIN open-demo acceptance runner.
# Creates only temporary demo resources and never prints JWT/API keys.

set -euo pipefail

ORIN_BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
ORIN_AI_BASE_URL="${ORIN_AI_BASE_URL:-http://127.0.0.1:8000}"
ORIN_ADMIN_USERNAME="${ORIN_ADMIN_USERNAME:-admin}"
ORIN_ADMIN_PASSWORD="${ORIN_ADMIN_PASSWORD:-admin123}"
ORIN_OPEN_DEMO_AGENT_ID="${ORIN_OPEN_DEMO_AGENT_ID:-}"
ORIN_OPEN_DEMO_RUN_BUSINESS_SMOKE="${ORIN_OPEN_DEMO_RUN_BUSINESS_SMOKE:-1}"
ORIN_OPEN_DEMO_RUN_WORKFLOW_SUBTASK="${ORIN_OPEN_DEMO_RUN_WORKFLOW_SUBTASK:-0}"

ORIN_BASE_URL="${ORIN_BASE_URL%/}"
ORIN_AI_BASE_URL="${ORIN_AI_BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
TOKEN=""
DEMO_API_KEY_ID=""
DEMO_API_KEY_SECRET=""
DEMO_WORKFLOW_ID=""
DEMO_OWNER_USER_ID=""
WARN_COUNT=0
SKIP_COUNT=0

cleanup() {
  set +e
  if [ -n "$DEMO_API_KEY_ID" ] && [ -n "$TOKEN" ]; then
    curl -fsS -X DELETE "$ORIN_BASE_URL/api/v1/api-keys/$DEMO_API_KEY_ID" \
      --noproxy "*" \
      -H "Authorization: Bearer $TOKEN" \
      -H "X-User-Id: ${DEMO_OWNER_USER_ID:-1}" \
      -H "Accept: application/json" >/dev/null 2>&1
  fi
  if [ -n "$DEMO_WORKFLOW_ID" ] && [ -n "$TOKEN" ]; then
    curl -fsS -X DELETE "$ORIN_BASE_URL/api/workflows/$DEMO_WORKFLOW_ID" \
      --noproxy "*" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Accept: application/json" >/dev/null 2>&1
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

pass() { echo "PASS $1"; }
warn() { WARN_COUNT=$((WARN_COUNT + 1)); echo "WARN $1"; }
skip() { SKIP_COUNT=$((SKIP_COUNT + 1)); echo "SKIP $1"; }
fail() { echo "FAIL $1"; exit 1; }

is_2xx() {
  [[ "$1" =~ ^2[0-9][0-9]$ ]]
}

request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  local body=""
  local auth_header=""
  local extra_headers=()

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --body)
        body="$2"
        shift 2
        ;;
      --auth)
        auth_header="Authorization: Bearer $TOKEN"
        shift
        ;;
      --header)
        extra_headers+=("$2")
        shift 2
        ;;
      *)
        fail "internal script error: unknown request option"
        ;;
    esac
  done

  local code
  local curl_headers=("-H" "Accept: application/json")
  if [ -n "$auth_header" ]; then
    curl_headers+=("-H" "$auth_header")
  fi
  if [ "${#extra_headers[@]}" -gt 0 ]; then
    for header in "${extra_headers[@]}"; do
      curl_headers+=("-H" "$header")
    done
  fi
  set +e
  if [ -n "$body" ]; then
    curl_headers+=("-H" "Content-Type: application/json")
    code="$(curl -sS --connect-timeout 5 --max-time 60 \
      --noproxy "*" \
      -o "$out" -w "%{http_code}" \
      -X "$method" \
      "${curl_headers[@]}" \
      --data-binary "@$body" \
      "$url" 2>/dev/null)"
  else
    code="$(curl -sS --connect-timeout 5 --max-time 60 \
      --noproxy "*" \
      -o "$out" -w "%{http_code}" \
      -X "$method" \
      "${curl_headers[@]}" \
      "$url" 2>/dev/null)"
  fi
  local rc=$?
  set -e
  if [ "$rc" -ne 0 ] || [ -z "$code" ]; then
    echo "000"
  else
    echo "$code"
  fi
}

json_value() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

try:
    value = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    sys.exit(0)

for part in sys.argv[2].split("."):
    if isinstance(value, dict):
        value = value.get(part)
    elif isinstance(value, list) and part.isdigit():
        idx = int(part)
        value = value[idx] if idx < len(value) else None
    else:
        value = None
    if value is None:
        break

if value is None:
    sys.exit(0)
if isinstance(value, bool):
    print("true" if value else "false")
elif isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False, separators=(",", ":")))
else:
    print(value)
PY
}

require_2xx() {
  local label="$1"
  local code="$2"
  if ! is_2xx "$code"; then
    fail "$label returned HTTP $code"
  fi
}

write_login_payload() {
  python3 - "$1" "$ORIN_ADMIN_USERNAME" "$ORIN_ADMIN_PASSWORD" <<'PY'
import json
import sys

json.dump(
    {"username": sys.argv[2], "password": sys.argv[3], "rememberMe": False},
    open(sys.argv[1], "w", encoding="utf-8"),
    ensure_ascii=False,
    separators=(",", ":"),
)
PY
}

write_api_key_payload() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

payload = {
    "name": "open-demo-acceptance-client",
    "description": "Temporary key for ORIN open-demo MCP acceptance",
    "rateLimitPerMinute": 60,
    "rateLimitPerDay": 1000,
    "monthlyTokenQuota": 100000,
    "targetUserId": sys.argv[2],
}
json.dump(payload, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
PY
}

write_workflow_payload() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

payload = {
    "workflowName": sys.argv[2],
    "description": "Open demo MCP workflow acceptance",
    "mcpExposed": True,
    "workflowType": "DAG",
    "timeoutSeconds": 60,
    "workflowDefinition": {
        "version": "orin.workflow.v1",
        "kind": "workflow",
        "metadata": {"execution": {"maxRetries": 0}},
        "graph": {
            "nodes": [
                {"id": "start", "type": "start", "title": "Start"},
                {
                    "id": "code",
                    "type": "code",
                    "title": "Summarize",
                    "data": {
                        "code": "print('open demo workflow accepted')",
                        "code_language": "python3",
                    },
                },
                {
                    "id": "end",
                    "type": "end",
                    "title": "End",
                    "data": {"outputs": [{"name": "answer", "value": "{{ code.result }}"}]},
                },
            ],
            "edges": [
                {"source": "start", "target": "code"},
                {"source": "code", "target": "end"},
            ],
        },
    },
}
json.dump(payload, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
PY
}

health_checks() {
  local out="$TMP_DIR/backend-v1-health.json"
  local code
  code="$(request GET "$ORIN_BASE_URL/v1/health" "$out")"
  require_2xx "backend /v1/health" "$code"
  pass "backend /v1/health reachable"

  out="$TMP_DIR/backend-api-health.json"
  code="$(request GET "$ORIN_BASE_URL/api/v1/health" "$out")"
  require_2xx "backend /api/v1/health" "$code"
  pass "backend /api/v1/health reachable"

  out="$TMP_DIR/ai-health.json"
  code="$(request GET "$ORIN_AI_BASE_URL/health" "$out")"
  require_2xx "AI Engine /health" "$code"
  local rabbitmq_status
  rabbitmq_status="$(json_value "$out" dependencies.rabbitmq.status)"
  if [ "$rabbitmq_status" = "disabled" ]; then
    warn "AI Engine RabbitMQ worker is disabled; workflow subtask strong smoke will be skipped unless services are restarted with MQ enabled"
  else
    pass "AI Engine RabbitMQ status=$rabbitmq_status"
  fi
  pass "AI Engine /health reachable"
}

login() {
  local body="$TMP_DIR/login-request.json"
  local out="$TMP_DIR/login-response.json"
  local code
  write_login_payload "$body"
  code="$(request POST "$ORIN_BASE_URL/api/v1/auth/login" "$out" --body "$body")"
  require_2xx "auth login" "$code"
  TOKEN="$(json_value "$out" token)"
  if [ -z "$TOKEN" ]; then
    fail "auth login did not return a token"
  fi
  pass "auth login returned JWT (redacted)"
}

create_demo_api_key() {
  local body="$TMP_DIR/api-key-request.json"
  local out="$TMP_DIR/api-key-response.json"
  local code
  if [ -z "$DEMO_OWNER_USER_ID" ]; then
    fail "demo owner user id is required before creating MCP API key"
  fi
  write_api_key_payload "$body" "$DEMO_OWNER_USER_ID"
  code="$(request POST "$ORIN_BASE_URL/api/v1/api-keys" "$out" \
    --auth \
    --header "X-User-Id: $DEMO_OWNER_USER_ID" \
    --body "$body")"
  require_2xx "demo api key create" "$code"
  DEMO_API_KEY_ID="$(json_value "$out" apiKey.id)"
  DEMO_API_KEY_SECRET="$(json_value "$out" secretKey)"
  if [ -z "$DEMO_API_KEY_ID" ] || [ -z "$DEMO_API_KEY_SECRET" ]; then
    fail "demo api key response missing id/secret"
  fi
  pass "temporary CLIENT_ACCESS key created (keyId=$DEMO_API_KEY_ID, owner=$DEMO_OWNER_USER_ID, secret redacted)"
}

create_demo_workflow() {
  local stamp
  stamp="$(date +%Y%m%d%H%M%S)"
  local body="$TMP_DIR/workflow-request.json"
  local out="$TMP_DIR/workflow-response.json"
  local code
  write_workflow_payload "$body" "open-demo-mcp-$stamp-$$"
  code="$(request POST "$ORIN_BASE_URL/api/workflows" "$out" --auth --body "$body")"
  require_2xx "demo workflow create" "$code"
  DEMO_WORKFLOW_ID="$(json_value "$out" id)"
  DEMO_OWNER_USER_ID="$(json_value "$out" ownerUserId)"
  if [ -z "$DEMO_WORKFLOW_ID" ]; then
    fail "demo workflow create did not return id"
  fi
  if [ -z "$DEMO_OWNER_USER_ID" ]; then
    fail "demo workflow create did not return ownerUserId"
  fi
  pass "temporary exposed workflow created (id=$DEMO_WORKFLOW_ID, owner=$DEMO_OWNER_USER_ID)"

  local publish_out="$TMP_DIR/workflow-publish-response.json"
  code="$(request POST "$ORIN_BASE_URL/api/workflows/$DEMO_WORKFLOW_ID/publish" "$publish_out" --auth)"
  require_2xx "demo workflow publish" "$code"
  if [ "$(json_value "$publish_out" status)" != "ACTIVE" ]; then
    fail "demo workflow publish did not return ACTIVE status"
  fi
  pass "temporary exposed workflow published"
}

maybe_expose_agent() {
  if [ -z "$ORIN_OPEN_DEMO_AGENT_ID" ]; then
    skip "MCP Agent call skipped because ORIN_OPEN_DEMO_AGENT_ID is not set"
    return
  fi
  local body="$TMP_DIR/agent-update-request.json"
  local out="$TMP_DIR/agent-update-response.json"
  local code
  printf '{"mcpExposed":true}' >"$body"
  code="$(request PUT "$ORIN_BASE_URL/api/v1/agents/$ORIN_OPEN_DEMO_AGENT_ID" "$out" --auth --body "$body")"
  require_2xx "demo agent expose" "$code"
  pass "configured agent exposed for MCP (agentId=$ORIN_OPEN_DEMO_AGENT_ID)"
}

run_base_business_smoke() {
  if [ "$ORIN_OPEN_DEMO_RUN_BUSINESS_SMOKE" != "1" ]; then
    skip "base business smoke skipped by ORIN_OPEN_DEMO_RUN_BUSINESS_SMOKE"
    return
  fi
  ORIN_BASE_URL="$ORIN_BASE_URL" \
  ORIN_AI_BASE_URL="$ORIN_AI_BASE_URL" \
  ORIN_ADMIN_USERNAME="$ORIN_ADMIN_USERNAME" \
  ORIN_ADMIN_PASSWORD="$ORIN_ADMIN_PASSWORD" \
  bash scripts/business-smoke.sh
}

run_mcp_smoke() {
  local require_agent_tool=0
  if [ -n "$ORIN_OPEN_DEMO_AGENT_ID" ]; then
    require_agent_tool=1
  fi
  ORIN_BASE_URL="$ORIN_BASE_URL" \
  ORIN_API_KEY="$DEMO_API_KEY_SECRET" \
  ORIN_MCP_CALL_TOOLS=1 \
  ORIN_MCP_AGENT_ID="$ORIN_OPEN_DEMO_AGENT_ID" \
  ORIN_MCP_REQUIRE_AGENT_TOOL="$require_agent_tool" \
  ORIN_MCP_WORKFLOW_TOOL="workflow.$DEMO_WORKFLOW_ID" \
  ORIN_MCP_REQUIRE_WORKFLOW_TOOL=1 \
  ORIN_MCP_REQUIRE_TRACE_METADATA=1 \
  bash scripts/mcp-open-demo-smoke.sh
}

run_workflow_subtask_smoke() {
  if [ "$ORIN_OPEN_DEMO_RUN_WORKFLOW_SUBTASK" != "1" ]; then
    skip "collaboration workflow subtask smoke skipped by ORIN_OPEN_DEMO_RUN_WORKFLOW_SUBTASK"
    return
  fi
  ORIN_BASE_URL="$ORIN_BASE_URL" \
  ORIN_AI_BASE_URL="$ORIN_AI_BASE_URL" \
  ORIN_ADMIN_USERNAME="$ORIN_ADMIN_USERNAME" \
  ORIN_ADMIN_PASSWORD="$ORIN_ADMIN_PASSWORD" \
  ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK=1 \
  bash scripts/business-smoke.sh
}

echo "=== ORIN Open Demo Acceptance ==="
echo "Backend: $ORIN_BASE_URL"
echo "AI Engine: $ORIN_AI_BASE_URL"
echo ""

health_checks
login
run_base_business_smoke
create_demo_workflow
create_demo_api_key
maybe_expose_agent
run_mcp_smoke
run_workflow_subtask_smoke

echo ""
echo "=== ORIN Open Demo Acceptance Complete ==="
echo "WARN count: $WARN_COUNT"
echo "SKIP count: $SKIP_COUNT"
echo "status: PASS"
