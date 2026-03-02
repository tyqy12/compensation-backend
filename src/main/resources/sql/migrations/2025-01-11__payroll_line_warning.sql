-- 添加 payroll_line 表的 warning 字段（幂等）
-- 用于存储薪资计算过程中的预警信息
-- 预警可能包括：税率异常、金额异常、社保基数异常等

SET @db := DATABASE();

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='warning'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `payroll_line` ADD COLUMN `warning` VARCHAR(500) NULL DEFAULT NULL COMMENT ''预警信息'' AFTER `note`',
    'SELECT ''warning exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- MySQL 不支持 PostgreSQL 风格的 partial index（WHERE ...）
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND INDEX_NAME='idx_payroll_line_warning'
);
SET @sql := IF(@idx_exists=0,
    'CREATE INDEX `idx_payroll_line_warning` ON `payroll_line` (`warning`)',
    'SELECT ''idx_payroll_line_warning exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
