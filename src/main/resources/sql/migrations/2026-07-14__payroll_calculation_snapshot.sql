-- 薪资计算事实/规则版本摘要，以及支付子域状态投影。
-- 仅增加可空字段，不改写历史结果；历史批次按后续流程逐步补齐。

SET NAMES utf8mb4;
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='calculation_status'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `calculation_status` varchar(32) DEFAULT ''draft'' COMMENT ''核算状态'' AFTER `currency`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='batch_revision'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `batch_revision` int DEFAULT 1 COMMENT ''业务批次版本号'' AFTER `calculation_status`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='input_snapshot_hash'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT ''薪资输入事实快照摘要'' AFTER `batch_revision`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='input_snapshot_json'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `input_snapshot_json` json DEFAULT NULL COMMENT ''薪资输入事实完整快照'' AFTER `input_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='payment_status'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `payment_status` varchar(32) DEFAULT NULL COMMENT ''支付子域状态投影'' AFTER `payment_batch_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND INDEX_NAME='idx_payroll_payment_status'
);
SET @sql := IF(@index_exists=0,
  'CREATE INDEX `idx_payroll_payment_status` ON `payroll_batch` (`payment_status`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND INDEX_NAME='idx_calculation_status'
);
SET @sql := IF(@index_exists=0,
  'CREATE INDEX `idx_calculation_status` ON `payroll_batch` (`calculation_status`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND INDEX_NAME='idx_batch_revision'
);
SET @sql := IF(@index_exists=0,
  'CREATE INDEX `idx_batch_revision` ON `payroll_batch` (`batch_revision`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='rule_snapshot_hash'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT ''薪资规则快照摘要'' AFTER `input_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='rule_snapshot_json'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `rule_snapshot_json` json DEFAULT NULL COMMENT ''薪资规则完整快照'' AFTER `rule_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='calculation_engine_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT ''计算引擎版本'' AFTER `rule_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='batch_revision'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `batch_revision` int NOT NULL DEFAULT 1 COMMENT ''工资行所属批次版本号'' AFTER `batch_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='input_snapshot_hash'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT ''薪资输入事实快照摘要'' AFTER `items_snapshot_json`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='rule_snapshot_hash'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT ''薪资规则快照摘要'' AFTER `input_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='calculation_engine_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT ''计算引擎版本'' AFTER `rule_snapshot_hash`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND INDEX_NAME='idx_batch_revision_employee'
);
SET @sql := IF(@index_exists=0,
  'CREATE INDEX `idx_batch_revision_employee` ON `payroll_line` (`batch_id`, `batch_revision`, `employee_id`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
