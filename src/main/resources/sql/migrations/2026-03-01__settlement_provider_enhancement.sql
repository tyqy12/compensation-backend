-- ============================================================================
-- 结算渠道增强 - 支持灵活的多渠道路由（增量兼容版）
-- 版本: V1.3
-- 日期: 2026-03-01
-- 说明:
--   1. 兼容增强 settlement_provider_config（基于 2026-02-26 结构补列）
--   2. 创建/增强 employee_type_provider_mapping
--   3. 扩展 employee 和 payroll_batch
--   4. 兼容 MySQL（不依赖 ADD COLUMN IF NOT EXISTS 语法）
-- ============================================================================

SET NAMES utf8mb4;
SET @db := DATABASE();

-- ============================================================================
-- 1) settlement_provider_config 增量增强（兼容旧结构）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `settlement_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用',
  `supports_batch` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持批量',
  `supports_query` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持查询',
  `supports_callback` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持回调',
  `single_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单笔最大金额',
  `daily_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单日最大金额',
  `callback_url` varchar(256) DEFAULT NULL COMMENT '回调地址',
  `callback_ips` varchar(512) DEFAULT NULL COMMENT '回调IP白名单',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算渠道配置表';

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='provider_type'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `provider_type` varchar(32) DEFAULT NULL COMMENT ''渠道类型'' AFTER `provider_name`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_endpoint'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `api_endpoint` varchar(255) DEFAULT NULL COMMENT ''API端点'' AFTER `provider_type`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_key'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `api_key` varchar(255) DEFAULT NULL COMMENT ''API密钥'' AFTER `api_endpoint`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='api_secret'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `api_secret` varchar(255) DEFAULT NULL COMMENT ''API密钥'' AFTER `api_key`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='merchant_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `merchant_id` varchar(64) DEFAULT NULL COMMENT ''商户ID'' AFTER `api_secret`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='notify_url'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `notify_url` varchar(255) DEFAULT NULL COMMENT ''回调URL'' AFTER `merchant_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='return_url'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `return_url` varchar(255) DEFAULT NULL COMMENT ''返回URL'' AFTER `notify_url`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='priority'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `priority` int NOT NULL DEFAULT ''100'' COMMENT ''优先级(数字越小优先级越高)'' AFTER `return_url`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='remark'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT ''备注'' AFTER `priority`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='create_by'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `create_by` varchar(50) DEFAULT NULL COMMENT ''创建人'' AFTER `update_time`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `update_by` varchar(50) DEFAULT NULL COMMENT ''更新人'' AFTER `create_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `deleted` tinyint(1) DEFAULT ''0'' COMMENT ''逻辑删除(0:未删除,1:已删除)'' AFTER `update_by`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='settlement_provider_config' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `settlement_provider_config` ADD COLUMN `version` int DEFAULT ''0'' COMMENT ''乐观锁版本号'' AFTER `deleted`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE settlement_provider_config
SET notify_url = callback_url
WHERE (notify_url IS NULL OR notify_url = '')
  AND callback_url IS NOT NULL
  AND callback_url <> '';

UPDATE settlement_provider_config
SET provider_type = provider_code
WHERE provider_type IS NULL OR provider_type = '';

UPDATE settlement_provider_config
SET priority = 100
WHERE priority IS NULL;

INSERT INTO settlement_provider_config
    (provider_code, provider_name, provider_type, enabled, priority)
VALUES
    ('alipay', '支付宝', 'alipay', 1, 100),
    ('yunzhanghu', '云账户', 'yunzhanghu', 1, 100)
ON DUPLICATE KEY UPDATE
    provider_name = VALUES(provider_name),
    enabled = VALUES(enabled),
    provider_type = COALESCE(provider_type, VALUES(provider_type));

-- ============================================================================
-- 2) employee_type_provider_mapping 创建与增强
-- ============================================================================
CREATE TABLE IF NOT EXISTS `employee_type_provider_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employment_type` varchar(32) NOT NULL COMMENT '员工类型：full_time/part_time/intern/contract',
  `provider_code` varchar(32) NOT NULL COMMENT '结算渠道编码',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（数字越大越高）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employment_provider` (`employment_type`,`provider_code`),
  KEY `idx_employment_type` (`employment_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工类型渠道映射表';

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee_type_provider_mapping' AND INDEX_NAME='uk_employment_provider'
);
SET @sql := IF(@idx_exists=0,
  'ALTER TABLE `employee_type_provider_mapping` ADD UNIQUE KEY `uk_employment_provider` (`employment_type`,`provider_code`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee_type_provider_mapping' AND INDEX_NAME='idx_employment_type'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_employment_type` ON `employee_type_provider_mapping` (`employment_type`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee_type_provider_mapping' AND INDEX_NAME='idx_enabled'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_enabled` ON `employee_type_provider_mapping` (`enabled`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO employee_type_provider_mapping
    (employment_type, provider_code, priority, enabled)
VALUES
    ('full_time', 'alipay', 10, 1),
    ('part_time', 'alipay', 10, 1),
    ('intern', 'alipay', 10, 1),
    ('contract', 'alipay', 10, 1)
ON DUPLICATE KEY UPDATE
    priority = VALUES(priority),
    enabled = VALUES(enabled);

-- ============================================================================
-- 3) 扩展 employee 表
-- ============================================================================
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='settlement_provider_code'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `employee` ADD COLUMN `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT ''结算渠道编码（优先级最高）'' AFTER `settlement_account_name`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 4) 扩展 payroll_batch 表
-- ============================================================================
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='settlement_provider_code'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `settlement_provider_code` varchar(32) DEFAULT NULL COMMENT ''结算渠道编码'' AFTER `payment_batch_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
