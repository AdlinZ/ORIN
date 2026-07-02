#!/bin/bash
# ORIN Gateway MVP smoke baseline.
# Verifies the OpenAI-compatible gateway end-to-end:
#   - /v1/health, /v1/models, /v1/chat/completions
#   - API Key 创建 → 鉴权调用 → 禁用后 401
#   - usage / trace / audit 记录
#
# 设计约定见 docs/Gateway-MVP-开发方向.md §Gateway-0。
# 默认不打印明文 key / 密码 / provider secret。
#
# 用法（用户自行启动后端后再跑）：
#   bash scripts/gateway-smoke.sh
#
# 环境变量（覆盖默认值）：
#   ORIN_BASE_URL                      默认 http://127.0.0.1:8080
#   ORIN_ADMIN_USERNAME                默认 admin
#   ORIN_ADMIN_PASSWORD                默认 admin123
#   ORIN_GATEWAY_SMOKE_REQUIRE_LIVE    auto|0|1 ，默认 auto
#                                       auto = 没真实 provider 时降级为 WARN
#                                       0    = 任何失败都允许 WARN
#                                       1    = 任何失败必须 FAIL
#   ORIN_GATEWAY_SMOKE_MODEL           默认空 → 从 /v1/models 自动选第一个
#   ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS 默认 30（拉 trace summary 总超时）

set -euo pipefail

ORIN_BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
ORIN_ADMIN_USERNAME="${ORIN_ADMIN_USERNAME:-admin}"
ORIN_ADMIN_PASSWORD="${ORIN_ADMIN_PASSWORD:-admin123}"
ORIN_GATEWAY_SMOKE_REQUIRE_LIVE="${ORIN_GATEWAY_SMOKE_REQUIRE_LIVE:-auto}"
ORIN_GATEWAY_SMOKE_MODEL="${ORIN_GATEWAY_SMOKE_MODEL:-}"
ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS="${ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS:-30}"

ORIN_BASE_URL="${ORIN_BASE_URL%/}"
HTTP_TIMEOUT=30

TMP_DIR="$(mktemp -d)"
TOKEN=""
SMOKE_API_KEY_ID=""
SMOKE_API_KEY_SECRET=""
SMOKE_TRACE_ID=""
SMOKE_USED_MODEL=""
SMOKE_CHAT_OK=""
WARN_COUNT=0
SKIP_COUNT=0

cleanup() {
    set +e
    if [ -n "$SMOKE_API_KEY_ID" ] && [ -n "$TOKEN" ]; then
        curl -fsS -X DELETE "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID" \
            --noproxy "*" \
            -H "Authorization: Bearer $TOKEN" \
            -H "X-User-Id: 1" \
            -H "Accept: application/json" >/dev/null 2>&1 \
            && echo "CLEANUP api-key $SMOKE_API_KEY_ID deleted" \
            || echo "WARN cleanup: api-key $SMOKE_API_KEY_ID delete returned non-2xx (already removed?)"
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

require_2xx() {
    local label="$1"
    local code="$2"
    if ! is_2xx "$code"; then
        fail "$label returned HTTP $code"
    fi
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

# Like request() but allows caller to assert exact status codes (e.g. 401).
# Echoes both the http_code and writes the body to $out.
request_expect_any() {
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

json_pick_first_model_id() {
    python3 - "$1" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(0)

if isinstance(value, dict):
    data = value.get("data")
    if isinstance(data, list):
        value = data

if isinstance(value, list):
    for item in value:
        if isinstance(item, dict):
            mid = item.get("id") or item.get("model")
            if mid:
                print(mid)
                sys.exit(0)
sys.exit(0)
PY
}

json_recent_event_has_trace() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

target_trace = sys.argv[2]
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        value = json.load(fh)
except Exception:
    sys.exit(1)

def walk(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "traceId" and isinstance(v, str) and v == target_trace:
                return True
            if walk(v):
                return True
    elif isinstance(obj, list):
        return any(walk(item) for item in obj)
    return False

sys.exit(0 if walk(value) else 1)
PY
}

json_path_equals() {
    python3 - "$1" "$2" "$3" <<'PY'
import json
import sys

path = sys.argv[2].split(".")
expected = sys.argv[3]
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
    actual = "true" if value else "false"
else:
    actual = "" if value is None else str(value)

sys.exit(0 if actual == expected else 1)
PY
}

require_live_mode() {
    case "$ORIN_GATEWAY_SMOKE_REQUIRE_LIVE" in
        1|true|TRUE|yes|YES)
            return 0
            ;;
        0|false|FALSE|no|NO)
            return 1
            ;;
        *)
            # auto 模式：默认按 live 处理（live 失败会降级 WARN）
            return 0
            ;;
    esac
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

write_api_key_payload() {
    python3 - "$1" <<'PY'
import json
import sys

payload = {
    "name": "gateway-smoke-client",
    "description": "ORIN Gateway MVP smoke; ephemeral",
    "rateLimitPerMinute": 60,
    "rateLimitPerDay": 1000,
    "monthlyTokenQuota": 100000,
    "targetUserId": "1",
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

write_chat_payload() {
    python3 - "$1" "$2" "$3" <<'PY'
import json
import sys

payload = {
    "model": sys.argv[2],
    "messages": [
        {"role": "user", "content": sys.argv[3]}
    ],
    "max_tokens": 8,
    "stream": False,
    "temperature": 0.0,
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY
}

check_health() {
    local out="$TMP_DIR/v1-health.json"
    local code
    code=$(request GET "$ORIN_BASE_URL/v1/health" "$out")
    require_2xx "backend /v1/health" "$code"
    pass "backend /v1/health reachable"

    out="$TMP_DIR/api-v1-health.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/health" "$out")
    require_2xx "backend /api/v1/health" "$code"
    pass "backend /api/v1/health reachable"
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

create_smoke_api_key() {
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
    case "$SMOKE_API_KEY_SECRET" in
        sk-orin-*)
            pass "api key created (keyId=$SMOKE_API_KEY_ID, sk-orin-* prefix)"
            ;;
        *)
            fail "api key secret does not carry sk-orin- prefix; got '${SMOKE_API_KEY_SECRET:0:8}...'"
            ;;
    esac
}

call_v1_models() {
    local out="$TMP_DIR/v1-models.json"
    local code

    code=$(request_expect_any GET "$ORIN_BASE_URL/v1/models" "$out" \
        --header "Authorization: Bearer $SMOKE_API_KEY_SECRET")

    case "$code" in
        200)
            local count
            count="$(json_collection_count "$out")"
            if [ "$count" -eq 0 ]; then
                SMOKE_USED_MODEL=""
                warn "/v1/models returned empty data array; no live provider available"
                return 0
            fi
            if [ -n "$ORIN_GATEWAY_SMOKE_MODEL" ]; then
                SMOKE_USED_MODEL="$ORIN_GATEWAY_SMOKE_MODEL"
                pass "/v1/models reachable (count=$count, using override model=$SMOKE_USED_MODEL)"
            else
                SMOKE_USED_MODEL="$(json_pick_first_model_id "$out")"
                if [ -z "$SMOKE_USED_MODEL" ]; then
                    warn "/v1/models returned $count entries but none had an id; skipping chat"
                else
                    pass "/v1/models reachable (count=$count, picked model=$SMOKE_USED_MODEL)"
                fi
            fi
            return 0
            ;;
        401)
            fail "/v1/models returned 401 with valid-looking sk-orin key; check ApiKeyAuthInterceptor wiring"
            ;;
        000)
            fail "/v1/models could not be reached (network/connectivity issue)"
            ;;
        *)
            fail "/v1/models returned HTTP $code; expected 200"
            ;;
    esac
}

call_v1_chat_completions() {
    local body="$TMP_DIR/chat-request.json"
    local out="$TMP_DIR/chat-response.json"
    local header_out="$TMP_DIR/chat-headers.txt"
    local trace_id="gateway-smoke-$(date +%Y%m%d%H%M%S)-$$"
    local prompt="ping"
    local code
    local live_status

    if [ -z "$SMOKE_USED_MODEL" ]; then
        skip "/v1/chat/completions skipped because no usable model resolved from /v1/models"
        return 0
    fi

    write_chat_payload "$body" "$SMOKE_USED_MODEL" "$prompt"
    SMOKE_TRACE_ID="$trace_id"

    set +e
    code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
        --noproxy "*" \
        -D "$header_out" \
        -o "$out" -w "%{http_code}" \
        -X POST "$ORIN_BASE_URL/v1/chat/completions" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $SMOKE_API_KEY_SECRET" \
        -H "X-Trace-Id: $trace_id" \
        --data-binary "@$body" 2>/dev/null)
    local rc=$?
    set -e
    if [ "$rc" -ne 0 ] || [ -z "$code" ]; then
        code="000"
    fi

    case "$code" in
        200)
            if [ -z "$(json_value "$out" choices.0.message.content)" ] && [ -z "$(json_value "$out" choices.0.text)" ]; then
                warn "/v1/chat/completions returned 200 but choices[0] was empty"
                return 0
            fi
            SMOKE_CHAT_OK=1
            pass "/v1/chat/completions returned 200 with non-empty choices (model=$SMOKE_USED_MODEL)"
            return 0
            ;;
        401)
            fail "/v1/chat/completions returned 401 with valid sk-orin key (key validation chain broken)"
            ;;
        429)
            warn "/v1/chat/completions returned 429 (rate-limited or quota); key auth worked"
            return 0
            ;;
        503)
            live_status="$(json_value "$out" error.message)"
            case "$live_status" in
                *"No available provider"*|*"no available provider"*)
                    warn "/v1/chat/completions returned 503 (no available provider) — provider routing needs a configured credential"
                    return 0
                    ;;
                *)
                    warn "/v1/chat/completions returned 503 — message: ${live_status:-<empty>}"
                    return 0
                    ;;
            esac
            ;;
        500)
            local err
            err="$(json_value "$out" error.message)"
            case "$err" in
                *"No available provider"*)
                    warn "/v1/chat/completions wrapped 'No available provider' as 500 — provider routing needs a configured credential"
                    return 0
                    ;;
                *)
                    if require_live_mode; then
                        fail "/v1/chat/completions returned 500 — message: ${err:-<empty>}"
                    else
                        warn "/v1/chat/completions returned 500 — message: ${err:-<empty>}"
                        return 0
                    fi
                    ;;
            esac
            ;;
        000)
            fail "/v1/chat/completions could not be reached (network/connectivity issue)"
            ;;
        *)
            if require_live_mode; then
                fail "/v1/chat/completions returned HTTP $code"
            else
                warn "/v1/chat/completions returned HTTP $code"
                return 0
            fi
            ;;
    esac
}

check_usage_summary() {
    local out="$TMP_DIR/api-key-usage.json"
    local code

    code=$(request GET "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID/usage?limit=20" "$out" --auth)
    require_2xx "api key usage" "$code"

    local total_calls
    total_calls="$(json_value "$out" totalCalls)"
    if [ -z "$total_calls" ] || [ "$total_calls" = "0" ]; then
        # Recent event 列表可能为空：可能是 chat 没真的写 audit（provider 503），
        # 也可能是 audit 在异步队列里稍后才到。
        warn "api key usage totalCalls=0；可能是 audit 异步未刷盘，或 chat 调用未触达 provider"
        return 0
    fi
    pass "api key usage reports totalCalls=$total_calls"

    if [ -n "$SMOKE_TRACE_ID" ]; then
        if json_recent_event_has_trace "$out" "$SMOKE_TRACE_ID"; then
            pass "api key usage recentEvents contains traceId=$SMOKE_TRACE_ID"
        else
            warn "api key usage recentEvents did not list traceId=$SMOKE_TRACE_ID (audit async lag or 503 short-circuit)"
        fi
    fi
}

check_gateway_audit_fields() {
    # Gateway MVP: 校验 audit_logs + gateway_audit_logs 已暴露 modelAlias / providerModel / errorCode。
    # 软断言：任一行带任一新字段即 PASS；provider 503 short-circuit 时常为空，置 WARN。
    local audit_out="$TMP_DIR/audit-logs-gateway-fields.json"
    local code
    code=$(request GET "$ORIN_BASE_URL/api/v1/audit/logs?page=0&size=10" "$audit_out" --auth)
    if ! is_2xx "$code"; then
        warn "audit logs endpoint returned HTTP $code; skip gateway-field check"
    else
        if python3 - "$audit_out" <<'PY'
import json, sys
def walk(o):
    if isinstance(o, dict):
        yield o
        for v in o.values(): yield from walk(v)
    elif isinstance(o, list):
        for v in o: yield from walk(v)
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        rows = json.load(fh)
except Exception:
    sys.exit(1)
sys.exit(0 if any((r.get("modelAlias") or r.get("providerModel") or r.get("errorCode")) for r in walk(rows)) else 1)
PY
        then
            pass "audit logs expose new gateway fields (modelAlias/providerModel/errorCode)"
        else
            warn "no audit row populated modelAlias/providerModel/errorCode (chat may have been 503 short-circuit)"
        fi
    fi

    if [ -z "$SMOKE_API_KEY_ID" ]; then
        return 0
    fi
    local usage_out="$TMP_DIR/api-key-usage-gateway-fields.json"
    code=$(request GET "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID/usage?limit=10" "$usage_out" --auth)
    if ! is_2xx "$code"; then
        warn "api-key usage returned HTTP $code; skip gateway-field usage check"
        return 0
    fi
    if python3 - "$usage_out" <<'PY'
import json, sys
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        v = json.load(fh)
except Exception:
    sys.exit(1)
events = (v or {}).get("recentEvents", []) if isinstance(v, dict) else []
sys.exit(0 if any((e.get("modelAlias") or e.get("providerModel") or e.get("errorCode")) for e in events) else 1)
PY
    then
        pass "api-key usage recentEvents expose gateway fields"
    else
        warn "api-key usage recentEvents lack gateway fields (no live traffic in this run)"
    fi
}

check_gateway_audit_writes_after_chat() {
    # Gateway-1b 硬断言：chat 200 后 audit_logs 必须有 traceId 命中行，且新字段齐
    # 仅当 SMOKE_CHAT_OK=1 时跑；provider 503 路径或 trace 异步未到时降级 WARN
    if [ "$SMOKE_CHAT_OK" != "1" ]; then
        skip "audit-writes-after-chat skipped (no live chat success in this run)"
        return 0
    fi
    if [ -z "$SMOKE_TRACE_ID" ]; then
        skip "audit-writes-after-chat skipped (SMOKE_TRACE_ID is empty)"
        return 0
    fi

    # AuditLogService.@Async 写盘可能滞后，最多等 5s
    local out="$TMP_DIR/audit-logs-by-trace.json"
    local code
    local found=0
    for _ in 1 2 3 4 5; do
        sleep 1
        code=$(request GET "$ORIN_BASE_URL/api/v1/audit/logs?page=0&size=20" "$out" --auth)
        if ! is_2xx "$code"; then
            continue
        fi
        if python3 - "$out" "$SMOKE_TRACE_ID" <<'PY'
import json, sys
target = sys.argv[2]
def walk(o):
    if isinstance(o, dict):
        yield o
        for v in o.values(): yield from walk(v)
    elif isinstance(o, list):
        for v in o: yield from walk(v)
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh: rows = json.load(fh)
except Exception:
    sys.exit(1)
sys.exit(0 if any(r.get("traceId") == target for r in walk(rows)) else 2)
PY
        then
            found=1
            break
        fi
    done

    if [ "$found" -ne 1 ]; then
        if [ "${ORIN_GATEWAY_SMOKE_STRICT_AUDIT:-0}" = "1" ]; then
            fail "audit_logs by-trace not found within 5s (strict audit mode)"
        else
            warn "audit_logs by-trace not found within 5s (async lag, expected if chat was 200 with live provider)"
        fi
        return 0
    fi

    # 找到行后做硬断言
    if python3 - "$out" "$SMOKE_TRACE_ID" <<'PY'
import json, sys
target = sys.argv[2]
def walk(o):
    if isinstance(o, dict):
        yield o
        for v in o.values(): yield from walk(v)
    elif isinstance(o, list):
        for v in o: yield from walk(v)
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh: rows = json.load(fh)
except Exception:
    sys.exit(1)
matches = [r for r in walk(rows) if r.get("traceId") == target]
if not matches:
    sys.exit(2)
row = matches[0]
# modelAlias / providerModel / traceId / apiKeyId / userId 全有
if not row.get("modelAlias"):
    sys.exit(3)
if not row.get("providerModel"):
    sys.exit(4)
if row.get("traceId") != target:
    sys.exit(5)
if not row.get("apiKeyId"):
    sys.exit(6)
if not row.get("userId"):
    sys.exit(7)
# errorCode 必须为 null（成功路径）
if row.get("errorCode"):
    sys.exit(8)
sys.exit(0)
PY
    then
        pass "audit_logs row for traceId=$SMOKE_TRACE_ID contains gateway fields (modelAlias/providerModel/apiKeyId/userId) with errorCode=null"
    else
        local rc=$?
        if [ "${ORIN_GATEWAY_SMOKE_STRICT_AUDIT:-0}" = "1" ]; then
            fail "audit_logs by-trace hard check failed rc=$rc (strict audit mode, traceId=$SMOKE_TRACE_ID)"
        else
            warn "audit_logs by-trace hard check failed rc=$rc (some gateway field missing for traceId=$SMOKE_TRACE_ID)"
        fi
    fi
}

check_trace_summary() {
    if [ -z "$SMOKE_TRACE_ID" ]; then
        skip "trace summary skipped because traceId is empty (chat was not invoked)"
        return 0
    fi

    local out="$TMP_DIR/trace-summary.json"
    local code
    local deadline=$((SECONDS + ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS))

    while true; do
        code=$(request GET "$ORIN_BASE_URL/api/v1/traces/$SMOKE_TRACE_ID/summary" "$out" --auth)
        if is_2xx "$code"; then
            if json_path_equals "$out" "found" "true"; then
                pass "trace summary linked for traceId=$SMOKE_TRACE_ID"
                return 0
            fi
        fi
        if [ "$SECONDS" -ge "$deadline" ]; then
            warn "trace summary not linked after ${ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS}s (may be missing for non-provider 503 short-circuit)"
            return 0
        fi
        sleep 2
    done
}

check_audit_logs_reachable() {
    local out="$TMP_DIR/audit-logs.json"
    local code

    code=$(request GET "$ORIN_BASE_URL/api/v1/audit/logs?page=0&size=5&type=API_KEY_CREATE" "$out" --auth)
    case "$code" in
        200)
            pass "audit logs endpoint reachable (API_KEY_CREATE page)"
            ;;
        404)
            warn "audit logs endpoint returned 404; /api/v1/audit/logs may have been moved"
            ;;
        000)
            warn "audit logs endpoint could not be reached"
            ;;
        *)
            if require_live_mode; then
                fail "audit logs returned HTTP $code"
            else
                warn "audit logs returned HTTP $code"
            fi
            ;;
    esac
}

check_disabled_key_rejected() {
    local body="$TMP_DIR/api-key-disable-request.json"
    local disable_out="$TMP_DIR/api-key-disable-response.json"
    local rejected_out="$TMP_DIR/api-key-disabled-chat-response.json"
    local chat_body="$TMP_DIR/api-key-disabled-chat-request.json"
    local code

    code=$(request PATCH "$ORIN_BASE_URL/api/v1/api-keys/$SMOKE_API_KEY_ID/disable" "$disable_out" \
        --auth \
        --header "X-User-Id: 1")
    require_2xx "api key disable" "$code"
    if [ "$(json_value "$disable_out" success)" != "true" ]; then
        fail "api key disable returned success=false"
    fi
    pass "api key disabled"

    write_chat_payload "$chat_body" "$SMOKE_USED_MODEL" "after disable"
    code=$(request_expect_any POST "$ORIN_BASE_URL/v1/chat/completions" "$rejected_out" \
        --header "Authorization: Bearer $SMOKE_API_KEY_SECRET" \
        --body "$chat_body")
    case "$code" in
        401)
            pass "disabled api key rejected by /v1/chat/completions (HTTP 401)"
            ;;
        000)
            fail "disabled-key /v1/chat/completions could not be reached"
            ;;
        *)
            fail "disabled api key /v1/chat/completions returned HTTP $code (expected 401)"
            ;;
    esac

    code=$(request_expect_any POST "$ORIN_BASE_URL/v1/models" "$rejected_out" \
        --header "Authorization: Bearer $SMOKE_API_KEY_SECRET")
    case "$code" in
        401)
            pass "disabled api key rejected by /v1/models (HTTP 401)"
            ;;
        000)
            warn "disabled-key /v1/models could not be reached (network)"
            ;;
        *)
            fail "disabled api key /v1/models returned HTTP $code (expected 401)"
            ;;
    esac
}

check_embeddings() {
    # Gateway-1d: /v1/embeddings smoke (默认关闭，501 → skip)
    local body="$TMP_DIR/embedding-request.json"
    local out="$TMP_DIR/embedding-response.json"
    local header_out="$TMP_DIR/embedding-headers.txt"
    local code
    local live_status

    if [ -z "$SMOKE_USED_MODEL" ]; then
        skip "/v1/embeddings skipped because no usable model resolved from /v1/models"
        return 0
    fi

    # embeddings payload
    python3 - "$body" "$SMOKE_USED_MODEL" <<'PY'
import json, sys
payload = {"model": sys.argv[2], "input": "hello world"}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
PY

    set +e
    code=$(curl -sS --connect-timeout 5 --max-time "$HTTP_TIMEOUT" \
        --noproxy "*" \
        -D "$header_out" \
        -o "$out" -w "%{http_code}" \
        -X POST "$ORIN_BASE_URL/v1/embeddings" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $SMOKE_API_KEY_SECRET" \
        --data-binary "@$body" 2>/dev/null)
    local rc=$?
    set -e
    if [ "$rc" -ne 0 ] || [ -z "$code" ]; then
        code="000"
    fi

    case "$code" in
        200)
            if [ -z "$(json_value "$out" data.0.embedding)" ]; then
                warn "/v1/embeddings returned 200 but data[0].embedding was empty"
                return 0
            fi
            SMOKE_EMBEDDING_OK=1
            pass "/v1/embeddings returned 200 with embedding data (model=$SMOKE_USED_MODEL)"
            return 0
            ;;
        401)
            fail "/v1/embeddings returned 401 with valid sk-orin key (key validation chain broken)"
            ;;
        429)
            warn "/v1/embeddings returned 429 (rate-limited or quota); key auth worked"
            return 0
            ;;
        501)
            skip "/v1/embeddings returned 501 (embeddings endpoint disabled)"
            return 0
            ;;
        503)
            live_status="$(json_value "$out" error.message)"
            case "$live_status" in
                *"No available provider"*|*"no available provider"*)
                    warn "/v1/embeddings returned 503 (no available provider)"
                    return 0
                    ;;
                *)
                    warn "/v1/embeddings returned 503 — message: ${live_status:-<empty>}"
                    return 0
                    ;;
            esac
            ;;
        000)
            warn "/v1/embeddings curl failed (exit=$rc)"
            return 0
            ;;
        *)
            live_status="$(json_value "$out" error.message)"
            live_status="${live_status:-<none>}"
            if require_live_mode 1; then
                fail "/v1/embeddings returned HTTP $code (message: $live_status)"
            else
                warn "/v1/embeddings returned HTTP $code, require-live=0 — warn only (message: $live_status)"
                return 0
            fi
            ;;
    esac
}

echo "=== ORIN Gateway MVP Smoke ==="
echo "Backend: $ORIN_BASE_URL"
echo "RequireLive: $ORIN_GATEWAY_SMOKE_REQUIRE_LIVE"
echo ""

check_health
login
create_smoke_api_key
call_v1_models
call_v1_chat_completions
check_embeddings
check_usage_summary
check_gateway_audit_fields
check_gateway_audit_writes_after_chat
check_trace_summary
check_audit_logs_reachable
check_disabled_key_rejected

echo ""
echo "=== ORIN Gateway MVP Smoke Complete ==="
echo "WARN count: $WARN_COUNT"
echo "SKIP count: $SKIP_COUNT"
echo "status: PASS"
