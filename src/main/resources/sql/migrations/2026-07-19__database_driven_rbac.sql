-- Database-driven RBAC catalog.
-- Runtime authorization reads these tables. The legacy actions_json columns remain
-- only as a migration compatibility source and are not the authorization source.

ALTER TABLE sys_resource
    ADD COLUMN IF NOT EXISTS access_mode varchar(20) NOT NULL DEFAULT 'USER'
    COMMENT '访问模式: PUBLIC/USER/EXTERNAL';

CREATE TABLE IF NOT EXISTS sys_permission_action (
    id bigint NOT NULL AUTO_INCREMENT,
    code varchar(100) NOT NULL,
    name varchar(100) NOT NULL,
    description varchar(255) DEFAULT NULL,
    http_methods varchar(100) DEFAULT NULL COMMENT '逗号分隔的HTTP方法，*表示全部',
    authority varchar(150) DEFAULT NULL COMMENT '外部令牌所需authority，可空',
    status varchar(20) NOT NULL DEFAULT 'enabled',
    order_num int NOT NULL DEFAULT 0,
    props_json text DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_action_code (code),
    KEY idx_permission_action_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限操作目录';

CREATE TABLE IF NOT EXISTS sys_resource_action (
    id bigint NOT NULL AUTO_INCREMENT,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    props_json text DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_action (resource_id, action_id),
    KEY idx_resource_action_resource (resource_id, status, deleted),
    KEY idx_resource_action_action (action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源可用操作';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id bigint NOT NULL AUTO_INCREMENT,
    role_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    effect varchar(10) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
    scope_json text DEFAULT NULL COMMENT '数据范围策略JSON',
    condition_json text DEFAULT NULL COMMENT '附加条件JSON',
    status varchar(20) NOT NULL DEFAULT 'enabled',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, resource_id, action_id),
    KEY idx_role_permission_subject (role_id, status, deleted),
    KEY idx_role_permission_resource (resource_id, action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源-操作权限';

CREATE TABLE IF NOT EXISTS sys_user_permission (
    id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    effect varchar(10) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
    scope_json text DEFAULT NULL COMMENT '数据范围策略JSON',
    condition_json text DEFAULT NULL COMMENT '附加条件JSON',
    status varchar(20) NOT NULL DEFAULT 'enabled',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_permission (user_id, resource_id, action_id),
    KEY idx_user_permission_subject (user_id, status, deleted),
    KEY idx_user_permission_resource (resource_id, action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源-操作权限';

INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'read', '读取', '读取资源数据', 'GET,HEAD', NULL, 'enabled', 10, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'read');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'write', '写入', '创建或更新资源数据', 'POST,PUT,PATCH', NULL, 'enabled', 20, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'write');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'delete', '删除', '删除资源数据', 'DELETE', NULL, 'enabled', 30, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'delete');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'execute', '执行', '执行非CRUD业务动作', 'POST,PUT,PATCH', NULL, 'enabled', 40, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'execute');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'payroll:read', '外部薪酬读取', '外部应用读取薪酬数据', 'GET', 'SCOPE_payroll:read', 'enabled', 100, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'payroll:read');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'payslip:read', '外部工资条读取', '外部应用读取工资条', 'GET', 'SCOPE_payslip:read', 'enabled', 110, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'payslip:read');
INSERT INTO sys_permission_action
    (code, name, description, http_methods, authority, status, order_num, create_by)
SELECT 'app:ping', '外部应用探活', '外部应用探活接口', 'GET', NULL, 'enabled', 120, 'rbac_migration'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'app:ping');

UPDATE sys_permission_action
SET authority = NULL
WHERE code = 'app:ping' AND authority IN ('ROLE_APP', 'SCOPE_app:ping') AND deleted = 0;

UPDATE sys_resource
SET access_mode = CASE
    WHEN path LIKE '/v1/payroll%' OR path LIKE '/v1/payslips%' OR path = '/v1/ping' THEN 'EXTERNAL'
    ELSE COALESCE(NULLIF(access_mode, ''), 'USER')
END
WHERE deleted = 0;

INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.public.auth.login', '登录', '/auth/login', 'PUBLIC', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.public.auth.login');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.public.auth.refresh', '刷新令牌', '/auth/refresh', 'PUBLIC', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.public.auth.refresh');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.public.system.health', '系统健康检查', '/system/health', 'PUBLIC', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.public.system.health');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.public.external.token', '外部应用令牌', '/v1/oauth/token', 'PUBLIC', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.public.external.token');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.admin.permission.actions', '权限操作目录', '/admin/permission-actions', 'USER', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.admin.permission.actions');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.admin.permission.action-items', '权限操作明细', '/admin/permission-actions/**', 'USER', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.admin.permission.action-items');
INSERT INTO sys_resource (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.admin.permission.resource-actions', '资源操作绑定', '/admin/permission-actions/resources/*', 'USER', 'enabled', NOW(), NOW(), 'rbac_migration', 'rbac_migration', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.admin.permission.resource-actions');

-- The application startup migrator completes resource-action and permission backfill
-- because legacy action JSON needs tolerant parsing across old database versions.

-- Approval flow configurations are database data. Fill only missing or historical
-- empty placeholders so administrator-maintained flows are never overwritten.
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'payroll.approval.flow',
       '[{"stepNo":1,"stepName":"部门负责人审批","role":"ROLE_MANAGER","approverType":"EMPLOYEE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务负责人审批","role":"ROLE_FINANCE","timeoutHours":24,"optional":false},{"stepNo":3,"stepName":"总监审批","role":"ROLE_ADMIN","timeoutHours":48,"optional":false,"finalStep":true}]',
       'json', '薪资批次/发放审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='payroll.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'adhoc.approval.flow',
       '[{"stepNo":1,"stepName":"直接上级审批","role":"ROLE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务审批","role":"ROLE_FINANCE","timeoutHours":24,"optional":false,"finalStep":true}]',
       'json', '临时支付审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='adhoc.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'offline.approval.flow',
       '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]',
       'json', '架构外员工审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='offline.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'employee.profile-change.approval.flow',
       '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]',
       'json', '员工资料变更审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='employee.profile-change.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'platform.bind.approval.flow',
       '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]',
       'json', '平台账号绑定审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='platform.bind.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'permission.approval.flow',
       '[{"stepNo":1,"stepName":"管理员审批","role":"ROLE_ADMIN","timeoutHours":24,"optional":false,"finalStep":true}]',
       'json', '权限授权审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='permission.approval.flow' AND deleted=0);
INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time)
SELECT 'payroll.dispute.approval.flow',
       '[{"stepNo":1,"stepName":"负责人核实","role":"ROLE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务复核","role":"ROLE_FINANCE","timeoutHours":24,"optional":false},{"stepNo":3,"stepName":"老板终审","role":"ROLE_ADMIN","timeoutHours":48,"optional":true,"finalStep":true}]',
       'json', '薪酬异议审批流程配置(JSON)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='payroll.dispute.approval.flow' AND deleted=0);
UPDATE sys_config
SET config_value='[{"stepNo":1,"stepName":"负责人核实","role":"ROLE_MANAGER","timeoutHours":24,"optional":false},{"stepNo":2,"stepName":"财务复核","role":"ROLE_FINANCE","timeoutHours":24,"optional":false},{"stepNo":3,"stepName":"老板终审","role":"ROLE_ADMIN","timeoutHours":48,"optional":true,"finalStep":true}]'
WHERE config_key='payroll.dispute.approval.flow' AND deleted=0
  AND (config_value IS NULL OR TRIM(config_value)='');
