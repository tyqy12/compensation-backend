-- RBAC 角色表增强
-- Add: role_type, sort_order, is_editable, icon, remarks fields
-- Usage: mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-01-10__rbac_role_enhance.sql

SET NAMES utf8mb4;
SET @now := NOW();

-- 1) sys_role 表增加字段
SET @db := DATABASE();

-- 检查并添加 role_type 字段
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_role' AND COLUMN_NAME='role_type'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_role` ADD COLUMN `role_type` VARCHAR(20) DEFAULT ''CUSTOM'' COMMENT ''角色类型: SYSTEM/BUSINESS/CUSTOM'' AFTER `description`',
    'SELECT ''role_type exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 sort_order 字段
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_role' AND COLUMN_NAME='sort_order'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_role` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT ''排序号'' AFTER `role_type`',
    'SELECT ''sort_order exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 is_editable 字段
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_role' AND COLUMN_NAME='is_editable'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_role` ADD COLUMN `is_editable` TINYINT(1) DEFAULT 1 COMMENT ''是否可编辑: 1-可编辑, 0-系统角色不可编辑'' AFTER `sort_order`',
    'SELECT ''is_editable exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 icon 字段
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_role' AND COLUMN_NAME='icon'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_role` ADD COLUMN `icon` VARCHAR(100) DEFAULT NULL COMMENT ''角色图标'' AFTER `is_editable`',
    'SELECT ''icon exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 remarks 字段
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_role' AND COLUMN_NAME='remarks'
);
SET @sql := IF(@exists=0,
    'ALTER TABLE `sys_role` ADD COLUMN `remarks` VARCHAR(500) DEFAULT NULL COMMENT ''备注'' AFTER `icon`',
    'SELECT ''remarks exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 初始化系统角色（如果不存在）
INSERT INTO `sys_role` (`code`, `name`, `description`, `role_type`, `sort_order`, `is_editable`, `icon`, `status`)
VALUES
    ('ADMIN', '系统管理员', '拥有所有系统权限', 'SYSTEM', 1, 0, 'crown', 'enabled'),
    ('MANAGER', '部门经理', '部门管理和审批权限', 'BUSINESS', 2, 1, 'team', 'enabled'),
    ('FINANCE', '财务人员', '薪酬管理和支付权限', 'BUSINESS', 3, 1, 'wallet', 'enabled'),
    ('HR', '人力资源', '员工管理权限', 'BUSINESS', 4, 1, 'user', 'enabled'),
    ('EMPLOYEE', '普通员工', '个人工资条查看权限', 'BUSINESS', 5, 1, 'contacts', 'enabled')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `role_type` = VALUES(`role_type`),
    `sort_order` = VALUES(`sort_order`),
    `is_editable` = VALUES(`is_editable`),
    `icon` = VALUES(`icon`),
    `status` = VALUES(`status`);

-- 3) 初始化默认角色资源关联（管理员拥有所有资源）
-- 获取管理员角色ID
SET @role_admin := (SELECT id FROM sys_role WHERE code='ADMIN' LIMIT 1);

-- 如果还没有资源关联，给管理员分配所有资源
INSERT IGNORE INTO `sys_role_resource` (`role_id`, `resource_id`, `actions_json`, `create_time`)
SELECT @role_admin, id, '["*"]', @now
FROM `sys_resource`
WHERE `status` = 'enabled'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_resource`
      WHERE `role_id` = @role_admin AND `resource_id` = `sys_resource`.id
  );

SELECT 'role enhancement migration completed' AS msg;

SET FOREIGN_KEY_CHECKS = 1;
