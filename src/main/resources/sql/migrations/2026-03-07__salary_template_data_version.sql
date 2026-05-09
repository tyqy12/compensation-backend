-- ============================================================================
-- 薪资模板数据版本号
-- 目标：确保 salary_template 实体、全量 schema 与已有库结构一致。
-- ============================================================================

SET NAMES utf8mb4;
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_template' AND COLUMN_NAME='data_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `salary_template` ADD COLUMN `data_version` bigint DEFAULT ''1'' COMMENT ''模板数据版本号'' AFTER `status`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE salary_template
SET data_version = 1
WHERE data_version IS NULL;
