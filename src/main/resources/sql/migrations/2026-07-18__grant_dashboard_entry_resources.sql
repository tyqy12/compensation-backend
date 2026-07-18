-- 补齐财务和人力角色的工作台入口及数据接口授权。
-- 工作台资源元数据允许 ADMIN/FINANCE/HR，但历史角色授权只保留了 ADMIN 的页面入口。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

INSERT INTO sys_role_resource (`role_id`, `resource_id`, `actions_json`, `create_time`, `update_time`)
SELECT role.id,
       resource.id,
       NULL,
       @now,
       @now
FROM sys_role role
JOIN sys_resource resource
  ON resource.code IN (
      'dashboard',
      'api.dashboard.metrics',
      'api.dashboard.status',
      'api.dashboard.todos',
      'api.dashboard.activities'
  )
 AND resource.status = 'enabled'
WHERE role.code IN ('FINANCE', 'HR', 'role.finance', 'role.hr')
ON DUPLICATE KEY UPDATE
    `deleted` = 0,
    `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
    `update_time` = VALUES(`update_time`);

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE user_role.deleted = 0
      AND role.deleted = 0
      AND role.code IN ('FINANCE', 'HR', 'role.finance', 'role.hr')
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
