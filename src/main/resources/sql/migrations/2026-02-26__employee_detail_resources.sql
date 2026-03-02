-- 员工详情新增记录接口资源（审批/发薪/支付）
-- 兼容已部署环境，确保 API 资源鉴权可识别

SET NAMES utf8mb4;
SET @now := NOW();

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employee.approvals','员工管理-审批记录','/api/employee/{id}/approvals',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),175,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.payslips','员工管理-发薪记录','/api/employee/{id}/payslips',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),176,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.payments','员工管理-支付记录','/api/employee/{id}/payments',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),177,JSON_OBJECT('method','GET'),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
`name`=VALUES(`name`),
`path`=VALUES(`path`),
`parent_id`=VALUES(`parent_id`),
`order_num`=VALUES(`order_num`),
`props_json`=VALUES(`props_json`),
`status`='enabled',
`update_time`=@now;

INSERT IGNORE INTO sys_role_resource (`role_id`, `resource_id`, `actions_json`, `create_time`, `update_time`)
SELECT r.id,
       s.id,
       JSON_ARRAY('read'),
       @now,
       @now
FROM sys_role r
JOIN sys_resource s ON s.code IN ('api.employee.approvals', 'api.employee.payslips', 'api.employee.payments')
WHERE r.code IN ('ADMIN', 'HR', 'MANAGER', 'FINANCE');
