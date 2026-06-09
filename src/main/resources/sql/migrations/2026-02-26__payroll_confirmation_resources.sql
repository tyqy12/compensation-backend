-- 发薪确认流程 API 资源注册（幂等）

SET NAMES utf8mb4;
SET @now := NOW();

SET @menu_payroll_id := (SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1);

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payroll.confirmations.pending','待确认列表','/api/payroll/confirmations/pending',NULL,NULL,@menu_payroll_id,210,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR','MANAGER','EMPLOYEE')),'enabled',@now,@now),
('API','api.payroll.confirmations.summary','确认汇总','/api/payroll/confirmations/batches/*/summary',NULL,NULL,@menu_payroll_id,211,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','HR')),'enabled',@now,@now),
('API','api.payroll.confirmations.confirm','工资条确认','/api/payroll/confirmations/payslips/*/confirm',NULL,NULL,@menu_payroll_id,212,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.confirmations.object','工资条异议','/api/payroll/confirmations/payslips/*/object',NULL,NULL,@menu_payroll_id,213,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.confirmations.batch-confirm','负责人批量确认','/api/payroll/confirmations/batches/*/batch-confirm',NULL,NULL,@menu_payroll_id,214,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.confirmations.assign','分配确认负责人','/api/payroll/confirmations/batches/*/assign',NULL,NULL,@menu_payroll_id,215,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
`name`=VALUES(`name`),
`path`=VALUES(`path`),
`parent_id`=VALUES(`parent_id`),
`order_num`=VALUES(`order_num`),
`props_json`=VALUES(`props_json`),
`status`='enabled',
`update_time`=@now;
