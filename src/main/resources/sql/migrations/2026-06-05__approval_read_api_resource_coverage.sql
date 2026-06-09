-- 补齐审批查询接口资源，避免非管理员审批人访问待办、详情、步骤时被 fail-closed 过滤器拦截。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @approval_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1)
);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.approval.workflow.list','审批-列表查询','/api/approval/workflows',NULL,NULL,@approval_parent_id,193,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.pending','审批-待我审批','/api/approval/workflows/pending',NULL,NULL,@approval_parent_id,194,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.my','审批-我发起的','/api/approval/workflows/my',NULL,NULL,@approval_parent_id,195,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.detail','审批-详情','/api/approval/workflows/{id}',NULL,NULL,@approval_parent_id,196,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.steps','审批-步骤列表','/api/approval/workflows/{id}/steps',NULL,NULL,@approval_parent_id,197,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `props_json` = VALUES(`props_json`),
  `status` = 'enabled',
  `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END, @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'api.approval.workflow.list',
    'api.approval.workflow.pending',
    'api.approval.workflow.my',
    'api.approval.workflow.detail',
    'api.approval.workflow.steps'
)
WHERE role.code IN ('ADMIN', 'FINANCE', 'MANAGER', 'role.admin.all', 'role.finance', 'role.manager')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, NULL, @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'api.approval.workflow.list',
    'api.approval.workflow.pending',
    'api.approval.workflow.my',
    'api.approval.workflow.detail',
    'api.approval.workflow.steps'
)
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
