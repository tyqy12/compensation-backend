-- 补齐员工自助资料 API 对历史 role.* 角色编码的授权，避免老库升级后普通员工自助入口被资源过滤器拦截。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, JSON_ARRAY('*'), @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'api.employee.me.detail',
    'api.employee.me.contact-update',
    'api.employee.me.change-request-create',
    'api.employee.me.change-request-list'
)
WHERE role.code IN (
    'ADMIN', 'FINANCE', 'HR', 'MANAGER', 'EMPLOYEE',
    'role.admin.all', 'role.finance', 'role.hr', 'role.manager', 'role.employee'
)
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.code IN (
        'ADMIN', 'FINANCE', 'HR', 'MANAGER', 'EMPLOYEE',
        'role.admin.all', 'role.finance', 'role.hr', 'role.manager', 'role.employee'
    )
    AND COALESCE(user_role.deleted, 0) = 0
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
