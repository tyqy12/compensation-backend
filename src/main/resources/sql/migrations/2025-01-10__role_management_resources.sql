-- ============================================
-- 角色管理资源初始化迁移脚本
-- 用于添加角色管理相关的菜单和API资源
-- Usage: mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-01-10__role_management_resources.sql
-- ============================================

SET NAMES utf8mb4;
SET @now := NOW();

-- 确保系统管理父菜单存在
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','admin','系统管理',NULL,NULL,'setting',NULL,80,JSON_OBJECT('roles', JSON_ARRAY('ADMIN'), 'keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 获取系统管理菜单ID
SET @menu_admin := (SELECT id FROM sys_resource WHERE code='admin' LIMIT 1);

-- ============================================
-- 角色管理相关资源
-- ============================================

-- 角色管理页面 (component 路径不带 pages/ 前缀，因为 DynamicPageRenderer 会自动添加)
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('VIEW','admin.roles','角色管理','/admin/roles','admin/RoleManagement','safety-certificate',@menu_admin,10,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 获取角色管理菜单ID
SET @menu_admin_roles := (SELECT id FROM sys_resource WHERE code='admin.roles' LIMIT 1);

-- 角色管理 API 资源
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
-- 角色 CRUD
('API','api.admin.roles.list','角色管理-列表','/api/admin/roles',NULL,NULL,@menu_admin_roles,10,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.enabled','角色管理-启用的角色','/api/admin/roles/enabled',NULL,NULL,@menu_admin_roles,15,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.detail','角色管理-详情','/api/admin/roles/{id}',NULL,NULL,@menu_admin_roles,20,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.create','角色管理-创建','/api/admin/roles',NULL,NULL,@menu_admin_roles,30,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.update','角色管理-更新','/api/admin/roles/{id}',NULL,NULL,@menu_admin_roles,40,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.delete','角色管理-删除','/api/admin/roles/{id}',NULL,NULL,@menu_admin_roles,50,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.disable','角色管理-禁用','/api/admin/roles/{id}/disable',NULL,NULL,@menu_admin_roles,60,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.enable','角色管理-启用','/api/admin/roles/{id}/enable',NULL,NULL,@menu_admin_roles,70,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.copy','角色管理-复制','/api/admin/roles/{id}/copy',NULL,NULL,@menu_admin_roles,80,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
-- 权限分配
('API','api.admin.roles.resources.get','角色管理-获取资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@menu_admin_roles,90,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.resources.assign','角色管理-分配资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@menu_admin_roles,100,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.resources.revoke','角色管理-撤销资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@menu_admin_roles,110,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 确认
SELECT 'role management resources migration completed' AS msg;
SELECT id, type, code, name, path, parent_id, order_num, icon, status
FROM sys_resource
WHERE code IN ('admin', 'admin.roles', 'api.admin.roles.list')
ORDER BY id;
