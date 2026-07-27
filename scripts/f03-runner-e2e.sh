#!/usr/bin/env bash
# Real F03 vertical smoke: JWT user -> frozen AgentVersion -> enrolled Docker
# Runner -> TaskRuntime -> events/result persisted by the Control Plane.

set -euo pipefail

BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
RUNNER_BASE_URL="${ORIN_RUNNER_BASE_URL:-http://host.lima.internal:8080}"
ADMIN_USERNAME="${ORIN_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ORIN_ADMIN_PASSWORD:-}"
RUNNER_IMAGE="${ORIN_RUNNER_IMAGE:-orin-runner:f03}"
TIMEOUT_SECONDS="${ORIN_F03_TIMEOUT_SECONDS:-120}"

if [[ -z "$ADMIN_PASSWORD" ]]; then
    echo "ERROR ORIN_ADMIN_PASSWORD is required (it is never read from source files)." >&2
    exit 2
fi
for command in curl jq docker; do
    command -v "$command" >/dev/null || {
        echo "ERROR required command is missing: $command" >&2
        exit 2
    }
done

stamp="$(date +%s)-$$"
runner_name="f03-e2e-$stamp"
container_name="orin-f03-runner-$stamp"
credential_volume="orin-f03-credential-$stamp"
tmp_dir="$(mktemp -d)"

cleanup() {
    docker rm -f "$container_name" >/dev/null 2>&1 || true
    docker volume rm "$credential_volume" >/dev/null 2>&1 || true
    [[ -d "$tmp_dir" ]] && rm -r -- "$tmp_dir"
}
trap cleanup EXIT

request() {
    local method="$1"
    local path="$2"
    local output="$3"
    local body="${4:-}"
    local token="${5:-}"
    local args=(
        --silent --show-error
        --output "$output"
        --write-out "%{http_code}"
        --request "$method"
        --header "Accept: application/json"
    )
    [[ -n "$token" ]] && args+=(--header "Authorization: Bearer $token")
    if [[ -n "$body" ]]; then
        args+=(--header "Content-Type: application/json" --data "$body")
    fi
    curl "${args[@]}" "${BASE_URL%/}$path"
}

expect_2xx() {
    local label="$1"
    local status="$2"
    local response="$3"
    if [[ "$status" != 2* ]]; then
        local error_code
        error_code="$(jq -r '.code // "unknown"' "$response" 2>/dev/null || echo unknown)"
        echo "ERROR $label returned HTTP $status (code=$error_code)" >&2
        exit 1
    fi
}

login_response="$tmp_dir/login.json"
status="$(request POST "/api/v1/auth/login" "$login_response" \
    "$(jq -nc --arg username "$ADMIN_USERNAME" --arg password "$ADMIN_PASSWORD" \
        '{username:$username,password:$password,rememberMe:false}')")"
expect_2xx "login" "$status" "$login_response"
jwt="$(jq -er '.token' "$login_response")"
echo "PASS authenticated (JWT redacted)"

agent_response="$tmp_dir/agent.json"
status="$(request POST "/api/v1/agents" "$agent_response" \
    "$(jq -nc --arg name "F03 deterministic Agent $stamp" \
        '{name:$name,description:"Real Runner E2E fixture"}')" "$jwt")"
expect_2xx "create Agent" "$status" "$agent_response"
agent_id="$(jq -er '.agentId' "$agent_response")"

draft_response="$tmp_dir/draft.json"
status="$(request PUT "/api/v1/agents/$agent_id/draft" "$draft_response" \
    "$(jq -nc --arg name "F03 deterministic Agent $stamp" \
        '{name:$name,description:"Real Runner E2E fixture",mode:"agent",
          modelName:"deterministic",providerType:"ORIN_DETERMINISTIC",
          systemPrompt:"Return the deterministic test result.",
          temperature:0,topP:1,maxTokens:256,pendingSecretRefs:[]}')" "$jwt")"
expect_2xx "save Agent draft" "$status" "$draft_response"

freeze_response="$tmp_dir/freeze.json"
freeze_key="f03-$stamp"
status="$(curl --silent --show-error --output "$freeze_response" --write-out "%{http_code}" \
    --request POST "${BASE_URL%/}/api/v1/agents/$agent_id/versions" \
    --header "Accept: application/json" \
    --header "Authorization: Bearer $jwt" \
    --header "Idempotency-Key: $freeze_key")"
expect_2xx "freeze Agent" "$status" "$freeze_response"
version_id="$(jq -er '.agent_version_id' "$freeze_response")"
echo "PASS Agent frozen (agentId=$agent_id versionId=$version_id)"

token_response="$tmp_dir/enrollment-token.json"
status="$(request POST "/api/v1/runner-enrollment-tokens" "$token_response" \
    "$(jq -nc --arg name "$runner_name" '{name:$name,ttlMinutes:10}')" "$jwt")"
expect_2xx "create enrollment token" "$status" "$token_response"
enrollment_token="$(jq -er '.token' "$token_response")"

docker volume create "$credential_volume" >/dev/null
docker run --detach --name "$container_name" \
    --volume "$credential_volume:/root/.orin" \
    --env "ORIN_ENROLLMENT_TOKEN=$enrollment_token" \
    "$RUNNER_IMAGE" enroll \
    --name "$runner_name" \
    --url "$RUNNER_BASE_URL" >/dev/null
unset enrollment_token

deadline=$((SECONDS + TIMEOUT_SECONDS))
runner_id=""
while (( SECONDS < deadline )); do
    runners_response="$tmp_dir/runners.json"
    status="$(request GET "/api/v1/runners?size=100" "$runners_response" "" "$jwt")"
    expect_2xx "list Runners" "$status" "$runners_response"
    runner_id="$(jq -r --arg name "$runner_name" \
        '.content[]? | select(.name == $name and .status == "ONLINE") | .id' \
        "$runners_response" | head -n 1)"
    [[ -n "$runner_id" ]] && break
    if ! docker inspect "$container_name" --format '{{.State.Running}}' | grep -qx true; then
        echo "ERROR Runner container exited before becoming ONLINE" >&2
        docker logs --tail 40 "$container_name" >&2
        exit 1
    fi
    sleep 1
done
[[ -n "$runner_id" ]] || {
    echo "ERROR Runner did not become ONLINE within ${TIMEOUT_SECONDS}s" >&2
    exit 1
}
echo "PASS Runner enrolled and ONLINE (runnerId=$runner_id)"

run_input="execute-f03-$stamp"
run_response="$tmp_dir/run.json"
status="$(request POST "/api/v1/runs" "$run_response" \
    "$(jq -nc \
        --arg agentId "$agent_id" \
        --arg versionId "$version_id" \
        --arg runnerId "$runner_id" \
        --arg input "$run_input" \
        '{agentId:$agentId,agentVersionId:$versionId,runnerId:$runnerId,input:$input}')" "$jwt")"
expect_2xx "create Run" "$status" "$run_response"
run_id="$(jq -er '.id' "$run_response")"

final_status=""
deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
    status="$(request GET "/api/v1/runs/$run_id" "$run_response" "" "$jwt")"
    expect_2xx "get Run" "$status" "$run_response"
    final_status="$(jq -r '.status' "$run_response")"
    [[ "$final_status" == "COMPLETED" || "$final_status" == "FAILED" ]] && break
    sleep 1
done

if [[ "$final_status" != "COMPLETED" ]]; then
    error_code="$(jq -r '.terminalReason // .errorMessage // "unknown"' "$run_response")"
    echo "ERROR Run ended as $final_status ($error_code)" >&2
    docker logs --tail 60 "$container_name" >&2
    exit 1
fi

expected_output="ORIN deterministic runner result: $run_input"
actual_output="$(jq -r '.output' "$run_response")"
[[ "$actual_output" == "$expected_output" ]] || {
    echo "ERROR deterministic result mismatch" >&2
    exit 1
}

logs_response="$tmp_dir/logs.json"
status="$(request GET "/api/v1/runs/$run_id/logs" "$logs_response" "" "$jwt")"
expect_2xx "get Run logs" "$status" "$logs_response"
log_count="$(jq 'length' "$logs_response")"
(( log_count >= 2 )) || {
    echo "ERROR expected at least 2 persisted Runner logs, got $log_count" >&2
    exit 1
}

# F04: verify events endpoint
events_response="$tmp_dir/events.json"
status="$(request GET "/api/v1/runs/$run_id/events" "$events_response" "" "$jwt")"
expect_2xx "get Run events (F04)" "$status" "$events_response"
f04_event_count="$(jq 'length' "$events_response")"
(( f04_event_count >= 2 )) || {
    echo "ERROR F04 expected at least 2 events, got $f04_event_count" >&2
    exit 1
}

# F04: verify assignments endpoint
assignments_response="$tmp_dir/assignments.json"
status="$(request GET "/api/v1/runs/$run_id/assignments" "$assignments_response" "" "$jwt")"
expect_2xx "get Run assignments (F04)" "$status" "$assignments_response"
assignment_count="$(jq 'length' "$assignments_response")"
(( assignment_count >= 1 )) || {
    echo "ERROR F04 expected at least 1 assignment, got $assignment_count" >&2
    exit 1
}

trace_id="$(jq -er '.traceId' "$run_response")"
echo "PASS Run completed through TaskRuntime (runId=$run_id traceId=$trace_id logs=$log_count events=$f04_event_count assignments=$assignment_count)"
echo "F03_RUNNER_E2E_OK"
