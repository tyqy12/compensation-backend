#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
TOKEN="${TOKEN:-}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-15}"

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: TOKEN is required"
  echo "Usage: TOKEN=<bearer-token> $0"
  exit 1
fi

AUTH_HEADER="Authorization: Bearer ${TOKEN}"

PASS_COUNT=0
FAIL_COUNT=0

log_pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[PASS] %s\n' "$1"
}

log_fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %s\n' "$1"
}

run_check() {
  local name="$1"
  local expected_pattern="$2"
  shift 2
  local response
  if ! response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" "$@")"; then
    log_fail "${name} (curl failed)"
    return
  fi
  if printf '%s' "${response}" | rg -q "${expected_pattern}"; then
    log_pass "${name}"
  else
    log_fail "${name}"
    printf '  response: %s\n' "${response}"
  fi
}

echo "Running backend go-live smoke checks against ${BASE_URL}"

run_check "system health" '"code"\s*:\s*200|success|UP|healthy' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/system/health"

run_check "audit log missing returns not found" '1002|RESOURCE_NOT_FOUND|审计日志不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/admin/audit-logs/999999"

run_check "app registry missing returns not found" '1002|RESOURCE_NOT_FOUND|应用不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/admin/app-registry/999999"

run_check "organization sync task missing returns not found" '1002|RESOURCE_NOT_FOUND|同步任务不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/system/org/sync-task/not-exists"

run_check "payment batch missing detail returns not found" '1002|RESOURCE_NOT_FOUND|批次不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/payment/batch/PB-404-NOT-FOUND"

run_check "payment batch missing precheck returns not found" '1002|RESOURCE_NOT_FOUND|批次不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/payment/batch/PB-404-NOT-FOUND/precheck?persistFailure=true"

run_check "payment record missing returns not found" '1002|RESOURCE_NOT_FOUND|支付记录不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/payment/record/999999"

run_check "task schedule missing returns not found" '1002|RESOURCE_NOT_FOUND|任务不存在' \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/v1/admin/tasks/999999"

run_check "task pause missing returns not found" '1002|RESOURCE_NOT_FOUND|任务不存在' \
  -X POST \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/v1/admin/tasks/999999/pause"

run_check "task resume missing returns not found" '1002|RESOURCE_NOT_FOUND|任务不存在' \
  -X POST \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/v1/admin/tasks/999999/resume"

run_check "task delete missing returns not found" '1002|RESOURCE_NOT_FOUND|任务不存在' \
  -X DELETE \
  -H "${AUTH_HEADER}" \
  "${BASE_URL}/v1/admin/tasks/999999"

echo
echo "Smoke result: pass=${PASS_COUNT}, fail=${FAIL_COUNT}"

if [[ "${FAIL_COUNT}" -gt 0 ]]; then
  exit 1
fi

