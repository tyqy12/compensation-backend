#!/usr/bin/env bash

set -euo pipefail

# Yunzhanghu integration-config CRUD regression script
# Requirements: bash, curl, sed
# Usage:
#   BASE_URL=http://localhost:8080/api USERNAME=admin ./scripts/test_yunzhanghu_config_crud.sh

BASE_URL=${BASE_URL:-"http://localhost:8080/api"}
USERNAME=${USERNAME:-"admin"}
PLATFORM="yunzhanghu"

echo "[i] Using BASE_URL=${BASE_URL}"

curl_json() {
  local method="$1"; shift
  local url="$1"; shift
  local body="${1:-}"
  if [[ -n "${body}" ]]; then
    curl -sS -X "${method}" "${url}" -H "Content-Type: application/json" -d "${body}"
  else
    curl -sS -X "${method}" "${url}"
  fi
}

extract_token() {
  tr -d '\n' | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

assert_contains() {
  local text="$1"
  local expect="$2"
  local label="$3"
  if ! echo "${text}" | grep -Fq "${expect}"; then
    echo "[x] ${label} 失败，未找到: ${expect}" >&2
    echo "[x] 响应内容: ${text}" >&2
    exit 1
  fi
}

echo "[1/8] 获取本地开发 JWT（ADMIN）..."
DEV_TOKEN_RESP=$(curl_json POST "${BASE_URL}/auth/dev-token" \
  '{"username":"'"${USERNAME}"'","roles":["ADMIN"],"authorities":["*:*:*"]}')
JWT=$(echo "${DEV_TOKEN_RESP}" | extract_token || true)
if [[ -z "${JWT}" ]]; then
  echo "[x] 获取 JWT 失败" >&2
  echo "${DEV_TOKEN_RESP}" >&2
  exit 1
fi
AUTH_HEADER=( -H "Authorization: Bearer ${JWT}" )
echo "[i] Token acquired"

echo "[2/8] 初始列表查询..."
LIST_BEFORE=$(curl -sS -X GET "${BASE_URL}/admin/integration-configs" "${AUTH_HEADER[@]}")
assert_contains "${LIST_BEFORE}" "\"platformType\":\"${PLATFORM}\"" "列表查询"
echo "[✓] 初始列表查询通过"

echo "[3/8] 保存（启用）云账户配置..."
ENABLE_PAYLOAD='{
  "enabled": true,
  "yunzhanghu": {
    "dealerId": "dealer-1234",
    "brokerId": "broker-5678",
    "appKey": "appkey-9999",
    "des3Key": "0123456789abcdef01234567",
    "rsaPrivateKey": "-----BEGIN PRIVATE KEY-----FAKE-PRIVATE-KEY-----END PRIVATE KEY-----",
    "rsaPublicKey": "-----BEGIN PUBLIC KEY-----FAKE-PUBLIC-KEY-----END PUBLIC KEY-----",
    "signType": "rsa",
    "url": "https://api-service.yunzhanghu.com/sandbox",
    "notifyUrl": "https://example.com/api/v1/settlement/callback/yunzhanghu",
    "projectId": "payroll",
    "dealerPlatformName": "CompensationSystem",
    "checkName": "Check",
    "isDebug": true
  }
}'
SAVE_RESP=$(curl -sS -X PUT "${BASE_URL}/admin/integration-configs/${PLATFORM}" "${AUTH_HEADER[@]}" \
  -H "Content-Type: application/json" -d "${ENABLE_PAYLOAD}")
assert_contains "${SAVE_RESP}" "\"data\":\"配置保存成功\"" "保存配置"
echo "[✓] 保存配置通过"

echo "[4/8] 读取详情（应为 enabled=true，且配置有脱敏字段）..."
DETAIL_ENABLED=$(curl -sS -X GET "${BASE_URL}/admin/integration-configs/${PLATFORM}" "${AUTH_HEADER[@]}")
assert_contains "${DETAIL_ENABLED}" "\"platformType\":\"${PLATFORM}\"" "详情读取"
assert_contains "${DETAIL_ENABLED}" "\"enabled\":true" "详情读取"
assert_contains "${DETAIL_ENABLED}" "\"dealerId\":\"***" "详情脱敏"
echo "[✓] 启用态详情读取通过"

echo "[5/8] 禁用配置（DELETE）..."
DISABLE_RESP=$(curl -sS -X DELETE "${BASE_URL}/admin/integration-configs/${PLATFORM}" "${AUTH_HEADER[@]}")
assert_contains "${DISABLE_RESP}" "\"data\":\"配置已禁用\"" "禁用配置"
echo "[✓] 禁用操作通过"

echo "[6/8] 读取详情（应为 enabled=false，但仍可回显 config）..."
DETAIL_DISABLED=$(curl -sS -X GET "${BASE_URL}/admin/integration-configs/${PLATFORM}" "${AUTH_HEADER[@]}")
assert_contains "${DETAIL_DISABLED}" "\"enabled\":false" "禁用后详情读取"
assert_contains "${DETAIL_DISABLED}" "\"config\":" "禁用后详情读取"
assert_contains "${DETAIL_DISABLED}" "\"dealerId\":\"***" "禁用后脱敏回显"
echo "[✓] 禁用后详情可读通过"

echo "[7/8] 重新启用配置（验证可再次编辑保存）..."
RE_ENABLE_RESP=$(curl -sS -X PUT "${BASE_URL}/admin/integration-configs/${PLATFORM}" "${AUTH_HEADER[@]}" \
  -H "Content-Type: application/json" -d "${ENABLE_PAYLOAD}")
assert_contains "${RE_ENABLE_RESP}" "\"data\":\"配置保存成功\"" "重新启用"
echo "[✓] 重新启用通过"

echo "[8/8] 最终列表校验（configured=true）..."
LIST_AFTER=$(curl -sS -X GET "${BASE_URL}/admin/integration-configs" "${AUTH_HEADER[@]}")
assert_contains "${LIST_AFTER}" "\"platformType\":\"${PLATFORM}\"" "最终列表"
assert_contains "${LIST_AFTER}" "\"configured\":true" "最终列表"
echo "[✓] 云账户配置 CRUD 回归通过"

