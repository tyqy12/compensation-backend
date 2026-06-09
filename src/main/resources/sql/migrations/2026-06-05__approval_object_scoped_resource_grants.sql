-- 审批工作流接口按对象级权限控制，避免运行时审批人角色被静态资源角色挡住。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @approval_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1)
);

UPDATE sys_resource
SET props_json = JSON_REMOVE(COALESCE(props_json, JSON_OBJECT()), '$.roles'),
    parent_id = COALESCE(@approval_parent_id, parent_id),
    update_time = @now
WHERE code IN (
    'api.approval.workflow.list',
    'api.approval.workflow.pending',
    'api.approval.workflow.my',
    'api.approval.workflow.detail',
    'api.approval.workflow.steps',
    'api.approval.workflow.approve',
    'api.approval.workflow.reject',
    'api.approval.workflow.cancel'
);

UPDATE sys_resource
SET props_json = JSON_REMOVE(COALESCE(props_json, JSON_OBJECT()), '$.roles'),
    update_time = @now
WHERE code = 'menu.approval';

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id,
       resource.id,
       CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
       @now,
       @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'menu.approval',
    'approval.workflow.detail',
    'api.approval.workflow.list',
    'api.approval.workflow.pending',
    'api.approval.workflow.my',
    'api.approval.workflow.detail',
    'api.approval.workflow.steps',
    'api.approval.workflow.approve',
    'api.approval.workflow.reject',
    'api.approval.workflow.cancel'
)
WHERE role.status = 'enabled'
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.status = 'enabled'
    AND COALESCE(user_role.deleted, 0) = 0
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
