-- ============================================================================
-- 菜单资源更新脚本 - 远程数据库执行
-- 执行前请备份数据库！
-- ============================================================================

-- 查看当前资源数量（确认是否需要更新）
SELECT COUNT(*) AS total_resources FROM sys_resource;

-- ============================================================================
-- 1. 新增审批管理相关菜单
-- ============================================================================

-- 审批管理菜单
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'MENU', 'menu.approval', '审批管理', '/approval/workflows', 'approval/Workflows', 'safety-certificate',
       NULL, 25, '{"roles":["ADMIN","FINANCE","MANAGER"],"keepAlive":true}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'menu.approval');

-- 审批详情 VIEW
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'VIEW', 'approval.workflow.detail', '审批详情', '/approval/workflows/:id', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 192, '{}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'approval.workflow.detail');

-- ============================================================================
-- 2. 新增审计日志、监控、任务调度菜单（在系统管理下）
-- ============================================================================

-- 先获取系统管理菜单的ID
SELECT id, code FROM sys_resource WHERE code = 'admin';

-- 审计日志菜单
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'MENU', 'admin.audit', '审计日志', '/admin/audit-logs', 'admin/AuditLogs', 'file-search',
       (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1), 84, '{"keepAlive":true}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'admin.audit');

-- 系统监控菜单
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'MENU', 'admin.monitor', '系统监控', '/admin/monitor', 'admin/Monitor', 'dashboard',
       (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1), 85, '{"keepAlive":true}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'admin.monitor');

-- 任务调度菜单
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'MENU', 'admin.tasks', '任务调度', '/admin/tasks', 'admin/TaskSchedules', 'schedule',
       (SELECT id FROM sys_resource WHERE code = 'admin' LIMIT 1), 86, '{"keepAlive":true}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'admin.tasks');

-- ============================================================================
-- 3. 新增 API 资源
-- ============================================================================

-- 审批相关 API
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.approval.workflow.list', '审批-列表查询', '/api/approval/workflows', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 193, '{"method":"GET","roles":["ADMIN","FINANCE","MANAGER"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.approval.workflow.list');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.approval.workflow.pending', '审批-待我审批', '/api/approval/workflows/pending', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 194, '{"method":"GET","roles":["ADMIN","FINANCE","MANAGER"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.approval.workflow.pending');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.approval.workflow.my', '审批-我发起的', '/api/approval/workflows/my', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 195, '{"method":"GET"}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.approval.workflow.my');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.approval.workflow.detail', '审批-详情', '/api/approval/workflows/{id}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 196, '{"method":"GET"}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.approval.workflow.detail');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.approval.workflow.steps', '审批-步骤列表', '/api/approval/workflows/{id}/steps', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'menu.approval' LIMIT 1), 197, '{"method":"GET"}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.approval.workflow.steps');

-- 审计日志 API
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.list', '审计日志-列表查询', '/api/admin/audit-logs', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 198, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.list');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.detail', '审计日志-详情', '/api/admin/audit-logs/{id}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 199, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.detail');

-- 审计日志统计 API
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.stats.today-login', '审计日志-今日登录统计', '/api/admin/audit-logs/stats/today-login', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 200, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.stats.today-login');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.stats.summary', '审计日志-统计摘要', '/api/admin/audit-logs/stats/summary', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 201, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.stats.summary');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.stats.operations', '审计日志-操作类型统计', '/api/admin/audit-logs/stats/operations', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 202, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.stats.operations');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.security.login-failures', '审计日志-登录失败监控', '/api/admin/audit-logs/security/login-failures', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 203, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.security.login-failures');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.security.clear-failures', '审计日志-清除登录失败', '/api/admin/audit-logs/security/login-failures/{username}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 204, '{"method":"DELETE","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.security.clear-failures');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.audit.metrics', '审计日志-实时指标', '/api/admin/audit-logs/metrics', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.audit' LIMIT 1), 205, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.audit.metrics');

-- 监控 API
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.monitor.summary', '监控-摘要信息', '/api/admin/monitor/summary', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.monitor' LIMIT 1), 210, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.monitor.summary');

-- 任务调度 API
INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.list', '任务调度-列表查询', '/api/v1/admin/tasks', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 211, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.list');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.detail', '任务调度-详情查询', '/api/v1/admin/tasks/{id}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 212, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.detail');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.create', '任务调度-创建任务', '/api/v1/admin/tasks', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 213, '{"method":"POST","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.create');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.update', '任务调度-更新任务', '/api/v1/admin/tasks/{id}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 214, '{"method":"PUT","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.update');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.delete', '任务调度-删除任务', '/api/v1/admin/tasks/{id}', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 215, '{"method":"DELETE","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.delete');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.pause', '任务调度-暂停任务', '/api/v1/admin/tasks/{id}/pause', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 206, '{"method":"POST","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.pause');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.resume', '任务调度-恢复任务', '/api/v1/admin/tasks/{id}/resume', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 207, '{"method":"POST","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.resume');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.trigger', '任务调度-手动触发', '/api/v1/admin/tasks/{id}/trigger', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 208, '{"method":"POST","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.trigger');

INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
SELECT 'API', 'api.admin.task.logs', '任务调度-执行日志', '/api/v1/admin/tasks/{id}/logs', NULL, NULL,
       (SELECT id FROM sys_resource WHERE code = 'admin.tasks' LIMIT 1), 209, '{"method":"GET","roles":["ADMIN"]}', 'enabled',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.admin.task.logs');

-- ============================================================================
-- 4. 验证更新结果
-- ============================================================================

-- 查看新增的资源
SELECT id, type, code, name, path, order_num
FROM sys_resource
WHERE code IN ('menu.approval', 'admin.audit', 'admin.monitor', 'admin.tasks',
               'api.approval.workflow.list', 'api.approval.workflow.pending',
               'api.admin.audit.list', 'api.admin.monitor.summary',
               'api.admin.task.list', 'api.admin.task.trigger')
ORDER BY order_num;

-- 查看系统管理下的所有子菜单
SELECT id, type, code, name, order_num
FROM sys_resource
WHERE parent_id = (SELECT id FROM sys_resource WHERE code = 'admin')
   OR code = 'admin'
ORDER BY order_num;

-- 查看总资源数
SELECT COUNT(*) AS total_resources FROM sys_resource;
