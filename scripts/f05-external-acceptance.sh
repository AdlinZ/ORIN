#!/usr/bin/env bash
set -euo pipefail

# F05 release acceptance: creates a deterministic Agent, a temporary Runner and a
# temporary Endpoint.  It never reads a credential from source files and never
# prints the JWT, enrollment token or one-time client API key.
BASE_URL="${ORIN_BASE_URL:?set ORIN_BASE_URL explicitly, for example http://127.0.0.1:18080}"
RUNNER_BASE_URL="${ORIN_RUNNER_BASE_URL:?set ORIN_RUNNER_BASE_URL to the Control Plane address reachable from Docker}"
ORIN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USER="${ORIN_ADMIN_USERNAME:-admin}"
PASSWORD="${ORIN_ADMIN_PASSWORD:?set ORIN_ADMIN_PASSWORD; no default password is permitted}"
RUNNER_IMAGE="${ORIN_RUNNER_IMAGE:-orin-runner:f04}"
TIMEOUT_SECONDS="${ORIN_F05_TIMEOUT_SECONDS:-120}"
REQUIRE_STDIO_BRIDGE="${ORIN_F05_REQUIRE_STDIO_BRIDGE:-0}"
BRIDGE_PYTHON="${ORIN_MCP_BRIDGE_PYTHON:-}"
TMP_DIR="$(mktemp -d)"
AGENT_ID=""
ENDPOINT_ID=""
TOKEN=""
API_KEY=""
API_KEY_ID=""
stamp="$(date +%s)-$$"
runner_name="f05-e2e-$stamp"
container_name="orin-f05-runner-$stamp"
credential_volume="orin-f05-credential-$stamp"

cleanup() {
  if [[ -n "$ENDPOINT_ID" && -n "$TOKEN" ]]; then
    curl -sS -X POST "$BASE_URL/api/v1/endpoints/$ENDPOINT_ID/deactivate" \
      -H "Authorization: Bearer $TOKEN" >/dev/null || true
  fi
  if [[ -n "$API_KEY_ID" && -n "$TOKEN" ]]; then
    curl -sS -X DELETE "$BASE_URL/api/v1/gateway/secrets/$API_KEY_ID" \
      -H "Authorization: Bearer $TOKEN" >/dev/null || true
  fi
  docker rm -f "$container_name" >/dev/null 2>&1 || true
  docker volume rm "$credential_volume" >/dev/null 2>&1 || true
  [[ -d "$TMP_DIR" ]] && rm -r -- "$TMP_DIR"
}
trap cleanup EXIT

fail() { printf 'F05 E2E FAIL: %s\n' "$1" >&2; exit 1; }
pass() { printf 'F05 E2E PASS: %s\n' "$1"; }
request() { curl -sS -w '\n%{http_code}' "$@"; }

for command in curl jq docker; do
  command -v "$command" >/dev/null || fail "required command is missing: $command"
done
docker image inspect "$RUNNER_IMAGE" >/dev/null 2>&1 || fail "Runner image is unavailable: $RUNNER_IMAGE"

login_raw="$(request -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary "{\"username\":\"$USER\",\"password\":\"$PASSWORD\"}")"
code="${login_raw##*$'\n'}"; body="${login_raw%$'\n'*}"
[[ "$code" =~ ^2 ]] || fail "admin login HTTP $code"
TOKEN="$(printf '%s' "$body" | jq -r '.token // .accessToken // .data.token // empty')"
[[ -n "$TOKEN" && "$TOKEN" != null ]] || fail 'login response did not contain a JWT'
pass 'temporary admin login'

auth=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
agent_name="F05 acceptance Agent $stamp"
endpoint_name="F05 acceptance Endpoint $stamp"
api_key_name="$endpoint_name-key"
create="$(request -X POST "$BASE_URL/api/v1/agents" "${auth[@]}" \
  --data-binary "$(jq -nc --arg name "$agent_name" '{name:$name,description:"ephemeral F05 release acceptance"}')")"
code="${create##*$'\n'}"; body="${create%$'\n'*}"
[[ "$code" =~ ^2 ]] || fail "Agent create HTTP $code"
AGENT_ID="$(printf '%s' "$body" | jq -r '.agentId // .id // .data.agentId // empty')"
[[ -n "$AGENT_ID" ]] || fail 'Agent create response did not contain agentId'

curl -fsS -X PUT "$BASE_URL/api/v1/agents/$AGENT_ID/draft" "${auth[@]}" \
  --data-binary "$(jq -nc --arg name "$agent_name" \
    '{name:$name,description:"ephemeral F05 release acceptance",mode:"agent",modelName:"deterministic",providerType:"ORIN_DETERMINISTIC",systemPrompt:"Return the deterministic test result.",temperature:0,topP:1,maxTokens:256,pendingSecretRefs:[]}')" >/dev/null
curl -fsS -X POST "$BASE_URL/api/v1/agents/$AGENT_ID/versions" "${auth[@]}" \
  -H "Idempotency-Key: f05-$stamp" >/dev/null
versions="$(curl -fsS "$BASE_URL/api/v1/agents/$AGENT_ID/versions" "${auth[@]}")"
VERSION_ID="$(printf '%s' "$versions" | jq -r 'map(select(.status == "FROZEN"))[0].agent_version_id // map(select(.status == "FROZEN"))[0].id // empty')"
[[ -n "$VERSION_ID" ]] || fail 'freeze did not produce a FROZEN version'
pass 'temporary Agent created and frozen'

publish="$(request -X POST "$BASE_URL/api/v1/endpoints" "${auth[@]}" \
  --data-binary "$(jq -nc --arg name "$endpoint_name" --arg agentId "$AGENT_ID" --arg agentVersionId "$VERSION_ID" \
    '{name:$name,endpointType:"REST_API",agentId:$agentId,agentVersionId:$agentVersionId,description:"ephemeral external acceptance"}')")"
code="${publish##*$'\n'}"; body="${publish%$'\n'*}"
[[ "$code" =~ ^2 ]] || fail "Endpoint publish HTTP $code: $body"
ENDPOINT_ID="$(printf '%s' "$body" | jq -r '.id // empty')"
API_KEY="$(printf '%s' "$body" | jq -r '.secretKey // empty')"
[[ -n "$ENDPOINT_ID" && -n "$API_KEY" ]] || fail 'publish response omitted endpoint id or one-time API key'
# The one-time key value intentionally never leaves this process.  Its non-secret
# identifier is fetched only to revoke the temporary key in cleanup.
keys="$(curl -fsS "$BASE_URL/api/v1/gateway/secrets/client-access" -H "Authorization: Bearer $TOKEN")"
API_KEY_ID="$(printf '%s' "$keys" | jq -r --arg name "$api_key_name" '.[] | select(.name == $name) | .secretId // empty' | head -n 1)"
[[ -n "$API_KEY_ID" ]] || fail 'publish-created API key was not discoverable for cleanup'
pass 'Endpoint published with one-time key held in process memory'

# F05 has to prove a completed external call, so it provisions its own ephemeral
# Runner instead of relying on an operator-maintained machine.  The enrollment
# credential exists only in this Docker volume and is removed by cleanup.
enrollment="$(request -X POST "$BASE_URL/api/v1/runner-enrollment-tokens" "${auth[@]}" \
  --data-binary "$(jq -nc --arg name "$runner_name" '{name:$name,ttlMinutes:10}')")"
code="${enrollment##*$'\n'}"; body="${enrollment%$'\n'*}"
[[ "$code" =~ ^2 ]] || fail "Runner enrollment token HTTP $code"
ENROLLMENT_TOKEN="$(printf '%s' "$body" | jq -r '.token // empty')"
[[ -n "$ENROLLMENT_TOKEN" ]] || fail 'Runner enrollment response omitted token'

docker volume create "$credential_volume" >/dev/null
docker run --detach --name "$container_name" \
  --volume "$credential_volume:/root/.orin" \
  --env "ORIN_ENROLLMENT_TOKEN=$ENROLLMENT_TOKEN" \
  "$RUNNER_IMAGE" enroll --name "$runner_name" --url "$RUNNER_BASE_URL" >/dev/null
unset ENROLLMENT_TOKEN

runner_id=""
deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  runners="$(curl -fsS "$BASE_URL/api/v1/runners?size=100" -H "Authorization: Bearer $TOKEN")"
  runner_id="$(printf '%s' "$runners" | jq -r --arg name "$runner_name" \
    '.content[]? | select(.name == $name and .status == "ONLINE") | .id' | head -n 1)"
  [[ -n "$runner_id" ]] && break
  if ! docker inspect "$container_name" --format '{{.State.Running}}' | grep -qx true; then
    docker logs --tail 40 "$container_name" >&2 || true
    fail 'temporary Runner exited before becoming ONLINE'
  fi
  sleep 1
done
[[ -n "$runner_id" ]] || fail "temporary Runner did not become ONLINE within ${TIMEOUT_SECONDS}s"
pass 'temporary Runner enrolled and ONLINE'

run="$(request -X POST "$BASE_URL/v1/endpoints/$ENDPOINT_ID/run" -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' --data-binary '{"input":"F05 external acceptance","timeoutMs":60000}')"
code="${run##*$'\n'}"; body="${run%$'\n'*}"
[[ "$code" =~ ^2 ]] || fail "external REST HTTP $code"
RUN_ID="$(printf '%s' "$body" | jq -r '.runId // empty')"; STATUS_URL="$(printf '%s' "$body" | jq -r '.statusUrl // empty')"
[[ -n "$RUN_ID" ]] || fail 'REST response omitted runId'
for _ in $(seq 1 30); do
  status="$(curl -fsS "$BASE_URL${STATUS_URL:-/v1/endpoints/$ENDPOINT_ID/runs/$RUN_ID}" -H "Authorization: Bearer $API_KEY")"
  state="$(printf '%s' "$status" | jq -r '.status // empty')"
  [[ "$state" == COMPLETED ]] && break
  [[ "$state" == FAILED || "$state" == CANCELLED ]] && fail "Runner completed Run with $state"
  sleep 2
done
[[ "$state" == COMPLETED ]] || fail "Run did not reach COMPLETED (last state: $state)"
TRACE_ID="$(printf '%s' "$status" | jq -r '.traceId // empty')"
[[ -n "$TRACE_ID" ]] || fail 'statusUrl response omitted traceId'
pass "external REST completed Run (traceId redacted, runId=$RUN_ID)"

invalid="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/v1/endpoints/$ENDPOINT_ID/run" -H 'Authorization: Bearer sk-orin-invalid-acceptance' -H 'Content-Type: application/json' --data-binary '{"input":"invalid"}')"
[[ "$invalid" == 401 ]] || fail "invalid key expected 401, got $invalid"
pass 'invalid API key rejected with 401'

# MCP uses the same in-memory key and is intentionally not logged.
init='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"f05-acceptance","version":"1"}}}'
list='{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
MCP_INIT="$(curl -fsS -X POST "$BASE_URL/v1/mcp" -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' --data-binary "$init")"
printf '%s' "$MCP_INIT" | jq -e '.result.serverInfo.name == "ORIN"' >/dev/null \
  || fail 'MCP initialize did not return the ORIN server identity'
MCP_LIST="$(curl -fsS -X POST "$BASE_URL/v1/mcp" -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' --data-binary "$list")"
TOOL_NAME="endpoint.$ENDPOINT_ID"
printf '%s' "$MCP_LIST" | jq -e --arg name "$TOOL_NAME" \
  '.result.tools | any(.name == $name)' >/dev/null \
  || fail 'MCP tools/list did not expose the newly published Endpoint to its assigned API key'
CALL="$(printf '%s' "$TOOL_NAME" | jq -Rs '{jsonrpc:"2.0",id:3,method:"tools/call",params:{name:(rtrimstr("\\n")),arguments:{input:"F05 MCP external acceptance"}}}')"
MCP_CALL="$(curl -fsS -X POST "$BASE_URL/v1/mcp" -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' --data-binary "$CALL")"
printf '%s' "$MCP_CALL" | jq -e '(.result.isError // false) == false and (.result.content | length) > 0' >/dev/null || fail 'MCP tools/call returned an error'
pass 'MCP initialize, tools/list, and tools/call completed'

# Optional release gate: exercise the published endpoint through the shipped
# stdio bridge, which is the same transport desktop MCP clients use.  It is
# opt-in because a normal F05 smoke does not require the bridge virtualenv.
if [[ "$REQUIRE_STDIO_BRIDGE" == "1" ]]; then
  [[ -n "$BRIDGE_PYTHON" && -x "$BRIDGE_PYTHON" ]] \
    || fail 'ORIN_MCP_BRIDGE_PYTHON must point to the bridge virtualenv Python'
  ORIN_BASE_URL="$BASE_URL" ORIN_API_KEY="$API_KEY" ORIN_EXPECTED_TOOL="$TOOL_NAME" \
    PYTHONPATH="$ORIN_ROOT/orin-mcp-bridge${PYTHONPATH:+:$PYTHONPATH}" \
    "$BRIDGE_PYTHON" - <<'PY'
import asyncio
import os
import sys

from mcp import ClientSession
from mcp.client.stdio import StdioServerParameters, stdio_client


async def main() -> None:
    bridge_root = os.environ["PYTHONPATH"].split(":", 1)[0]
    env = dict(os.environ)
    params = StdioServerParameters(
        command=sys.executable,
        args=["-m", "orin_mcp_bridge"],
        env=env,
        cwd=bridge_root,
    )
    expected_tool = os.environ["ORIN_EXPECTED_TOOL"]
    async with stdio_client(params) as (read_stream, write_stream):
        async with ClientSession(read_stream, write_stream) as session:
            await session.initialize()
            tools = await session.list_tools()
            if expected_tool not in {tool.name for tool in tools.tools}:
                raise RuntimeError("published Endpoint was absent from stdio bridge tools/list")
            result = await session.call_tool(expected_tool, {"input": "F05 stdio bridge acceptance"})
            if result.isError or not result.content:
                raise RuntimeError("stdio bridge tools/call returned an error")


asyncio.run(main())
PY
  pass 'MCP stdio bridge initialize, tools/list, and tools/call completed'
fi

curl -fsS -X POST "$BASE_URL/api/v1/endpoints/$ENDPOINT_ID/deactivate" "${auth[@]}" >/dev/null
inactive="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/v1/endpoints/$ENDPOINT_ID/run" -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' --data-binary '{"input":"inactive"}')"
[[ "$inactive" == 503 ]] || fail "inactive Endpoint expected 503, got $inactive"
pass 'inactive Endpoint rejected with 503'
printf '%s\n' "F05 external acceptance complete: endpoint=$ENDPOINT_ID run=$RUN_ID"
