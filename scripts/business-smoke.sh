#!/bin/bash
# ORIN business smoke baseline.
# Verifies core business HTTP lanes without printing credentials or request bodies.

set -euo pipefail

ORIN_BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
ORIN_AI_BASE_URL="${ORIN_AI_BASE_URL:-http://127.0.0.1:8000}"
ORIN_ADMIN_USERNAME="${ORIN_ADMIN_USERNAME:-admin}"
ORIN_ADMIN_PASSWORD="${ORIN_ADMIN_PASSWORD:-admin123}"
ORIN_BUSINESS_SMOKE_AGENT_ID="${ORIN_BUSINESS_SMOKE_AGENT_ID:-}"
ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS="${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS:-120}"
ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED="${ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED:-auto}"
ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK="${ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK:-false}"
ORIN_BACKEND_AUTHORIZATION="${ORIN_BACKEND_AUTHORIZATION:-}"
RABBITMQ_HOST="${RABBITMQ_HOST:-127.0.0.1}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"

ORIN_BASE_URL="${ORIN_BASE_URL%/}"
ORIN_AI_BASE_URL="${ORIN_AI_BASE_URL%/}"
HTTP_TIMEOUT=30

TMP_DIR="$(mktemp -d)"
TOKEN=""
SMOKE_API_KEY_ID=""
SMOKE_API_KEY_SECRET=""
WORKFLOW_ID=""
WORKFLOW_TASK_ID=""
WORKFLOW_TASK_STATUS=""
FAILURE_WORKFLOW_ID=""
FAILURE_WORKFLOW_TASK_ID=""
FAILURE_WORKFLOW_TASK_STATUS=""
FAILURE_REPLAY_TASK_ID=""
FAILURE_REPLAY_TASK_STATUS=""
COLLAB_PACKAGE_ID=""
COLLAB_PACKAGE_OPEN=0
WARN_COUNT=0
SKIP_COUNT=0

cleanup() {
    set +e
    if [ -n "$COLLAB_PACKAGE_ID" ] && [ "$COLLAB_PACKAGE_OPEN" -eq 1 ] && [ -n "$TOKEN" ]; then
        local body="$TMP_DIR/collab-cleanup.json"
        printf '{"result":"business smoke cleanup"}' >"$body"
        curl -fsS -X POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/complete" \
            --noproxy "*" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Accept: application/json" \
            -H "Content-Type: application/json" \
            --data-binary "@$body" >/dev/null 2>&1 \
        || curl -fsS -X POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/cancel" \
            --noproxy "*" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Accept: application/json" >/dev/null 2>&1
    fi
    if [ -n "$SMOKE_API_KEY_ID" ] && [ -n "$TOKEN" ]; then
        curl -fsS -X DELETE "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID" \
            --noproxy "*" \
            -H "Authorization: Bearer $TOKEN" \
            -H "X-User-Id: 1" \
            -H "Accept: application/json" >/dev/null 2>&1
    fi
    if [ -n "$WORKFLOW_ID" ] && [ -n "$TOKEN" ]; then
        if [ "$WORKFLOW_TASK_STATUS" = "QUEUED" ] && [ -n "$WORKFLOW_TASK_ID" ]; then
            curl -fsS -X POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$WORKFLOW_TASK_ID/cancel" \
                --noproxy "*" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/json" >/dev/null 2>&1
        fi
        if [ "$WORKFLOW_TASK_STATUS" != "RUNNING" ] && [ "$WORKFLOW_TASK_STATUS" != "RETRYING" ]; then
            curl -fsS -X DELETE "$ORIN_BASE_URL/api/workflows/$WORKFLOW_ID" \
                --noproxy "*" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/json" >/dev/null 2>&1
        fi
    fi
    if [ -n "$FAILURE_WORKFLOW_ID" ] && [ -n "$TOKEN" ]; then
        if [ "$FAILURE_REPLAY_TASK_STATUS" = "QUEUED" ] && [ -n "$FAILURE_REPLAY_TASK_ID" ]; then
            curl -fsS -X POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$FAILURE_REPLAY_TASK_ID/cancel" \
                --noproxy "*" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/json" >/dev/null 2>&1
        fi
        if [ "$FAILURE_WORKFLOW_TASK_STATUS" = "QUEUED" ] && [ -n "$FAILURE_WORKFLOW_TASK_ID" ]; then
            curl -fsS -X POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$FAILURE_WORKFLOW_TASK_ID/cancel" \
                --noproxy "*" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/json" >/dev/null 2>&1
        fi
        if [ "$FAILURE_WORKFLOW_TASK_STATUS" != "RUNNING" ] && [ "$FAILURE_WORKFLOW_TASK_STATUS" != "RETRYING" ] \
            && [ "$FAILURE_REPLAY_TASK_STATUS" != "RUNNING" ] && [ "$FAILURE_REPLAY_TASK_STATUS" != "RETRYING" ]; then
            curl -fsS -X DELETE "$ORIN_BASE_URL/api/workflows/$FAILURE_WORKFLOW_ID" \
                --noproxy "*" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/json" >/dev/null 2>&1
        fi
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

pass() {
    echo "PASS $1"
}

warn() {
    WARN_COUNT=$((WARN_COUNT + 1))
    echo "WARN $1"
}

skip() {
    SKIP_COUNT=$((SKIP_COUNT + 1))
    echo "SKIP $1"
}

fail() {
    echo "FAIL $1"
    exit 1
}

is_2xx() {
    [[ "$1" =~ ^2[0-9][0-9]$ ]]
}

request() {
    local method="$1"
    local url="$2"
    local out="$3"
    shift 3
    local body=""
    local headers=()

    while [ "$#" -gt 0 ]; do
        case "$1" in
            --body)
                body="$2"
                shift 2
                ;;
            --auth)
                headers+=("-H" "Authorization: Bearer $TOKEN")
                shift
                ;;
            --header)
                headers+=("-H" "$2")
                shift 2
                ;;
            *)
                fail "internal script error: unknown request option"
                ;;
        esac
    done

    local code
    set +e
    if [ -n "$body" ]; then
        code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
            --noproxy "*" \
            -o "$out" -w "%{http_code}" \
            -X "$method" \
            -H "Accept: application/json" \
            -H "Content-Type: application/json" \
            ${headers+"${headers[@]}"} \
            --data-binary "@$body" \
            "$url" 2>/dev/null)
    else
        code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
            --noproxy "*" \
            -o "$out" -w "%{http_code}" \
            -X "$method" \
            -H "Accept: application/json" \
            ${headers+"${headers[@]}"} \
            "$url" 2>/dev/null)
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

path = sys.argv[2].split(".")
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(0)

for part in path:
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

json_collection_count() {
    python3 - "$1" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    print("0")
    sys.exit(0)

if isinstance(value, list):
    print(len(value))
    sys.exit(0)
if isinstance(value, dict):
    for key in ("content", "data", "items", "records", "list"):
        nested = value.get(key)
        if isinstance(nested, list):
            print(len(nested))
            sys.exit(0)
print("0")
PY
}

json_number_ge() {
    python3 - "$1" "$2" "$3" <<'PY'
import json
import sys

path = sys.argv[2].split(".")
minimum = int(sys.argv[3])

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(1)

for part in path:
    if isinstance(value, dict):
        value = value.get(part)
    elif isinstance(value, list) and part.isdigit():
        idx = int(part)
        value = value[idx] if idx < len(value) else None
    else:
        value = None
    if value is None:
        break

if isinstance(value, bool):
    sys.exit(1)
if isinstance(value, (int, float)) and value >= minimum:
    sys.exit(0)
sys.exit(1)
PY
}

json_find_item_value() {
    python3 - "$1" "$2" "$3" "$4" <<'PY'
import json
import sys

source = sys.argv[1]
match_key = sys.argv[2]
match_value = sys.argv[3]
target_key = sys.argv[4]

try:
    with open(source, "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(0)

if isinstance(value, dict):
    for key in ("content", "data", "items", "records", "list"):
        nested = value.get(key)
        if isinstance(nested, list):
            value = nested
            break

if not isinstance(value, list):
    sys.exit(0)

for item in value:
    if not isinstance(item, dict):
        continue
    if str(item.get(match_key, "")) != match_value:
        continue
    result = item.get(target_key)
    if result is None:
        sys.exit(0)
    if isinstance(result, bool):
        print("true" if result else "false")
    elif isinstance(result, (dict, list)):
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    else:
        print(result)
    sys.exit(0)
PY
}

require_no_error_payload() {
    python3 - "$1" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(1)

def has_error_payload(obj):
    if isinstance(obj, dict):
        for key, nested in obj.items():
            normalized_key = str(key).lower()
            if normalized_key == "status" and isinstance(nested, str) and nested.upper() == "ERROR":
                return True
            if normalized_key == "error":
                if nested is None or nested is False or nested == "":
                    continue
                if isinstance(nested, (list, dict)) and len(nested) == 0:
                    continue
                return True
            if has_error_payload(nested):
                return True
    elif isinstance(obj, list):
        return any(has_error_payload(item) for item in obj)
    return False

sys.exit(1 if has_error_payload(value) else 0)
PY
}

has_queue_unavailable_error() {
    python3 - "$1" <<'PY'
import json
import sys

needles = (
    "queue unavailable",
    "rabbit",
    "connection refused",
    "任务队列不可用",
    "工作流未能入队",
)

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(1)

def walk(obj):
    if isinstance(obj, str):
        lower = obj.lower()
        return any(needle in lower for needle in needles)
    if isinstance(obj, dict):
        return any(walk(v) for v in obj.values())
    if isinstance(obj, list):
        return any(walk(v) for v in obj)
    return False

sys.exit(0 if walk(value) else 1)
PY
}

requires_workflow_completed() {
    case "$ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED" in
        1|true|TRUE|yes|YES)
            return 0
            ;;
        0|false|FALSE|no|NO)
            return 1
            ;;
        *)
            rabbitmq_reachable
            ;;
    esac
}

requires_workflow_subtask_smoke() {
    case "$ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK" in
        1|true|TRUE|yes|YES)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

rabbitmq_reachable() {
    python3 - "$RABBITMQ_HOST" "$RABBITMQ_PORT" <<'PY'
import socket
import sys

host = sys.argv[1]
port = int(sys.argv[2])

try:
    with socket.create_connection((host, port), timeout=1.5):
        sys.exit(0)
except OSError:
    sys.exit(1)
PY
}

write_login_payload() {
    python3 - "$1" "$ORIN_ADMIN_USERNAME" "$ORIN_ADMIN_PASSWORD" <<'PY'
import json
import sys

payload = {
    "username": sys.argv[2],
    "password": sys.argv[3],
    "rememberMe": False,
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_agent_chat_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "message": "business smoke ping",
    "max_tokens": 32,
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_api_key_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "name": "business-smoke-client",
    "description": "Business smoke API key lifecycle check",
    "rateLimitPerMinute": 60,
    "rateLimitPerDay": 1000,
    "monthlyTokenQuota": 100000,
    "targetUserId": "1",
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_mcp_initialize_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2025-03-26",
        "clientInfo": {"name": "orin-business-smoke", "version": "1.0.0"},
        "capabilities": {},
    },
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_workflow_payload() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

payload = {
    "workflowName": sys.argv[2],
    "description": "Business smoke workflow",
    "mcpExposed": False,
    "workflowType": "DAG",
    "timeoutSeconds": 60,
    "workflowDefinition": {
        "version": "orin.workflow.v1",
        "kind": "workflow",
        "metadata": {
            "execution": {
                "maxRetries": 0
            }
        },
        "graph": {
            "nodes": [
                {"id": "start", "type": "start", "title": "Start"},
                {
                    "id": "code",
                    "type": "code",
                    "title": "Code",
                    "data": {
                        "code": "print('business smoke')",
                        "code_language": "python3",
                    },
                },
                {
                    "id": "end",
                    "type": "end",
                    "title": "End",
                    "data": {
                        "outputs": [
                            {"name": "answer", "value": "{{ code.result }}"}
                        ]
                    },
                },
            ],
            "edges": [
                {"source": "start", "target": "code"},
                {"source": "code", "target": "end"},
            ],
        },
    },
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_failing_workflow_payload() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

payload = {
    "workflowName": sys.argv[2],
    "description": "Business smoke failing workflow",
    "mcpExposed": False,
    "workflowType": "DAG",
    "timeoutSeconds": 60,
    "retryPolicy": {
        "maxRetries": 0
    },
    "workflowDefinition": {
        "version": "orin.workflow.v1",
        "kind": "workflow",
        "graph": {
            "nodes": [
                {"id": "start", "type": "start", "title": "Start"},
                {
                    "id": "fail",
                    "type": "http_request",
                    "title": "Failing HTTP",
                    "data": {
                        "method": "GET"
                    },
                },
                {
                    "id": "end",
                    "type": "end",
                    "title": "End",
                    "data": {
                        "outputs": [
                            {"name": "answer", "value": "{{ fail.statusCode }}"}
                        ]
                    },
                },
            ],
            "edges": [
                {"source": "start", "target": "fail"},
                {"source": "fail", "target": "end"},
            ],
        },
    },
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_workflow_execute_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "query": "business smoke",
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_collab_create_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "intent": "Validate ORIN business smoke baseline",
    "category": "GENERAL",
    "priority": "NORMAL",
    "complexity": "SIMPLE",
    "collaborationMode": "SEQUENTIAL",
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_collab_decompose_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "capabilities": ["analysis", "generation", "review"],
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_collab_workflow_decompose_payload() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

payload = {
    "capabilities": ["workflow"],
    "subtasks": [
        {
            "subTaskId": "workflow-1",
            "description": "Run the business smoke workflow as a collaboration subtask",
            "expectedRole": "WORKFLOW",
            "workflowId": int(sys.argv[2]),
            "inputData": {
                "inputs": {
                    "query": "business smoke workflow subtask"
                }
            },
            "dependsOn": [],
        }
    ],
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

absolute_backend_url() {
    local value="$1"
    if [[ "$value" =~ ^https?:// ]]; then
        echo "$value"
    else
        echo "$ORIN_BASE_URL$value"
    fi
}

require_2xx() {
    local label="$1"
    local code="$2"
    if ! is_2xx "$code"; then
        fail "$label returned HTTP $code"
    fi
}

require_trace_summary_found() {
    local trace_id="$1"
    local out="$2"
    local label="$3"
    local code

    code=$(request GET "$ORIN_BASE_URL/api/v1/traces/$trace_id/summary" "$out" --auth)
    require_2xx "$label trace summary" "$code"
    if [ "$(json_value "$out" found)" != "true" ]; then
        fail "$label trace summary did not find traceId=$trace_id"
    fi
}

check_health() {
    local out="$TMP_DIR/health-backend-v1.json"
    local code
    code=$(request GET "$ORIN_BASE_URL/v1/health" "$out")
    require_2xx "backend /v1/health" "$code"
    pass "backend /v1/health reachable"

    out="$TMP_DIR/health-backend-api.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/health" "$out")
    require_2xx "backend /api/v1/health" "$code"
    pass "backend /api/v1/health reachable"

    out="$TMP_DIR/health-ai.json"
    code=$(request GET "$ORIN_AI_BASE_URL/health" "$out")
    require_2xx "AI Engine /health" "$code"
    pass "AI Engine /health reachable"
}

login() {
    local body="$TMP_DIR/login-request.json"
    local out="$TMP_DIR/login-response.json"
    local code
    write_login_payload "$body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/auth/login" "$out" --body "$body")
    require_2xx "auth login" "$code"
    TOKEN="$(json_value "$out" token)"
    if [ -z "$TOKEN" ]; then
        fail "auth login did not return a token"
    fi
    if [ -n "$(json_value "$out" user.password)" ]; then
        fail "auth login response exposed password field"
    fi
    pass "auth login returned JWT (redacted)"
}

check_agents() {
    local out="$TMP_DIR/agents-list.json"
    local code
    code=$(request GET "$ORIN_BASE_URL/api/v1/agents" "$out" --auth)
    require_2xx "agent list" "$code"
    local count
    count="$(json_collection_count "$out")"
    pass "agent list reachable (count=$count)"

    if [ -z "$ORIN_BUSINESS_SMOKE_AGENT_ID" ]; then
        skip "agent chat skipped because ORIN_BUSINESS_SMOKE_AGENT_ID is not set"
        return
    fi

    local body="$TMP_DIR/agent-chat-request.json"
    local chat_out="$TMP_DIR/agent-chat-response.json"
    local trace_id="business-smoke-agent-$(date +%Y%m%d%H%M%S)-$$"
    write_agent_chat_payload "$body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/agents/$ORIN_BUSINESS_SMOKE_AGENT_ID/chat" "$chat_out" \
        --auth \
        --header "X-Trace-Id: $trace_id" \
        --body "$body")
    require_2xx "agent chat" "$code"
    if ! require_no_error_payload "$chat_out"; then
        fail "agent chat returned an error payload"
    fi

    local summary_out="$TMP_DIR/agent-chat-trace-summary.json"
    require_trace_summary_found "$trace_id" "$summary_out" "agent chat"
    if ! json_number_ge "$summary_out" "counts.auditLogs" 1 \
        && ! json_number_ge "$summary_out" "counts.traceSteps" 1; then
        fail "agent chat trace summary has no auditLogs or traceSteps"
    fi
    pass "agent chat reachable and trace summary linked for configured agent"
}

check_api_key_lifecycle() {
    local body="$TMP_DIR/api-key-create-request.json"
    local out="$TMP_DIR/api-key-create-response.json"
    local code

    write_api_key_payload "$body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/api-keys" "$out" \
        --auth \
        --header "X-User-Id: 1" \
        --body "$body")
    require_2xx "api key create" "$code"
    SMOKE_API_KEY_ID="$(json_value "$out" apiKey.id)"
    SMOKE_API_KEY_SECRET="$(json_value "$out" secretKey)"
    if [ -z "$SMOKE_API_KEY_ID" ] || [ -z "$SMOKE_API_KEY_SECRET" ]; then
        fail "api key create response missing id/secret"
    fi
    pass "api key created (keyId=$SMOKE_API_KEY_ID, secret redacted)"

    local mcp_body="$TMP_DIR/api-key-mcp-initialize-request.json"
    local mcp_out="$TMP_DIR/api-key-mcp-initialize-response.json"
    write_mcp_initialize_payload "$mcp_body"
    set +e
    code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
        --noproxy "*" \
        -o "$mcp_out" -w "%{http_code}" \
        -X POST "$ORIN_BASE_URL/v1/mcp" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $SMOKE_API_KEY_SECRET" \
        --data-binary "@$mcp_body" 2>/dev/null)
    local rc=$?
    set -e
    if [ "$rc" -ne 0 ] || [ -z "$code" ]; then
        code="000"
    fi
    require_2xx "api key mcp initialize" "$code"
    pass "api key accepted by /v1/mcp initialize"

    local disable_out="$TMP_DIR/api-key-disable-response.json"
    code=$(request PATCH "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID/disable" "$disable_out" \
        --auth \
        --header "X-User-Id: 1")
    require_2xx "api key disable" "$code"
    if [ "$(json_value "$disable_out" success)" != "true" ]; then
        fail "api key disable returned success=false"
    fi
    pass "api key disabled"

    local rejected_out="$TMP_DIR/api-key-disabled-mcp-response.json"
    set +e
    code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
        --noproxy "*" \
        -o "$rejected_out" -w "%{http_code}" \
        -X POST "$ORIN_BASE_URL/v1/mcp" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $SMOKE_API_KEY_SECRET" \
        --data-binary "@$mcp_body" 2>/dev/null)
    rc=$?
    set -e
    if [ "$rc" -ne 0 ] || [ -z "$code" ]; then
        code="000"
    fi
    if [ "$code" != "401" ]; then
        fail "disabled api key mcp request returned HTTP $code"
    fi
    pass "disabled api key rejected by /v1/mcp"

    local delete_out="$TMP_DIR/api-key-delete-response.json"
    code=$(request DELETE "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID" "$delete_out" \
        --auth \
        --header "X-User-Id: 1")
    require_2xx "api key delete" "$code"
    SMOKE_API_KEY_ID=""
    SMOKE_API_KEY_SECRET=""
    pass "api key deleted for cleanup"
}

poll_workflow_task() {
    local status_url="$1"
    local out="$TMP_DIR/workflow-task-status.json"
    local deadline=$((SECONDS + ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS))
    local code

    while true; do
        code=$(request GET "$(absolute_backend_url "$status_url")" "$out" --auth)
        require_2xx "workflow task status" "$code"

        WORKFLOW_TASK_STATUS="$(json_value "$out" status)"
        case "$WORKFLOW_TASK_STATUS" in
            COMPLETED)
                pass "workflow task completed"
                return
                ;;
            FAILED|DEAD)
                if has_queue_unavailable_error "$out"; then
                    if requires_workflow_completed; then
                        fail "workflow execution queue unavailable while ORIN_BUSINESS_SMOKE_REQUIRE_WORKFLOW_COMPLETED is enabled"
                    fi
                    warn "workflow execution queue unavailable; submission/status path verified"
                    return
                fi
                fail "workflow task finished with status=$WORKFLOW_TASK_STATUS"
                ;;
            QUEUED|RUNNING|RETRYING)
                if [ "$SECONDS" -ge "$deadline" ]; then
                    if requires_workflow_completed; then
                        fail "workflow task remained $WORKFLOW_TASK_STATUS after ${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS}s"
                    fi
                    warn "workflow task remained $WORKFLOW_TASK_STATUS after ${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS}s; submission/status path verified"
                    return
                fi
                sleep 2
                ;;
            *)
                warn "workflow task returned non-standard status '$WORKFLOW_TASK_STATUS'; status path verified"
                return
                ;;
        esac
    done
}

check_workflow_task_recovery_guards() {
    if [ -z "$WORKFLOW_TASK_ID" ] || [ "$WORKFLOW_TASK_STATUS" != "COMPLETED" ]; then
        return
    fi

    local cancel_out="$TMP_DIR/workflow-task-cancel-completed-response.json"
    local code
    code=$(request POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$WORKFLOW_TASK_ID/cancel" "$cancel_out" --auth)
    if [ "$code" != "400" ]; then
        fail "completed workflow task cancel guard returned HTTP $code"
    fi
    pass "completed workflow task cannot be cancelled"

    local replay_out="$TMP_DIR/workflow-task-replay-completed-response.json"
    code=$(request POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$WORKFLOW_TASK_ID/replay" "$replay_out" --auth)
    if [ "$code" != "400" ]; then
        fail "completed workflow task replay guard returned HTTP $code"
    fi
    pass "completed workflow task cannot be replayed"
}

poll_workflow_task_until_failed() {
    local status_url="$1"
    local out="$TMP_DIR/failing-workflow-task-status.json"
    local deadline=$((SECONDS + ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS))
    local code

    while true; do
        code=$(request GET "$(absolute_backend_url "$status_url")" "$out" --auth)
        require_2xx "failing workflow task status" "$code"

        FAILURE_WORKFLOW_TASK_STATUS="$(json_value "$out" status)"
        case "$FAILURE_WORKFLOW_TASK_STATUS" in
            FAILED)
                pass "failing workflow task reached FAILED"
                return 0
                ;;
            DEAD)
                fail "failing workflow task reached DEAD before replay baseline"
                ;;
            COMPLETED)
                fail "failing workflow unexpectedly completed"
                ;;
            QUEUED|RUNNING|RETRYING)
                if [ "$SECONDS" -ge "$deadline" ]; then
                    if requires_workflow_completed; then
                        fail "failing workflow task remained $FAILURE_WORKFLOW_TASK_STATUS after ${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS}s"
                    fi
                    warn "failing workflow task remained $FAILURE_WORKFLOW_TASK_STATUS; replay scenario skipped"
                    return 1
                fi
                sleep 2
                ;;
            *)
                warn "failing workflow task returned non-standard status '$FAILURE_WORKFLOW_TASK_STATUS'; replay scenario skipped"
                return 1
                ;;
        esac
    done
}

check_workflow_failure_recovery() {
    local stamp
    stamp="$(date +%Y%m%d%H%M%S)"
    local name="business-smoke-fail-$stamp-$$"
    local body="$TMP_DIR/failing-workflow-create-request.json"
    local out="$TMP_DIR/failing-workflow-create-response.json"
    local code

    write_failing_workflow_payload "$body" "$name"
    code=$(request POST "$ORIN_BASE_URL/api/workflows" "$out" --auth --body "$body")
    require_2xx "failing workflow create" "$code"
    FAILURE_WORKFLOW_ID="$(json_value "$out" id)"
    if [ -z "$FAILURE_WORKFLOW_ID" ]; then
        fail "failing workflow create did not return id"
    fi
    pass "failing workflow created (id=$FAILURE_WORKFLOW_ID)"

    local publish_out="$TMP_DIR/failing-workflow-publish-response.json"
    code=$(request POST "$ORIN_BASE_URL/api/workflows/$FAILURE_WORKFLOW_ID/publish" "$publish_out" --auth)
    require_2xx "failing workflow publish" "$code"
    pass "failing workflow published"

    local exec_body="$TMP_DIR/failing-workflow-execute-request.json"
    local exec_out="$TMP_DIR/failing-workflow-execute-response.json"
    write_workflow_execute_payload "$exec_body"
    code=$(request POST "$ORIN_BASE_URL/api/workflows/$FAILURE_WORKFLOW_ID/execute?triggeredBy=business-smoke" "$exec_out" --auth --body "$exec_body")
    require_2xx "failing workflow execute submission" "$code"

    FAILURE_WORKFLOW_TASK_ID="$(json_value "$exec_out" taskId)"
    local status_url
    status_url="$(json_value "$exec_out" statusUrl)"
    if [ -z "$FAILURE_WORKFLOW_TASK_ID" ] || [ -z "$status_url" ]; then
        fail "failing workflow execute response missing taskId/statusUrl"
    fi
    pass "failing workflow submitted (taskId=$FAILURE_WORKFLOW_TASK_ID)"

    if ! poll_workflow_task_until_failed "$status_url"; then
        return
    fi

    local replay_out="$TMP_DIR/failing-workflow-replay-response.json"
    code=$(request POST "$ORIN_BASE_URL/api/v1/workflow-tasks/$FAILURE_WORKFLOW_TASK_ID/replay" "$replay_out" --auth)
    require_2xx "failing workflow replay" "$code"
    FAILURE_REPLAY_TASK_ID="$(json_value "$replay_out" newTaskId)"
    if [ -z "$FAILURE_REPLAY_TASK_ID" ]; then
        fail "failing workflow replay did not return newTaskId"
    fi
    pass "failing workflow replay created new task (newTaskId=$FAILURE_REPLAY_TASK_ID)"

    local original_out="$TMP_DIR/failing-workflow-original-after-replay.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/workflow-tasks/$FAILURE_WORKFLOW_TASK_ID" "$original_out" --auth)
    require_2xx "failing workflow original task after replay" "$code"
    local original_status
    original_status="$(json_value "$original_out" status)"
    if [ "$original_status" != "FAILED" ]; then
        fail "failing workflow original task status changed after replay"
    fi
    pass "failing workflow original task remained FAILED"

    local new_out="$TMP_DIR/failing-workflow-new-task-status.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/workflow-tasks/$FAILURE_REPLAY_TASK_ID" "$new_out" --auth)
    require_2xx "failing workflow new task query" "$code"
    FAILURE_REPLAY_TASK_STATUS="$(json_value "$new_out" status)"
    case "$FAILURE_REPLAY_TASK_STATUS" in
        QUEUED|RUNNING|COMPLETED|FAILED)
            pass "failing workflow new task has legal status (status=$FAILURE_REPLAY_TASK_STATUS)"
            ;;
        *)
            fail "failing workflow new task has illegal status=$FAILURE_REPLAY_TASK_STATUS"
            ;;
    esac
}

check_workflow() {
    local stamp
    stamp="$(date +%Y%m%d%H%M%S)"
    local name="business-smoke-$stamp-$$"
    local body="$TMP_DIR/workflow-create-request.json"
    local out="$TMP_DIR/workflow-create-response.json"
    local code

    write_workflow_payload "$body" "$name"
    code=$(request POST "$ORIN_BASE_URL/api/workflows" "$out" --auth --body "$body")
    require_2xx "workflow create" "$code"
    WORKFLOW_ID="$(json_value "$out" id)"
    if [ -z "$WORKFLOW_ID" ]; then
        fail "workflow create did not return id"
    fi
    pass "workflow created (id=$WORKFLOW_ID)"

    local publish_out="$TMP_DIR/workflow-publish-response.json"
    code=$(request POST "$ORIN_BASE_URL/api/workflows/$WORKFLOW_ID/publish" "$publish_out" --auth)
    require_2xx "workflow publish" "$code"
    local status
    status="$(json_value "$publish_out" status)"
    if [ "$status" != "ACTIVE" ]; then
        fail "workflow publish did not return ACTIVE status"
    fi
    pass "workflow published"

    local exec_body="$TMP_DIR/workflow-execute-request.json"
    local exec_out="$TMP_DIR/workflow-execute-response.json"
    write_workflow_execute_payload "$exec_body"
    code=$(request POST "$ORIN_BASE_URL/api/workflows/$WORKFLOW_ID/execute?triggeredBy=business-smoke" "$exec_out" --auth --body "$exec_body")
    require_2xx "workflow execute submission" "$code"

    WORKFLOW_TASK_ID="$(json_value "$exec_out" taskId)"
    local instance_id
    local trace_id
    local status_url
    instance_id="$(json_value "$exec_out" workflowInstanceId)"
    trace_id="$(json_value "$exec_out" traceId)"
    status_url="$(json_value "$exec_out" statusUrl)"
    WORKFLOW_TASK_STATUS="$(json_value "$exec_out" status)"

    if [ -z "$WORKFLOW_TASK_ID" ] || [ -z "$instance_id" ] || [ -z "$trace_id" ] || [ -z "$status_url" ]; then
        fail "workflow execute response missing taskId/workflowInstanceId/traceId/statusUrl"
    fi
    pass "workflow submitted (taskId=$WORKFLOW_TASK_ID, instanceId=$instance_id, traceId=$trace_id)"

    poll_workflow_task "$status_url"
    check_workflow_task_recovery_guards

    local instance_out="$TMP_DIR/workflow-instance-response.json"
    code=$(request GET "$ORIN_BASE_URL/api/workflows/instances/$instance_id" "$instance_out" --auth)
    require_2xx "workflow instance query" "$code"
    local instance_trace
    instance_trace="$(json_value "$instance_out" traceId)"
    if [ -n "$instance_trace" ] && [ "$instance_trace" != "$trace_id" ]; then
        fail "workflow instance traceId does not match submission traceId"
    fi
    pass "workflow instance query reachable"

    local summary_out="$TMP_DIR/workflow-trace-summary.json"
    require_trace_summary_found "$trace_id" "$summary_out" "workflow"
    local summary_trace
    summary_trace="$(json_value "$summary_out" workflowInstance.traceId)"
    if [ "$summary_trace" != "$trace_id" ]; then
        fail "workflow trace summary workflowInstance traceId does not match submission traceId"
    fi
    if ! json_number_ge "$summary_out" "counts.workflowTasks" 1; then
        fail "workflow trace summary has no workflowTasks"
    fi
    pass "workflow trace summary linked"
}

check_collaboration() {
    local trace_id="business-smoke-$(date +%Y%m%d%H%M%S)-$$"
    local body="$TMP_DIR/collab-create-request.json"
    local out="$TMP_DIR/collab-create-response.json"
    local code

    write_collab_create_payload "$body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages" "$out" \
        --auth \
        --header "X-User-Id: business-smoke" \
        --header "X-Trace-Id: $trace_id" \
        --body "$body")
    require_2xx "collaboration package create" "$code"
    COLLAB_PACKAGE_ID="$(json_value "$out" packageId)"
    if [ -z "$COLLAB_PACKAGE_ID" ]; then
        fail "collaboration package create did not return packageId"
    fi
    COLLAB_PACKAGE_OPEN=1
    pass "collaboration package created (packageId=$COLLAB_PACKAGE_ID, traceId=$trace_id)"

    local decompose_body="$TMP_DIR/collab-decompose-request.json"
    local decompose_out="$TMP_DIR/collab-decompose-response.json"
    write_collab_decompose_payload "$decompose_body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/decompose" "$decompose_out" --auth --body "$decompose_body")
    require_2xx "collaboration package decompose" "$code"
    pass "collaboration package decomposed"

    local package_out="$TMP_DIR/collab-package-response.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID" "$package_out" --auth)
    require_2xx "collaboration package query" "$code"
    pass "collaboration package query reachable"

    local subtasks_out="$TMP_DIR/collab-subtasks-response.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/subtasks" "$subtasks_out" --auth)
    require_2xx "collaboration subtasks query" "$code"
    local subtask_count
    subtask_count="$(json_collection_count "$subtasks_out")"
    pass "collaboration subtasks query reachable (count=$subtask_count)"

    local events_out="$TMP_DIR/collab-events-response.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/collaboration/events/$COLLAB_PACKAGE_ID" "$events_out" --auth)
    require_2xx "collaboration events query" "$code"
    local event_count
    event_count="$(json_collection_count "$events_out")"
    pass "collaboration events query reachable (count=$event_count)"

    local summary_out="$TMP_DIR/collab-trace-summary.json"
    require_trace_summary_found "$trace_id" "$summary_out" "collaboration"
    if ! json_number_ge "$summary_out" "counts.collaborationPackages" 1; then
        fail "collaboration trace summary has no collaborationPackages"
    fi
    pass "collaboration trace summary linked"

    local cleanup_body="$TMP_DIR/collab-complete-request.json"
    local cleanup_out="$TMP_DIR/collab-complete-response.json"
    printf '{"result":"business smoke verified"}' >"$cleanup_body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/complete" "$cleanup_out" --auth --body "$cleanup_body")
    if is_2xx "$code"; then
        COLLAB_PACKAGE_OPEN=0
        pass "collaboration package completed for cleanup"
    else
        local cancel_out="$TMP_DIR/collab-cancel-response.json"
        code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/cancel" "$cancel_out" --auth)
        if is_2xx "$code"; then
            COLLAB_PACKAGE_OPEN=0
            pass "collaboration package cancelled for cleanup"
        else
            warn "collaboration cleanup endpoint returned HTTP $code; package may remain visible"
        fi
    fi
}

poll_collaboration_subtask_completed() {
    local package_id="$1"
    local subtask_id="$2"
    local out="$TMP_DIR/collab-workflow-subtasks-poll.json"
    local deadline=$((SECONDS + ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS))
    local code
    local status
    local error_message

    while true; do
        code=$(request GET "$ORIN_BASE_URL/api/v1/collaboration/packages/$package_id/subtasks" "$out" --auth)
        require_2xx "collaboration workflow subtask status" "$code"
        status="$(json_find_item_value "$out" subTaskId "$subtask_id" status)"
        case "$status" in
            COMPLETED)
                pass "collaboration workflow subtask completed"
                return
                ;;
            FAILED|CANCELLED|SKIPPED)
                error_message="$(json_find_item_value "$out" subTaskId "$subtask_id" errorMessage)"
                fail "collaboration workflow subtask finished with status=$status${error_message:+, error=$error_message}"
                ;;
            PENDING|RUNNING|"")
                if [ "$SECONDS" -ge "$deadline" ]; then
                    fail "collaboration workflow subtask remained ${status:-missing} after ${ORIN_BUSINESS_SMOKE_TIMEOUT_SECONDS}s"
                fi
                sleep 2
                ;;
            *)
                fail "collaboration workflow subtask returned unexpected status=$status"
                ;;
        esac
    done
}

require_collaboration_workflow_subtask_materialized() {
    local package_id="$1"
    local subtask_id="$2"
    local out="$TMP_DIR/collab-workflow-subtasks-materialized.json"
    local code
    local role
    local input_data

    code=$(request GET "$ORIN_BASE_URL/api/v1/collaboration/packages/$package_id/subtasks" "$out" --auth)
    require_2xx "collaboration workflow subtasks materialization query" "$code"
    role="$(json_find_item_value "$out" subTaskId "$subtask_id" expectedRole)"
    input_data="$(json_find_item_value "$out" subTaskId "$subtask_id" inputData)"

    if [ -z "$role" ]; then
        fail "collaboration workflow subtask was not materialized; deploy backend code that supports explicit decompose subtasks"
    fi
    if [ "$role" != "WORKFLOW" ]; then
        fail "collaboration workflow subtask has role=$role, expected WORKFLOW"
    fi
    case "$input_data" in
        *"workflowId"*)
            pass "collaboration workflow subtask materialized"
            ;;
        *)
            fail "collaboration workflow subtask missing workflowId in inputData"
            ;;
    esac
}

check_collaboration_workflow_subtask() {
    if ! requires_workflow_subtask_smoke; then
        skip "collaboration workflow subtask smoke skipped because ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK is not enabled"
        return
    fi
    if [ -z "$WORKFLOW_ID" ]; then
        fail "collaboration workflow subtask smoke requires a published workflow id"
    fi
    if ! rabbitmq_reachable; then
        fail "collaboration workflow subtask smoke requires RabbitMQ at $RABBITMQ_HOST:$RABBITMQ_PORT"
    fi
    if [ -z "$ORIN_BACKEND_AUTHORIZATION" ]; then
        warn "ORIN_BACKEND_AUTHORIZATION is not exported in this shell; assuming the AI Engine worker was started with it"
    fi

    local trace_id="business-smoke-collab-workflow-$(date +%Y%m%d%H%M%S)-$$"
    local body="$TMP_DIR/collab-workflow-create-request.json"
    local out="$TMP_DIR/collab-workflow-create-response.json"
    local code

    write_collab_create_payload "$body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages" "$out" \
        --auth \
        --header "X-User-Id: business-smoke" \
        --header "X-Trace-Id: $trace_id" \
        --body "$body")
    require_2xx "collaboration workflow package create" "$code"
    COLLAB_PACKAGE_ID="$(json_value "$out" packageId)"
    if [ -z "$COLLAB_PACKAGE_ID" ]; then
        fail "collaboration workflow package create did not return packageId"
    fi
    COLLAB_PACKAGE_OPEN=1
    pass "collaboration workflow package created (packageId=$COLLAB_PACKAGE_ID, traceId=$trace_id)"

    local decompose_body="$TMP_DIR/collab-workflow-decompose-request.json"
    local decompose_out="$TMP_DIR/collab-workflow-decompose-response.json"
    write_collab_workflow_decompose_payload "$decompose_body" "$WORKFLOW_ID"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/decompose" "$decompose_out" --auth --body "$decompose_body")
    require_2xx "collaboration workflow package decompose" "$code"
    pass "collaboration workflow package decomposed"
    require_collaboration_workflow_subtask_materialized "$COLLAB_PACKAGE_ID" "workflow-1"

    local execute_out="$TMP_DIR/collab-workflow-execute-response.json"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/subtasks/workflow-1/execute" "$execute_out" \
        --auth \
        --header "X-Trace-Id: $trace_id")
    if [ "$code" != "202" ]; then
        fail "collaboration workflow subtask execute returned HTTP $code"
    fi
    pass "collaboration workflow subtask execution started"

    poll_collaboration_subtask_completed "$COLLAB_PACKAGE_ID" "workflow-1"
    local subtask_result
    subtask_result="$(json_find_item_value "$TMP_DIR/collab-workflow-subtasks-poll.json" subTaskId "workflow-1" result)"
    case "$subtask_result" in
        *"Workflow enqueued:"*)
            pass "collaboration workflow subtask used AI Engine TaskRuntime"
            ;;
        *)
            fail "collaboration workflow subtask did not return the AI Engine workflow enqueue result"
            ;;
    esac

    local summary_out="$TMP_DIR/collab-workflow-trace-summary.json"
    require_trace_summary_found "$trace_id" "$summary_out" "collaboration workflow subtask"
    if ! json_number_ge "$summary_out" "counts.collaborationPackages" 1; then
        fail "collaboration workflow subtask trace summary has no collaborationPackages"
    fi
    if ! json_number_ge "$summary_out" "counts.workflowTasks" 1; then
        fail "collaboration workflow subtask trace summary has no workflowTasks"
    fi
    pass "collaboration workflow subtask trace summary linked"

    local cleanup_body="$TMP_DIR/collab-workflow-complete-request.json"
    local cleanup_out="$TMP_DIR/collab-workflow-complete-response.json"
    printf '{"result":"business smoke workflow subtask verified"}' >"$cleanup_body"
    code=$(request POST "$ORIN_BASE_URL/api/v1/collaboration/packages/$COLLAB_PACKAGE_ID/complete" "$cleanup_out" --auth --body "$cleanup_body")
    if is_2xx "$code"; then
        COLLAB_PACKAGE_OPEN=0
        pass "collaboration workflow package completed for cleanup"
    else
        warn "collaboration workflow cleanup endpoint returned HTTP $code; package may remain visible"
    fi
}

echo "=== ORIN Business Smoke ==="
echo "Backend: $ORIN_BASE_URL"
echo "AI Engine: $ORIN_AI_BASE_URL"
echo ""

check_health
login
check_api_key_lifecycle
check_agents
check_workflow
check_workflow_failure_recovery
check_collaboration
check_collaboration_workflow_subtask

echo ""
echo "=== ORIN Business Smoke Complete ==="
echo "WARN count: $WARN_COUNT"
echo "SKIP count: $SKIP_COUNT"
echo "status: PASS"
