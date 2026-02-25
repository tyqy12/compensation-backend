-- Payroll menus and API resources registration (idempotent)
-- Usage: mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-10-01__payroll_resources.sql

SET NAMES utf8mb4;
SET @now := NOW();

-- Ensure core roles exist
INSERT INTO `sys_role`(`code`,`name`,`description`,`status`,`create_time`,`update_time`)
VALUES
('role.admin.all','系统管理员','拥有全部权限','enabled',@now,@now),
('role.finance','财务','财务相关可见/可用','enabled',@now,@now),
('role.hr','人力资源','模板与周期配置、查看','enabled',@now,@now),
('role.manager','部门负责人','范围内查看与核对','enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`description`=VALUES(`description`),`status`='enabled',`update_time`=@now;

SET @rid_admin    := (SELECT id FROM sys_role WHERE code='role.admin.all' LIMIT 1);
SET @rid_finance  := (SELECT id FROM sys_role WHERE code='role.finance' LIMIT 1);
SET @rid_hr       := (SELECT id FROM sys_role WHERE code='role.hr' LIMIT 1);
SET @rid_manager  := (SELECT id FROM sys_role WHERE code='role.manager' LIMIT 1);

-- Menus: place under system menu if present, else create root payroll menu
SET @menu_system_id := (SELECT id FROM sys_resource WHERE code='menu.system' LIMIT 1);

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES ('MENU','menu.system.payroll','薪酬管理',NULL,NULL,'MoneyCollect',@menu_system_id,30,NULL,'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `name`='薪酬管理',`parent_id`=@menu_system_id,`status`='enabled',`update_time`=@now;
SET @menu_payroll_id := (SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1);

-- Views
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('VIEW','view.payroll.cycles','发薪周期','/payroll/cycles',NULL,'Calendar',@menu_payroll_id,1,NULL,'enabled',@now,@now),
('VIEW','view.payroll.templates','薪资模板','/payroll/templates',NULL,'Template',@menu_payroll_id,2,NULL,'enabled',@now,@now),
('VIEW','view.payroll.batches','发薪批次','/payroll/batches',NULL,'Collection',@menu_payroll_id,3,NULL,'enabled',@now,@now)
ON DUPLICATE KEY UPDATE `parent_id`=@menu_payroll_id,`status`='enabled',`update_time`=@now;

-- APIs (method in props_json)
INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payroll.cycles.create','创建发薪周期','/api/payroll/cycles',NULL,NULL,@menu_payroll_id,10,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.cycles.update','更新发薪周期','/api/payroll/cycles/*',NULL,NULL,@menu_payroll_id,11,'{"method":"PUT"}','enabled',@now,@now),
('API','api.payroll.cycles.list','查询发薪周期','/api/payroll/cycles',NULL,NULL,@menu_payroll_id,12,'{"method":"GET"}','enabled',@now,@now),
('API','api.payroll.cycles.detail','周期详情','/api/payroll/cycles/*',NULL,NULL,@menu_payroll_id,13,'{"method":"GET"}','enabled',@now,@now),

('API','api.payroll.templates.create','创建薪资模板','/api/payroll/templates',NULL,NULL,@menu_payroll_id,20,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.templates.update','更新薪资模板','/api/payroll/templates/*',NULL,NULL,@menu_payroll_id,21,'{"method":"PUT"}','enabled',@now,@now),
('API','api.payroll.templates.list','查询薪资模板','/api/payroll/templates',NULL,NULL,@menu_payroll_id,22,'{"method":"GET"}','enabled',@now,@now),
('API','api.payroll.templates.detail','模板详情','/api/payroll/templates/*',NULL,NULL,@menu_payroll_id,23,'{"method":"GET"}','enabled',@now,@now),

('API','api.payroll.batches.create','创建发薪批次','/api/payroll/batches',NULL,NULL,@menu_payroll_id,30,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.batches.update','更新发薪批次','/api/payroll/batches/*',NULL,NULL,@menu_payroll_id,31,'{"method":"PUT"}','enabled',@now,@now),
('API','api.payroll.batches.lock','锁定批次','/api/payroll/batches/*/lock',NULL,NULL,@menu_payroll_id,32,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.batches.submit','提交审批','/api/payroll/batches/*/submit-approval',NULL,NULL,@menu_payroll_id,33,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.batches.dryrun','试算','/api/payroll/batches/*/dry-run',NULL,NULL,@menu_payroll_id,34,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.batches.compute','计算落地','/api/payroll/batches/*/compute',NULL,NULL,@menu_payroll_id,35,'{"method":"POST"}','enabled',@now,@now),
('API','api.payroll.batches.list','查询发薪批次','/api/payroll/batches',NULL,NULL,@menu_payroll_id,36,'{"method":"GET"}','enabled',@now,@now),
('API','api.payroll.batches.detail','批次详情','/api/payroll/batches/*',NULL,NULL,@menu_payroll_id,37,'{"method":"GET"}','enabled',@now,@now)
ON DUPLICATE KEY UPDATE `parent_id`=@menu_payroll_id,`status`='enabled',`update_time`=@now;

-- IDs
SET @res_menu_payroll := (SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1);
SET @res_view_cycles  := (SELECT id FROM sys_resource WHERE code='view.payroll.cycles' LIMIT 1);
SET @res_view_tmpls   := (SELECT id FROM sys_resource WHERE code='view.payroll.templates' LIMIT 1);
SET @res_view_batches := (SELECT id FROM sys_resource WHERE code='view.payroll.batches' LIMIT 1);

-- Admin role: grant all enabled resources (idempotent simple grant)
DELETE FROM sys_role_resource WHERE role_id=@rid_admin;
INSERT INTO sys_role_resource(role_id, resource_id, create_time, update_time)
SELECT @rid_admin, r.id, @now, @now FROM sys_resource r WHERE r.status='enabled';

-- Finance: menus + views + all payroll APIs
DELETE FROM sys_role_resource WHERE role_id=@rid_finance;
INSERT INTO sys_role_resource(role_id, resource_id, create_time, update_time)
SELECT @rid_finance, r.id, @now, @now FROM sys_resource r
WHERE r.status='enabled' AND (
  r.id IN (@res_menu_payroll, @res_view_cycles, @res_view_tmpls, @res_view_batches)
  OR r.code LIKE 'api.payroll.%'
);

-- HR: menus + templates/cycles views + read APIs
DELETE FROM sys_role_resource WHERE role_id=@rid_hr;
INSERT INTO sys_role_resource(role_id, resource_id, create_time, update_time)
SELECT @rid_hr, r.id, @now, @now FROM sys_resource r
WHERE r.status='enabled' AND (
  r.id IN (@res_menu_payroll, @res_view_cycles, @res_view_tmpls)
  OR r.code IN ('api.payroll.cycles.list','api.payroll.cycles.detail','api.payroll.templates.list','api.payroll.templates.detail')
);

-- Manager: menus + batches view + read batch APIs
DELETE FROM sys_role_resource WHERE role_id=@rid_manager;
INSERT INTO sys_role_resource(role_id, resource_id, create_time, update_time)
SELECT @rid_manager, r.id, @now, @now FROM sys_resource r
WHERE r.status='enabled' AND (
  r.id IN (@res_menu_payroll, @res_view_batches)
  OR r.code IN ('api.payroll.batches.list','api.payroll.batches.detail')
);

-- Done
SELECT 'payroll resources migration applied' AS msg;

