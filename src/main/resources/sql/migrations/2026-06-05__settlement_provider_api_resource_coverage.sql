-- 补齐结算通道配置 API 资源，避免财务用户在 /settlement fail-closed 前缀下被资源层误拦截。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @payments_api_parent_id := COALESCE(
    (SELECT id FROM sys_resource WHERE code = 'payments.api' LIMIT 1),
    (SELECT id FROM sys_resource WHERE code = 'payments' LIMIT 1)
);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.settlement.provider-config.list','结算渠道配置-列表','/api/settlement/provider-config',NULL,NULL,@payments_api_parent_id,140,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.create','结算渠道配置-创建','/api/settlement/provider-config',NULL,NULL,@payments_api_parent_id,141,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.detail','结算渠道配置-详情','/api/settlement/provider-config/{id}',NULL,NULL,@payments_api_parent_id,142,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.update','结算渠道配置-更新','/api/settlement/provider-config/{id}',NULL,NULL,@payments_api_parent_id,143,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.delete','结算渠道配置-删除','/api/settlement/provider-config/{id}',NULL,NULL,@payments_api_parent_id,144,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.status','结算渠道配置-状态切换','/api/settlement/provider-config/{id}/status',NULL,NULL,@payments_api_parent_id,145,JSON_OBJECT('method','PATCH','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.by-code','结算渠道配置-按代码查询','/api/settlement/provider-config/code/{providerCode}',NULL,NULL,@payments_api_parent_id,146,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.provider-config.enabled','结算渠道配置-启用列表','/api/settlement/provider-config/enabled',NULL,NULL,@payments_api_parent_id,147,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.list','结算路由映射-列表','/api/settlement/routing/mapping',NULL,NULL,@payments_api_parent_id,150,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.create','结算路由映射-创建','/api/settlement/routing/mapping',NULL,NULL,@payments_api_parent_id,151,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.update','结算路由映射-更新','/api/settlement/routing/mapping/{id}',NULL,NULL,@payments_api_parent_id,152,JSON_OBJECT('method','PUT','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.delete','结算路由映射-删除','/api/settlement/routing/mapping/{id}',NULL,NULL,@payments_api_parent_id,153,JSON_OBJECT('method','DELETE','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.status','结算路由映射-状态切换','/api/settlement/routing/mapping/{id}/status',NULL,NULL,@payments_api_parent_id,154,JSON_OBJECT('method','PATCH','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.settlement.routing.mapping.by-type','结算路由映射-按员工类型查询','/api/settlement/routing/mapping/type/{employmentType}',NULL,NULL,@payments_api_parent_id,155,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `props_json` = VALUES(`props_json`),
  `status` = 'enabled',
  `update_time` = @now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT role.id,
       resource.id,
       CASE WHEN role.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END,
       @now,
       @now
FROM sys_role role
JOIN sys_resource resource ON resource.code IN (
    'api.settlement.provider-config.list',
    'api.settlement.provider-config.create',
    'api.settlement.provider-config.detail',
    'api.settlement.provider-config.update',
    'api.settlement.provider-config.delete',
    'api.settlement.provider-config.status',
    'api.settlement.provider-config.by-code',
    'api.settlement.provider-config.enabled',
    'api.settlement.routing.mapping.list',
    'api.settlement.routing.mapping.create',
    'api.settlement.routing.mapping.update',
    'api.settlement.routing.mapping.delete',
    'api.settlement.routing.mapping.status',
    'api.settlement.routing.mapping.by-type'
)
WHERE role.code IN ('ADMIN', 'FINANCE', 'role.admin.all', 'role.finance')
ON DUPLICATE KEY UPDATE
  `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
  `update_time` = @now;

UPDATE sys_user user
JOIN (
    SELECT DISTINCT user_role.user_id
    FROM sys_user_role user_role
    JOIN sys_role role ON role.id = user_role.role_id
    WHERE role.code IN ('ADMIN', 'FINANCE', 'role.admin.all', 'role.finance')
) affected ON affected.user_id = user.id
SET user.permission_version = COALESCE(user.permission_version, 0) + 1,
    user.update_time = @now;

COMMIT;
