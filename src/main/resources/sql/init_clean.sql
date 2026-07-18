-- ============================================
-- Compensation Assistant System - Clean Initialization SQL
-- Drops existing objects and recreates all tables with only the ADMIN user seeded.
-- DEPRECATED for production: this destructive compatibility script predates the
-- policy-versioned payroll schema. Production and shared environments must use
-- schema.sql for an empty database or the idempotent migrations/runner for an
-- existing database. Do not execute this file against an existing data set.
-- Usage:
--   mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/init_clean.sql
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop tables if exist (order considers FK constraints)
DROP TABLE IF EXISTS `sys_user_resource`;
DROP TABLE IF EXISTS `sys_user_permission`;
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_resource_action`;
DROP TABLE IF EXISTS `sys_permission_action`;
DROP TABLE IF EXISTS `sys_role_resource`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `resource_snapshot`;
DROP TABLE IF EXISTS `approval_step`;
DROP TABLE IF EXISTS `approval_workflow`;
DROP TABLE IF EXISTS `notification_record`;
DROP TABLE IF EXISTS `settlement_reconciliation`;
DROP TABLE IF EXISTS `employee_type_provider_mapping`;
DROP TABLE IF EXISTS `settlement_provider_config`;
DROP TABLE IF EXISTS `payroll_reconciliation_task`;
DROP TABLE IF EXISTS `payroll_approval_projection`;
DROP TABLE IF EXISTS `payroll_distribution_item`;
DROP TABLE IF EXISTS `payroll_distribution`;
DROP TABLE IF EXISTS `payroll_confirmation_record`;
DROP TABLE IF EXISTS `payroll_confirmation`;
DROP TABLE IF EXISTS `payroll_line`;
DROP TABLE IF EXISTS `payroll_batch`;
DROP TABLE IF EXISTS `payment_record`;
DROP TABLE IF EXISTS `payment_batch`;
DROP TABLE IF EXISTS `employee_department`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `employee`;
DROP TABLE IF EXISTS `org_department`;
DROP TABLE IF EXISTS `integration_config`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_resource`;

-- Employee
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` varchar(50) NOT NULL COMMENT '员工工号',
  `name` varchar(100) NOT NULL COMMENT '员工姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱地址',
  `encrypted_id_card` text COMMENT '加密后的身份证号',
  `department` varchar(500) DEFAULT NULL COMMENT '部门(兼容展示字段，多部门关系见employee_department)',
  `position` varchar(100) DEFAULT NULL COMMENT '职位',
  `employment_type` varchar(20) DEFAULT 'full_time' COMMENT '用工类型(full_time/part_time)',
  `is_offline` tinyint(1) DEFAULT '0' COMMENT '是否架构外员工',
  `manager_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) DEFAULT 'active' COMMENT '员工状态',
  `settlement_account_type` varchar(20) DEFAULT NULL COMMENT '收款账户类型(bank_card/alipay/wechat/other)',
  `settlement_account` text COMMENT '收款账户(加密存储)',
  `settlement_account_name` varchar(100) DEFAULT NULL COMMENT '收款账户实名/户名',
  `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码（优先级最高）',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行卡号(加密存储)',
  `bank_name` varchar(100) DEFAULT NULL COMMENT '开户银行',
  `bank_branch_name` varchar(120) DEFAULT NULL COMMENT '开户支行',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_id` (`employee_id`),
  KEY `idx_manager` (`manager_id`),
  KEY `idx_employee_status` (`status`),
  KEY `idx_offline` (`is_offline`),
  KEY `idx_employee_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工信息表';

-- Employee-Department (multi)
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
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp` (`employee_id`),
  KEY `idx_platform_dept` (`platform_type`,`platform_dept_id`),
  CONSTRAINT `fk_emp_dept_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工-部门多对多关联';

-- Payroll Batch
CREATE TABLE `payroll_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pay_cycle_id` bigint DEFAULT NULL COMMENT '周期ID',
  `rule_template_id` bigint DEFAULT NULL COMMENT '批次锁定的薪资规则包ID',
  `rule_template_version` bigint DEFAULT NULL COMMENT '批次锁定的薪资规则包版本',
  `period_label` varchar(20) DEFAULT NULL COMMENT '周期标签',
  `type` varchar(20) NOT NULL COMMENT 'full_time/part_time',
  `scope_json` json DEFAULT NULL COMMENT '范围JSON',
  `currency` varchar(10) DEFAULT 'CNY',
  `calculation_status` varchar(32) DEFAULT 'draft' COMMENT '核算状态',
  `batch_revision` int DEFAULT '1' COMMENT '业务批次版本号',
  `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资输入事实快照摘要',
  `input_snapshot_json` json DEFAULT NULL COMMENT '薪资输入事实完整快照',
  `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资规则快照摘要',
  `rule_snapshot_json` json DEFAULT NULL COMMENT '薪资规则完整快照',
  `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT '计算引擎版本',
  `status` varchar(20) DEFAULT 'draft' COMMENT '业务状态',
  `approval_workflow_id` bigint DEFAULT NULL COMMENT '审批流ID',
  `payment_batch_no` varchar(50) DEFAULT NULL COMMENT '最新支付批次号',
  `payment_status` varchar(32) DEFAULT NULL COMMENT '支付子域状态投影',
  `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码',
  `confirmation_required` tinyint(1) DEFAULT '1' COMMENT '是否需要员工确认',
  `confirmation_mode` varchar(20) DEFAULT 'individual' COMMENT '确认模式',
  `confirmation_completed_time` datetime DEFAULT NULL COMMENT '确认完成时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_batch_type_period_status` (`type`,`period_label`,`status`),
  KEY `idx_calculation_status` (`calculation_status`),
  KEY `idx_batch_revision` (`batch_revision`),
  KEY `idx_payroll_payment_status` (`payment_status`),
  KEY `idx_batch_rule_template` (`rule_template_id`,`rule_template_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪批次';

-- Payroll Line
CREATE TABLE `payroll_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `batch_revision` int NOT NULL DEFAULT '1' COMMENT '工资行所属批次版本号',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employment_type` varchar(20) NOT NULL COMMENT 'full_time/part_time',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `template_version` bigint DEFAULT NULL COMMENT '工资行使用的规则包版本',
  `items_snapshot_json` json DEFAULT NULL COMMENT '项快照JSON',
  `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资输入事实快照摘要',
  `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资规则快照摘要',
  `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT '计算引擎版本',
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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_batch_employee` (`batch_id`,`employee_id`),
  KEY `idx_batch_revision_employee` (`batch_id`,`batch_revision`,`employee_id`),
  KEY `idx_line_template_version` (`template_id`,`template_version`),
  KEY `idx_confirmation_assignee_status` (`confirmation_assignee_employee_id`,`confirmation_status`),
  KEY `idx_dispute_workflow` (`dispute_workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资行';

-- Payroll Confirmation
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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_no` (`confirmation_no`),
  UNIQUE KEY `uk_confirmation_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
  KEY `idx_confirmation_status` (`confirmation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认单';

CREATE TABLE `payroll_confirmation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `confirmation_id` bigint NOT NULL COMMENT '确认单ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `line_id` bigint NOT NULL COMMENT '薪资行ID',
  `record_status` varchar(32) NOT NULL COMMENT '确认记录状态',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  `comment` varchar(500) DEFAULT NULL COMMENT '备注',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_employee_deleted` (`confirmation_id`,`employee_id`,`deleted`),
  KEY `idx_confirmation_line` (`confirmation_id`,`line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认记录';

-- Payroll Distribution
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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_no` (`distribution_no`),
  UNIQUE KEY `uk_distribution_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
  KEY `idx_distribution_status` (`distribution_status`),
  KEY `idx_distribution_schedule` (`scheduled_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放单';

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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_employee_deleted` (`distribution_id`,`employee_id`,`deleted`),
  KEY `idx_distribution_item_status` (`distribution_id`,`item_status`),
  KEY `idx_distribution_payment_record` (`payment_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放明细';

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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_deleted` (`workflow_id`,`deleted`),
  KEY `idx_projection_distribution` (`distribution_id`),
  KEY `idx_projection_status` (`business_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资审批投影';

CREATE TABLE `payroll_reconciliation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distribution_id` bigint NOT NULL COMMENT '发放单ID',
  `task_status` varchar(32) NOT NULL COMMENT '任务状态',
  `expected_amount` decimal(15,2) DEFAULT '0.00' COMMENT '应发金额',
  `actual_amount` decimal(15,2) DEFAULT '0.00' COMMENT '实发金额',
  `difference` decimal(15,2) DEFAULT '0.00' COMMENT '差异金额',
  `result` varchar(32) DEFAULT NULL COMMENT '结果',
  `difference_detail` text COMMENT '差异明细JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconciliation_distribution_deleted` (`distribution_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资对账任务';

-- Payment Batch
CREATE TABLE `payment_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(50) NOT NULL COMMENT '批次号',
  `batch_name` varchar(200) NOT NULL COMMENT '批次名称',
  `payment_type` varchar(20) NOT NULL COMMENT '支付类型',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '总金额',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总笔数',
  `success_count` int DEFAULT '0' COMMENT '成功笔数',
  `failed_count` int DEFAULT '0' COMMENT '失败笔数',
  `status` varchar(20) DEFAULT 'draft' COMMENT '批次状态',
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
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_distribution` (`distribution_id`),
  KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付批次表';

-- Payment Record
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(50) NOT NULL COMMENT '批次号',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `payment_type` varchar(20) NOT NULL COMMENT '支付类型',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `payment_method` varchar(20) DEFAULT 'alipay' COMMENT '支付方式',
  `recipient_account` varchar(100) DEFAULT NULL COMMENT '收款账户；校验失败记录允许为空，补充收款信息后可重试',
  `recipient_name` varchar(100) NOT NULL COMMENT '收款人姓名',
  `payment_desc` varchar(500) DEFAULT NULL COMMENT '支付描述',
  `status` varchar(20) DEFAULT 'pending' COMMENT '支付状态',
  `alipay_order_no` varchar(100) DEFAULT NULL COMMENT '支付宝订单号',
  `alipay_trade_no` varchar(100) DEFAULT NULL COMMENT '支付宝交易号',
  `provider_code` varchar(32) NOT NULL DEFAULT 'alipay' COMMENT '渠道编码',
  `provider_order_no` varchar(64) DEFAULT NULL COMMENT '渠道商户订单号',
  `provider_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道交易流水号',
  `provider_metadata` json DEFAULT NULL COMMENT '渠道扩展信息',
  `id_card_hash` varchar(64) DEFAULT NULL COMMENT '身份证哈希',
  `error_code` varchar(50) DEFAULT NULL COMMENT '错误码',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `payment_time` datetime DEFAULT NULL COMMENT '实际支付时间',
  `notification_time` datetime DEFAULT NULL COMMENT '支付宝通知时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_payment_record_employee` (`employee_id`),
  KEY `idx_provider_order` (`provider_code`, `provider_order_no`),
  KEY `idx_provider_trade` (`provider_code`, `provider_trade_no`),
  CONSTRAINT `fk_payment_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

-- Settlement Provider Config
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
  `config_json` text COMMENT '渠道配置JSON',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  `supports_batch` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否支持批量',
  `supports_query` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否支持查询',
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

-- Employee Type Provider Mapping
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

-- Settlement Reconciliation
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
  `unmatched_local` json DEFAULT NULL COMMENT '本地未匹配列表',
  `unmatched_provider` json DEFAULT NULL COMMENT '渠道未匹配列表',
  `status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/PROCESSING/COMPLETED',
  `processed_by` bigint DEFAULT NULL COMMENT '处理人ID',
  `processed_at` datetime DEFAULT NULL COMMENT '处理时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_recon_date` (`recon_date`, `provider_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算对账表';

-- Approval Workflow
CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_name` varchar(100) NOT NULL COMMENT '流程名称',
  `workflow_type` varchar(50) NOT NULL COMMENT '流程类型(BATCH/ADHOC/OFFLINE/EMPLOYEE_PROFILE_CHANGE/PLATFORM_BIND)',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务标识',
  `current_step` int DEFAULT 0 COMMENT '当前步骤',
  `total_steps` int DEFAULT 0 COMMENT '总步骤',
  `status` varchar(20) DEFAULT 'pending' COMMENT '流程状态',
  `initiator_id` bigint DEFAULT NULL COMMENT '发起人',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `current_approver_id` bigint DEFAULT NULL COMMENT '当前审批人',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `workflow_data` text COMMENT '流程数据(JSON)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_key` (`business_key`, `business_type`),
  KEY `idx_sys_user_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程表';

-- Approval Step
CREATE TABLE `approval_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '流程ID',
  `step_no` int NOT NULL COMMENT '步骤序号',
  `step_name` varchar(100) NOT NULL COMMENT '步骤名称',
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) NOT NULL COMMENT '审批人姓名',
  `status` varchar(20) DEFAULT 'pending' COMMENT '步骤状态',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `reject_reason` text COMMENT '拒绝原因',
  `approve_comment` text COMMENT '审批意见',
  `timeout_hours` int DEFAULT '24' COMMENT '超时时间(小时)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_step` (`workflow_id`, `step_no`),
  KEY `idx_approver` (`approver_id`),
  CONSTRAINT `fk_step_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批步骤表';

-- Notification
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
  KEY `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发送记录表';

-- Audit Log
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `operation` varchar(100) NOT NULL COMMENT '操作类型',
  `method` varchar(10) NOT NULL COMMENT '请求方法',
  `request_url` varchar(500) NOT NULL COMMENT '请求URL',
  `request_ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(1000) DEFAULT NULL COMMENT '用户代理',
  `request_params` text COMMENT '请求参数',
  `response_result` text COMMENT '响应结果',
  `error_msg` text COMMENT '错误信息',
  `execution_time` bigint DEFAULT NULL COMMENT '执行时间(ms)',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务关键字',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- Sys Config
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `remark` varchar(500) DEFAULT NULL COMMENT '配置备注',
  `config_type` varchar(20) DEFAULT 'string' COMMENT '配置类型',
  `config_desc` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `is_encrypted` tinyint(1) DEFAULT '0' COMMENT '是否加密',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- Org Department
CREATE TABLE `org_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型',
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
  KEY `idx_parent` (`parent_platform_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';

-- Integration Config
CREATE TABLE `integration_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay/yunzhanghu)',
  `config_json` text COMMENT '配置JSON(加密或明文)',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_type` (`platform_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台集成配置表';

-- RBAC: Resource
CREATE TABLE `sys_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type` varchar(20) NOT NULL COMMENT '资源类型: MENU/VIEW/ACTION/API',
  `code` varchar(100) NOT NULL COMMENT '全局唯一编码',
  `name` varchar(100) NOT NULL COMMENT '资源名称',
  `path` varchar(255) DEFAULT NULL COMMENT '路由或接口路径',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `access_mode` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '访问模式: PUBLIC/USER/EXTERNAL',
  `parent_id` bigint DEFAULT NULL COMMENT '父资源ID',
  `order_num` int DEFAULT 0 COMMENT '排序号',
  `props_json` json DEFAULT NULL COMMENT '扩展元信息(JSON)',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_code` (`code`),
  KEY `idx_type` (`type`),
  KEY `idx_parent_order` (`parent_id`, `order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统资源表';

CREATE TABLE `sys_permission_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `http_methods` varchar(100) DEFAULT NULL,
  `authority` varchar(150) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'enabled',
  `order_num` int NOT NULL DEFAULT '0',
  `props_json` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_action_code` (`code`),
  KEY `idx_permission_action_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限操作目录';

CREATE TABLE `sys_resource_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `action_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'enabled',
  `props_json` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_action` (`resource_id`,`action_id`),
  KEY `idx_resource_action_resource` (`resource_id`,`status`,`deleted`),
  KEY `idx_resource_action_action` (`action_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源可用操作';

CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `action_id` bigint NOT NULL,
  `effect` varchar(10) NOT NULL DEFAULT 'ALLOW',
  `scope_json` text,
  `condition_json` text,
  `status` varchar(20) NOT NULL DEFAULT 'enabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`resource_id`,`action_id`),
  KEY `idx_role_permission_subject` (`role_id`,`status`,`deleted`),
  KEY `idx_role_permission_resource` (`resource_id`,`action_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源-操作权限';

CREATE TABLE `sys_user_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `action_id` bigint NOT NULL,
  `effect` varchar(10) NOT NULL DEFAULT 'ALLOW',
  `scope_json` text,
  `condition_json` text,
  `status` varchar(20) NOT NULL DEFAULT 'enabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_permission` (`user_id`,`resource_id`,`action_id`),
  KEY `idx_user_permission_subject` (`user_id`,`status`,`deleted`),
  KEY `idx_user_permission_resource` (`resource_id`,`action_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源-操作权限';

-- RBAC: Role
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `role_type` varchar(20) DEFAULT 'CUSTOM' COMMENT '角色类型: SYSTEM/BUSINESS/CUSTOM',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `is_editable` tinyint(1) DEFAULT '1' COMMENT '是否可编辑',
  `icon` varchar(100) DEFAULT NULL COMMENT '角色图标',
  `remarks` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- RBAC: Role-Resource
CREATE TABLE `sys_role_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_resource` (`role_id`, `resource_id`),
  KEY `idx_sys_role_resource_role` (`role_id`),
  KEY `idx_sys_role_resource_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源授权关系';

-- RBAC: User-Role
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `granted_by` bigint DEFAULT NULL COMMENT '授权人ID',
  `granted_at` datetime DEFAULT NULL COMMENT '授权时间',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间',
  `remarks` varchar(500) DEFAULT NULL COMMENT '备注',
  `delete_by` varchar(50) DEFAULT NULL COMMENT '删除人',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关系';

-- RBAC: User-Resource
CREATE TABLE `sys_user_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_sys_user_resource_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源个性授权';

-- RBAC: Resource Snapshot
CREATE TABLE `resource_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '审批流程ID',
  `before_json` json DEFAULT NULL COMMENT '变更前快照',
  `after_json` json DEFAULT NULL COMMENT '变更后拟态',
  `actor_id` bigint DEFAULT NULL COMMENT '发起人',
  `reason` varchar(255) DEFAULT NULL COMMENT '原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源授权变更快照';

-- Sys User (after RBAC tables because of potential FKs)
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(200) NOT NULL COMMENT '密码(BCrypt加密)',
  `real_name` varchar(100) NOT NULL COMMENT '真实姓名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `status` varchar(20) DEFAULT 'active' COMMENT '用户状态',
  `roles` varchar(500) DEFAULT NULL COMMENT '角色列表(逗号分隔)',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `permission_version` int DEFAULT '0' COMMENT '权限版本',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- External identity (多平台账号绑定)
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

-- Seed baseline roles and ADMIN user
INSERT INTO `sys_role` (`code`, `name`, `description`, `role_type`, `sort_order`, `is_editable`, `icon`, `status`, `create_by`)
VALUES
('ADMIN', '系统管理员', '拥有所有系统权限', 'SYSTEM', 1, 0, 'crown', 'enabled', 'system'),
('MANAGER', '部门经理', '部门管理和审批权限', 'BUSINESS', 2, 1, 'team', 'enabled', 'system'),
('FINANCE', '财务人员', '薪酬管理和支付权限', 'BUSINESS', 3, 1, 'wallet', 'enabled', 'system'),
('HR', '人力资源', '员工管理权限', 'BUSINESS', 4, 1, 'user', 'enabled', 'system'),
('EMPLOYEE', '普通员工', '个人工资条查看权限', 'BUSINESS', 5, 1, 'contacts', 'enabled', 'system');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `roles`, `create_by`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXIUYGSG2v8vwAkAbJXhRXz4CZG', '系统管理员', 'admin@yiyundao.com', 'active', 'ROLE_ADMIN', 'system');

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `granted_by`, `granted_at`, `deleted`, `create_time`, `create_by`)
SELECT u.id, r.id, u.id, NOW(), 0, NOW(), 'system'
FROM `sys_user` u
JOIN `sys_role` r ON r.code = 'ADMIN'
WHERE u.username = 'admin';

INSERT INTO `sys_permission_action` (`code`,`name`,`description`,`http_methods`,`authority`,`status`,`order_num`,`create_by`)
VALUES
('read','读取','读取资源数据','GET,HEAD',NULL,'enabled',10,'system'),
('write','写入','创建或更新资源数据','POST,PUT,PATCH',NULL,'enabled',20,'system'),
('delete','删除','删除资源数据','DELETE',NULL,'enabled',30,'system'),
('execute','执行','执行非CRUD业务动作','POST,PUT,PATCH',NULL,'enabled',40,'system'),
('payroll:read','外部薪酬读取','外部应用读取薪酬数据','GET','SCOPE_payroll:read','enabled',100,'system'),
('payslip:read','外部工资条读取','外部应用读取工资条','GET','SCOPE_payslip:read','enabled',110,'system'),
('app:ping','外部应用探活','外部应用探活接口','GET',NULL,'enabled',120,'system');

INSERT INTO `sys_resource` (`type`,`code`,`name`,`path`,`access_mode`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`version`)
VALUES
('API','rbac.public.auth.login','登录','/auth/login','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.auth.refresh','刷新令牌','/auth/refresh','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.auth.oauth','认证回调','/auth/oauth/**','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.user.auth.me','当前用户权限','/auth/me/**','USER','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.system.health','系统健康检查','/system/health','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.actuator.health','Actuator健康检查','/actuator/health','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.favicon','站点图标','/favicon.ico','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.external.token','外部应用令牌','/v1/oauth/token','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.payment.notify','支付通知','/alipay/notify','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.public.settlement.callback','结算回调','/v1/settlement/callback/**','PUBLIC','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.external.payroll','外部薪酬接口','/v1/payroll/**','EXTERNAL','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.external.payslip','外部工资条接口','/v1/payslips/**','EXTERNAL','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.external.ping','外部应用探活','/v1/ping','EXTERNAL','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.admin.permission.actions','权限操作目录','/admin/permission-actions','USER','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.admin.permission.action-items','权限操作明细','/admin/permission-actions/**','USER','enabled',NOW(),NOW(),'system','system',0,0),
('API','rbac.admin.permission.resource-actions','资源操作绑定','/admin/permission-actions/resources/*','USER','enabled',NOW(),NOW(),'system','system',0,0);

INSERT INTO `sys_resource_action` (`resource_id`,`action_id`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`version`)
SELECT r.id,a.id,'enabled',NOW(),NOW(),'system','system',0,0
FROM `sys_resource` r JOIN `sys_permission_action` a
WHERE (r.code IN ('rbac.public.auth.login','rbac.public.auth.refresh','rbac.public.external.token','rbac.public.payment.notify') AND a.code='write')
   OR (r.code IN ('rbac.public.auth.oauth','rbac.public.system.health','rbac.public.actuator.health','rbac.public.favicon') AND a.code='read')
   OR (r.code='rbac.public.settlement.callback' AND a.code='write')
   OR (r.code='rbac.user.auth.me' AND a.code='read')
   OR (r.code='rbac.external.payroll' AND a.code='payroll:read')
   OR (r.code='rbac.external.payslip' AND a.code='payslip:read')
   OR (r.code='rbac.external.ping' AND a.code='app:ping')
   OR (r.code='rbac.admin.permission.actions' AND a.code IN ('read','write','delete'))
   OR (r.code='rbac.admin.permission.action-items' AND a.code IN ('read','write','delete'))
   OR (r.code='rbac.admin.permission.resource-actions' AND a.code IN ('read','write'));

INSERT INTO `sys_role_resource` (`role_id`, `resource_id`, `actions_json`, `create_time`, `create_by`)
SELECT r.id, res.id, '["*"]', NOW(), 'system'
FROM `sys_role` r
JOIN `sys_resource` res ON res.status = 'enabled'
WHERE r.code = 'ADMIN';

-- Minimal system config seeds (optional safe defaults)
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_desc`) VALUES
('system.name', '薪酬助手系统', 'string', '系统名称'),
('approval.timeout.hours', '24', 'number', '审批超时时间(小时)'),
('payroll.approval.flow', '[{"stepNo":1,"stepName":"部门负责人审批","role":"ROLE_MANAGER","approverType":"EMPLOYEE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务负责人审批","role":"ROLE_FINANCE","timeoutHours":24,"optional":false},{"stepNo":3,"stepName":"总监审批","role":"ROLE_ADMIN","timeoutHours":48,"optional":false,"finalStep":true}]', 'json', '薪资批次/发放审批流程配置(JSON)'),
('adhoc.approval.flow', '[{"stepNo":1,"stepName":"直接上级审批","role":"ROLE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务审批","role":"ROLE_FINANCE","timeoutHours":24,"optional":false,"finalStep":true}]', 'json', '临时支付审批流程配置(JSON)'),
('offline.approval.flow', '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]', 'json', '架构外员工审批流程配置(JSON)'),
('employee.profile-change.approval.flow', '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]', 'json', '员工资料变更审批流程配置(JSON)'),
('platform.bind.approval.flow', '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]', 'json', '平台账号绑定审批流程配置(JSON)'),
('permission.approval.flow', '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]', 'json', '权限授权审批流程配置(JSON)'),
('payroll.dispute.approval.flow', '[{"stepNo":1,"stepName":"负责人核实","role":"ROLE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务复核","role":"ROLE_FINANCE","timeoutHours":24,"optional":false},{"stepNo":3,"stepName":"老板终审","role":"ROLE_ADMIN","timeoutHours":48,"optional":true,"finalStep":true}]', 'json', '薪酬异议审批流程配置(JSON)'),
('notification.retry.max', '3', 'number', '通知重试最大次数');

SET FOREIGN_KEY_CHECKS = 1;
