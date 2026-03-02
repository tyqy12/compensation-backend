-- SQL 升级脚本：部门表、通知记录、配置兼容列与审计日志补栏位
-- 适用：MySQL 8.0+
-- 运行方式（示例）：
--   mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-09-29__dept_and_binding_approval.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 部门表（分平台，独立树）
CREATE TABLE IF NOT EXISTS `org_department` (
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

-- 2) 通知记录表（用于统计通知成功率等指标；若已存在则跳过）
CREATE TABLE IF NOT EXISTS `notification_record` (
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

-- 3) sys_config 兼容列（与实体 SysConfig 对齐）
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_config' AND COLUMN_NAME='remark'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `sys_config` ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT ''配置备注'' AFTER `config_value`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) audit_log 增量栏位（与 BaseEntity 对齐）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_time'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL COMMENT ''更新人'' AFTER `create_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `deleted` TINYINT(1) DEFAULT ''0'' COMMENT ''逻辑删除(0:未删除,1:已删除)'' AFTER `update_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='audit_log' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `audit_log` ADD COLUMN `version` INT DEFAULT ''0'' COMMENT ''乐观锁版本号'' AFTER `deleted`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- 可选：为 sys_user.employee_id 增加外键（若业务允许强约束）
-- ALTER TABLE `sys_user`
--   ADD CONSTRAINT `fk_sys_user_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee`(`id`);
