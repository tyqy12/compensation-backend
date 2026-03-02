-- approval_workflow 增加 employee_id 关联字段（幂等）
-- 用于员工详情页按员工维度查询审批记录

SET NAMES utf8mb4;
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='approval_workflow' AND COLUMN_NAME='employee_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `approval_workflow` ADD COLUMN `employee_id` bigint DEFAULT NULL COMMENT ''关联员工ID'' AFTER `initiator_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='approval_workflow' AND INDEX_NAME='idx_employee'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_employee` ON `approval_workflow` (`employee_id`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
