-- 薪酬合规核算基础：政策版本、累计个税、扣除申报、多地参保和计算证据链。
-- 只新增能力和可空字段，不覆盖已经结算的工资结果。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `payroll_policy_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL COMMENT '政策编码',
  `name` varchar(200) NOT NULL COMMENT '政策名称',
  `policy_type` varchar(32) NOT NULL COMMENT 'tax/social/housing/calendar',
  `region_code` varchar(32) DEFAULT NULL COMMENT '统筹地区/地区编码',
  `collection_entity_code` varchar(100) DEFAULT NULL COMMENT '征缴主体编码',
  `person_category` varchar(64) DEFAULT NULL COMMENT '人员类别',
  `industry_risk_level` varchar(32) DEFAULT NULL COMMENT '工伤行业风险档次',
  `effective_from` date NOT NULL COMMENT '生效日期',
  `effective_to` date DEFAULT NULL COMMENT '失效日期',
  `source_document` varchar(200) DEFAULT NULL COMMENT '依据文件',
  `source_url` varchar(500) DEFAULT NULL COMMENT '官方来源地址',
  `payload_json` json DEFAULT NULL COMMENT '政策参数JSON',
  `status` varchar(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/review/published/retired',
  `version_no` bigint NOT NULL DEFAULT '1' COMMENT '政策版本',
  `checksum` varchar(64) DEFAULT NULL COMMENT '参数摘要',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payroll_policy_code_version` (`code`,`version_no`,`deleted`),
  KEY `idx_payroll_policy_resolve` (`policy_type`,`region_code`,`status`,`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬法规政策版本';

CREATE TABLE IF NOT EXISTS `payroll_tax_bracket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `policy_id` bigint NOT NULL,
  `tax_year` int NOT NULL,
  `bracket_level` int NOT NULL,
  `upper_limit` decimal(18,2) DEFAULT NULL COMMENT '累计应纳税所得额上限，NULL为无上限',
  `rate` decimal(12,8) NOT NULL,
  `quick_deduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payroll_tax_bracket` (`policy_id`,`tax_year`,`bracket_level`,`deleted`),
  KEY `idx_payroll_tax_bracket_policy` (`policy_id`,`tax_year`,`bracket_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='居民工资薪金累计预扣税率表';

CREATE TABLE IF NOT EXISTS `payroll_tax_deduction_declaration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `tax_year` int NOT NULL,
  `deduction_type` varchar(40) NOT NULL COMMENT '七项专项附加扣除及其他依法扣除',
  `subject_key` varchar(128) DEFAULT NULL COMMENT '子女/老人/房屋/证书等业务主体',
  `allocation_ratio` decimal(8,6) NOT NULL DEFAULT '1.000000',
  `monthly_amount` decimal(18,2) DEFAULT NULL,
  `annual_amount` decimal(18,2) DEFAULT NULL,
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL,
  `credential_ref` varchar(255) DEFAULT NULL COMMENT '凭证元数据引用，不存明文证件',
  `evidence_json` json DEFAULT NULL COMMENT '声明和凭证审计元数据',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/expired',
  `source_type` varchar(32) DEFAULT NULL COMMENT 'employee_declaration/import/tax_platform',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tax_deduction_employee_year` (`employee_id`,`tax_year`,`deduction_type`,`status`),
  KEY `idx_tax_deduction_effective` (`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个税专项附加扣除和其他扣除申报';

CREATE TABLE IF NOT EXISTS `payroll_tax_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `withholding_entity_id` bigint DEFAULT NULL COMMENT '扣缴义务人',
  `tax_year` int NOT NULL,
  `tax_month` int NOT NULL,
  `payroll_batch_id` bigint DEFAULT NULL,
  `payroll_line_id` bigint DEFAULT NULL,
  `cumulative_income` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_tax_exempt_income` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_basic_deduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_special_deduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_special_additional` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_other_deduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_taxable_income` decimal(18,2) NOT NULL DEFAULT '0.00',
  `tax_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000',
  `quick_deduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_tax` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_tax_reduction` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_withheld_tax` decimal(18,2) NOT NULL DEFAULT '0.00',
  `current_withholding_tax` decimal(18,2) NOT NULL DEFAULT '0.00',
  `policy_id` bigint DEFAULT NULL,
  `calculation_hash` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/posted/reversed',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_ledger_employee_period_batch` (`employee_id`,`tax_year`,`tax_month`,`payroll_batch_id`,`deleted`),
  KEY `idx_tax_ledger_previous` (`employee_id`,`withholding_entity_id`,`tax_year`,`tax_month`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工年度累计个税台账';

CREATE TABLE IF NOT EXISTS `payroll_contribution_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL,
  `region_code` varchar(32) NOT NULL COMMENT '统筹地区/公积金中心',
  `collection_entity_code` varchar(100) DEFAULT NULL,
  `contribution_type` varchar(40) NOT NULL COMMENT 'pension/medical/unemployment/work_injury/maternity/housing_fund',
  `person_category` varchar(64) DEFAULT NULL,
  `household_type` varchar(32) DEFAULT NULL,
  `industry_risk_level` varchar(32) DEFAULT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `base_min` decimal(18,2) DEFAULT NULL,
  `base_max` decimal(18,2) DEFAULT NULL,
  `employer_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000',
  `employee_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000',
  `employer_fixed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `employee_fixed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `rounding_mode` varchar(20) DEFAULT 'HALF_UP',
  `minimum_amount` decimal(18,2) DEFAULT NULL,
  `source_document` varchar(200) DEFAULT NULL,
  `source_url` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'draft',
  `version_no` bigint NOT NULL DEFAULT '1',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contribution_policy_version` (`code`,`version_no`,`deleted`),
  KEY `idx_contribution_policy_resolve` (`region_code`,`contribution_type`,`effective_from`,`effective_to`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地区五险一金政策参数';

CREATE TABLE IF NOT EXISTS `payroll_enrollment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `contribution_type` varchar(40) NOT NULL,
  `region_code` varchar(32) NOT NULL,
  `collection_entity_code` varchar(100) DEFAULT NULL,
  `account_no_encrypted` text COMMENT '社保/公积金账户密文',
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1',
  `event_type` varchar(32) DEFAULT NULL COMMENT 'enroll/transfer_in/transfer_out/freeze/resume/clear',
  `policy_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_enrollment_employee_type` (`employee_id`,`contribution_type`,`status`),
  KEY `idx_enrollment_period` (`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工多地参保/公积金关系';

CREATE TABLE IF NOT EXISTS `payroll_contribution_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payroll_batch_id` bigint DEFAULT NULL,
  `payroll_line_id` bigint DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `contribution_type` varchar(40) NOT NULL,
  `region_code` varchar(32) NOT NULL,
  `policy_id` bigint DEFAULT NULL,
  `declared_wage` decimal(18,2) DEFAULT NULL,
  `contribution_base` decimal(18,2) DEFAULT NULL,
  `employer_rate` decimal(12,8) DEFAULT NULL,
  `employee_rate` decimal(12,8) DEFAULT NULL,
  `employer_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `employee_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `adjustment_of_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'calculated',
  `calculation_hash` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_contribution_record_batch` (`payroll_batch_id`,`payroll_line_id`),
  KEY `idx_contribution_record_employee_period` (`employee_id`,`contribution_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五险一金缴费计算结果';

CREATE TABLE IF NOT EXISTS `payroll_calculation_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payroll_batch_id` bigint DEFAULT NULL,
  `payroll_line_id` bigint DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `sequence` int NOT NULL,
  `step_code` varchar(64) NOT NULL,
  `item_code` varchar(64) DEFAULT NULL,
  `input_json` json DEFAULT NULL,
  `output_value` decimal(18,6) DEFAULT NULL,
  `formula` text,
  `rule_version` varchar(128) DEFAULT NULL,
  `source_ref` varchar(255) DEFAULT NULL,
  `rounding_mode` varchar(20) DEFAULT NULL,
  `checksum` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calculation_trace_line_sequence` (`payroll_line_id`,`sequence`,`deleted`),
  KEY `idx_calculation_trace_batch_line` (`payroll_batch_id`,`payroll_line_id`,`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬计算逐步证据链';

SET @db := DATABASE();

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_contribution_policy' AND COLUMN_NAME='reviewed_by');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_contribution_policy` ADD COLUMN `reviewed_by` bigint DEFAULT NULL AFTER `version_no`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_contribution_policy' AND COLUMN_NAME='reviewed_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_contribution_policy` ADD COLUMN `reviewed_at` datetime DEFAULT NULL AFTER `reviewed_by`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_contribution_policy' AND COLUMN_NAME='published_by');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_contribution_policy` ADD COLUMN `published_by` bigint DEFAULT NULL AFTER `reviewed_at`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_contribution_policy' AND COLUMN_NAME='published_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_contribution_policy` ADD COLUMN `published_at` datetime DEFAULT NULL AFTER `published_by`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='pay_date');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `pay_date` date DEFAULT NULL COMMENT ''实际发放日期'' AFTER `currency`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='tax_year');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `tax_year` int DEFAULT NULL COMMENT ''税务年度'' AFTER `pay_date`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='tax_month');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `tax_month` int DEFAULT NULL COMMENT ''税款所属月'' AFTER `tax_year`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='tax_withholding_entity_id');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `tax_withholding_entity_id` bigint DEFAULT NULL COMMENT ''扣缴义务人'' AFTER `tax_month`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='tax_basic_deduction_months');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `tax_basic_deduction_months` int DEFAULT NULL COMMENT ''本单位任职受雇月份数'' AFTER `tax_withholding_entity_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='policy_package_id');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `policy_package_id` bigint DEFAULT NULL COMMENT ''政策包版本'' AFTER `tax_basic_deduction_months`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='result_hash');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `result_hash` varchar(64) DEFAULT NULL COMMENT ''结算结果摘要'' AFTER `remark`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='input_frozen_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `input_frozen_at` datetime DEFAULT NULL COMMENT ''输入冻结时间'' AFTER `result_hash`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='locked_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `locked_at` datetime DEFAULT NULL COMMENT ''结果锁定时间'' AFTER `input_frozen_at`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='closed_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `closed_at` datetime DEFAULT NULL COMMENT ''关账时间'' AFTER `locked_at`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='immutable_flag');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `immutable_flag` tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''结果是否不可变'' AFTER `closed_at`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='adjustment_of_batch_id');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_batch` ADD COLUMN `adjustment_of_batch_id` bigint DEFAULT NULL COMMENT ''调整所基于的原批次'' AFTER `immutable_flag`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='tax_breakdown_json');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_line` ADD COLUMN `tax_breakdown_json` json DEFAULT NULL COMMENT ''个税累计计算解释快照'' AFTER `net_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='tax_category');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `tax_category` varchar(40) DEFAULT NULL COMMENT ''税务分类'' AFTER `taxable`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='tax_exempt');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `tax_exempt` tinyint(1) DEFAULT 0 COMMENT ''是否免税'' AFTER `tax_category`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='pension_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `pension_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入养老基数'' AFTER `tax_exempt`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='medical_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `medical_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入医疗基数'' AFTER `pension_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='unemployment_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `unemployment_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入失业基数'' AFTER `medical_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='work_injury_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `work_injury_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入工伤基数'' AFTER `unemployment_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='maternity_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `maternity_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入生育基数'' AFTER `work_injury_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='housing_fund_base');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `housing_fund_base` tinyint(1) DEFAULT 0 COMMENT ''是否计入公积金基数'' AFTER `maternity_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='formula_json');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `formula_json` json DEFAULT NULL COMMENT ''受限公式AST/DSL'' AFTER `housing_fund_base`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='precision_scale');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `precision_scale` int DEFAULT 2 COMMENT ''计算精度'' AFTER `formula_json`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='rounding_mode');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `rounding_mode` varchar(20) DEFAULT ''HALF_UP'' COMMENT ''舍入方式'' AFTER `precision_scale`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='effective_from');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `effective_from` date DEFAULT NULL COMMENT ''生效日期'' AFTER `rounding_mode`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='salary_item' AND COLUMN_NAME='effective_to');
SET @sql := IF(@exists=0, 'ALTER TABLE `salary_item` ADD COLUMN `effective_to` date DEFAULT NULL COMMENT ''失效日期'' AFTER `effective_from`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_import_item' AND COLUMN_NAME='source_external_key');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_import_item` ADD COLUMN `source_external_key` varchar(191) DEFAULT NULL COMMENT ''来源系统业务幂等键'' AFTER `error_msg`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_import_item' AND COLUMN_NAME='business_date');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_import_item` ADD COLUMN `business_date` date DEFAULT NULL COMMENT ''业务日期'' AFTER `source_external_key`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_import_item' AND COLUMN_NAME='source_version');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_import_item` ADD COLUMN `source_version` varchar(64) DEFAULT NULL COMMENT ''来源版本'' AFTER `business_date`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_import_item' AND COLUMN_NAME='approval_status');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_import_item` ADD COLUMN `approval_status` varchar(20) DEFAULT ''approved'' COMMENT ''事实审批状态'' AFTER `source_version`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_import_item' AND COLUMN_NAME='imported_at');
SET @sql := IF(@exists=0, 'ALTER TABLE `payroll_import_item` ADD COLUMN `imported_at` datetime DEFAULT NULL COMMENT ''导入时间'' AFTER `approval_status`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `payroll_policy_package`
  (`code`,`name`,`policy_type`,`region_code`,`effective_from`,`source_document`,`source_url`,`payload_json`,`status`,`version_no`,`checksum`)
SELECT 'CN.RESIDENT_WAGE_WITHHOLDING', '居民个人工资薪金累计预扣预缴', 'tax', 'CN', '2019-01-01',
       '国家税务总局公告2018年第61号',
       'https://fgk.chinatax.gov.cn/zcfgk/c100015/c5200946/content.html',
       JSON_OBJECT(
         'basicDeductionPerMonth', 5000,
         'deductions', JSON_OBJECT(
           'infant_care', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01'),
           'child_education', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01'),
           'continuing_education', JSON_OBJECT('monthlyAmount', 400, 'vocationalAnnualAmount', 3600),
           'major_medical', JSON_OBJECT('annualThreshold', 15000, 'annualLimit', 80000, 'settlementOnly', true),
           'housing_loan_interest', JSON_OBJECT('monthlyAmount', 1000, 'maxMonths', 240),
           'rent', JSON_OBJECT('monthlyAmounts', JSON_ARRAY(800, 1100, 1500)),
           'elderly_care', JSON_OBJECT('singleChildMonthlyAmount', 3000, 'nonSingleTotalMonthlyAmount', 3000, 'perPersonLimit', 1500),
           'individual_pension', JSON_OBJECT('annualLimit', 12000, 'effectiveFrom', '2024-01-01')
         ),
         'annualBonusPolicyUntil', '2027-12-31'
       ),
       'published', 1, SHA2('CN.RESIDENT_WAGE_WITHHOLDING@1', 256)
WHERE NOT EXISTS (
  SELECT 1 FROM `payroll_policy_package` WHERE `code`='CN.RESIDENT_WAGE_WITHHOLDING' AND `version_no`=1 AND `deleted`=0
);

INSERT INTO `payroll_tax_bracket` (`policy_id`,`tax_year`,`bracket_level`,`upper_limit`,`rate`,`quick_deduction`)
SELECT p.id, y.tax_year, b.bracket_level, b.upper_limit, b.rate, b.quick_deduction
FROM `payroll_policy_package` p
CROSS JOIN (SELECT 2019 AS tax_year UNION ALL SELECT 2020 UNION ALL SELECT 2021 UNION ALL SELECT 2022 UNION ALL SELECT 2023 UNION ALL SELECT 2024 UNION ALL SELECT 2025 UNION ALL SELECT 2026) y
CROSS JOIN (
  SELECT 1 AS bracket_level, 36000.00 AS upper_limit, 0.03 AS rate, 0.00 AS quick_deduction
  UNION ALL SELECT 2, 144000.00, 0.10, 2520.00
  UNION ALL SELECT 3, 300000.00, 0.20, 16920.00
  UNION ALL SELECT 4, 420000.00, 0.25, 31920.00
  UNION ALL SELECT 5, 660000.00, 0.30, 52920.00
  UNION ALL SELECT 6, 960000.00, 0.35, 85920.00
  UNION ALL SELECT 7, NULL, 0.45, 181920.00
) b
WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0
  AND NOT EXISTS (
    SELECT 1 FROM `payroll_tax_bracket` t
    WHERE t.policy_id=p.id AND t.tax_year=y.tax_year AND t.bracket_level=b.bracket_level AND t.deleted=0
  );

-- 旧版固定税率模板不能继续参与核算或发布；不删除，保留为迁移证据。
UPDATE `salary_template`
SET `status`='disabled'
WHERE `deleted`=0
  AND `status`='enabled'
  AND (
    `tax_rule_json` IS NULL
    OR JSON_UNQUOTE(JSON_EXTRACT(`tax_rule_json`, '$.tax.mode')) <> 'cumulative_withholding'
  );

-- 合规工作台的菜单和接口权限，避免新页面落入旧的薪酬资源模型。
INSERT INTO `sys_resource`
  (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
SELECT 'VIEW', 'view.payroll.compliance', '合规核算', '/payroll/compliance', 'payroll/Compliance', 'SafetyCertificate',
       (SELECT id FROM `sys_resource` WHERE `code`='menu.system.payroll' LIMIT 1), 3,
       '{"roles":["ADMIN","FINANCE"]}', 'enabled', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_resource` WHERE `code`='view.payroll.compliance');

INSERT INTO `sys_resource`
  (`type`,`code`,`name`,`path`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
SELECT 'API', 'api.payroll.compliance.tax', '薪酬合规计算', '/api/payroll/compliance/*',
       (SELECT id FROM `sys_resource` WHERE `code`='menu.system.payroll' LIMIT 1), 320,
       '{"roles":["ADMIN","FINANCE"]}', 'enabled', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_resource` WHERE `code`='api.payroll.compliance.tax');

INSERT INTO `sys_role_resource` (`role_id`,`resource_id`,`actions_json`,`create_time`,`create_by`)
SELECT role.id, resource.id, '["*"]', NOW(), 'payroll_compliance_migration'
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.code IN ('view.payroll.compliance', 'api.payroll.compliance.tax')
WHERE role.code IN ('ADMIN', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_resource` existing
    WHERE existing.role_id=role.id AND existing.resource_id=resource.id AND existing.deleted=0
  );
