#!/usr/bin/env bash

set -euo pipefail

# Simple API smoke tests for local dev
# Requirements: bash, curl, sed

BASE_URL=${BASE_URL:-"http://localhost:8080/api"}
USERNAME=${USERNAME:-"alice"}

echo "[i] Using BASE_URL=${BASE_URL}"

function curl_json() {
  local method="$1"; shift
  local url="$1"; shift
  local body="${1:-}"
  if [[ -n "${body}" ]]; then
    curl -sS -X "${method}" "${url}" -H 'Content-Type: application/json' -d "${body}"
  else
    curl -sS -X "${method}" "${url}"
  fi
}

function extract_token() {
  # Extracts token value from ApiResponse { data: { token: "..." } }
  tr -d '\n' | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

function extract_id_from_data() {
  # Extracts numeric id from ApiResponse { data: 123 }
  tr -d '\n' | sed -n 's/.*"data"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

echo "[1/7] System health..."
HEALTH=$(curl_json GET "${BASE_URL}/system/health")
echo "${HEALTH}" | sed -E 's/.{200}/&\n/g'

echo "[2/7] Get dev JWT (roles=MANAGER,APPROVER; approval full permissions)..."
DEV_TOKEN_RESP=$(curl_json POST "${BASE_URL}/auth/dev-token" '{"username":"'"${USERNAME}"'","roles":["MANAGER","APPROVER"],"authorities":["approval:start","approval:approve","approval:reject","approval:cancel","approval:read"]}')
JWT=$(echo "${DEV_TOKEN_RESP}" | extract_token || true)
if [[ -z "${JWT}" ]]; then
  echo "[!] Failed to obtain JWT. Full response:" >&2
  echo "${DEV_TOKEN_RESP}" >&2
  exit 1
fi
AUTH_HEADER=( -H "Authorization: Bearer ${JWT}" )
echo "[i] Token acquired"

echo "[3/7] Start approval workflow (BATCH)..."
START_RESP=$(curl -sS -X POST "${BASE_URL}/approval/workflows" "${AUTH_HEADER[@]}" -H 'Content-Type: application/json' \
  -d '{"workflowType":"BATCH","businessKey":"BATCH_'"$(date +%Y%m%d_%H%M%S)"'","businessType":"PAYMENT","initiatorId":1,"workflowData":{"batchNo":"BATCH_DEV"}}')
echo "${START_RESP}" | sed -E 's/.{200}/&\n/g'
WID=$(echo "${START_RESP}" | extract_id_from_data || true)
if [[ -z "${WID}" ]]; then
  echo "[!] Failed to parse workflow id from response" >&2
  exit 1
fi
echo "[i] Workflow id: ${WID}"

echo "[4/7] My pending (approverId=2)..."
PENDING_2=$(curl_json GET "${BASE_URL}/approval/workflows/pending?approverId=2" | sed -E 's/.{200}/&\n/g')
echo "${PENDING_2}"

echo "[5/7] Approve step 1 as approverId=2..."
APPROVE_1=$(curl -sS -X POST "${BASE_URL}/approval/workflows/${WID}/approve" "${AUTH_HEADER[@]}" -H 'Content-Type: application/json' -d '{"approverId":2,"comment":"OK step1"}')
echo "${APPROVE_1}" | sed -E 's/.{200}/&\n/g'

echo "[6/7] Approve step 2 as approverId=3, then step 3 as approverId=1..."
APPROVE_2=$(curl -sS -X POST "${BASE_URL}/approval/workflows/${WID}/approve" "${AUTH_HEADER[@]}" -H 'Content-Type: application/json' -d '{"approverId":3,"comment":"OK step2"}')
echo "${APPROVE_2}" | sed -E 's/.{200}/&\n/g'
APPROVE_3=$(curl -sS -X POST "${BASE_URL}/approval/workflows/${WID}/approve" "${AUTH_HEADER[@]}" -H 'Content-Type: application/json' -d '{"approverId":1,"comment":"OK step3"}')
echo "${APPROVE_3}" | sed -E 's/.{200}/&\n/g'

echo "[7/7] Workflow detail & steps..."
DETAIL=$(curl_json GET "${BASE_URL}/approval/workflows/${WID}" | sed -E 's/.{200}/&\n/g')
echo "${DETAIL}"
STEPS=$(curl_json GET "${BASE_URL}/approval/workflows/${WID}/steps" | sed -E 's/.{200}/&\n/g')
echo "${STEPS}"

echo "[✓] API smoke tests finished"

