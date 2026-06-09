-- ============================================================================
-- 结算渠道增强 - 回滚脚本
-- 版本: V1.3 Rollback
-- 日期: 2026-03-01
-- 说明: 回滚 2026-03-01__settlement_provider_enhancement.sql 的增量变更
-- 注意: 保留 2026-02-26 已存在的基础表（settlement_provider_config / settlement_reconciliation）
-- ============================================================================

SET NAMES utf8mb4;
SET @db := DATABASE();

-- 1) 删除 payroll_batch 表扩展字段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='settlement_provider_code'
);
SET @sql := IF(@exists=1,
  'ALTER TABLE `payroll_batch` DROP COLUMN `settlement_provider_code`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 删除 employee 表扩展字段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='settlement_provider_code'
);
SET @sql := IF(@exists=1,
  'ALTER TABLE `employee` DROP COLUMN `settlement_provider_code`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 删除员工类型渠道映射表
DROP TABLE IF EXISTS employee_type_provider_mapping;

-- 4) 回滚 settlement_provider_config 的增量字段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='provider_type'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `provider_type`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_endpoint'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `api_endpoint`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_key'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `api_key`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_secret'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `api_secret`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='merchant_id'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `merchant_id`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='notify_url'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `notify_url`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='return_url'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `return_url`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='priority'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `priority`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='remark'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `remark`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='create_by'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `create_by`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `update_by`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `deleted`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=1, 'ALTER TABLE `settlement_provider_config` DROP COLUMN `version`', 'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
