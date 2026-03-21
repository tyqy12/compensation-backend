-- 员工自助资料维护 API 资源与授权
SET NAMES utf8mb4;
SET @now := NOW();

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.employee.me.detail','员工自助-本人资料','/api/employee/me',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),192,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.employee.me.contact-update','员工自助-联系方式更新','/api/employee/me/contact',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),193,JSON_OBJECT('method','PATCH'),'enabled',@now,@now),
('API','api.employee.me.change-request-create','员工自助-提交敏感信息变更','/api/employee/me/change-requests',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),194,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.employee.me.change-request-list','员工自助-查询敏感信息变更','/api/employee/me/change-requests',NULL,NULL,(SELECT id FROM sys_resource WHERE code='employees' LIMIT 1),195,JSON_OBJECT('method','GET'),'enabled',@now,@now)
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
       '["*"]',
       @now,
       @now
FROM sys_role r
JOIN sys_resource s ON s.code IN (
    'api.employee.me.detail',
    'api.employee.me.contact-update',
    'api.employee.me.change-request-create',
    'api.employee.me.change-request-list'
)
WHERE r.code IN ('ADMIN', 'EMPLOYEE', 'HR', 'FINANCE', 'MANAGER');
