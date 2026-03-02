-- Migration: Align audit_log with BaseEntity fields used by MyBatis-Plus
-- 兼容不支持 "ADD COLUMN IF NOT EXISTS" 的 MySQL 版本
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT ''update_time exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_by` varchar(50) DEFAULT NULL COMMENT ''更新人'' AFTER `create_by`',
  'SELECT ''update_by exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `deleted` tinyint(1) DEFAULT ''0'' COMMENT ''逻辑删除(0:未删除,1:已删除)'' AFTER `update_by`',
  'SELECT ''deleted exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `version` int DEFAULT ''0'' COMMENT ''乐观锁版本号'' AFTER `deleted`',
  'SELECT ''version exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
