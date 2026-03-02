-- 员工财务账户字段扩展（幂等）
-- 目标：
-- 1) 支持多种收款账户类型（银行卡/支付宝/微信/其他）
-- 2) 兼容历史 bank_account 数据并自动回填新字段
-- 3) 增加收款账户解密 API 资源（仅管理员）

SET NAMES utf8mb4;
SET @db := DATABASE();
SET @now := NOW();

-- 1. employee 增加收款账户相关字段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='settlement_account_type'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `employee` ADD COLUMN `settlement_account_type` varchar(20) DEFAULT NULL COMMENT ''收款账户类型(bank_card/alipay/wechat/other)'' AFTER `status`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='settlement_account'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `employee` ADD COLUMN `settlement_account` varchar(128) DEFAULT NULL COMMENT ''收款账户(加密存储)'' AFTER `settlement_account_type`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='settlement_account_name'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `employee` ADD COLUMN `settlement_account_name` varchar(100) DEFAULT NULL COMMENT ''收款账户实名/户名'' AFTER `settlement_account`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='employee' AND COLUMN_NAME='bank_branch_name'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `employee` ADD COLUMN `bank_branch_name` varchar(120) DEFAULT NULL COMMENT ''开户支行'' AFTER `bank_name`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 历史数据回填
UPDATE employee
SET settlement_account = bank_account
WHERE (settlement_account IS NULL OR settlement_account = '')
  AND bank_account IS NOT NULL
  AND bank_account <> '';

UPDATE employee
SET settlement_account_type = 'bank_card'
WHERE (settlement_account_type IS NULL OR settlement_account_type = '')
  AND (
    (settlement_account IS NOT NULL AND settlement_account <> '')
    OR (bank_account IS NOT NULL AND bank_account <> '')
  );

UPDATE employee
SET settlement_account_name = name
WHERE (settlement_account_name IS NULL OR settlement_account_name = '')
  AND (
    (settlement_account IS NOT NULL AND settlement_account <> '')
    OR (bank_account IS NOT NULL AND bank_account <> '')
  );

-- 3. API 资源：解密收款账户
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employee.decrypt-settlement','员工管理-收款账户解密','/api/employee/{id}/settlement-account',NULL,NULL,
 (SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),191,
 JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
`name`=VALUES(`name`),
`path`=VALUES(`path`),
`parent_id`=VALUES(`parent_id`),
`order_num`=VALUES(`order_num`),
`props_json`=VALUES(`props_json`),
`status`='enabled',
`update_time`=@now;

INSERT IGNORE INTO sys_role_resource (`role_id`, `resource_id`, `actions_json`, `create_time`, `update_time`)
SELECT r.id,
       s.id,
       JSON_ARRAY('read'),
       @now,
       @now
FROM sys_role r
JOIN sys_resource s ON s.code = 'api.employee.decrypt-settlement'
WHERE r.code = 'ADMIN';
