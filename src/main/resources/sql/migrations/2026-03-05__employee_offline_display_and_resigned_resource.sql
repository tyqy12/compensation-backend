-- 架构外员工展示命名优化 + 离职员工列表资源
SET @now = NOW();
SET @menu_employees = (SELECT id FROM sys_resource WHERE code = 'employees' LIMIT 1);

-- 将“离线列表”展示名统一为“架构外员工列表”（不改 code/path，保持兼容）
UPDATE sys_resource
SET name = '员工管理-架构外员工列表',
    update_time = @now
WHERE code = 'api.employee.offline-list';

-- 新增离职员工列表资源
INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.employee.resigned-list','员工管理-离职员工列表','/api/employee/resigned',
    NULL,NULL,@menu_employees,165,
    JSON_OBJECT('method','GET'),'enabled',@now,@now
WHERE @menu_employees IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

-- 为员工管理相关角色授权“架构外员工列表/离职员工列表”两个API资源
INSERT INTO `sys_role_resource`(`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT
    r.id,
    res.id,
    CASE WHEN r.code = 'ADMIN' THEN JSON_ARRAY('*') ELSE NULL END,
    @now,
    @now
FROM `sys_role` r
JOIN `sys_resource` res ON res.code IN ('api.employee.offline-list', 'api.employee.resigned-list')
WHERE r.code IN ('role.admin.all', 'role.finance', 'role.hr', 'role.manager', 'ADMIN', 'MANAGER', 'FINANCE', 'HR')
ON DUPLICATE KEY UPDATE
    `actions_json`=COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
    `update_time`=@now;
