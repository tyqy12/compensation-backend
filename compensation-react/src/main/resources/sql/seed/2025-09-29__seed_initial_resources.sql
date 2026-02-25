-- Seed initial sys_resource data (id auto-increment, code unique)
-- Usage:
--   mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql

START TRANSACTION;

INSERT INTO `sys_resource` (`type`, `code`, `name`, `path`, `component`, `icon`, `parent_id`, `order_num`, `props_json`, `status`)
VALUES
  ('MENU',  'dashboard',                 '工作台',       '/',                          'dashboard/Dashboard',   'dashboard', NULL,  1,  CAST('{"keepAlive":true, "affix":true}' AS JSON), 'enabled'),

  ('MENU',  'employees',                 '员工管理',     '/employees',                 'employees/List',        'team',      NULL, 10,  CAST('{"keepAlive":true}' AS JSON),              'enabled'),
  ('VIEW',  'employees.detail',          '员工详情',     '/employees/:id',             'employees/Detail',      NULL,        NULL, 11,  CAST('{}' AS JSON),                                   'enabled'),

  ('MENU',  'payments.batches',          '支付批次',     '/payments/batches',          'payments/Batches',      'wallet',    NULL, 20,  CAST('{"keepAlive":true}' AS JSON),              'enabled'),
  ('VIEW',  'payments.batch.detail',     '批次详情',     '/payments/batches/:batchNo', 'payments/BatchDetail',  NULL,        NULL, 21,  CAST('{}' AS JSON),                                   'enabled'),

  ('MENU',  'admin.user-binding',        '用户绑定',     '/admin/user-binding',        'admin/UserBinding',     'user-switch', NULL, 80, CAST('{}' AS JSON),                                'enabled'),

  ('MENU',  'system.integration',        '集成配置',     '/system/integration',        'system/IntegrationConfig','global',   NULL, 90,  CAST('{}' AS JSON),                                  'enabled'),
  ('MENU',  'system.org-sync',           '组织同步',     '/system/org-sync',           'system/OrgSync',        'sync',      NULL, 91,  CAST('{}' AS JSON),                                  'enabled'),

  ('API',   'api.payment.batch.start',   '启动支付批次', '/api/payment/batch/{batchNo}/start', NULL,            NULL,        NULL,  0,  CAST('{"method":"POST"}' AS JSON),              'enabled'),
  ('API',   'api.payment.record.retry',  '重试失败记录', '/api/payment/record/{recordId}/retry', NULL,          NULL,        NULL,  0,  CAST('{"method":"POST"}' AS JSON),              'enabled'),
  ('API',   'api.system.org.sync',       '组织同步触发', '/api/system/org/sync',       NULL,                    NULL,        NULL,  0,  CAST('{"method":"POST"}' AS JSON),              'enabled')
ON DUPLICATE KEY UPDATE
  `type`       = VALUES(`type`),
  `name`       = VALUES(`name`),
  `path`       = VALUES(`path`),
  `component`  = VALUES(`component`),
  `icon`       = VALUES(`icon`),
  `parent_id`  = VALUES(`parent_id`),
  `order_num`  = VALUES(`order_num`),
  `props_json` = VALUES(`props_json`),
  `status`     = VALUES(`status`);

COMMIT;

