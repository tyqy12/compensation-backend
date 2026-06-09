-- 补齐审批撤销接口资源，避免 /approval fail-closed 过滤器拦截发起人撤销流程。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @approval_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1)
);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.approval.workflow.cancel','审批-撤销','/api/approval/workflows/{id}/cancel',NULL,NULL,@approval_parent_id,200,JSON_OBJECT('method','POST'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),
  `path`=VALUES(`path`),
  `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`),
  `props_json`=VALUES(`props_json`),
  `status`='enabled',
  `update_time`=@now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END, @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code = 'api.approval.workflow.cancel'
WHERE role.code IN ('ADMIN', 'FINANCE', 'MANAGER', 'role.admin.all', 'role.finance', 'role.manager')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, NULL, @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code = 'api.approval.workflow.cancel'
WHERE role.code IN ('HR', 'EMPLOYEE', 'role.hr', 'role.employee')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.code IN (
        'ADMIN', 'FINANCE', 'MANAGER', 'HR', 'EMPLOYEE',
        'role.admin.all', 'role.finance', 'role.manager', 'role.hr', 'role.employee'
    )
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
