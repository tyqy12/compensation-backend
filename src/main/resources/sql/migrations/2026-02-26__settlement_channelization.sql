-- 结算渠道化改造（云账户接入前置）
-- 目标：
-- 1) payment_record 增加 provider 通用字段
-- 2) 新增 settlement_provider_config / settlement_reconciliation 表
-- 3) 迁移存量支付宝数据到 provider 字段

SET NAMES utf8mb4;
SET @db := DATABASE();

-- =========================
-- payment_record 新增字段
-- =========================
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND COLUMN_NAME='provider_code'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payment_record` ADD COLUMN `provider_code` varchar(32) NOT NULL DEFAULT ''alipay'' COMMENT ''渠道编码: alipay/yunzhanghu/wechat/bank'' AFTER `alipay_trade_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND COLUMN_NAME='provider_order_no'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payment_record` ADD COLUMN `provider_order_no` varchar(64) DEFAULT NULL COMMENT ''渠道侧商户订单号(我方生成)'' AFTER `provider_code`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND COLUMN_NAME='provider_trade_no'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payment_record` ADD COLUMN `provider_trade_no` varchar(64) DEFAULT NULL COMMENT ''渠道侧平台流水号(渠道返回)'' AFTER `provider_order_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND COLUMN_NAME='provider_metadata'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payment_record` ADD COLUMN `provider_metadata` json DEFAULT NULL COMMENT ''渠道扩展信息'' AFTER `provider_trade_no`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND COLUMN_NAME='id_card_hash'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payment_record` ADD COLUMN `id_card_hash` varchar(64) DEFAULT NULL COMMENT ''收款人身份证哈希'' AFTER `provider_metadata`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND INDEX_NAME='idx_provider_order'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_provider_order` ON `payment_record` (`provider_code`, `provider_order_no`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payment_record' AND INDEX_NAME='idx_provider_trade'
);
SET @sql := IF(@idx_exists=0,
  'CREATE INDEX `idx_provider_trade` ON `payment_record` (`provider_code`, `provider_trade_no`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =========================
-- 存量数据迁移
-- =========================
UPDATE payment_record
SET provider_code = 'alipay'
WHERE provider_code IS NULL OR provider_code = '';

UPDATE payment_record
SET provider_order_no = alipay_order_no
WHERE (provider_order_no IS NULL OR provider_order_no = '')
  AND alipay_order_no IS NOT NULL;

UPDATE payment_record
SET provider_trade_no = alipay_trade_no
WHERE (provider_trade_no IS NULL OR provider_trade_no = '')
  AND alipay_trade_no IS NOT NULL;

UPDATE payment_record
SET provider_metadata = '{"legacy":true}'
WHERE provider_metadata IS NULL
  AND alipay_order_no IS NOT NULL;

-- =========================
-- 新增渠道配置表
-- =========================
CREATE TABLE IF NOT EXISTS `settlement_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `provider_name` varchar(64) NOT NULL COMMENT '渠道名称',
  `config_json` text COMMENT '渠道配置JSON',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用',
  `supports_batch` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持批量',
  `supports_query` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持主动查询',
  `supports_callback` tinyint NOT NULL DEFAULT '1' COMMENT '是否支持回调',
  `single_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单笔最大金额',
  `daily_max_amount` decimal(15,2) DEFAULT NULL COMMENT '单日最大金额',
  `callback_url` varchar(256) DEFAULT NULL COMMENT '回调地址',
  `callback_ips` varchar(512) DEFAULT NULL COMMENT '回调IP白名单',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算渠道配置表';

INSERT INTO settlement_provider_config
(`provider_code`, `provider_name`, `enabled`, `supports_batch`, `supports_query`, `supports_callback`)
VALUES
('alipay', '支付宝', 1, 1, 1, 1),
('yunzhanghu', '云账户', 0, 1, 1, 1)
ON DUPLICATE KEY UPDATE
`provider_name`=VALUES(`provider_name`),
`supports_batch`=VALUES(`supports_batch`),
`supports_query`=VALUES(`supports_query`),
`supports_callback`=VALUES(`supports_callback`);

-- =========================
-- 新增对账表
-- =========================
CREATE TABLE IF NOT EXISTS `settlement_reconciliation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `processed_at` timestamp NULL DEFAULT NULL COMMENT '处理时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '处理备注',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_date` (`recon_date`,`provider_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道对账表';
