-- 补齐薪酬发放单失败重试接口的 RBAC 资源与角色授权。
-- 已有数据库需要迁移；本脚本只更新权限数据，不调用业务接口。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @menu_payroll_id := (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1);

INSERT INTO sys_resource
    (`type`, `code`, `name`, `path`, `component`, `icon`, `parent_id`, `order_num`, `props_json`, `status`, `create_time`, `update_time`)
VALUES
    ('API', 'api.payroll.distributions.retry', '薪酬发放单-失败重试',
     '/api/payroll/distributions/*/retry', NULL, NULL, @menu_payroll_id, 251,
     JSON_OBJECT('method', 'POST', 'roles', JSON_ARRAY('ADMIN', 'FINANCE')),
     'enabled', @now, @now)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `path` = VALUES(`path`),
    `parent_id` = VALUES(`parent_id`),
    `order_num` = VALUES(`order_num`),
    `props_json` = VALUES(`props_json`),
    `status` = 'enabled',
    `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`, `resource_id`, `actions_json`, `create_time`, `update_time`)
SELECT role.id,
       resource.id,
       CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
       @now,
       @now
FROM sys_role role
JOIN sys_resource resource ON resource.code = 'api.payroll.distributions.retry'
WHERE role.code IN ('ADMIN', 'FINANCE', 'role.admin.all', 'role.finance')
ON DUPLICATE KEY UPDATE
    `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
    `update_time` = @now;

COMMIT;
