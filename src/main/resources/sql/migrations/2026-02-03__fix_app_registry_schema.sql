-- 修复 app_registry 表结构，确保与实体类字段一致
-- 日期: 2026-02-03
-- 描述: 统一字段命名，添加 rate_limit 相关字段，修复 id 自增属性

-- 1. 修复 id 字段，确保有 AUTO_INCREMENT 属性
SET @hasAutoIncrement = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'id'
      AND EXTRA LIKE '%auto_increment%'
);

SET @sql = IF(@hasAutoIncrement = 0,
    'ALTER TABLE `app_registry` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT "主键ID"',
    'SELECT "id column already has AUTO_INCREMENT, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 检查并修改 client_secret_hash 字段名为 client_secret (如果存在)
SET @columnExists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'client_secret_hash'
);

SET @sql = IF(@columnExists > 0,
    'ALTER TABLE `app_registry` CHANGE COLUMN `client_secret_hash` `client_secret` VARCHAR(255) NOT NULL COMMENT "客户端密钥哈希值"',
    'SELECT "client_secret_hash column does not exist, skipping rename" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 添加 rate_limit_enabled 字段(如果不存在)
SET @columnExists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'rate_limit_enabled'
);

SET @sql = IF(@columnExists = 0,
    'ALTER TABLE `app_registry` ADD COLUMN `rate_limit_enabled` TINYINT(1) DEFAULT 1 COMMENT "是否启用速率限制"',
    'SELECT "rate_limit_enabled column already exists, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 添加 rate_limit_per_minute 字段(如果不存在)
SET @columnExists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'rate_limit_per_minute'
);

SET @sql = IF(@columnExists = 0,
    'ALTER TABLE `app_registry` ADD COLUMN `rate_limit_per_minute` INT DEFAULT 600 COMMENT "每分钟请求限制"',
    'SELECT "rate_limit_per_minute column already exists, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 修改 scopes 字段长度(从 200 增加到 500，与实际数据库一致)
SET @columnExists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'scopes'
      AND CHARACTER_MAXIMUM_LENGTH < 500
);

SET @sql = IF(@columnExists > 0,
    'ALTER TABLE `app_registry` MODIFY COLUMN `scopes` VARCHAR(500) NULL COMMENT "应用权限范围"',
    'SELECT "scopes column already has correct length, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. 修改 ip_whitelist 字段类型(从 TEXT 改为 VARCHAR(1000)，与实际数据库一致)
SET @columnType = (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'ip_whitelist'
);

SET @sql = IF(@columnType = 'text',
    'ALTER TABLE `app_registry` MODIFY COLUMN `ip_whitelist` VARCHAR(1000) NULL COMMENT "IP白名单JSON"',
    'SELECT "ip_whitelist column already has correct type, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. 添加 webhook_url 字段(如果不存在)
SET @columnExists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_registry'
      AND COLUMN_NAME = 'webhook_url'
);

SET @sql = IF(@columnExists = 0,
    'ALTER TABLE `app_registry` ADD COLUMN `webhook_url` VARCHAR(300) NULL COMMENT "Webhook回调URL"',
    'SELECT "webhook_url column already exists, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
