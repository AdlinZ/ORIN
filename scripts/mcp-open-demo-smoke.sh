#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
API_KEY="${ORIN_API_KEY:-}"
CALL_TOOLS="${ORIN_MCP_CALL_TOOLS:-0}"
AGENT_TOOL="${ORIN_MCP_AGENT_TOOL:-}"
AGENT_ID="${ORIN_MCP_AGENT_ID:-}"
AGENT_MESSAGE="${ORIN_MCP_AGENT_MESSAGE:-Summarize the ORIN MCP open demo status in one short paragraph.}"
WORKFLOW_TOOL="${ORIN_MCP_WORKFLOW_TOOL:-}"
MCP_ORIGIN="${ORIN_MCP_ORIGIN:-}"
REQUIRE_AGENT_TOOL="${ORIN_MCP_REQUIRE_AGENT_TOOL:-0}"
REQUIRE_WORKFLOW_TOOL="${ORIN_MCP_REQUIRE_WORKFLOW_TOOL:-0}"
REQUIRE_TRACE_METADATA="${ORIN_MCP_REQUIRE_TRACE_METADATA:-0}"

if [ -z "$API_KEY" ]; then
  echo "error: ORIN_API_KEY is required and must be a CLIENT_ACCESS key" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

agent_tool_from_id() {
  python3 - "$1" <<'PY'
import base64
import sys

agent_id = sys.argv[1].encode("utf-8")
encoded = base64.urlsafe_b64encode(agent_id).decode("ascii").rstrip("=")
print(f"agent.{encoded}")
PY
}

if [ -z "$AGENT_TOOL" ] && [ -n "$AGENT_ID" ]; then
  AGENT_TOOL="$(agent_tool_from_id "$AGENT_ID")"
fi

post_mcp() {
  local payload="$1"
  local out="$2"
  local code
  local headers=(
    -H "Authorization: Bearer $API_KEY"
    -H "Content-Type: application/json"
  )
  if [ -n "$MCP_ORIGIN" ]; then
    headers+=(-H "Origin: $MCP_ORIGIN")
  fi
  code="$(curl -sS -o "$out" -w "%{http_code}" \
    -X POST "$BASE_URL/v1/mcp" \
    "${headers[@]}" \
    --data-binary @"$payload")"
  if [ "$code" -lt 200 ] || [ "$code" -ge 300 ]; then
    echo "error: /v1/mcp returned HTTP $code" >&2
    python3 - "$out" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    if isinstance(data, dict):
        print(json.dumps({k: v for k, v in data.items() if k.lower() != "authorization"}, ensure_ascii=False))
except Exception:
    pass
PY
    exit 1
  fi
}

write_initialize() {
  cat > "$1" <<'JSON'
{
  "jsonrpc": "2.0",
  "id": "orin-demo-init",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-03-26",
    "capabilities": {},
    "clientInfo": { "name": "orin-open-demo-smoke", "version": "1.0.0" }
  }
}
JSON
}

write_tools_list() {
  cat > "$1" <<'JSON'
{
  "jsonrpc": "2.0",
  "id": "orin-demo-tools",
  "method": "tools/list",
  "params": {}
}
JSON
}

write_tool_call() {
  local name="$1"
  local kind="$2"
  local path="$3"
  local message="$4"
  python3 - "$name" "$kind" "$path" "$message" <<'PY'
import json, sys
name, kind, path, message = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
arguments = {"message": message}
if kind == "workflow":
    arguments = {"query": "Summarize the ORIN MCP open demo status in one short paragraph."}
payload = {
    "jsonrpc": "2.0",
    "id": f"orin-demo-call-{kind}",
    "method": "tools/call",
    "params": {"name": name, "arguments": arguments},
}
with open(path, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False)
PY
}

extract_tool() {
  local prefix="$1"
  local preferred="$2"
  python3 - "$TMP_DIR/tools-response.json" "$prefix" "$preferred" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
prefix, preferred = sys.argv[2], sys.argv[3]
tools = data.get("result", {}).get("tools", [])
names = [tool.get("name", "") for tool in tools if str(tool.get("name", "")).startswith(prefix)]
if preferred:
    print(preferred if preferred in names else "")
else:
    print(names[0] if names else "")
PY
}

summarize_tools() {
  python3 - "$TMP_DIR/tools-response.json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
tools = data.get("result", {}).get("tools", [])
agent_count = sum(1 for tool in tools if str(tool.get("name", "")).startswith("agent."))
workflow_count = sum(1 for tool in tools if str(tool.get("name", "")).startswith("workflow."))
print(f"tools/list ok: total={len(tools)} agents={agent_count} workflows={workflow_count}")
PY
}

summarize_call() {
  local label="$1"
  local response="$2"
  local require_trace="$3"
  python3 - "$label" "$response" "$require_trace" <<'PY'
import json, sys
label, path, require_trace = sys.argv[1], sys.argv[2], sys.argv[3]
data = json.load(open(path, encoding="utf-8"))
if "error" in data:
    print(f"{label} call returned MCP error: {data['error'].get('code')} {data['error'].get('message')}")
    sys.exit(1)
result = data.get("result", {})
if result.get("isError") is True:
    print(f"{label} call returned tool error")
    sys.exit(1)
content = result.get("content", [])
text = "\n".join(item.get("text", "") for item in content if isinstance(item, dict))
if not text.strip():
    print(f"{label} call returned empty text content")
    sys.exit(1)
trace_present = "Trace ID:" in text or "traceId" in text
package_present = "Package ID:" in text or "packageId" in text
print(f"{label} call ok: contentItems={len(content)} traceMetadata={trace_present}")
if require_trace == "1" and not trace_present:
    print(f"{label} call missing trace metadata")
    sys.exit(1)
if require_trace == "1" and label == "agent" and not package_present:
    print("agent call missing package metadata")
    sys.exit(1)
PY
}

write_initialize "$TMP_DIR/init.json"
post_mcp "$TMP_DIR/init.json" "$TMP_DIR/init-response.json"
echo "initialize ok"

write_tools_list "$TMP_DIR/tools.json"
post_mcp "$TMP_DIR/tools.json" "$TMP_DIR/tools-response.json"
summarize_tools

if [ "$CALL_TOOLS" = "1" ]; then
  selected_agent="$(extract_tool "agent." "$AGENT_TOOL")"
  selected_workflow="$(extract_tool "workflow." "$WORKFLOW_TOOL")"

  if [ -n "$selected_agent" ]; then
    write_tool_call "$selected_agent" "agent" "$TMP_DIR/agent-call.json" "$AGENT_MESSAGE"
    post_mcp "$TMP_DIR/agent-call.json" "$TMP_DIR/agent-call-response.json"
    summarize_call "agent" "$TMP_DIR/agent-call-response.json" "$REQUIRE_TRACE_METADATA"
  else
    if [ "$REQUIRE_AGENT_TOOL" = "1" ]; then
      echo "error: required exposed agent tool not found" >&2
      echo "hint: ensure the Agent is mcpExposed=true and owned by the CLIENT_ACCESS key user" >&2
      exit 1
    fi
    echo "agent call skipped: no exposed agent tool found"
  fi

  if [ -n "$selected_workflow" ]; then
    write_tool_call "$selected_workflow" "workflow" "$TMP_DIR/workflow-call.json" "$AGENT_MESSAGE"
    post_mcp "$TMP_DIR/workflow-call.json" "$TMP_DIR/workflow-call-response.json"
    summarize_call "workflow" "$TMP_DIR/workflow-call-response.json" "$REQUIRE_TRACE_METADATA"
  else
    if [ "$REQUIRE_WORKFLOW_TOOL" = "1" ]; then
      echo "error: required exposed workflow tool not found" >&2
      exit 1
    fi
    echo "workflow call skipped: no exposed workflow tool found"
  fi
else
  echo "tool calls skipped: set ORIN_MCP_CALL_TOOLS=1 to call exposed Agent/Workflow tools"
fi
