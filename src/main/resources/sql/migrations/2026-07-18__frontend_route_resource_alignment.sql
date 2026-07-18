-- Frontend route/resource alignment.
-- The application runner applies the same idempotent reconciliation during production startup.

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @payroll_menu := (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' AND deleted = 0 LIMIT 1);
SET @employees_menu := (SELECT id FROM sys_resource WHERE code = 'employees' AND deleted = 0 LIMIT 1);

-- Canonical pages which are statically registered in compensation-react.
INSERT INTO sys_resource
    (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, create_by, update_by, deleted, version)
VALUES
    ('VIEW', 'view.employees.me', '员工自助资料', '/employees/me', 'employees/Profile', NULL, @employees_menu, 30,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'EMPLOYEE'), 'hidden', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.batch.entry', '批次录入', '/payroll/batches/:batchId/entry', 'payroll/Entry', NULL, @payroll_menu, 32,
        JSON_OBJECT('hidden', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.compliance', '合规核算', '/payroll/compliance', 'payroll/Compliance', 'safety-certificate', @payroll_menu, 34,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.rules', '规则管理', '/payroll/rules', 'payroll/Rules', 'file-protect', @payroll_menu, 35,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.calendar', '薪酬日历', '/payroll/calendar', 'payroll/Calendar', 'calendar', @payroll_menu, 36,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.confirmations', '确认工作台', '/payroll/confirmations', 'payroll/Confirmations', 'check-circle', @payroll_menu, 38,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR', 'MANAGER', 'EMPLOYEE'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.distributions', '薪酬发放', '/payroll/distributions', 'payroll/Distributions', 'bank', @payroll_menu, 39,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.reconciliations', '薪酬对账', '/payroll/reconciliations', 'payroll/Reconciliations', 'audit', @payroll_menu, 40,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.batch.ledger', '薪酬批次台账', '/payroll/batches/:batchId/ledger', 'payroll/BatchLedger', NULL, @payroll_menu, 42,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'MANAGER'), 'hidden', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.batch.manager', '经理核对', '/payroll/batches/:batchId/manager-review', 'payroll/ManagerReview', NULL, @payroll_menu, 43,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'MANAGER'), 'hidden', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('VIEW', 'view.payroll.pt-readonly', '兼职薪酬查询', '/payroll/pt-readonly', 'payroll/PartTimeReadonly', 'search', @payroll_menu, 44,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('MENU', 'admin.user-binding', '用户绑定', '/admin/user-binding', 'admin/UserBinding', 'user',
        (SELECT id FROM sys_resource WHERE code = 'admin' AND deleted = 0 LIMIT 1), 81,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0),
    ('MENU', 'admin.app-registry', '外部应用注册', '/admin/app-registry', 'admin/AppRegistry', 'appstore',
        (SELECT id FROM sys_resource WHERE code = 'admin' AND deleted = 0 LIMIT 1), 84,
        JSON_OBJECT('roles', JSON_ARRAY('ADMIN'), 'keepAlive', TRUE), 'enabled', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0)
ON DUPLICATE KEY UPDATE
    type = VALUES(type), name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon),
    parent_id = VALUES(parent_id), order_num = VALUES(order_num), props_json = VALUES(props_json), status = 'enabled',
    update_by = 'frontend_route_alignment', update_time = @now, deleted = 0;

-- Ensure duplicate/retired resources cannot win route matching or reappear in navigation.
UPDATE sys_resource
SET status = 'disabled', update_by = 'frontend_route_alignment', update_time = @now
WHERE code IN (
    'business', 'admin.users', 'admin.resources', 'view.payroll.batches', 'view.payroll.cycles', 'view.payroll.templates',
    'menu.payroll.import', 'menu.payroll.reports', 'approval.workflow.detail'
)
AND deleted = 0;

UPDATE sys_resource
SET name = '薪酬运营', component = 'payroll/Operations', path = '/payroll/batches', parent_id = @payroll_menu,
    order_num = 31, props_json = JSON_OBJECT('roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR', 'MANAGER'), 'keepAlive', TRUE),
    status = 'enabled', update_by = 'frontend_route_alignment', update_time = @now
WHERE code = 'menu.payroll.batches' AND deleted = 0;

-- Remove grants to disabled resources and restore the current role-to-page grants.
UPDATE sys_role_resource rr
JOIN sys_resource r ON r.id = rr.resource_id
SET rr.deleted = 1, rr.update_by = 'frontend_route_alignment', rr.update_time = @now
WHERE rr.deleted = 0 AND r.status = 'disabled' AND r.code IN (
    'business', 'admin.users', 'admin.resources', 'view.payroll.batches', 'view.payroll.cycles', 'view.payroll.templates',
    'menu.payroll.import', 'menu.payroll.reports', 'approval.workflow.detail'
);

UPDATE sys_user_resource ur
JOIN sys_resource r ON r.id = ur.resource_id
SET ur.deleted = 1, ur.update_by = 'frontend_route_alignment', ur.update_time = @now
WHERE ur.deleted = 0 AND r.status = 'disabled' AND r.code IN (
    'business', 'admin.users', 'admin.resources', 'view.payroll.batches', 'view.payroll.cycles', 'view.payroll.templates',
    'menu.payroll.import', 'menu.payroll.reports', 'approval.workflow.detail'
);

INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT role.id, resource.id, '["*"]', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.status = 'enabled' AND resource.deleted = 0
WHERE role.code = 'ADMIN' AND role.deleted = 0 AND resource.type IN ('MENU', 'VIEW')
ON DUPLICATE KEY UPDATE deleted = 0, update_by = 'frontend_route_alignment', update_time = @now;

INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT role.id, resource.id, '["*"]', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'dashboard', 'employees', 'payments', 'payments.batches', 'menu.approval', 'menu.system.payroll', 'menu.payroll.batches',
    'view.payroll.batch.entry', 'view.payroll.compliance', 'view.payroll.rules', 'view.payroll.calendar',
    'view.payroll.confirmations', 'view.payroll.distributions', 'view.payroll.reconciliations',
    'view.payroll.batch.ledger', 'view.payroll.batch.manager', 'view.payroll.pt-readonly'
) AND resource.status = 'enabled' AND resource.deleted = 0
WHERE role.code = 'FINANCE' AND role.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, update_by = 'frontend_route_alignment', update_time = @now;

INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT role.id, resource.id, '["*"]', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'dashboard', 'employees', 'menu.system.payroll', 'menu.payroll.batches', 'view.payroll.batch.entry',
    'view.payroll.rules', 'view.payroll.calendar', 'view.payroll.confirmations'
) AND resource.status = 'enabled' AND resource.deleted = 0
WHERE role.code = 'HR' AND role.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, update_by = 'frontend_route_alignment', update_time = @now;

INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT role.id, resource.id, '["*"]', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'menu.approval', 'menu.system.payroll', 'menu.payroll.batches', 'view.payroll.confirmations',
    'view.payroll.batch.ledger', 'view.payroll.batch.manager'
) AND resource.status = 'enabled' AND resource.deleted = 0
WHERE role.code = 'MANAGER' AND role.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, update_by = 'frontend_route_alignment', update_time = @now;

INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT role.id, resource.id, '["*"]', @now, @now, 'frontend_route_alignment', 'frontend_route_alignment', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.code IN ('view.employees.me', 'view.payroll.confirmations')
    AND resource.status = 'enabled' AND resource.deleted = 0
WHERE role.code = 'EMPLOYEE' AND role.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, update_by = 'frontend_route_alignment', update_time = @now;

UPDATE sys_user SET permission_version = COALESCE(permission_version, 0) + 1,
    update_by = 'frontend_route_alignment', update_time = @now
WHERE status = 'active';

COMMIT;
