-- 员工主数据一致性加固
-- 新增员工相关审批流编码，兼容旧库 workflow_type varchar(20)。
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=@db AND TABLE_NAME='approval_workflow' AND COLUMN_NAME='workflow_type') > 0,
    'ALTER TABLE `approval_workflow` MODIFY COLUMN `workflow_type` varchar(50) NOT NULL COMMENT ''流程类型(BATCH/ADHOC/OFFLINE/EMPLOYEE_PROFILE_CHANGE/PLATFORM_BIND)''',
    'SELECT 1 AS noop'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- settlement_account 保存随机 IV + 密文 + Base64，不能使用按明文长度设计的 varchar(128)。
ALTER TABLE `employee`
    MODIFY COLUMN `department` varchar(500) DEFAULT NULL
        COMMENT '部门(兼容展示字段，多部门关系见employee_department)',
    MODIFY COLUMN `settlement_account` text
        COMMENT '收款账户(加密存储)';

UPDATE `employee_department`
SET `platform_type` = 'manual'
WHERE `platform_type` IS NULL OR `platform_type` = '';

-- 关系替换必须按员工和平台隔离；该索引支持同步与查询路径（幂等）。
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee_department'
      AND INDEX_NAME='idx_employee_department_platform'
);
SET @sql := IF(
    @idx_exists=0,
    'CREATE INDEX `idx_employee_department_platform` ON `employee_department` (`employee_id`, `platform_type`, `deleted`)',
    'SELECT 1 AS noop'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
