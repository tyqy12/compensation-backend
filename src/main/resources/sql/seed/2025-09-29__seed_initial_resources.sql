-- 将初始资源写入 MySQL（按 code 幂等插入/更新）
-- 用法：mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql
SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

-- 顶层菜单
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','dashboard','工作台','/','dashboard/Dashboard','dashboard',NULL,1,JSON_OBJECT('roles',JSON_ARRAY('ADMIN','FINANCE','HR'),'keepAlive',TRUE,'affix',TRUE),'enabled',@now,@now)
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
('API','api.employee.me.change-request-list','员工自助-查询敏感信息变更','/api/employee/me/change-requests',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),195,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.list','员工管理-列表查询(复数路径)','/api/employees',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),200,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.detail','员工管理-详情(复数路径)','/api/employees/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),201,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.create','员工管理-创建(复数路径)','/api/employees',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),202,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employees.update','员工管理-更新(复数路径)','/api/employees/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),203,JSON_OBJECT('method','PUT'),'enabled',@now,@now),
('API','api.employees.status','员工管理-状态更新(复数路径)','/api/employees/{id}/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),204,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employees.bind-platform','员工管理-绑定平台(复数路径)','/api/employees/{id}/bind-platform',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),205,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.unbind-platform','员工管理-解绑平台','/api/employee/{id}/unbind-platform',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),206,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.unbind-platform','员工管理-解绑平台(复数路径)','/api/employees/{id}/unbind-platform',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),207,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.offline-list','员工管理-架构外员工列表(复数路径)','/api/employees/offline',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),208,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.resigned-list','员工管理-离职员工列表(复数路径)','/api/employees/resigned',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),209,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.batch-import','员工管理-批量导入(复数路径)','/api/employees/batch-import',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),210,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employees.approvals','员工管理-审批记录(复数路径)','/api/employees/{id}/approvals',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),211,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.payslips','员工管理-发薪记录(复数路径)','/api/employees/{id}/payslips',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),212,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.payments','员工管理-支付记录(复数路径)','/api/employees/{id}/payments',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),213,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employees.decrypt-id-card','员工管理-身份证解密(复数路径)','/api/employees/{id}/id-card',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),214,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.decrypt-bank','员工管理-银行卡解密(复数路径)','/api/employees/{id}/bank-account',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),215,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.employees.decrypt-settlement','员工管理-收款账户解密(复数路径)','/api/employees/{id}/settlement-account',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),216,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 支付模块 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payment.batch.list','支付批次-查询','/api/payment/batch',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),30,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.detail','支付批次-详情','/api/payment/batch/{batchNo}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),40,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.records','支付批次-记录列表','/api/payment/batch/{batchNo}/records',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),50,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.precheck','支付批次-发放预校验','/api/payment/batch/{batchNo}/precheck',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),55,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.record.detail','支付记录-详情','/api/payment/record/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),60,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.transfer-status','支付记录-查询状态','/api/payment/transfer-status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),70,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.start','支付批次-启动转账','/api/payment/batch/{batchNo}/start',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),80,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payment.batch.retry-failed','支付批次-重试失败记录','/api/payment/batch/{batchNo}/retry-failed',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),85,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payment.record.retry','支付记录-重试','/api/payment/record/{recordId}/retry',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),90,JSON_OBJECT('method','POST'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 工作台 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.dashboard.metrics','工作台-指标','/api/dashboard/metrics',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),50,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR')),'enabled',@now,@now),
('API','api.dashboard.status','工作台-状态','/api/dashboard/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),60,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR')),'enabled',@now,@now),
('API','api.dashboard.todos','工作台-待办','/api/dashboard/todos',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),70,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR')),'enabled',@now,@now),
('API','api.dashboard.activities','工作台-动态','/api/dashboard/activities',NULL,NULL,(SELECT id FROM sys_resource WHERE code='dashboard' LIMIT 1),80,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 结算通道配置 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.settlement.provider-config.list','结算渠道配置-列表','/api/settlement/provider-config',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),140,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.create','结算渠道配置-创建','/api/settlement/provider-config',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),141,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.detail','结算渠道配置-详情','/api/settlement/provider-config/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),142,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.update','结算渠道配置-更新','/api/settlement/provider-config/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),143,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.delete','结算渠道配置-删除','/api/settlement/provider-config/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),144,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.status','结算渠道配置-状态切换','/api/settlement/provider-config/{id}/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),145,JSON_OBJECT('method','PATCH','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.by-code','结算渠道配置-按代码查询','/api/settlement/provider-config/code/{providerCode}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),146,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.enabled','结算渠道配置-启用列表','/api/settlement/provider-config/enabled',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),147,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.list','结算路由映射-列表','/api/settlement/routing/mapping',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),150,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.create','结算路由映射-创建','/api/settlement/routing/mapping',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),151,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.update','结算路由映射-更新','/api/settlement/routing/mapping/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),152,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.delete','结算路由映射-删除','/api/settlement/routing/mapping/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),153,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.status','结算路由映射-状态切换','/api/settlement/routing/mapping/{id}/status',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),154,JSON_OBJECT('method','PATCH','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.by-type','结算路由映射-按员工类型查询','/api/settlement/routing/mapping/type/{employmentType}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),155,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 组织同步 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.system.org.platforms','组织同步-平台列表','/api/system/org/platforms',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),10,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.system.org.check','组织同步-连接检测','/api/system/org/check',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),20,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.fetch','组织同步-取数','/api/system/org/fetch',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),40,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.fetch-tree','组织同步-部门树拉取','/api/system/org/fetch-tree',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),50,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.import','组织同步-导入','/api/system/org/import',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),60,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.sync-async','组织同步-异步任务','/api/system/org/sync-async',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),70,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.sync-task-detail','组织同步-异步任务详情','/api/system/org/sync-task/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),75,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.department-tree','组织架构-部门树','/api/system/org/departments/tree',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1),80,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 审批流程 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.approval.workflow.list','审批-列表查询','/api/approval/workflows',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),193,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.pending','审批-待我审批','/api/approval/workflows/pending',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),194,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.my','审批-我发起的','/api/approval/workflows/my',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),195,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.detail','审批-详情','/api/approval/workflows/{id}',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),196,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.steps','审批-步骤列表','/api/approval/workflows/{id}/steps',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),197,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.approval.workflow.approve','审批-通过','/api/approval/workflows/{id}/approve',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),198,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.approval.workflow.reject','审批-驳回','/api/approval/workflows/{id}/reject',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),199,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.approval.workflow.cancel','审批-撤销','/api/approval/workflows/{id}/cancel',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='menu.approval' LIMIT 1),(SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1)),200,JSON_OBJECT('method','POST'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

-- 系统管理 API
INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.system.info','系统信息','/api/system/info',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system' LIMIT 1),140,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.list','开放应用-列表','/api/admin/app-registry',NULL,NULL,(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1),151,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.create','开放应用-创建','/api/admin/app-registry',NULL,NULL,(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1),152,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.update','开放应用-更新','/api/admin/app-registry/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1),153,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.rotate-secret','开放应用-轮换密钥','/api/admin/app-registry/{id}/rotate-secret',NULL,NULL,(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1),154,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.detail','开放应用-详情','/api/admin/app-registry/{id}',NULL,NULL,(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1),155,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.create','支付批次管理-创建批次','/api/admin/payment/batch',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),156,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.cancel','支付批次管理-取消批次','/api/admin/payment/batch/{id}/cancel',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),157,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.stats','支付批次管理-统计','/api/admin/payment/batch/stats',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),158,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-failures.list','支付失败补偿-列表','/api/admin/payment/batch/failures',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),159,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-failures.retry','支付失败补偿-重试','/api/admin/payment/batch/failures/{id}/retry',NULL,NULL,(SELECT id FROM sys_resource WHERE code='payments.batches' LIMIT 1),160,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.role.detail','系统管理-角色详情','/api/admin/roles/{id}',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='admin.roles' LIMIT 1),(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1)),161,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.user-recent','审计日志-用户最近操作','/api/admin/audit-logs/user/{username}/recent',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='admin.audit' LIMIT 1),(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1)),162,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.time-range','审计日志-时间范围查询','/api/admin/audit-logs/time-range',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='admin.audit' LIMIT 1),(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1)),163,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.metrics','审计日志-实时指标','/api/admin/audit-logs/metrics',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='admin.audit' LIMIT 1),(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1)),164,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.users.provision-from-employees','系统管理-按员工回填账号','/api/admin/users/provision-from-employees',NULL,NULL,COALESCE((SELECT id FROM sys_resource WHERE code='admin.users' LIMIT 1),(SELECT id FROM sys_resource WHERE code='admin' LIMIT 1)),165,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.alipay-cert-upload','集成配置-上传支付宝证书','/api/admin/integration-configs/alipay/cert-upload',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.integration' LIMIT 1),209,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.enable','集成配置-启用','/api/admin/integration-configs/{platformType}/enable',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.integration' LIMIT 1),210,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=@now;

COMMIT;
