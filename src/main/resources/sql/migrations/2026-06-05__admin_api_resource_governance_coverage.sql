-- 补齐管理员专属 API 资源定义，避免 fail-closed 过滤器依赖管理员兜底放行。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @system_parent_id := (SELECT id FROM sys_resource WHERE code = 'system' LIMIT 1);
SET @admin_parent_id := (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1);
SET @admin_users_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'admin.auth-center' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'admin.users' LIMIT 1),
    @admin_parent_id
);
SET @admin_roles_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'admin.roles' LIMIT 1),
    @admin_parent_id
);
SET @admin_audit_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1),
    @admin_parent_id
);
SET @payments_batches_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'payments.batches' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'payments' LIMIT 1),
    @admin_parent_id
);
SET @system_integration_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'system.integration' LIMIT 1),
    @system_parent_id,
    @admin_parent_id
);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.system.info','系统信息','/api/system/info',NULL,NULL,@system_parent_id,188,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.list','开放应用-列表','/api/admin/app-registry',NULL,NULL,@admin_parent_id,189,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.create','开放应用-创建','/api/admin/app-registry',NULL,NULL,@admin_parent_id,190,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.update','开放应用-更新','/api/admin/app-registry/{id}',NULL,NULL,@admin_parent_id,191,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.rotate-secret','开放应用-轮换密钥','/api/admin/app-registry/{id}/rotate-secret',NULL,NULL,@admin_parent_id,192,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.app-registry.detail','开放应用-详情','/api/admin/app-registry/{id}',NULL,NULL,@admin_parent_id,193,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.create','支付批次管理-创建批次','/api/admin/payment/batch',NULL,NULL,@payments_batches_parent_id,194,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.cancel','支付批次管理-取消批次','/api/admin/payment/batch/{id}/cancel',NULL,NULL,@payments_batches_parent_id,195,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-batch.stats','支付批次管理-统计','/api/admin/payment/batch/stats',NULL,NULL,@payments_batches_parent_id,196,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-failures.list','支付失败补偿-列表','/api/admin/payment/batch/failures',NULL,NULL,@payments_batches_parent_id,197,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.payment-failures.retry','支付失败补偿-重试','/api/admin/payment/batch/failures/{id}/retry',NULL,NULL,@payments_batches_parent_id,198,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.role.detail','系统管理-角色详情','/api/admin/roles/{id}',NULL,NULL,@admin_roles_parent_id,170,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.user-recent','审计日志-用户最近操作','/api/admin/audit-logs/user/{username}/recent',NULL,NULL,@admin_audit_parent_id,210,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.time-range','审计日志-时间范围查询','/api/admin/audit-logs/time-range',NULL,NULL,@admin_audit_parent_id,211,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.audit.metrics','审计日志-实时指标','/api/admin/audit-logs/metrics',NULL,NULL,@admin_audit_parent_id,216,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.users.provision-from-employees','系统管理-按员工回填账号','/api/admin/users/provision-from-employees',NULL,NULL,@admin_users_parent_id,232,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.alipay-cert-upload','集成配置-上传支付宝证书','/api/admin/integration-configs/alipay/cert-upload',NULL,NULL,@system_integration_parent_id,226,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `props_json` = VALUES(`props_json`),
  `status` = 'enabled',
  `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id, resource.id, JSON_ARRAY('*'), @now, @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'api.system.info',
    'api.admin.app-registry.list',
    'api.admin.app-registry.create',
    'api.admin.app-registry.update',
    'api.admin.app-registry.rotate-secret',
    'api.admin.app-registry.detail',
    'api.admin.payment-batch.create',
    'api.admin.payment-batch.cancel',
    'api.admin.payment-batch.stats',
    'api.admin.payment-failures.list',
    'api.admin.payment-failures.retry',
    'api.admin.role.detail',
    'api.admin.audit.user-recent',
    'api.admin.audit.time-range',
    'api.admin.audit.metrics',
    'api.admin.users.provision-from-employees',
    'api.admin.integration-config.alipay-cert-upload'
)
WHERE role.code IN ('ADMIN', 'role.admin.all')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.code IN ('ADMIN', 'role.admin.all')
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
