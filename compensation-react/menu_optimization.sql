-- ====================================
-- 菜单结构优化脚本
-- 添加父菜单并重新组织现有菜单项
-- ====================================

-- 1. 添加新的父菜单
INSERT INTO sys_resource (id, type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, deleted, version) VALUES
-- 业务管理父菜单
(15, 'MENU', 'business', '业务管理', NULL, NULL, 'appstore', NULL, 10, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 支付管理父菜单 (业务管理下的子菜单)
(16, 'MENU', 'payments', '支付管理', NULL, NULL, 'wallet', 15, 20, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 系统管理父菜单 (仅ADMIN角色可见)
(17, 'MENU', 'admin', '系统管理', NULL, NULL, 'setting', NULL, 80, '{"roles": ["ADMIN"]}', 'enabled', NOW(), NOW(), 0, 0),
-- 系统配置父菜单
(18, 'MENU', 'system', '系统配置', NULL, NULL, 'control', NULL, 90, '{}', 'enabled', NOW(), NOW(), 0, 0);

-- 2. 更新现有菜单项的层级关系和排序
-- 将员工管理移到业务管理下
UPDATE sys_resource SET parent_id = 15, order_num = 11 WHERE id = 2; -- employees -> business

-- 将支付批次移到支付管理下
UPDATE sys_resource SET parent_id = 16, order_num = 21 WHERE id = 4; -- payments.batches -> payments

-- 将管理功能移到系统管理下
UPDATE sys_resource SET parent_id = 17, order_num = 81 WHERE id = 6;  -- admin.user-binding -> admin
UPDATE sys_resource SET parent_id = 17, order_num = 82 WHERE id = 12; -- admin.resources.v2 -> admin
UPDATE sys_resource SET parent_id = 17, order_num = 83 WHERE id = 13; -- admin.role-auth -> admin
UPDATE sys_resource SET parent_id = 17, order_num = 84 WHERE id = 14; -- admin.user-auth -> admin

-- 将系统配置功能移到系统配置下
UPDATE sys_resource SET parent_id = 18, order_num = 91 WHERE id = 7; -- system.integration -> system
UPDATE sys_resource SET parent_id = 18, order_num = 92 WHERE id = 8; -- system.org-sync -> system

-- 3. 验证更新结果
SELECT
    r1.id,
    r1.type,
    r1.code,
    r1.name,
    r1.parent_id,
    r2.name as parent_name,
    r1.order_num,
    r1.icon,
    r1.props_json
FROM sys_resource r1
LEFT JOIN sys_resource r2 ON r1.parent_id = r2.id
WHERE r1.deleted = 0 AND r1.type = 'MENU'
ORDER BY
    COALESCE(r1.parent_id, r1.id),
    r1.order_num;