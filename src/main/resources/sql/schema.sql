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
  `platform_user_id` varchar(100) DEFAULT NULL COMMENT '平台用户ID(企微/钉钉/飞书)',
  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',
  `is_offline` tinyint(1) DEFAULT '0' COMMENT '是否离线员工(0:否,1:是)',
  `manager_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) DEFAULT 'active' COMMENT '员工状态(active:在职,inactive:离职,suspended:停职)',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行卡号(加密存储)',
  `bank_name` varchar(100) DEFAULT NULL COMMENT '开户银行',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_id` (`employee_id`),
  KEY `idx_platform_user` (`platform_user_id`, `platform_type`),
  KEY `idx_manager` (`manager_id`),
  KEY `idx_status` (`status`),
  KEY `idx_offline` (`is_offline`),
  KEY `idx_create_time` (`create_time`)
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
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `cutoff_date` date DEFAULT NULL,
  `status` varchar(20) DEFAULT 'open',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cycle` (`type`,`period_label`),
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
  `status` varchar(20) DEFAULT 'draft',
  `approval_workflow_id` bigint DEFAULT NULL,
  `payment_batch_no` varchar(50) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_batch_type_period_status` (`type`,`period_label`,`status`),
  KEY `idx_cycle` (`pay_cycle_id`)
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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_batch_employee` (`batch_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资行';

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
  KEY `idx_employee` (`employee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_payment_type` (`payment_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_payment_time` (`payment_time`),
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
  KEY `idx_status` (`status`),
  KEY `idx_payment_type` (`payment_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_submit_time` (`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付批次表';

-- ============================================
-- 4. 审批流程表 (approval_workflow)
-- ============================================
DROP TABLE IF EXISTS `approval_workflow`;
CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_name` varchar(100) NOT NULL COMMENT '流程名称',
  `workflow_type` varchar(20) NOT NULL COMMENT '流程类型(BATCH:批量支付,ADHOC:临时支付,OFFLINE:离线员工)',
  `business_key` varchar(100) NOT NULL COMMENT '业务关键字(如batch_no)',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `current_step` int DEFAULT '1' COMMENT '当前步骤',
  `total_steps` int NOT NULL COMMENT '总步骤数',
  `status` varchar(20) DEFAULT 'pending' COMMENT '流程状态(pending:待审批,approved:已通过,rejected:已拒绝,cancelled:已取消)',
  `initiator_id` bigint NOT NULL COMMENT '发起人ID',
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
  KEY `idx_status` (`status`),
  KEY `idx_initiator` (`initiator_id`),
  KEY `idx_current_approver` (`current_approver_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程表';

-- ============================================
-- 5. 审批步骤表 (approval_step)
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
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
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
  `platform_user_id` varchar(100) DEFAULT NULL COMMENT '平台用户ID',
  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型',
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
  KEY `idx_employee` (`employee_id`),
  KEY `idx_platform_user` (`platform_user_id`, `platform_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `fk_user_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

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
  KEY `idx_create_time` (`create_time`)
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
  KEY `idx_create_time` (`create_time`)
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
  KEY `idx_create_time` (`create_time`)
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
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';

DROP TABLE IF EXISTS `integration_config`;
CREATE TABLE `integration_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay)',
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
  KEY `idx_create_time` (`create_time`)
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
CREATE INDEX `idx_employee_platform_status` ON `employee` (`platform_type`, `status`, `is_offline`);
CREATE INDEX `idx_employee_dept_position` ON `employee` (`department`, `position`);

-- 支付记录表复合索引
CREATE INDEX `idx_payment_batch_status` ON `payment_record` (`batch_no`, `status`);
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
