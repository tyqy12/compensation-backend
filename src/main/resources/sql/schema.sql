-- ============================================
-- 薪酬助手系统 - 数据库结构设计
-- Phase 1: 核心表结构创建
-- ============================================
-- ⚠️ 警告：本文件包含 DROP TABLE IF EXISTS + 全量重建语句，
-- 仅允许用于“全新空库初始化”或“一次性本地重置”。
-- 禁止在任何已有业务数据环境执行（含共享开发库、测试库、生产库）。
-- 增量变更请使用：src/main/resources/sql/migrations/*.sql

-- 设置数据库字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 员工信息表 (employee)
-- ============================================
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` varchar(50) NOT NULL COMMENT '员工工号',
  `name` varchar(100) NOT NULL COMMENT '员工姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱地址',
  `encrypted_id_card` text COMMENT '加密后的身份证号(SM4+AES双重加密)',
  `department` varchar(100) DEFAULT NULL COMMENT '部门',
  `position` varchar(100) DEFAULT NULL COMMENT '职位',
  `employment_type` varchar(20) DEFAULT 'full_time' COMMENT '用工类型(full_time/part_time)',
  `is_offline` tinyint(1) DEFAULT '0' COMMENT '是否架构外员工(0:否,1:是)',
  `manager_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) DEFAULT 'active' COMMENT '员工状态(active:在职,inactive:离职,suspended:停职)',
  `settlement_account_type` varchar(20) DEFAULT NULL COMMENT '收款账户类型(bank_card/alipay/wechat/other)',
  `settlement_account` varchar(128) DEFAULT NULL COMMENT '收款账户(加密存储)',
  `settlement_account_name` varchar(100) DEFAULT NULL COMMENT '收款账户实名/户名',
  `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码（优先级最高）',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行卡号(加密存储)',
  `bank_name` varchar(100) DEFAULT NULL COMMENT '开户银行',
  `bank_branch_name` varchar(120) DEFAULT NULL COMMENT '开户支行',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_id` (`employee_id`),
  KEY `idx_manager` (`manager_id`),
  KEY `idx_employee_status` (`status`),
  KEY `idx_offline` (`is_offline`),
  KEY `idx_employee_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工信息表';

-- ============================================
-- 1.1 员工-部门关联表 (employee_department)
-- ============================================
DROP TABLE IF EXISTS `employee_department`;
CREATE TABLE `employee_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型',
  `platform_dept_id` varchar(100) DEFAULT NULL COMMENT '平台部门ID',
  `local_dept_id` bigint DEFAULT NULL COMMENT '本地部门ID',
  `dept_name` varchar(200) NOT NULL COMMENT '部门名称',
  `is_primary` tinyint(1) DEFAULT '0' COMMENT '是否主部门',
  `order_num` int DEFAULT '0' COMMENT '顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp` (`employee_id`),
  KEY `idx_platform_dept` (`platform_type`,`platform_dept_id`),
  CONSTRAINT `fk_emp_dept_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工-部门多对多关联';

-- ============================================
-- 1.2 薪酬核心表（M1）
-- ============================================
DROP TABLE IF EXISTS `salary_item`;
CREATE TABLE `salary_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) NOT NULL COMMENT '项编码',
  `name` varchar(100) NOT NULL COMMENT '项名称',
  `type` varchar(20) NOT NULL COMMENT 'earning/deduction',
  `taxable` tinyint(1) DEFAULT '1' COMMENT '是否计税',
  `show_on_payslip` tinyint(1) DEFAULT '1' COMMENT '工资条显示',
  `order_num` int DEFAULT '0' COMMENT '排序',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_code` (`code`),
  KEY `idx_status_order` (`status`,`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资项字典';

DROP TABLE IF EXISTS `salary_template`;
CREATE TABLE `salary_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `type` varchar(20) NOT NULL COMMENT 'full_time/part_time',
  `items_json` json DEFAULT NULL COMMENT '项配置JSON',
  `tax_rule_json` json DEFAULT NULL COMMENT '税社保口径JSON',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资模板';

DROP TABLE IF EXISTS `pay_cycle`;
CREATE TABLE `pay_cycle` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type` varchar(20) NOT NULL COMMENT 'monthly/custom',
  `period_label` varchar(20) NOT NULL COMMENT '周期标签(YYYY-MM)',
  `cycle_code` varchar(64) DEFAULT NULL COMMENT '周期编码',
  `cycle_name` varchar(100) DEFAULT NULL COMMENT '周期名称',
  `cycle_type` varchar(20) DEFAULT NULL COMMENT '周期类型(monthly/semi_monthly/weekly/biweekly/custom)',
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `cutoff_date` date DEFAULT NULL,
  `pay_day` tinyint DEFAULT NULL COMMENT '发薪日(1-31)',
  `lead_days` int DEFAULT NULL COMMENT '提前天数',
  `grace_days` int DEFAULT NULL COMMENT '宽限天数',
  `timezone` varchar(50) DEFAULT NULL COMMENT '时区',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `next_execution_time` datetime DEFAULT NULL COMMENT '下次执行时间',
  `last_execution_time` datetime DEFAULT NULL COMMENT '最近执行时间',
  `status` varchar(20) DEFAULT 'draft',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cycle` (`type`,`period_label`),
  UNIQUE KEY `uk_cycle_code` (`cycle_code`),
  KEY `idx_cycle_status_type` (`status`,`cycle_type`),
  KEY `idx_cycle_next_execution` (`next_execution_time`),
  KEY `idx_status_start` (`status`,`start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪周期';

DROP TABLE IF EXISTS `payroll_batch`;
CREATE TABLE `payroll_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pay_cycle_id` bigint DEFAULT NULL COMMENT '周期ID',
  `period_label` varchar(20) DEFAULT NULL COMMENT '周期标签',
  `type` varchar(20) NOT NULL COMMENT 'full_time/part_time',
  `scope_json` json DEFAULT NULL COMMENT '范围JSON',
  `currency` varchar(10) DEFAULT 'CNY',
  `calculation_status` varchar(32) DEFAULT 'draft' COMMENT '核算状态',
  `batch_revision` int DEFAULT '1' COMMENT '业务批次版本号',
  `status` varchar(20) DEFAULT 'draft',
  `approval_workflow_id` bigint DEFAULT NULL,
  `payment_batch_no` varchar(50) DEFAULT NULL,
  `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码',
  `confirmation_required` tinyint(1) DEFAULT '1' COMMENT '是否需要员工确认',
  `confirmation_mode` varchar(20) DEFAULT 'individual' COMMENT '确认模式(individual/group)',
  `confirmation_completed_time` datetime DEFAULT NULL COMMENT '确认完成时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_batch_type_period_status` (`type`,`period_label`,`status`),
  KEY `idx_cycle` (`pay_cycle_id`),
  KEY `idx_calculation_status` (`calculation_status`),
  KEY `idx_batch_revision` (`batch_revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪批次';

DROP TABLE IF EXISTS `payroll_line`;
CREATE TABLE `payroll_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employment_type` varchar(20) NOT NULL COMMENT 'full_time/part_time',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `items_snapshot_json` json DEFAULT NULL COMMENT '项快照JSON',
  `gross_amount` decimal(12,2) DEFAULT '0.00',
  `tax_amount` decimal(12,2) DEFAULT '0.00',
  `social_amount` decimal(12,2) DEFAULT '0.00',
  `net_amount` decimal(12,2) DEFAULT '0.00',
  `currency` varchar(10) DEFAULT 'CNY',
  `status` varchar(20) DEFAULT 'draft',
  `note` varchar(500) DEFAULT NULL,
  `warning` varchar(500) DEFAULT NULL COMMENT '预警信息',
  `confirmation_assignee_employee_id` bigint DEFAULT NULL COMMENT '确认负责人员工ID',
  `confirmation_status` varchar(30) DEFAULT 'pending' COMMENT '确认状态',
  `confirmed_by_user_id` bigint DEFAULT NULL COMMENT '确认人用户ID',
  `confirmed_by_employee_id` bigint DEFAULT NULL COMMENT '确认人员工ID',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `confirmation_comment` varchar(500) DEFAULT NULL COMMENT '确认备注/签字',
  `objection_reason` varchar(500) DEFAULT NULL COMMENT '异议原因',
  `objection_at` datetime DEFAULT NULL COMMENT '异议时间',
  `dispute_workflow_id` bigint DEFAULT NULL COMMENT '异议审批流程ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_batch_employee` (`batch_id`,`employee_id`),
  KEY `idx_confirmation_assignee_status` (`confirmation_assignee_employee_id`,`confirmation_status`),
  KEY `idx_dispute_workflow` (`dispute_workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资行';

DROP TABLE IF EXISTS `payroll_confirmation`;
CREATE TABLE `payroll_confirmation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `confirmation_no` varchar(64) NOT NULL COMMENT '确认单号',
  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
  `batch_revision` int NOT NULL COMMENT '批次版本号',
  `require_confirmation` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否需要确认',
  `deadline` datetime DEFAULT NULL COMMENT '确认截止时间',
  `timeout_strategy` varchar(32) DEFAULT NULL COMMENT '超时策略',
  `confirmation_status` varchar(32) NOT NULL COMMENT '确认单状态',
  `total_employees` int NOT NULL DEFAULT '0' COMMENT '总人数',
  `confirmed_count` int NOT NULL DEFAULT '0' COMMENT '已确认人数',
  `rejected_count` int NOT NULL DEFAULT '0' COMMENT '拒绝人数',
  `policy_id` varchar(64) DEFAULT NULL COMMENT '策略ID/模式',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_no` (`confirmation_no`),
  UNIQUE KEY `uk_confirmation_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
  KEY `idx_confirmation_status` (`confirmation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认单';

DROP TABLE IF EXISTS `payroll_confirmation_record`;
CREATE TABLE `payroll_confirmation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `confirmation_id` bigint NOT NULL COMMENT '确认单ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `line_id` bigint NOT NULL COMMENT '薪资行ID',
  `record_status` varchar(32) NOT NULL COMMENT '确认记录状态',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  `comment` varchar(500) DEFAULT NULL COMMENT '备注',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_employee_deleted` (`confirmation_id`,`employee_id`,`deleted`),
  KEY `idx_confirmation_line` (`confirmation_id`,`line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认记录';

DROP TABLE IF EXISTS `payroll_distribution`;
CREATE TABLE `payroll_distribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distribution_no` varchar(64) NOT NULL COMMENT '发放单号',
  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
  `batch_revision` int NOT NULL COMMENT '批次版本号',
  `total_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '应发总额快照',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '应发人数快照',
  `scheduled_date` date NOT NULL COMMENT '计划发放日期',
  `retry_limit` int NOT NULL DEFAULT '3' COMMENT '最大重试次数',
  `allow_partial` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许部分成功',
  `distribution_status` varchar(32) NOT NULL COMMENT '发放状态',
  `actual_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '实发金额',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功人数',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败人数',
  `current_attempt` int NOT NULL DEFAULT '0' COMMENT '当前尝试号',
  `approval_workflow_id` bigint DEFAULT NULL COMMENT '审批流ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_no` (`distribution_no`),
  UNIQUE KEY `uk_distribution_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
  KEY `idx_distribution_status` (`distribution_status`),
  KEY `idx_distribution_schedule` (`scheduled_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放单';

DROP TABLE IF EXISTS `payroll_distribution_item`;
CREATE TABLE `payroll_distribution_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distribution_id` bigint NOT NULL COMMENT '发放单ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `line_id` bigint NOT NULL COMMENT '薪资行ID',
  `employee_name` varchar(128) DEFAULT NULL COMMENT '员工姓名快照',
  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收款人姓名快照',
  `account_no_encrypted` text COMMENT '收款账户密文',
  `account_no_masked` varchar(128) DEFAULT NULL COMMENT '收款账户脱敏',
  `account_type` varchar(32) DEFAULT NULL COMMENT '账户类型',
  `payment_method` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码',
  `amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '应发金额快照',
  `item_status` varchar(32) NOT NULL COMMENT '明细状态',
  `payment_record_id` bigint DEFAULT NULL COMMENT '最新支付记录ID',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_employee_deleted` (`distribution_id`,`employee_id`,`deleted`),
  KEY `idx_distribution_item_status` (`distribution_id`,`item_status`),
  KEY `idx_distribution_payment_record` (`payment_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放明细';

DROP TABLE IF EXISTS `payroll_approval_projection`;
CREATE TABLE `payroll_approval_projection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
  `batch_revision` int NOT NULL COMMENT '批次版本号',
  `distribution_id` bigint DEFAULT NULL COMMENT '发放单ID',
  `workflow_id` bigint NOT NULL COMMENT '审批流ID',
  `business_status` varchar(32) NOT NULL COMMENT '业务状态',
  `submitter_id` bigint DEFAULT NULL COMMENT '提交人ID',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
  `current_approver_id` bigint DEFAULT NULL COMMENT '当前审批人',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `result` varchar(64) DEFAULT NULL COMMENT '审批结果',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_deleted` (`workflow_id`,`deleted`),
  KEY `idx_projection_distribution` (`distribution_id`),
  KEY `idx_projection_status` (`business_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资审批投影';

DROP TABLE IF EXISTS `payroll_reconciliation_task`;
CREATE TABLE `payroll_reconciliation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distribution_id` bigint NOT NULL COMMENT '发放单ID',
  `task_status` varchar(32) NOT NULL COMMENT '任务状态',
  `expected_amount` decimal(15,2) DEFAULT '0.00' COMMENT '应发金额',
  `actual_amount` decimal(15,2) DEFAULT '0.00' COMMENT '实发金额',
  `difference` decimal(15,2) DEFAULT '0.00' COMMENT '差异金额',
  `result` varchar(32) DEFAULT NULL COMMENT '结果',
  `difference_detail` text COMMENT '差异明细JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconciliation_distribution_deleted` (`distribution_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资对账任务';

DROP TABLE IF EXISTS `payroll_adjustment`;
CREATE TABLE `payroll_adjustment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `line_id` bigint NOT NULL COMMENT '工资行ID',
  `item_code` varchar(50) NOT NULL COMMENT '项编码',
  `amount` decimal(12,2) NOT NULL COMMENT '调整金额',
  `reason` varchar(200) DEFAULT NULL COMMENT '原因',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_line` (`line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资调整';

DROP TABLE IF EXISTS `timesheet_entry`;
CREATE TABLE `timesheet_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `work_date` date NOT NULL COMMENT '工作日期',
  `hours` decimal(6,2) DEFAULT NULL COMMENT '工时(小时)',
  `units` decimal(10,2) DEFAULT NULL COMMENT '产出数量',
  `project` varchar(100) DEFAULT NULL COMMENT '项目',
  `department` varchar(100) DEFAULT NULL COMMENT '部门展示',
  `source` varchar(20) DEFAULT 'api' COMMENT 'manual/import/api',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_emp_date` (`employee_id`,`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工时/产出条目';

-- 导入暂存表
DROP TABLE IF EXISTS `payroll_import_item`;
CREATE TABLE `payroll_import_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `item_code` varchar(50) NOT NULL COMMENT '项编码',
  `amount` decimal(12,2) NOT NULL COMMENT '金额',
  `note` varchar(200) DEFAULT NULL COMMENT '备注',
  `source_name` varchar(200) DEFAULT NULL COMMENT '来源文件',
  `row_no` int DEFAULT NULL COMMENT '行号',
  `status` varchar(20) DEFAULT 'valid' COMMENT 'valid/invalid',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_batch_emp` (`batch_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬导入暂存';

-- ============================================
-- 2. 支付记录表 (payment_record)
-- ============================================
DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(50) NOT NULL COMMENT '批次号',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `payment_type` varchar(20) NOT NULL COMMENT '支付类型(salary:工资,bonus:奖金,reimbursement:报销)',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `payment_method` varchar(20) DEFAULT 'alipay' COMMENT '支付方式(alipay:支付宝)',
  `recipient_account` varchar(100) NOT NULL COMMENT '收款账户',
  `recipient_name` varchar(100) NOT NULL COMMENT '收款人姓名',
  `payment_desc` varchar(500) DEFAULT NULL COMMENT '支付描述',
  `status` varchar(20) DEFAULT 'pending' COMMENT '支付状态(pending:待处理,processing:处理中,success:成功,failed:失败,cancelled:已取消)',
  `alipay_order_no` varchar(100) DEFAULT NULL COMMENT '支付宝订单号',
  `alipay_trade_no` varchar(100) DEFAULT NULL COMMENT '支付宝交易号',
  `provider_code` varchar(32) NOT NULL DEFAULT 'alipay' COMMENT '渠道编码: alipay/yunzhanghu/wechat/bank',
  `provider_order_no` varchar(64) DEFAULT NULL COMMENT '渠道侧商户订单号(我方生成)',
  `provider_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道侧平台流水号(渠道返回)',
  `provider_metadata` json DEFAULT NULL COMMENT '渠道扩展信息',
  `id_card_hash` varchar(64) DEFAULT NULL COMMENT '收款人身份证哈希',
  `error_code` varchar(50) DEFAULT NULL COMMENT '错误码',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `payment_time` datetime DEFAULT NULL COMMENT '实际支付时间',
  `notification_time` datetime DEFAULT NULL COMMENT '支付宝通知时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alipay_order` (`alipay_order_no`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_payment_record_employee` (`employee_id`),
  KEY `idx_payment_record_status` (`status`),
  KEY `idx_payment_record_payment_type` (`payment_type`),
  KEY `idx_payment_record_create_time` (`create_time`),
  KEY `idx_payment_time` (`payment_time`),
  KEY `idx_provider_order` (`provider_code`, `provider_order_no`),
  KEY `idx_provider_trade` (`provider_code`, `provider_trade_no`),
  CONSTRAINT `fk_payment_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

-- ============================================
-- 3. 支付批次表 (payment_batch)
-- ============================================
DROP TABLE IF EXISTS `payment_batch`;
CREATE TABLE `payment_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(50) NOT NULL COMMENT '批次号',
  `batch_name` varchar(200) NOT NULL COMMENT '批次名称',
  `payment_type` varchar(20) NOT NULL COMMENT '支付类型',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '总金额',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总笔数',
  `success_count` int DEFAULT '0' COMMENT '成功笔数',
  `failed_count` int DEFAULT '0' COMMENT '失败笔数',
  `status` varchar(20) DEFAULT 'draft' COMMENT '批次状态(draft:草稿,submitted:已提交,approved:已审批,processing:处理中,completed:已完成,failed:失败)',
  `distribution_id` bigint DEFAULT NULL COMMENT '关联发放单ID',
  `payment_status` varchar(32) DEFAULT NULL COMMENT '支付处理状态',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `process_start_time` datetime DEFAULT NULL COMMENT '开始处理时间',
  `process_end_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `processor_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `remark` text COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_payment_batch_status` (`status`),
  KEY `idx_distribution` (`distribution_id`),
  KEY `idx_payment_status` (`payment_status`),
  KEY `idx_payment_batch_payment_type` (`payment_type`),
  KEY `idx_payment_batch_create_time` (`create_time`),
  KEY `idx_submit_time` (`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付批次表';

-- ============================================
-- 4. 结算渠道配置表 (settlement_provider_config)
-- ============================================
DROP TABLE IF EXISTS `settlement_provider_config`;
CREATE TABLE `settlement_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `provider_name` varchar(64) NOT NULL COMMENT '渠道名称',
  `provider_type` varchar(32) DEFAULT NULL COMMENT '渠道类型',
  `api_endpoint` varchar(255) DEFAULT NULL COMMENT 'API端点',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `api_secret` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户ID',
  `notify_url` varchar(255) DEFAULT NULL COMMENT '回调URL',
  `return_url` varchar(255) DEFAULT NULL COMMENT '返回URL',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级(数字越小优先级越高)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `config_json` text COMMENT '渠道配置JSON(建议加密)',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  `supports_batch` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否支持批量',
  `supports_query` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否支持主动查询',
  `supports_callback` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否支持回调',
  `single_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单笔最大金额',
  `daily_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单日最大金额',
  `callback_url` varchar(256) DEFAULT NULL COMMENT '回调地址',
  `callback_ips` varchar(512) DEFAULT NULL COMMENT '回调IP白名单',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算渠道配置表';

INSERT INTO `settlement_provider_config`
(`provider_code`, `provider_name`, `provider_type`, `enabled`, `priority`, `supports_batch`, `supports_query`, `supports_callback`)
VALUES
('alipay', '支付宝', 'alipay', 1, 100, 1, 1, 1),
('yunzhanghu', '云账户', 'yunzhanghu', 0, 100, 1, 1, 1);

-- ============================================
-- 4.1 员工类型渠道映射表 (employee_type_provider_mapping)
-- ============================================
DROP TABLE IF EXISTS `employee_type_provider_mapping`;
CREATE TABLE `employee_type_provider_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employment_type` varchar(32) NOT NULL COMMENT '员工类型：full_time/part_time/intern/contract',
  `provider_code` varchar(32) NOT NULL COMMENT '结算渠道编码',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（数字越大越高）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employment_provider` (`employment_type`, `provider_code`),
  KEY `idx_employment_type` (`employment_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工类型渠道映射表';

INSERT INTO `employee_type_provider_mapping`
(`employment_type`, `provider_code`, `priority`, `enabled`)
VALUES
('full_time', 'alipay', 10, 1),
('part_time', 'alipay', 10, 1),
('intern', 'alipay', 10, 1),
('contract', 'alipay', 10, 1);

-- ============================================
-- 5. 结算对账表 (settlement_reconciliation)
-- ============================================
DROP TABLE IF EXISTS `settlement_reconciliation`;
CREATE TABLE `settlement_reconciliation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `recon_date` date NOT NULL COMMENT '对账日期',
  `provider_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总笔数',
  `total_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '总金额',
  `match_count` int NOT NULL DEFAULT '0' COMMENT '匹配笔数',
  `match_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '匹配金额',
  `diff_count` int NOT NULL DEFAULT '0' COMMENT '差异笔数',
  `diff_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '差异金额',
  `unmatched_local` json DEFAULT NULL COMMENT '我方多账记录ID列表',
  `unmatched_provider` json DEFAULT NULL COMMENT '渠道多账记录列表',
  `status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/PROCESSING/COMPLETED',
  `processed_by` bigint DEFAULT NULL COMMENT '处理人ID',
  `processed_at` datetime DEFAULT NULL COMMENT '处理时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '处理备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_recon_date` (`recon_date`, `provider_code`),
  KEY `idx_recon_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道对账表';

-- ============================================
-- 6. 审批流程表 (approval_workflow)
-- ============================================
DROP TABLE IF EXISTS `approval_workflow`;
CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_name` varchar(100) NOT NULL COMMENT '流程名称',
  `workflow_type` varchar(20) NOT NULL COMMENT '流程类型(BATCH:批量支付,ADHOC:临时支付,OFFLINE:架构外员工)',
  `business_key` varchar(100) NOT NULL COMMENT '业务关键字(如batch_no)',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `current_step` int DEFAULT '1' COMMENT '当前步骤',
  `total_steps` int NOT NULL COMMENT '总步骤数',
  `status` varchar(20) DEFAULT 'pending' COMMENT '流程状态(pending:待审批,approved:已通过,rejected:已拒绝,cancelled:已取消)',
  `initiator_id` bigint NOT NULL COMMENT '发起人ID',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `current_approver_id` bigint DEFAULT NULL COMMENT '当前审批人ID',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `workflow_data` json DEFAULT NULL COMMENT '流程数据(JSON格式)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_key` (`business_key`, `business_type`),
  KEY `idx_workflow_type` (`workflow_type`),
  KEY `idx_approval_workflow_status` (`status`),
  KEY `idx_initiator` (`initiator_id`),
  KEY `idx_approval_workflow_employee` (`employee_id`),
  KEY `idx_current_approver` (`current_approver_id`),
  KEY `idx_approval_step_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程表';

-- ============================================
-- 7. 审批步骤表 (approval_step)
-- ============================================
DROP TABLE IF EXISTS `approval_step`;
CREATE TABLE `approval_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '流程ID',
  `step_no` int NOT NULL COMMENT '步骤序号',
  `step_name` varchar(100) NOT NULL COMMENT '步骤名称',
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) NOT NULL COMMENT '审批人姓名',
  `status` varchar(20) DEFAULT 'pending' COMMENT '步骤状态(pending:待处理,approved:已通过,rejected:已拒绝,skipped:已跳过)',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `reject_reason` text COMMENT '拒绝原因',
  `approve_comment` text COMMENT '审批意见',
  `timeout_hours` int DEFAULT '24' COMMENT '超时时间(小时)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_step` (`workflow_id`, `step_no`),
  KEY `idx_approver` (`approver_id`),
  KEY `idx_approval_step_status` (`status`),
  KEY `idx_approval_workflow_create_time` (`create_time`),
  CONSTRAINT `fk_step_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批步骤表';

-- ============================================
-- 6. 系统用户表 (sys_user)
-- ============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(200) NOT NULL COMMENT '密码(BCrypt加密)',
  `real_name` varchar(100) NOT NULL COMMENT '真实姓名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `status` varchar(20) DEFAULT 'active' COMMENT '用户状态(active:激活,inactive:禁用)',
  `roles` varchar(500) DEFAULT NULL COMMENT '角色列表(逗号分隔)',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_sys_user_employee` (`employee_id`),
  KEY `idx_sys_user_status` (`status`),
  KEY `idx_notification_record_create_time` (`create_time`),
  CONSTRAINT `fk_user_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ============================================
-- 6.1 外部平台身份绑定表 (external_identity)
-- ============================================
DROP TABLE IF EXISTS `external_identity`;
CREATE TABLE `external_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider` varchar(20) NOT NULL COMMENT '平台标识(wechat/dingtalk/feishu)',
  `tenant_key` varchar(100) NOT NULL DEFAULT 'default' COMMENT '租户标识(如corpId/appId/appKey)',
  `subject_type` varchar(30) NOT NULL DEFAULT 'platform_user_id' COMMENT '主体类型(user_id/open_id/union_id/platform_user_id)',
  `subject_id` varchar(191) NOT NULL COMMENT '平台主体ID',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `user_id` bigint DEFAULT NULL COMMENT '关联用户ID',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '同平台主账号标记',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  `source` varchar(20) DEFAULT NULL COMMENT '来源(sync/manual/oauth/migration/approval)',
  `bound_at` datetime DEFAULT NULL COMMENT '绑定时间',
  `unbound_at` datetime DEFAULT NULL COMMENT '解绑时间',
  `last_seen_at` datetime DEFAULT NULL COMMENT '最近使用时间',
  `ext_json` json DEFAULT NULL COMMENT '扩展字段',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_subject` (`provider`, `tenant_key`, `subject_type`, `subject_id`, `deleted`),
  KEY `idx_employee_provider_status` (`employee_id`, `provider`, `status`),
  KEY `idx_user_provider_status` (`user_id`, `provider`, `status`),
  KEY `idx_subject_lookup` (`provider`, `tenant_key`, `subject_type`, `status`),
  CONSTRAINT `fk_external_identity_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`),
  CONSTRAINT `fk_external_identity_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部平台身份绑定表';

-- ============================================
-- 7. 通知记录表 (notification_record)
-- ============================================
DROP TABLE IF EXISTS `notification_record`;
CREATE TABLE `notification_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notification_type` varchar(50) NOT NULL COMMENT '通知类型',
  `channel` varchar(50) NOT NULL COMMENT '通知渠道',
  `recipient_id` varchar(100) NOT NULL COMMENT '接收人ID',
  `recipient_name` varchar(100) DEFAULT NULL COMMENT '接收人姓名',
  `title` varchar(200) DEFAULT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `template_id` varchar(100) DEFAULT NULL COMMENT '模板ID',
  `template_params` text COMMENT '模板参数(JSON)',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务标识',
  `status` varchar(50) NOT NULL DEFAULT 'pending' COMMENT '通知状态',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `max_retry` int DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `send_time` datetime DEFAULT NULL COMMENT '实际发送时间',
  `response_code` varchar(50) DEFAULT NULL COMMENT '响应码',
  `response_message` varchar(255) DEFAULT NULL COMMENT '响应消息',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `fallback_channels` varchar(255) DEFAULT NULL COMMENT '失败回退渠道(JSON数组)',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_status_next_retry` (`status`, `next_retry_time`),
  KEY `idx_business_type_key` (`business_type`, `business_key`),
  KEY `idx_recipient_channel` (`recipient_id`, `channel`),
  KEY `idx_sys_user_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发送记录表';

-- ============================================
-- 7. 操作日志表 (audit_log)
-- ============================================
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `operation` varchar(100) NOT NULL COMMENT '操作类型',
  `method` varchar(10) NOT NULL COMMENT '请求方法(GET,POST,PUT,DELETE)',
  `request_url` varchar(500) NOT NULL COMMENT '请求URL',
  `request_ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(1000) DEFAULT NULL COMMENT '用户代理',
  `request_params` text COMMENT '请求参数',
  `response_result` text COMMENT '响应结果',
  `error_msg` text COMMENT '错误信息',
  `execution_time` bigint DEFAULT NULL COMMENT '执行时间(毫秒)',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务关键字',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_operation` (`operation`),
  KEY `idx_business` (`business_type`, `business_key`),
  KEY `idx_audit_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表(5年保存期)';

-- ============================================
-- 8. 系统配置表 (sys_config)
-- ============================================
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `remark` varchar(500) DEFAULT NULL COMMENT '配置备注',
  `config_type` varchar(20) DEFAULT 'string' COMMENT '配置类型(string,number,boolean,json)',
  `config_desc` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `is_encrypted` tinyint(1) DEFAULT '0' COMMENT '是否加密(0:否,1:是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_sys_config_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================
-- 9. 集成配置表 (integration_config)
-- 用于存储企微/钉钉/飞书/支付宝等第三方平台配置(JSON)
-- ============================================
DROP TABLE IF EXISTS `org_department`;
CREATE TABLE `org_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',
  `platform_dept_id` varchar(100) NOT NULL COMMENT '平台部门ID',
  `name` varchar(200) NOT NULL COMMENT '部门名称',
  `parent_platform_dept_id` varchar(100) DEFAULT NULL COMMENT '平台父部门ID',
  `parent_id` bigint DEFAULT NULL COMMENT '本地父部门ID',
  `order_num` int DEFAULT NULL COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_dept` (`platform_type`, `platform_dept_id`),
  KEY `idx_parent` (`parent_platform_dept_id`),
  KEY `idx_org_department_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';

DROP TABLE IF EXISTS `integration_config`;
CREATE TABLE `integration_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay/yunzhanghu)',
  `config_json` text COMMENT '配置JSON',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_type` (`platform_type`),
  KEY `idx_integration_config_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台集成配置表';

-- ============================================
-- 初始化基础数据
-- ============================================

-- 插入系统配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_desc`) VALUES
('system.name', '薪酬助手系统', 'string', '系统名称'),
('payment.daily.limit', '10000.00', 'number', '单人单日支付限额(元)'),
('payment.batch.max.size', '1000', 'number', '批量支付最大笔数'),
('approval.timeout.hours', '24', 'number', '审批超时时间(小时)'),
('payroll.dispute.approval.flow', '', 'json', '薪酬异议审批流程配置(JSON，空值使用默认链路)'),
('notification.retry.max', '3', 'number', '通知重试最大次数'),
('audit.log.retention.days', '1825', 'number', '审计日志保留天数(5年)');

-- 插入默认管理员用户
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `roles`, `create_by`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXIUYGSG2v8vwAkAbJXhRXz4CZG', '系统管理员', 'admin@yiyundao.com', 'active', 'ROLE_ADMIN', 'system');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 创建索引优化查询性能
-- ============================================

-- 员工表复合索引
CREATE INDEX `idx_employee_status_offline` ON `employee` (`status`, `is_offline`);
CREATE INDEX `idx_employee_dept_position` ON `employee` (`department`, `position`);

-- 支付记录表复合索引
CREATE INDEX `idx_payment_record_batch_status` ON `payment_record` (`batch_no`, `status`);
CREATE INDEX `idx_payment_time_range` ON `payment_record` (`create_time`, `payment_time`);

-- 审批流程复合索引
CREATE INDEX `idx_workflow_type_status` ON `approval_workflow` (`workflow_type`, `status`);
CREATE INDEX `idx_workflow_approver_time` ON `approval_workflow` (`current_approver_id`, `submit_time`);

-- 审计日志分区表(可选 - 用于大数据量场景)
-- ALTER TABLE audit_log PARTITION BY RANGE (YEAR(create_time)) (
--   PARTITION p2024 VALUES LESS THAN (2025),
--   PARTITION p2025 VALUES LESS THAN (2026),
--   PARTITION p2026 VALUES LESS THAN (2027),
--   PARTITION p_future VALUES LESS THAN MAXVALUE
-- );
