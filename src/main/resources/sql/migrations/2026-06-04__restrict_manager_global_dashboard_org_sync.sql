-- 收紧负责人角色对全局工作台、组织同步写接口与组织同步管理读接口的访问。
-- 工作台返回全局员工、支付、绑定率与审计动态；组织同步接口会影响或暴露全局员工/部门与同步审计数据。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR')),
    update_time = @now
WHERE code IN (
    'dashboard',
    'api.dashboard.metrics',
    'api.dashboard.status',
    'api.dashboard.todos',
    'api.dashboard.activities'
);

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN')),
    update_time = @now
WHERE code IN (
    'api.system.org.fetch',
    'api.system.org.import',
    'api.system.org.sync',
    'api.system.org.sync-async'
);

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN')),
    update_time = @now
WHERE code IN (
    'api.system.org.check',
    'api.system.org.fetch-tree',
    'api.system.org.sync-task-detail',
    'api.system.org.history'
);

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN', 'FINANCE')),
    update_time = @now
WHERE code IN (
    'api.payroll.batches.ledger',
    'api.payroll.confirmations.assign',
    'api.payroll.distributions.list',
    'api.payroll.distributions.detail',
    'api.payroll.distributions.items',
    'api.payroll.distributions.reconciliation',
    'api.payroll.reconciliations.list',
    'api.payroll.reconciliations.detail'
);

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN', 'FINANCE', 'EMPLOYEE')),
    update_time = @now
WHERE code IN (
    'api.payroll.payslips.list',
    'api.payroll.payslips.detail',
    'api.payroll.payslips.download'
);

UPDATE sys_resource
SET props_json = JSON_SET(COALESCE(NULLIF(props_json, ''), JSON_OBJECT()),
                          '$.roles', JSON_ARRAY('ADMIN', 'FINANCE', 'HR')),
    update_time = @now
WHERE code = 'api.payroll.confirmations.summary';

DELETE rr
FROM sys_role_resource rr
JOIN sys_role role ON role.id = rr.role_id
JOIN sys_resource resource ON resource.id = rr.resource_id
WHERE role.code IN ('MANAGER', 'role.manager')
  AND resource.code IN (
      'dashboard',
      'api.dashboard.metrics',
      'api.dashboard.status',
      'api.dashboard.todos',
      'api.dashboard.activities',
      'api.system.org.fetch',
      'api.system.org.import',
      'api.system.org.sync',
      'api.system.org.sync-async',
      'api.system.org.check',
      'api.system.org.fetch-tree',
      'api.system.org.sync-task-detail',
      'api.system.org.history',
      'api.payroll.batches.ledger',
      'api.payroll.confirmations.summary',
      'api.payroll.confirmations.assign',
      'api.payroll.distributions.list',
      'api.payroll.distributions.detail',
      'api.payroll.distributions.items',
      'api.payroll.distributions.reconciliation',
      'api.payroll.reconciliations.list',
      'api.payroll.reconciliations.detail',
      'api.payroll.payslips.list',
      'api.payroll.payslips.detail',
      'api.payroll.payslips.download'
  );

DELETE rr
FROM sys_role_resource rr
JOIN sys_role role ON role.id = rr.role_id
JOIN sys_resource resource ON resource.id = rr.resource_id
WHERE role.code IN ('HR', 'EMPLOYEE', 'role.hr', 'role.employee')
  AND resource.code = 'api.payroll.confirmations.assign';

UPDATE sys_user u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM sys_user_role ur
    JOIN sys_role role ON role.id = ur.role_id
    WHERE role.code IN ('MANAGER', 'role.manager')
) affected ON affected.user_id = u.id
SET u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_time = @now;

COMMIT;
