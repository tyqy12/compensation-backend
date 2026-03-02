-- 发薪确认流程增强（员工签字确认/异议/负责人批量确认）
-- 幂等迁移：新增 payroll_batch 与 payroll_line 相关字段和索引

SET NAMES utf8mb4;
SET @db := DATABASE();

-- ==================== payroll_batch ====================

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='confirmation_required'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `confirmation_required` tinyint(1) DEFAULT 1 COMMENT ''是否需要员工确认'' AFTER `payment_batch_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='confirmation_mode'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `confirmation_mode` varchar(20) DEFAULT ''individual'' COMMENT ''确认模式(individual/group)'' AFTER `confirmation_required`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='confirmation_completed_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `confirmation_completed_time` datetime DEFAULT NULL COMMENT ''确认完成时间'' AFTER `confirmation_mode`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== payroll_line ====================

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmation_assignee_employee_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmation_assignee_employee_id` bigint DEFAULT NULL COMMENT ''确认负责人员工ID'' AFTER `warning`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmation_status'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmation_status` varchar(30) DEFAULT ''pending'' COMMENT ''确认状态'' AFTER `confirmation_assignee_employee_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmed_by_user_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmed_by_user_id` bigint DEFAULT NULL COMMENT ''确认人用户ID'' AFTER `confirmation_status`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmed_by_employee_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmed_by_employee_id` bigint DEFAULT NULL COMMENT ''确认人员工ID'' AFTER `confirmed_by_user_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmed_at'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmed_at` datetime DEFAULT NULL COMMENT ''确认时间'' AFTER `confirmed_by_employee_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='confirmation_comment'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `confirmation_comment` varchar(500) DEFAULT NULL COMMENT ''确认备注/签字'' AFTER `confirmed_at`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='objection_reason'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `objection_reason` varchar(500) DEFAULT NULL COMMENT ''异议原因'' AFTER `confirmation_comment`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='objection_at'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `objection_at` datetime DEFAULT NULL COMMENT ''异议时间'' AFTER `objection_reason`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='dispute_workflow_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `dispute_workflow_id` bigint DEFAULT NULL COMMENT ''异议审批流程ID'' AFTER `objection_at`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND INDEX_NAME='idx_confirmation_assignee_status'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_confirmation_assignee_status` ON `payroll_line` (`confirmation_assignee_employee_id`, `confirmation_status`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND INDEX_NAME='idx_dispute_workflow'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_dispute_workflow` ON `payroll_line` (`dispute_workflow_id`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== data backfill ====================

UPDATE `payroll_batch`
SET `confirmation_required` = 1
WHERE `confirmation_required` IS NULL;

UPDATE `payroll_batch`
SET `confirmation_mode` = 'individual'
WHERE `confirmation_mode` IS NULL OR `confirmation_mode` = '';

UPDATE `payroll_line`
SET `confirmation_assignee_employee_id` = `employee_id`
WHERE `confirmation_assignee_employee_id` IS NULL;

UPDATE `payroll_line`
SET `confirmation_status` = 'pending'
WHERE `confirmation_status` IS NULL OR `confirmation_status` = '';
