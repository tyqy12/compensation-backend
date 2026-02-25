-- ============================================
-- 菜单资源初始化脚本
-- 用于初始化 sys_resource 表的菜单数据
-- ============================================

SET NAMES utf8mb4;

-- 清空现有菜单数据（可选，如果需要重新初始化）
-- DELETE FROM sys_resource WHERE type IN ('MENU', 'VIEW');

-- ============================================
-- 一级菜单
-- ============================================
INSERT INTO sys_resource (id, type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, deleted, version) VALUES
-- 工作台/首页
(1, 'VIEW', 'dashboard', '工作台', '/dashboard', 'dashboard/Dashboard', 'dashboard', NULL, 5, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 业务管理父菜单
(15, 'MENU', 'business', '业务管理', NULL, NULL, 'appstore', NULL, 10, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 系统管理父菜单
(17, 'MENU', 'admin', '系统管理', NULL, NULL, 'setting', NULL, 80, '{"roles": ["ADMIN"]}', 'enabled', NOW(), NOW(), 0, 0);

-- ============================================
-- 业务管理子菜单
-- ============================================
-- 员工管理
INSERT INTO sys_resource (id, type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, deleted, version) VALUES
(2, 'VIEW', 'employees', '员工管理', '/employees', 'employees/List', 'team', 15, 11, '{}', 'enabled', NOW(), NOW(), 0, 0);

-- ============================================
-- 系统管理子菜单
-- ============================================
INSERT INTO sys_resource (id, type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, deleted, version) VALUES
-- 用户绑定
(6, 'VIEW', 'admin.user-binding', '用户绑定', '/admin/user-binding', 'admin/UserBinding', 'user-switch', 17, 81, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 资源管理
(12, 'VIEW', 'admin.resources', '资源管理', '/admin/resources', 'admin/ResourceManager', 'permission', 17, 82, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 角色管理
(13, 'VIEW', 'admin.roles', '角色管理', '/admin/roles', 'admin/RoleManagement', 'safety-certificate', 17, 83, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 角色权限（旧版）
(18, 'VIEW', 'admin.role-auth', '角色授权', '/admin/role-auth', 'admin/RoleAuthorization', 'lock', 17, 84, '{}', 'enabled', NOW(), NOW(), 0, 0),
-- 用户权限
(14, 'VIEW', 'admin.user-auth', '用户权限', '/admin/users', 'admin/UserAuthorization', 'user', 17, 85, '{}', 'enabled', NOW(), NOW(), 0, 0);

SELECT id, type, code, name, path, parent_id, order_num, icon, status
FROM sys_resource
WHERE deleted = 0
ORDER BY COALESCE(parent_id, id), order_num;
