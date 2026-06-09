-- 补齐员工复数别名与薪酬报表 API 资源，避免 fail-closed 过滤器拦截非管理员实际业务请求。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @employees_parent_id := (SELECT id FROM sys_resource WHERE code = 'employees' LIMIT 1);
SET @payroll_report_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'menu.payroll.reports' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1)
);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employees.list','员工管理-列表查询(复数路径)','/api/employees',NULL,NULL,@employees_parent_id,200,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.detail','员工管理-详情(复数路径)','/api/employees/{id}',NULL,NULL,@employees_parent_id,201,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.create','员工管理-创建(复数路径)','/api/employees',NULL,NULL,@employees_parent_id,202,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employees.update','员工管理-更新(复数路径)','/api/employees/{id}',NULL,NULL,@employees_parent_id,203,JSON_OBJECT('method','PUT'),'enabled',@now,@now),
('API','api.employees.status','员工管理-状态更新(复数路径)','/api/employees/{id}/status',NULL,NULL,@employees_parent_id,204,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employees.bind-platform','员工管理-绑定平台(复数路径)','/api/employees/{id}/bind-platform',NULL,NULL,@employees_parent_id,205,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.unbind-platform','员工管理-解绑平台','/api/employee/{id}/unbind-platform',NULL,NULL,@employees_parent_id,206,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.unbind-platform','员工管理-解绑平台(复数路径)','/api/employees/{id}/unbind-platform',NULL,NULL,@employees_parent_id,207,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.offline-list','员工管理-架构外员工列表(复数路径)','/api/employees/offline',NULL,NULL,@employees_parent_id,208,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.resigned-list','员工管理-离职员工列表(复数路径)','/api/employees/resigned',NULL,NULL,@employees_parent_id,209,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.batch-import','员工管理-批量导入(复数路径)','/api/employees/batch-import',NULL,NULL,@employees_parent_id,210,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employees.approvals','员工管理-审批记录(复数路径)','/api/employees/{id}/approvals',NULL,NULL,@employees_parent_id,211,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.payslips','员工管理-发薪记录(复数路径)','/api/employees/{id}/payslips',NULL,NULL,@employees_parent_id,212,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.payments','员工管理-支付记录(复数路径)','/api/employees/{id}/payments',NULL,NULL,@employees_parent_id,213,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.decrypt-id-card','员工管理-身份证解密(复数路径)','/api/employees/{id}/id-card',NULL,NULL,@employees_parent_id,214,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.decrypt-bank','员工管理-银行卡解密(复数路径)','/api/employees/{id}/bank-account',NULL,NULL,@employees_parent_id,215,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.decrypt-settlement','员工管理-收款账户解密(复数路径)','/api/employees/{id}/settlement-account',NULL,NULL,@employees_parent_id,216,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.payroll.reports.basic','薪酬报表-基础报表','/api/payroll/reports/basic',NULL,NULL,@payroll_report_parent_id,249,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.reports.basic-export','薪酬报表-基础报表导出','/api/payroll/reports/basic/export',NULL,NULL,@payroll_report_parent_id,250,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `props_json` = VALUES(`props_json`),
  `status` = 'enabled',
  `update_time` = @now;

-- 复数路径别名沿用单数路径的角色资源授权，兼容现网可能已经微调过的员工权限。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT rr.role_id, alias_resource.id, rr.actions_json, @now, @now
FROM sys_role_resource rr
JOIN sys_resource source_resource ON source_resource.id = rr.resource_id
JOIN sys_resource alias_resource ON alias_resource.code = CASE source_resource.code
    WHEN 'api.employee.list' THEN 'api.employees.list'
    WHEN 'api.employee.detail' THEN 'api.employees.detail'
    WHEN 'api.employee.create' THEN 'api.employees.create'
    WHEN 'api.employee.update' THEN 'api.employees.update'
    WHEN 'api.employee.status' THEN 'api.employees.status'
    WHEN 'api.employee.bind-platform' THEN 'api.employees.bind-platform'
    WHEN 'api.employee.offline-list' THEN 'api.employees.offline-list'
    WHEN 'api.employee.resigned-list' THEN 'api.employees.resigned-list'
    WHEN 'api.employee.batch-import' THEN 'api.employees.batch-import'
    WHEN 'api.employee.approvals' THEN 'api.employees.approvals'
    WHEN 'api.employee.payslips' THEN 'api.employees.payslips'
    WHEN 'api.employee.payments' THEN 'api.employees.payments'
    WHEN 'api.employee.decrypt-id-card' THEN 'api.employees.decrypt-id-card'
    WHEN 'api.employee.decrypt-bank' THEN 'api.employees.decrypt-bank'
    WHEN 'api.employee.decrypt-settlement' THEN 'api.employees.decrypt-settlement'
END
WHERE source_resource.code IN (
    'api.employee.list',
    'api.employee.detail',
    'api.employee.create',
    'api.employee.update',
    'api.employee.status',
    'api.employee.bind-platform',
    'api.employee.offline-list',
    'api.employee.resigned-list',
    'api.employee.batch-import',
    'api.employee.approvals',
    'api.employee.payslips',
    'api.employee.payments',
    'api.employee.decrypt-id-card',
    'api.employee.decrypt-bank',
    'api.employee.decrypt-settlement'
)
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

-- 解绑接口是管理员专属；单数路径之前也缺资源定义，复数别名一起补齐授权。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, JSON_ARRAY('*'), @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN ('api.employee.unbind-platform', 'api.employees.unbind-platform')
WHERE role.code IN ('ADMIN', 'role.admin.all')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

-- 薪酬基础报表控制器边界为财务/管理员。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id,
       resource.id,
       CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
       @now,
       @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN ('api.payroll.reports.basic', 'api.payroll.reports.basic-export')
WHERE role.code IN ('ADMIN', 'FINANCE', 'role.admin.all', 'role.finance')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.code IN (
        'ADMIN', 'FINANCE', 'HR', 'MANAGER',
        'role.admin.all', 'role.finance', 'role.hr', 'role.manager'
    )
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
