-- 发薪确认工作台资源补齐（VIEW + API + 角色授权）
-- 目的：修复“点击确认工作台跳转 403”以及确认接口权限缺失问题

SET NAMES utf8mb4;
SET @now := NOW();

SET @menu_payroll_id := (
    SELECT id
    FROM sys_resource
    WHERE code = 'menu.system.payroll'
    LIMIT 1
);

-- 1) 注册确认工作台 VIEW（用于前端路由守卫）
INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'VIEW','view.payroll.confirmations','确认工作台','/payroll/confirmations',
    'payroll/Confirmations','CheckCircle',@menu_payroll_id,38,
    JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `component`=VALUES(`component`),
    `icon`=VALUES(`icon`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

-- 2) 注册确认流程 API 资源
INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.pending','待确认列表','/api/payroll/confirmations/pending',
    NULL,NULL,@menu_payroll_id,210,JSON_OBJECT('method','GET'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.summary','确认汇总','/api/payroll/confirmations/batches/*/summary',
    NULL,NULL,@menu_payroll_id,211,JSON_OBJECT('method','GET'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.confirm','工资条确认','/api/payroll/confirmations/payslips/*/confirm',
    NULL,NULL,@menu_payroll_id,212,JSON_OBJECT('method','POST'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.object','工资条异议','/api/payroll/confirmations/payslips/*/object',
    NULL,NULL,@menu_payroll_id,213,JSON_OBJECT('method','POST'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.batch-confirm','负责人批量确认','/api/payroll/confirmations/batches/*/batch-confirm',
    NULL,NULL,@menu_payroll_id,214,JSON_OBJECT('method','POST'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

INSERT INTO `sys_resource`(
    `type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,
    `props_json`,`status`,`create_time`,`update_time`
)
SELECT
    'API','api.payroll.confirmations.assign','分配确认负责人','/api/payroll/confirmations/batches/*/assign',
    NULL,NULL,@menu_payroll_id,215,JSON_OBJECT('method','POST'),'enabled',@now,@now
WHERE @menu_payroll_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),
    `path`=VALUES(`path`),
    `parent_id`=VALUES(`parent_id`),
    `order_num`=VALUES(`order_num`),
    `props_json`=VALUES(`props_json`),
    `status`='enabled',
    `update_time`=@now;

-- 3) 为业务角色授予确认工作台 VIEW 与 API 资源
INSERT INTO `sys_role_resource`(`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT
    r.id,
    res.id,
    CASE WHEN r.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
    @now,
    @now
FROM `sys_role` r
JOIN `sys_resource` res ON res.code IN (
    'view.payroll.confirmations',
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

-- 4) 触发权限缓存失效：提升相关用户 permission_version
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
