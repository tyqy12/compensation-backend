-- ============================================================================
-- 修复 sys_user_role 表结构
-- 问题：表缺少 id 主键列和 BaseEntity 必要的审计字段
-- 执行时间：2026-01-28
-- ============================================================================

SET @db := DATABASE();

-- 1. 添加缺失的列（兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL）
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='id'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''主键ID'' FIRST',
    'SELECT ''id exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @id_auto := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='id' AND EXTRA LIKE '%auto_increment%'
);
SET @sql := IF(@id_auto=0,
    'ALTER TABLE `sys_user_role` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''主键ID''',
    'SELECT ''id auto_increment exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='update_time'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'SELECT ''update_time exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='create_by'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `create_by` varchar(50) DEFAULT NULL COMMENT ''创建人''',
    'SELECT ''create_by exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='update_by'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `update_by` varchar(50) DEFAULT NULL COMMENT ''更新人''',
    'SELECT ''update_by exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='deleted'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `deleted` tinyint(1) DEFAULT ''0'' COMMENT ''逻辑删除''',
    'SELECT ''deleted exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='version'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `version` int DEFAULT ''0'' COMMENT ''乐观锁版本号''',
    'SELECT ''version exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 将 id 列设为主键（如果还不是）
SET @pk_cols := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ',')
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND CONSTRAINT_NAME='PRIMARY'
);
SET @sql := CASE
    WHEN @pk_cols IS NULL THEN 'ALTER TABLE `sys_user_role` ADD PRIMARY KEY (`id`)'
    WHEN @pk_cols = 'id' THEN 'SELECT ''primary key is id'' AS msg'
    ELSE 'ALTER TABLE `sys_user_role` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`)'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 添加备注列（如果不存在）
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='granted_by'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `granted_by` bigint DEFAULT NULL COMMENT ''授权人ID''',
    'SELECT ''granted_by exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='granted_at'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `granted_at` datetime DEFAULT NULL COMMENT ''授权时间''',
    'SELECT ''granted_at exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='expires_at'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `expires_at` datetime DEFAULT NULL COMMENT ''过期时间''',
    'SELECT ''expires_at exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='remarks'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `remarks` varchar(500) DEFAULT NULL COMMENT ''备注''',
    'SELECT ''remarks exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='delete_by'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `delete_by` varchar(50) DEFAULT NULL COMMENT ''删除人''',
    'SELECT ''delete_by exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user_role' AND COLUMN_NAME='delete_time'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_user_role` ADD COLUMN `delete_time` datetime DEFAULT NULL COMMENT ''删除时间''',
    'SELECT ''delete_time exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 验证表结构
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_KEY,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_user_role'
ORDER BY ORDINAL_POSITION;

-- 5. 清理测试数据（如果有旧数据）
-- 注意：如果之前插入过数据，现在需要重新插入以确保 deleted=0
-- INSERT IGNORE 会自动跳过已存在的记录
