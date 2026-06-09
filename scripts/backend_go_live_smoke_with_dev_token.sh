#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
USERNAME="${USERNAME:-admin}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-15}"

extract_token() {
  tr -d '\n' | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

echo "Requesting dev token from ${BASE_URL}/auth/dev-token using username=${USERNAME}"

DEV_TOKEN_RESPONSE="$(
  curl -sS --max-time "${TIMEOUT_SECONDS}" \
    -X POST "${BASE_URL}/auth/dev-token" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USERNAME}\"}"
)"

TOKEN="$(printf '%s' "${DEV_TOKEN_RESPONSE}" | extract_token || true)"

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: failed to extract token from dev-token response"
  echo "${DEV_TOKEN_RESPONSE}"
  exit 1
fi

echo "Dev token acquired, running smoke checks"
TOKEN="${TOKEN}" BASE_URL="${BASE_URL}" TIMEOUT_SECONDS="${TIMEOUT_SECONDS}" \
  bash "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/backend_go_live_smoke.sh"

