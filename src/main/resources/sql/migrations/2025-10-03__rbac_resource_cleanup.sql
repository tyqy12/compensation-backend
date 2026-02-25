-- RBAC resource hierarchy cleanup and API coverage completion
-- Usage: mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-10-03__rbac_resource_cleanup.sql

SET NAMES utf8mb4;
SET @now := NOW();

-- Ensure core menu structure exists
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','dashboard','工作台','/','dashboard/Dashboard','dashboard',NULL,1,JSON_OBJECT('keepAlive', TRUE, 'affix', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','employees','员工管理','/employees','employees/List','team',NULL,10,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','business','业务管理',NULL,NULL,'appstore',NULL,20,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments','支付管理',NULL,NULL,'wallet',(SELECT id FROM sys_resource WHERE code='business' LIMIT 1),20,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments.batches','支付批次','/payments/batches','payments/Batches','wallet',(SELECT id FROM sys_resource WHERE code='payments' LIMIT 1),10,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('VIEW','payments.batch.detail','批次详情','/payments/batches/:batchNo','payments/BatchDetail',NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),20,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments.api','支付管理-接口',NULL,NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments' LIMIT 1),95,JSON_OBJECT('hidden', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','admin','系统管理',NULL,NULL,'setting',NULL,80,JSON_OBJECT('roles', JSON_ARRAY('ADMIN'), 'keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system','系统配置',NULL,NULL,'control',NULL,90,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system.integration','集成配置','/system/integration','system/IntegrationConfig','global',(SELECT id FROM sys_resource WHERE code='system' LIMIT 1),10,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system.org-sync','组织同步','/system/org-sync','system/OrgSync','sync',(SELECT id FROM sys_resource WHERE code='system' LIMIT 1),20,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('VIEW','employees.detail','员工详情','/employees/:id','employees/Detail',NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),20,JSON_OBJECT('keepAlive', TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Refresh key IDs
SET @menu_dashboard := (SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1);
SET @menu_employees := (SELECT id FROM sys_resource WHERE code='employees' LIMIT 1);
SET @menu_business  := (SELECT id FROM sys_resource WHERE code='business' LIMIT 1);
SET @menu_payments  := (SELECT id FROM sys_resource WHERE code='payments' LIMIT 1);
SET @menu_payments_api := (SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1);
SET @menu_payments_batches := (SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1);
SET @menu_system := (SELECT id FROM sys_resource WHERE code='system' LIMIT 1);
SET @menu_system_integration := (SELECT id FROM sys_resource WHERE code='system.integration' LIMIT 1);
SET @menu_system_org_sync := (SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1);
SET @menu_payroll := (SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1);

-- Adjust parent relationships
UPDATE sys_resource
SET parent_id = @menu_employees,
    order_num = 20,
    update_by = 'migration',
    update_time = @now
WHERE code = 'employees.detail';

UPDATE sys_resource
SET parent_id = @menu_payments,
    order_num = 10,
    props_json = JSON_OBJECT('keepAlive', TRUE),
    update_by = 'migration',
    update_time = @now
WHERE code = 'payments.batches';

UPDATE sys_resource
SET parent_id = @menu_payments_batches,
    order_num = 20,
    update_by = 'migration',
    update_time = @now
WHERE code = 'payments.batch.detail';

UPDATE sys_resource
SET parent_id = @menu_system,
    order_num = 10,
    update_by = 'migration',
    update_time = @now
WHERE code = 'system.integration';

UPDATE sys_resource
SET parent_id = @menu_system,
    order_num = 20,
    update_by = 'migration',
    update_time = @now
WHERE code = 'system.org-sync';

UPDATE sys_resource
SET parent_id = @menu_payments_api,
    order_num = 10,
    props_json = JSON_OBJECT('method', 'POST'),
    update_by = 'migration',
    update_time = @now
WHERE code = 'api.payment.batch.start';

UPDATE sys_resource
SET parent_id = @menu_payments_api,
    order_num = 20,
    props_json = JSON_OBJECT('method', 'POST'),
    update_by = 'migration',
    update_time = @now
WHERE code = 'api.payment.record.retry';

UPDATE sys_resource
SET parent_id = @menu_system_org_sync,
    order_num = 30,
    props_json = JSON_OBJECT('method', 'POST'),
    update_by = 'migration',
    update_time = @now
WHERE code = 'api.system.org.sync';

UPDATE sys_resource
SET props_json = JSON_OBJECT('roles', JSON_ARRAY('ADMIN','FINANCE','HR','MANAGER'), 'keepAlive', TRUE),
    update_by = 'migration',
    update_time = @now
WHERE code = 'menu.system.payroll';

-- Employees domain APIs
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employee.list','员工管理-列表查询','/api/employee',NULL,NULL,@menu_employees,100,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.detail','员工管理-详情','/api/employee/{id}',NULL,NULL,@menu_employees,110,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.create','员工管理-创建','/api/employee',NULL,NULL,@menu_employees,120,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.update','员工管理-更新','/api/employee/{id}',NULL,NULL,@menu_employees,130,JSON_OBJECT('method','PUT'),'enabled',@now,@now),
('API','api.employee.status','员工管理-状态更新','/api/employee/{id}/status',NULL,NULL,@menu_employees,140,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employee.bind-platform','员工管理-绑定平台','/api/employee/{id}/bind-platform',NULL,NULL,@menu_employees,150,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.offline-list','员工管理-离线列表','/api/employee/offline',NULL,NULL,@menu_employees,160,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.batch-import','员工管理-批量导入','/api/employee/batch-import',NULL,NULL,@menu_employees,170,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.decrypt-id-card','员工管理-身份证解密','/api/employee/{id}/id-card',NULL,NULL,@menu_employees,180,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employee.decrypt-bank','员工管理-银行卡解密','/api/employee/{id}/bank-account',NULL,NULL,@menu_employees,190,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Payment domain APIs
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payment.batch.list','支付批次-查询','/api/payment/batch',NULL,NULL,@menu_payments_api,30,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.detail','支付批次-详情','/api/payment/batch/{batchNo}',NULL,NULL,@menu_payments_api,40,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.records','支付批次-记录列表','/api/payment/batch/{batchNo}/records',NULL,NULL,@menu_payments_api,50,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.record.detail','支付记录-详情','/api/payment/record/{id}',NULL,NULL,@menu_payments_api,60,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.transfer-status','支付记录-查询状态','/api/payment/transfer-status',NULL,NULL,@menu_payments_api,70,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Dashboard data APIs
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.dashboard.metrics','工作台-指标','/api/dashboard/metrics',NULL,NULL,@menu_dashboard,50,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.status','工作台-状态','/api/dashboard/status',NULL,NULL,@menu_dashboard,60,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.todos','工作台-待办','/api/dashboard/todos',NULL,NULL,@menu_dashboard,70,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.activities','工作台-动态','/api/dashboard/activities',NULL,NULL,@menu_dashboard,80,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Organization sync APIs
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.system.org.platforms','组织同步-平台列表','/api/system/org/platforms',NULL,NULL,@menu_system_org_sync,10,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.system.org.check','组织同步-连接检测','/api/system/org/check',NULL,NULL,@menu_system_org_sync,20,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.fetch','组织同步-取数','/api/system/org/fetch',NULL,NULL,@menu_system_org_sync,40,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.fetch-tree','组织同步-部门树拉取','/api/system/org/fetch-tree',NULL,NULL,@menu_system_org_sync,50,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.import','组织同步-导入','/api/system/org/import',NULL,NULL,@menu_system_org_sync,60,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.sync-async','组织同步-异步任务','/api/system/org/sync-async',NULL,NULL,@menu_system_org_sync,70,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.department-tree','组织架构-部门树','/api/system/org/departments/tree',NULL,NULL,@menu_system_org_sync,80,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Payroll approval APIs
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.approval.workflow.approve','审批-通过','/api/approval/workflows/{id}/approve',NULL,NULL,@menu_payroll,90,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE','MANAGER')),'enabled',@now,@now),
('API','api.approval.workflow.reject','审批-驳回','/api/approval/workflows/{id}/reject',NULL,NULL,@menu_payroll,91,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE','MANAGER')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- Confirmation
SELECT 'rbac resource cleanup migration completed' AS msg;
