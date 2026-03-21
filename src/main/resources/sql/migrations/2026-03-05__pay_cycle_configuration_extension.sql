-- ============================================================================
-- 发薪周期配置扩展
-- 日期: 2026-03-05
-- 目标:
--   1) 扩展 pay_cycle 可配置字段（编码/名称/类型/发薪日/时区/调度信息）
--   2) 兼容历史数据，补齐默认值
--   3) 增加检索与唯一索引
-- ============================================================================

SET NAMES utf8mb4;
SET @db := DATABASE();

-- cycle_code
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='cycle_code'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `cycle_code` varchar(64) DEFAULT NULL COMMENT ''周期编码'' AFTER `period_label`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cycle_name
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='cycle_name'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `cycle_name` varchar(100) DEFAULT NULL COMMENT ''周期名称'' AFTER `cycle_code`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cycle_type
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='cycle_type'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `cycle_type` varchar(20) DEFAULT NULL COMMENT ''周期类型(monthly/semi_monthly/weekly/biweekly/custom)'' AFTER `cycle_name`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- pay_day
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='pay_day'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `pay_day` tinyint DEFAULT NULL COMMENT ''发薪日(1-31)'' AFTER `cutoff_date`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lead_days
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='lead_days'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `lead_days` int DEFAULT NULL COMMENT ''提前天数'' AFTER `pay_day`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- grace_days
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='grace_days'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `grace_days` int DEFAULT NULL COMMENT ''宽限天数'' AFTER `lead_days`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- timezone
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='timezone'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `timezone` varchar(50) DEFAULT NULL COMMENT ''时区'' AFTER `grace_days`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- description
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='description'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `description` varchar(500) DEFAULT NULL COMMENT ''描述'' AFTER `timezone`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- next_execution_time
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='next_execution_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `next_execution_time` datetime DEFAULT NULL COMMENT ''下次执行时间'' AFTER `description`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- last_execution_time
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='last_execution_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `last_execution_time` datetime DEFAULT NULL COMMENT ''最近执行时间'' AFTER `next_execution_time`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- status 默认值统一为 draft
SET @status_default := (
  SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='status'
  LIMIT 1
);
SET @sql := IF(@status_default IS NULL OR @status_default <> 'draft',
  'ALTER TABLE `pay_cycle` MODIFY COLUMN `status` varchar(20) DEFAULT ''draft''',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据回填
UPDATE pay_cycle SET status = 'draft'
WHERE status IS NULL OR status = '';

UPDATE pay_cycle SET cycle_name = CONCAT(period_label, ' 发薪周期')
WHERE cycle_name IS NULL OR cycle_name = '';

UPDATE pay_cycle SET cycle_type = type
WHERE cycle_type IS NULL OR cycle_type = '';

UPDATE pay_cycle SET timezone = 'UTC+8'
WHERE timezone IS NULL OR timezone = '';

UPDATE pay_cycle
SET cycle_code = UPPER(CONCAT('CYCLE_', REPLACE(type, '-', '_'), '_', REPLACE(period_label, '-', '_')))
WHERE cycle_code IS NULL OR cycle_code = '';

-- 索引补齐
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND INDEX_NAME='uk_cycle_code'
);
SET @sql := IF(@idx_exists=0,
  'CREATE UNIQUE INDEX `uk_cycle_code` ON `pay_cycle` (`cycle_code`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND INDEX_NAME='idx_cycle_status_type'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_cycle_status_type` ON `pay_cycle` (`status`, `cycle_type`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND INDEX_NAME='idx_cycle_next_execution'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_cycle_next_execution` ON `pay_cycle` (`next_execution_time`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
