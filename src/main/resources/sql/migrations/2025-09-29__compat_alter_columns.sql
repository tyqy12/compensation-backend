-- 兼容旧版本 MySQL 的列新增脚本（无 ADD COLUMN IF NOT EXISTS）
-- 使用 information_schema 判断后动态执行 ALTER TABLE
-- 运行：mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-09-29__compat_alter_columns.sql

SET NAMES utf8mb4;
SET @db := DATABASE();

-- sys_config.remark
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_config' AND COLUMN_NAME='remark'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `sys_config` ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT ''配置备注'' AFTER `config_value`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- audit_log.update_time
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- audit_log.update_by
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL COMMENT ''更新人'' AFTER `create_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- audit_log.deleted
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `deleted` TINYINT(1) DEFAULT ''0'' COMMENT ''逻辑删除(0:未删除,1:已删除)'' AFTER `update_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- audit_log.version
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `version` INT DEFAULT ''0'' COMMENT ''乐观锁版本号'' AFTER `deleted`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

