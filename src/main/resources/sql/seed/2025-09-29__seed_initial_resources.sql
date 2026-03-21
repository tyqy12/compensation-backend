-- 将初始资源写入 MySQL（按 code 幂等插入/更新）
-- 用法：mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql
SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

-- 顶层菜单
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','dashboard','工作台','/','dashboard/Dashboard','dashboard',NULL,1,JSON_OBJECT('keepAlive',TRUE,'affix',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','employees','员工管理','/employees','employees/List','team',NULL,10,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','business','业务管理',NULL,NULL,'appstore',NULL,20,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','admin','系统管理',NULL,NULL,'setting',NULL,80,JSON_OBJECT('roles',JSON_ARRAY('ADMIN'),'keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system','系统配置',NULL,NULL,'control',NULL,90,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','menu.system.payroll','薪酬管理',NULL,NULL,'MoneyCollect',NULL,30,JSON_OBJECT('roles',JSON_ARRAY('ADMIN','FINANCE','HR','MANAGER'),'keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 子菜单与视图
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('VIEW','employees.detail','员工详情','/employees/:id','employees/Detail',NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),20,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments','支付管理',NULL,NULL,'wallet',(SELECT id FROM sys_resource WHERE code='business' LIMIT 1),20,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments.batches','支付批次','/payments/batches','payments/Batches','wallet',(SELECT id FROM sys_resource WHERE code='payments' LIMIT 1),10,JSON_OBJECT('keepAlive',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('VIEW','payments.batch.detail','批次详情','/payments/batches/:batchNo','payments/BatchDetail',NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),20,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','payments.api','支付管理-接口',NULL,NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments' LIMIT 1),95,JSON_OBJECT('hidden',TRUE),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system.integration','集成配置','/system/integration','system/IntegrationConfig','global',(SELECT id FROM sys_resource WHERE code='system' LIMIT 1),10,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','system.org-sync','组织同步','/system/org-sync','system/OrgSync','sync',(SELECT id FROM sys_resource WHERE code='system' LIMIT 1),20,JSON_OBJECT(),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`component`=VALUES(`component`),`icon`=VALUES(`icon`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 员工模块 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employee.list','员工管理-列表查询','/api/employee',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),100,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.detail','员工管理-详情','/api/employee/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),110,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.create','员工管理-创建','/api/employee',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),120,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.update','员工管理-更新','/api/employee/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),130,JSON_OBJECT('method','PUT'),'enabled',@now,@now),
('API','api.employee.status','员工管理-状态更新','/api/employee/{id}/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),140,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employee.bind-platform','员工管理-绑定平台','/api/employee/{id}/bind-platform',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),150,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.offline-list','员工管理-架构外员工列表','/api/employee/offline',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),160,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.resigned-list','员工管理-离职员工列表','/api/employee/resigned',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),165,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.batch-import','员工管理-批量导入','/api/employee/batch-import',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),170,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.approvals','员工管理-审批记录','/api/employee/{id}/approvals',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),175,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.payslips','员工管理-发薪记录','/api/employee/{id}/payslips',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),176,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.payments','员工管理-支付记录','/api/employee/{id}/payments',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),177,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.decrypt-id-card','员工管理-身份证解密','/api/employee/{id}/id-card',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),180,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employee.decrypt-bank','员工管理-银行卡解密','/api/employee/{id}/bank-account',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),190,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employee.decrypt-settlement','员工管理-收款账户解密','/api/employee/{id}/settlement-account',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),191,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employee.me.detail','员工自助-本人资料','/api/employee/me',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),192,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.me.contact-update','员工自助-联系方式更新','/api/employee/me/contact',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),193,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employee.me.change-request-create','员工自助-提交敏感信息变更','/api/employee/me/change-requests',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),194,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.me.change-request-list','员工自助-查询敏感信息变更','/api/employee/me/change-requests',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),195,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 支付模块 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payment.batch.list','支付批次-查询','/api/payment/batch',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),30,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.detail','支付批次-详情','/api/payment/batch/{batchNo}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),40,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.records','支付批次-记录列表','/api/payment/batch/{batchNo}/records',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),50,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.record.detail','支付记录-详情','/api/payment/record/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),60,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.transfer-status','支付记录-查询状态','/api/payment/transfer-status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),70,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.start','支付批次-启动转账','/api/payment/batch/{batchNo}/start',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),80,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payment.record.retry','支付记录-重试','/api/payment/record/{recordId}/retry',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),90,JSON_OBJECT('method','POST'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 工作台 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.dashboard.metrics','工作台-指标','/api/dashboard/metrics',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),50,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.status','工作台-状态','/api/dashboard/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),60,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.todos','工作台-待办','/api/dashboard/todos',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),70,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.dashboard.activities','工作台-动态','/api/dashboard/activities',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),80,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 组织同步 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.system.org.platforms','组织同步-平台列表','/api/system/org/platforms',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),10,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.system.org.check','组织同步-连接检测','/api/system/org/check',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),20,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.fetch','组织同步-取数','/api/system/org/fetch',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),40,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.fetch-tree','组织同步-部门树拉取','/api/system/org/fetch-tree',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),50,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.import','组织同步-导入','/api/system/org/import',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),60,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.sync-async','组织同步-异步任务','/api/system/org/sync-async',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),70,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.system.org.department-tree','组织架构-部门树','/api/system/org/departments/tree',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),80,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 审批流程 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.approval.workflow.approve','审批-通过','/api/approval/workflows/{id}/approve',NULL,NULL,(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1),90,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE','MANAGER')),'enabled',@now,@now),
('API','api.approval.workflow.reject','审批-驳回','/api/approval/workflows/{id}/reject',NULL,NULL,(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1),91,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE','MANAGER')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

COMMIT;
