#!/usr/bin/env bash
# Real F01 acceptance: a Docker-isolated Runner is enrolled through the
# Control Plane, acknowledges Drain, is restored, becomes OFFLINE after its
# container stops, recovers after resume, and exits after Credential revoke.
#
# Prerequisites:
#   - an already-running Control Plane reachable from this shell and Runner;
#   - an image built from orin-ai-engine/Dockerfile.runner;
#   - an Administrator password supplied through ORIN_ADMIN_PASSWORD.
#
# This script deliberately never prints an enrollment token, Runner
# credential, or JWT.  It creates only timestamped Docker resources and
# removes those exact resources at exit.

set -euo pipefail

BASE_URL="${ORIN_BASE_URL:-http://127.0.0.1:8080}"
RUNNER_BASE_URL="${ORIN_RUNNER_BASE_URL:-http://host.lima.internal:8080}"
ADMIN_USERNAME="${ORIN_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ORIN_ADMIN_PASSWORD:-}"
RUNNER_IMAGE="${ORIN_RUNNER_IMAGE:-orin-runner:f01}"
ONLINE_TIMEOUT_SECONDS="${ORIN_F01_ONLINE_TIMEOUT_SECONDS:-120}"
OFFLINE_TIMEOUT_SECONDS="${ORIN_F01_OFFLINE_TIMEOUT_SECONDS:-150}"
REVOKE_TIMEOUT_SECONDS="${ORIN_F01_REVOKE_TIMEOUT_SECONDS:-90}"

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
runner_name="f01-e2e-$stamp"
container_name="orin-f01-runner-$stamp"
credential_volume="orin-f01-credential-$stamp"
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
    [[ -n "$body" ]] && args+=(--header "Content-Type: application/json" --data "$body")
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

runner_status() {
    local response="$tmp_dir/runner.json"
    local status
    status="$(request GET "/api/v1/runners/$runner_id" "$response" "" "$jwt")"
    expect_2xx "get Runner detail" "$status" "$response"
    jq -r '.status' "$response"
}

wait_for_status() {
    local wanted="$1"
    local timeout="$2"
    local deadline=$((SECONDS + timeout))
    while (( SECONDS < deadline )); do
        if [[ "$(runner_status)" == "$wanted" ]]; then
            return 0
        fi
        sleep 1
    done
    echo "ERROR Runner did not become $wanted within ${timeout}s" >&2
    return 1
}

wait_for_drain_ack() {
    local deadline=$((SECONDS + ONLINE_TIMEOUT_SECONDS))
    while (( SECONDS < deadline )); do
        runner_status >/dev/null
        if [[ "$(jq -r '.status' "$tmp_dir/runner.json")" == "DRAINING" \
            && "$(jq -r '.drainAckAt != null' "$tmp_dir/runner.json")" == "true" ]]; then
            return 0
        fi
        sleep 1
    done
    echo "ERROR Runner did not acknowledge Drain within ${ONLINE_TIMEOUT_SECONDS}s" >&2
    return 1
}

login_response="$tmp_dir/login.json"
status="$(request POST "/api/v1/auth/login" "$login_response" \
    "$(jq -nc --arg username "$ADMIN_USERNAME" --arg password "$ADMIN_PASSWORD" \
        '{username:$username,password:$password,rememberMe:false}')")"
expect_2xx "login" "$status" "$login_response"
jwt="$(jq -er '.token' "$login_response")"
echo "PASS authenticated (JWT redacted)"

token_response="$tmp_dir/enrollment-token.json"
status="$(request POST "/api/v1/runner-enrollment-tokens" "$token_response" \
    "$(jq -nc --arg name "$runner_name" '{name:$name,ttlMinutes:10}')" "$jwt")"
expect_2xx "create enrollment token" "$status" "$token_response"
enrollment_token="$(jq -er '.token' "$token_response")"

docker volume create "$credential_volume" >/dev/null
docker run --detach --name "$container_name" \
    --volume "$credential_volume:/root/.orin" \
    --env "ORIN_ENROLLMENT_TOKEN=$enrollment_token" \
    "$RUNNER_IMAGE" enroll --name "$runner_name" --url "$RUNNER_BASE_URL" >/dev/null
unset enrollment_token

deadline=$((SECONDS + ONLINE_TIMEOUT_SECONDS))
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
    echo "ERROR Runner did not become ONLINE within ${ONLINE_TIMEOUT_SECONDS}s" >&2
    exit 1
}
echo "PASS enrollment and initial heartbeat (runnerId=$runner_id)"

drain_response="$tmp_dir/drain.json"
status="$(request POST "/api/v1/runners/$runner_id/drain" "$drain_response" "" "$jwt")"
expect_2xx "request Drain" "$status" "$drain_response"
wait_for_drain_ack
echo "PASS Drain was acknowledged while Runner kept heartbeating"

restore_response="$tmp_dir/restore.json"
status="$(request POST "/api/v1/runners/$runner_id/restore" "$restore_response" "" "$jwt")"
expect_2xx "restore Runner" "$status" "$restore_response"
wait_for_status "ONLINE" "$ONLINE_TIMEOUT_SECONDS"
if [[ "$(jq -r '.drainRequested' "$tmp_dir/runner.json")" != "false" ]]; then
    echo "ERROR Restore did not clear drainRequested" >&2
    exit 1
fi
echo "PASS Restore returned Runner to ONLINE"

docker rm -f "$container_name" >/dev/null
wait_for_status "OFFLINE" "$OFFLINE_TIMEOUT_SECONDS"
echo "PASS stopped Runner became OFFLINE after heartbeat timeout"

docker run --detach --name "$container_name" \
    --volume "$credential_volume:/root/.orin" \
    "$RUNNER_IMAGE" >/dev/null
wait_for_status "ONLINE" "$ONLINE_TIMEOUT_SECONDS"
echo "PASS saved credential resumed Runner and restored ONLINE"

revoke_response="$tmp_dir/revoke.json"
status="$(request POST "/api/v1/runners/$runner_id/revoke" "$revoke_response" "" "$jwt")"
expect_2xx "revoke Runner" "$status" "$revoke_response"
wait_for_status "REVOKED" "$REVOKE_TIMEOUT_SECONDS"

deadline=$((SECONDS + REVOKE_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
    if ! docker inspect "$container_name" --format '{{.State.Running}}' | grep -qx true; then
        echo "PASS revoked credential stopped Runner process"
        echo "F01_RUNNER_LIFECYCLE_E2E_OK"
        exit 0
    fi
    sleep 1
done
echo "ERROR Runner remained active after credential revoke" >&2
exit 1
