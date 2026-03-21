-- 发薪确认工作台 API 角色授权补齐（修复点击工作台 403）
SET @now = NOW();

-- 确认流程相关 API 资源授权给业务角色（兼容新旧角色编码）
INSERT INTO `sys_role_resource`(`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT
    r.id,
    res.id,
    CASE WHEN r.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
    @now,
    @now
FROM `sys_role` r
JOIN `sys_resource` res ON res.code IN (
    'api.payroll.confirmations.pending',
    'api.payroll.confirmations.summary',
    'api.payroll.confirmations.confirm',
    'api.payroll.confirmations.object',
    'api.payroll.confirmations.batch-confirm',
    'api.payroll.confirmations.assign'
)
WHERE r.code IN (
    'ADMIN', 'FINANCE', 'HR', 'MANAGER', 'EMPLOYEE',
    'role.admin.all', 'role.finance', 'role.hr', 'role.manager', 'role.employee'
)
ON DUPLICATE KEY UPDATE
    `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
    `update_time` = @now;

-- 触发权限缓存失效：提升相关用户 permission_version
UPDATE `sys_user` u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM `sys_user_role` ur
    JOIN `sys_role` r ON r.id = ur.role_id
    WHERE r.code IN (
        'ADMIN', 'FINANCE', 'HR', 'MANAGER', 'EMPLOYEE',
        'role.admin.all', 'role.finance', 'role.hr', 'role.manager', 'role.employee'
    )
) t ON t.user_id = u.id
SET
    u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_time = @now;
