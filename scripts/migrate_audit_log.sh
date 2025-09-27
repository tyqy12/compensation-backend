#!/usr/bin/env bash

set -euo pipefail

# Migration helper for adding missing BaseEntity columns to audit_log.
# Requires mysql client. Works with MySQL 8.0+ (uses IF NOT EXISTS).
#
# Usage:
#   DB_HOST=localhost DB_PORT=3306 DB_NAME=compensation_dev \
#   DB_USER=root DB_PASSWORD=secret \
#   ./scripts/migrate_audit_log.sh
#

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-}
DB_USER=${DB_USER:-}
DB_PASSWORD=${DB_PASSWORD:-}

if [[ -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "[!] Please set DB_NAME, DB_USER and DB_PASSWORD environment variables." >&2
  echo "    Example: DB_NAME=compensation_dev DB_USER=root DB_PASSWORD=secret ./scripts/migrate_audit_log.sh" >&2
  exit 1
fi

echo "[i] Running audit_log migration on ${DB_HOST}:${DB_PORT}/${DB_NAME}"

SQL=$(cat <<'EOSQL'
ALTER TABLE `audit_log`
  ADD COLUMN IF NOT EXISTS `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

ALTER TABLE `audit_log`
  ADD COLUMN IF NOT EXISTS `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`;

ALTER TABLE `audit_log`
  ADD COLUMN IF NOT EXISTS `deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`;

ALTER TABLE `audit_log`
  ADD COLUMN IF NOT EXISTS `version` INT DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`;
EOSQL
)

echo "[i] Applying migration..."

if command -v mysql >/dev/null 2>&1; then
  MYSQL_OPTS=( -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" )
  echo "$SQL" | mysql "${MYSQL_OPTS[@]}"
  echo "[✓] Migration completed via mysql client."
  exit 0
fi

if command -v mariadb >/dev/null 2>&1; then
  MARIADB_OPTS=( -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" )
  echo "$SQL" | mariadb "${MARIADB_OPTS[@]}"
  echo "[✓] Migration completed via mariadb client."
  exit 0
fi

if command -v docker >/dev/null 2>&1; then
  echo "$SQL" | docker run --rm -i mysql:8.0 \
    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" --password="${DB_PASSWORD}" "${DB_NAME}"
  echo "[✓] Migration completed via dockerized mysql client."
  exit 0
fi

echo "[!] No mysql/mariadb client found, and docker not available. Please install mysql-client or docker, or run the SQL manually:" >&2
echo "" >&2
echo "$SQL" >&2
exit 1
