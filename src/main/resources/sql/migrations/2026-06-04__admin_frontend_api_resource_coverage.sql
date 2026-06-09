-- 补齐后台管理前端实际调用的 API 资源定义
-- 目标：
-- 1) 避免 fail-closed 的 ApiResourceAuthorizationFilter 对已有接口返回“接口未配置访问权限”
-- 2) 修复同一路径多 HTTP 方法被重复 JSON key 压成单方法的问题

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @admin_users_parent_id := (SELECT id FROM sys_resource WHERE code='admin.users' LIMIT 1);
SET @admin_roles_parent_id := (SELECT id FROM sys_resource WHERE code='admin.roles' LIMIT 1);
SET @admin_resources_parent_id := (SELECT id FROM sys_resource WHERE code='admin.resources' LIMIT 1);
SET @admin_audit_parent_id := (SELECT id FROM sys_resource WHERE code='admin.audit' LIMIT 1);
SET @system_integration_parent_id := (SELECT id FROM sys_resource WHERE code='system.integration' LIMIT 1);
SET @employees_parent_id := (SELECT id FROM sys_resource WHERE code='employees' LIMIT 1);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.admin.user.roles.read','系统管理-查询用户角色','/api/admin/users/{id}/roles',NULL,NULL,@admin_users_parent_id,165,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.search','系统管理-用户聚合搜索','/api/admin/users/search',NULL,NULL,@admin_users_parent_id,166,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.platform-binding.detail','系统管理-用户平台绑定详情','/api/admin/users/{id}/platform-binding',NULL,NULL,@admin_users_parent_id,167,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.platform-binding.update','系统管理-绑定用户平台账号','/api/admin/users/{id}/platform-binding',NULL,NULL,@admin_users_parent_id,168,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.platform-binding.delete','系统管理-解绑用户平台账号','/api/admin/users/{id}/platform-binding',NULL,NULL,@admin_users_parent_id,169,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.bind-employee','系统管理-绑定用户员工','/api/admin/users/{id}/bind-employee/{employeeId}',NULL,NULL,@admin_users_parent_id,170,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.resources.read','系统管理-查询用户个性权限','/api/admin/users/{id}/resources',NULL,NULL,@admin_users_parent_id,171,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.resources.update','系统管理-更新用户个性权限','/api/admin/users/{id}/resources',NULL,NULL,@admin_users_parent_id,172,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.admin.user.aggregate-resources','系统管理-用户聚合权限','/api/admin/users/{id}/aggregate-resources',NULL,NULL,@admin_users_parent_id,173,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.effective-resources','系统管理-用户有效权限','/api/admin/users/{id}/effective-resources',NULL,NULL,@admin_users_parent_id,174,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user.effective-resource-details','系统管理-用户有效权限详情','/api/admin/users/{id}/effective-resource-details',NULL,NULL,@admin_users_parent_id,175,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.user-bindings.list','系统管理-用户绑定列表','/api/admin/user-bindings',NULL,NULL,@admin_users_parent_id,176,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.enabled','系统管理-启用角色列表','/api/admin/roles/enabled',NULL,NULL,@admin_roles_parent_id,174,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.disable','系统管理-禁用角色','/api/admin/roles/{id}/disable',NULL,NULL,@admin_roles_parent_id,175,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.enable','系统管理-启用角色','/api/admin/roles/{id}/enable',NULL,NULL,@admin_roles_parent_id,176,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.copy','系统管理-复制角色','/api/admin/roles/{id}/copy',NULL,NULL,@admin_roles_parent_id,177,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.role.permissions.read','系统管理-查询角色权限','/api/admin/roles/{id}/permissions',NULL,NULL,@admin_roles_parent_id,178,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.role.permissions.revoke','系统管理-撤销角色权限','/api/admin/roles/{id}/permissions',NULL,NULL,@admin_roles_parent_id,180,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.resources.get','系统管理-查询角色兼容资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@admin_roles_parent_id,181,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.resources.assign','系统管理-分配角色兼容资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@admin_roles_parent_id,182,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.roles.resources.revoke','系统管理-撤销角色兼容资源权限','/api/admin/roles/{id}/resources',NULL,NULL,@admin_roles_parent_id,183,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.resource.import','系统管理-导入资源','/api/admin/resources/v2/import',NULL,NULL,@admin_resources_parent_id,186,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.resource.export','系统管理-导出资源','/api/admin/resources/v2/export',NULL,NULL,@admin_resources_parent_id,187,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.today-login','审计日志-今日登录统计','/api/admin/audit-logs/stats/today-login',NULL,NULL,@admin_audit_parent_id,211,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.summary','审计日志-统计摘要','/api/admin/audit-logs/stats/summary',NULL,NULL,@admin_audit_parent_id,212,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.operations','审计日志-操作统计','/api/admin/audit-logs/stats/operations',NULL,NULL,@admin_audit_parent_id,213,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.login-failures','审计日志-登录失败计数','/api/admin/audit-logs/security/login-failures',NULL,NULL,@admin_audit_parent_id,214,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.login-failures.clear','审计日志-清除登录失败计数','/api/admin/audit-logs/security/login-failures/{username}',NULL,NULL,@admin_audit_parent_id,215,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.list','集成配置-列表','/api/admin/integration-configs',NULL,NULL,@system_integration_parent_id,220,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.detail','集成配置-详情','/api/admin/integration-configs/{platformType}',NULL,NULL,@system_integration_parent_id,221,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.update','集成配置-保存','/api/admin/integration-configs/{platformType}',NULL,NULL,@system_integration_parent_id,222,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.delete','集成配置-禁用','/api/admin/integration-configs/{platformType}',NULL,NULL,@system_integration_parent_id,223,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.test-connection','集成配置-连接测试','/api/admin/integration-configs/{platformType}/test-connection',NULL,NULL,@system_integration_parent_id,225,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.employee.offline','员工管理-设置架构外标记','/api/admin/employees/{id}/offline',NULL,NULL,@employees_parent_id,230,JSON_OBJECT('method','PATCH','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.employee.manager','员工管理-指定架构外负责人','/api/admin/employees/{id}/manager',NULL,NULL,@employees_parent_id,231,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),
  `path`=VALUES(`path`),
  `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`),
  `props_json`=VALUES(`props_json`),
  `status`='enabled',
  `update_time`=@now;

UPDATE sys_resource
SET `name`='系统管理-分配角色权限',
    `path`='/api/admin/roles/{id}/permissions',
    `parent_id`=@admin_roles_parent_id,
    `order_num`=179,
    `props_json`=JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),
    `status`='enabled',
    `update_time`=@now
WHERE `code`='api.admin.role.permissions';

UPDATE sys_user u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM sys_user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE r.code IN ('ADMIN', 'role.admin.all')
) t ON t.user_id = u.id
SET u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_time = @now;

COMMIT;
