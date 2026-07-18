-- 为应用数据范围管理接口补齐 RBAC 资源。
SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @admin_parent_id := (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.admin.app-registry.data-grants.list','开放应用-数据范围列表','/api/admin/app-registry/{id}/data-grants',NULL,NULL,@admin_parent_id,194,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.data-grants.create','开放应用-授权数据范围','/api/admin/app-registry/{id}/data-grants',NULL,NULL,@admin_parent_id,195,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.data-grants.revoke','开放应用-撤销数据范围','/api/admin/app-registry/{id}/data-grants/{grantId}',NULL,NULL,@admin_parent_id,196,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, JSON_ARRAY('*'), @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
  'api.admin.app-registry.data-grants.list',
  'api.admin.app-registry.data-grants.create',
  'api.admin.app-registry.data-grants.revoke'
)
WHERE role.code IN ('ADMIN', 'role.admin.all')
ON DUPLICATE KEY UPDATE `actions_json`=VALUES(`actions_json`),`deleted`=0,`update_time`=@now;

COMMIT;
