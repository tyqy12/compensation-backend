-- 薪酬核算阻断项修复：第三方应用对象授权、个税台账 revision 和扣除政策版本。
-- 本文件面向已有数据库执行；不删除任何已结算工资或台账事实。

SET NAMES utf8mb4;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_line` ADD COLUMN `employee_no_snapshot` VARCHAR(50) DEFAULT NULL COMMENT ''核算时员工工号快照'' AFTER `employee_id`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_line'
    AND column_name = 'employee_no_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_tax_deduction_declaration` ADD COLUMN `facts_json` JSON DEFAULT NULL COMMENT ''扣除事实JSON'' AFTER `evidence_json`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_tax_deduction_declaration'
    AND column_name = 'facts_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_line` ADD COLUMN `employee_name_snapshot` VARCHAR(100) DEFAULT NULL COMMENT ''核算时员工姓名快照'' AFTER `employee_no_snapshot`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_line'
    AND column_name = 'employee_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_line` ADD COLUMN `department_snapshot` VARCHAR(500) DEFAULT NULL COMMENT ''核算时部门快照'' AFTER `employee_name_snapshot`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_line'
    AND column_name = 'department_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `app_data_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` bigint NOT NULL,
  `scope_type` varchar(32) NOT NULL,
  `scope_value` varchar(128) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_data_grant` (`app_id`,`scope_type`,`scope_value`,`deleted`),
  KEY `idx_app_data_grant_active` (`app_id`,`status`,`scope_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方应用数据范围授权';

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_tax_ledger` ADD COLUMN `payroll_batch_revision` INT NOT NULL DEFAULT 1 COMMENT ''工资批次版本'' AFTER `payroll_batch_id`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_tax_ledger'
    AND column_name = 'payroll_batch_revision'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `payroll_tax_deduction_declaration` ADD COLUMN `policy_id` BIGINT DEFAULT NULL COMMENT ''扣除政策版本'' AFTER `source_type`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'payroll_tax_deduction_declaration'
    AND column_name = 'policy_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `payroll_tax_ledger` DROP INDEX `uk_tax_ledger_employee_period_batch`',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'payroll_tax_ledger'
    AND index_name = 'uk_tax_ledger_employee_period_batch'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_tax_ledger_employee_period_batch_revision` ON `payroll_tax_ledger` (`employee_id`,`tax_year`,`tax_month`,`payroll_batch_id`,`payroll_batch_revision`,`deleted`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'payroll_tax_ledger'
    AND index_name = 'uk_tax_ledger_employee_period_batch_revision'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已存在的标准政策包也必须具备七项扣除参数，不能只修复新库种子。
UPDATE `payroll_policy_package`
SET `payload_json` = JSON_SET(
  COALESCE(`payload_json`, JSON_OBJECT()),
  '$.deductions', JSON_OBJECT(
    'infant_care', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01'),
    'child_education', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01'),
    'continuing_education', JSON_OBJECT('monthlyAmount', 400, 'vocationalAnnualAmount', 3600),
    'major_medical', JSON_OBJECT('annualThreshold', 15000, 'annualLimit', 80000, 'settlementOnly', TRUE),
    'housing_loan_interest', JSON_OBJECT('monthlyAmount', 1000, 'maxMonths', 240),
    'rent', JSON_OBJECT('monthlyAmounts', JSON_ARRAY(800, 1100, 1500)),
    'elderly_care', JSON_OBJECT('singleChildMonthlyAmount', 3000, 'nonSingleTotalMonthlyAmount', 3000, 'perPersonLimit', 1500),
    'individual_pension', JSON_OBJECT('annualLimit', 12000, 'effectiveFrom', '2024-01-01')
  )
)
WHERE `code` = 'CN.RESIDENT_WAGE_WITHHOLDING'
  AND `version_no` = 1
  AND `deleted` = 0
  AND JSON_EXTRACT(`payload_json`, '$.deductions') IS NULL;

UPDATE `payroll_line` pl
JOIN `employee` e ON e.`id` = pl.`employee_id`
SET pl.`employee_no_snapshot` = COALESCE(pl.`employee_no_snapshot`, e.`employee_id`),
    pl.`employee_name_snapshot` = COALESCE(pl.`employee_name_snapshot`, e.`name`),
    pl.`department_snapshot` = COALESCE(pl.`department_snapshot`, e.`department`)
WHERE pl.`deleted` = 0
  AND (pl.`employee_no_snapshot` IS NULL OR pl.`employee_name_snapshot` IS NULL OR pl.`department_snapshot` IS NULL);

-- 应用级数据范围管理 API，供 fail-closed 的资源过滤器匹配。
SET @now := NOW();
SET @admin_parent_id := (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1);
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.admin.app-registry.data-grants.list','开放应用-数据范围列表','/api/admin/app-registry/{id}/data-grants',NULL,NULL,@admin_parent_id,194,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.data-grants.create','开放应用-授权数据范围','/api/admin/app-registry/{id}/data-grants',NULL,NULL,@admin_parent_id,195,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.data-grants.revoke','开放应用-撤销数据范围','/api/admin/app-registry/{id}/data-grants/{grantId}',NULL,NULL,@admin_parent_id,196,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, JSON_ARRAY('*'), @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
  'api.admin.app-registry.data-grants.list',
  'api.admin.app-registry.data-grants.create',
  'api.admin.app-registry.data-grants.revoke'
)
WHERE role.code IN ('ADMIN', 'role.admin.all')
ON DUPLICATE KEY UPDATE `actions_json`=VALUES(`actions_json`),`deleted`=0,`update_time`=@now;

COMMIT;
