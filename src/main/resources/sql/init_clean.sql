-- ============================================
-- Compensation Assistant System - Clean Initialization SQL
-- Drops existing objects and recreates all tables with only the ADMIN user seeded.
-- Usage:
--   mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/init_clean.sql
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop tables if exist (order considers FK constraints)
DROP TABLE IF EXISTS `sys_user_resource`;
DROP TABLE IF EXISTS `sys_role_resource`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `resource_snapshot`;
DROP TABLE IF EXISTS `approval_step`;
DROP TABLE IF EXISTS `approval_workflow`;
DROP TABLE IF EXISTS `notification_record`;
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
  `department` varchar(100) DEFAULT NULL COMMENT '部门(主展示，逗号拼接)',
  `position` varchar(100) DEFAULT NULL COMMENT '职位',
  `employment_type` varchar(20) DEFAULT 'full_time' COMMENT '用工类型(full_time/part_time)',
  `platform_user_id` varchar(100) DEFAULT NULL COMMENT '平台用户ID',
  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',
  `is_offline` tinyint(1) DEFAULT '0' COMMENT '是否离线员工',
  `manager_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) DEFAULT 'active' COMMENT '员工状态',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行卡号(加密存储)',
  `bank_name` varchar(100) DEFAULT NULL COMMENT '开户银行',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_id` (`employee_id`),
  KEY `idx_platform_user` (`platform_user_id`, `platform_type`),
  KEY `idx_manager` (`manager_id`),
  KEY `idx_status` (`status`),
  KEY `idx_offline` (`is_offline`),
  KEY `idx_create_time` (`create_time`)
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
  UNIQUE KEY `uk_batch_no` (`batch_no`)
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
  `recipient_account` varchar(100) NOT NULL COMMENT '收款账户',
  `recipient_name` varchar(100) NOT NULL COMMENT '收款人姓名',
  `payment_desc` varchar(500) DEFAULT NULL COMMENT '支付描述',
  `status` varchar(20) DEFAULT 'pending' COMMENT '支付状态',
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
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_employee` (`employee_id`),
  CONSTRAINT `fk_payment_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

-- Approval Workflow
CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_name` varchar(100) NOT NULL COMMENT '流程名称',
  `workflow_type` varchar(50) NOT NULL COMMENT '流程类型',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务标识',
  `current_step` int DEFAULT 0 COMMENT '当前步骤',
  `total_steps` int DEFAULT 0 COMMENT '总步骤',
  `status` varchar(20) DEFAULT 'pending' COMMENT '流程状态',
  `initiator_id` bigint DEFAULT NULL COMMENT '发起人',
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
  UNIQUE KEY `uk_business_key` (`business_key`, `business_type`)
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
  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay)',
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

-- RBAC: Role
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
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
  KEY `idx_role` (`role_id`),
  KEY `idx_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源授权关系';

-- RBAC: User-Role
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
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
  KEY `idx_resource` (`resource_id`)
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
  `platform_user_id` varchar(100) DEFAULT NULL COMMENT '平台用户ID',
  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型',
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
  KEY `idx_employee` (`employee_id`),
  KEY `idx_platform_user` (`platform_user_id`, `platform_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- Seed only ADMIN user
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `roles`, `create_by`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXIUYGSG2v8vwAkAbJXhRXz4CZG', '系统管理员', 'admin@yiyundao.com', 'active', 'ROLE_ADMIN', 'system');

-- Minimal system config seeds (optional safe defaults)
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_desc`) VALUES
('system.name', '薪酬助手系统', 'string', '系统名称'),
('notification.retry.max', '3', 'number', '通知重试最大次数');

SET FOREIGN_KEY_CHECKS = 1;
