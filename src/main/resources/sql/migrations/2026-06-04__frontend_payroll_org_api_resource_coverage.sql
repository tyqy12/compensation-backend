-- 补齐前端薪酬与组织同步实际调用的 API 资源定义
-- 已有数据库需要迁移；仅更新 sys_resource / sys_role_resource，不直接调用业务接口。

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @menu_payroll_id := (SELECT id FROM sys_resource WHERE code='menu.system.payroll' LIMIT 1);
SET @system_org_sync_parent_id := (SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payroll.batches.ledger','薪酬批次-台账','/api/payroll/batches/*/ledger',NULL,NULL,@menu_payroll_id,215,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.batches.manager-review','薪酬批次-主管审核视图','/api/payroll/batches/*/manager-review',NULL,NULL,@menu_payroll_id,216,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','MANAGER')),'enabled',@now,@now),
('API','api.payroll.batches.submit-approval','薪酬批次-提交审批','/api/payroll/batches/*/submit-approval',NULL,NULL,@menu_payroll_id,219,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.batches.retry-payment','薪酬批次-重试支付','/api/payroll/batches/*/retry-payment',NULL,NULL,@menu_payroll_id,220,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.import.commit','薪酬导入-提交CSV','/api/payroll/import/commit',NULL,NULL,@menu_payroll_id,221,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.import.preview','薪酬导入-预览CSV','/api/payroll/import/preview',NULL,NULL,@menu_payroll_id,222,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.import.items','薪酬导入-条目列表','/api/payroll/import/batches/*/items',NULL,NULL,@menu_payroll_id,223,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payroll.import.item.manual','薪酬导入-手动新增条目','/api/payroll/import/batches/*/items/manual',NULL,NULL,@menu_payroll_id,224,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.import.item.update','薪酬导入-更新条目','/api/payroll/import/batches/*/items/*',NULL,NULL,@menu_payroll_id,225,JSON_OBJECT('method','PUT'),'enabled',@now,@now),
('API','api.payroll.import.item.delete','薪酬导入-删除条目','/api/payroll/import/batches/*/items/*',NULL,NULL,@menu_payroll_id,226,JSON_OBJECT('method','DELETE'),'enabled',@now,@now),
('API','api.payroll.import.salary-items','薪酬导入-薪资项列表','/api/payroll/import/salary-items',NULL,NULL,@menu_payroll_id,227,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payroll.templates.version','薪资模板-版本信息','/api/payroll/templates/*/version',NULL,NULL,@menu_payroll_id,232,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payroll.cycles.open','薪酬周期-开放周期','/api/payroll/cycles/open',NULL,NULL,@menu_payroll_id,235,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payroll.cycles.advance','薪酬周期-推进状态','/api/payroll/cycles/*/advance',NULL,NULL,@menu_payroll_id,238,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.payroll.cycles.delete','薪酬周期-删除','/api/payroll/cycles/*',NULL,NULL,@menu_payroll_id,239,JSON_OBJECT('method','DELETE'),'enabled',@now,@now),
('API','api.payroll.distributions.list','薪酬发放单-列表','/api/payroll/distributions',NULL,NULL,@menu_payroll_id,240,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.distributions.detail','薪酬发放单-详情','/api/payroll/distributions/*',NULL,NULL,@menu_payroll_id,241,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.distributions.items','薪酬发放单-明细','/api/payroll/distributions/*/items',NULL,NULL,@menu_payroll_id,242,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.distributions.reconciliation','薪酬发放单-对账任务','/api/payroll/distributions/*/reconciliation',NULL,NULL,@menu_payroll_id,243,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.reconciliations.list','薪酬对账-列表','/api/payroll/reconciliations',NULL,NULL,@menu_payroll_id,244,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.reconciliations.detail','薪酬对账-详情','/api/payroll/reconciliations/*',NULL,NULL,@menu_payroll_id,245,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.payslips.list','工资条-列表','/api/payroll/payslips',NULL,NULL,@menu_payroll_id,246,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','EMPLOYEE')),'enabled',@now,@now),
('API','api.payroll.payslips.detail','工资条-详情','/api/payroll/payslips/*',NULL,NULL,@menu_payroll_id,247,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','EMPLOYEE')),'enabled',@now,@now),
('API','api.payroll.payslips.download','工资条-下载','/api/payroll/payslips/*/download',NULL,NULL,@menu_payroll_id,248,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE','EMPLOYEE')),'enabled',@now,@now),
('API','api.payroll.reports.basic','薪酬报表-基础报表','/api/payroll/reports/basic',NULL,NULL,@menu_payroll_id,249,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.payroll.reports.basic-export','薪酬报表-基础报表导出','/api/payroll/reports/basic/export',NULL,NULL,@menu_payroll_id,250,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','FINANCE')),'enabled',@now,@now),
('API','api.system.org.sync','组织同步-手动同步','/api/system/org/sync',NULL,NULL,@system_org_sync_parent_id,157,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.system.org.history','组织同步-历史记录','/api/system/org/history',NULL,NULL,@system_org_sync_parent_id,159,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),
  `path`=VALUES(`path`),
  `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`),
  `props_json`=VALUES(`props_json`),
  `status`='enabled',
  `update_time`=@now;

-- 规范化已存在但 code 与初始化资源不一致的薪酬批次资源。
UPDATE sys_resource
SET `name`='薪酬批次-试算',
    `path`='/api/payroll/batches/*/dry-run',
    `parent_id`=@menu_payroll_id,
    `order_num`=214,
    `props_json`=JSON_OBJECT('method','POST'),
    `status`='enabled',
    `update_time`=@now
WHERE `code`='api.payroll.batches.dryrun';

UPDATE sys_resource
SET `name`='薪酬批次-试算',
    `path`='/api/payroll/batches/*/dry-run',
    `parent_id`=@menu_payroll_id,
    `order_num`=214,
    `props_json`=JSON_OBJECT('method','POST'),
    `status`='enabled',
    `update_time`=@now
WHERE `code`='api.payroll.batches.dry-run';

UPDATE sys_resource
SET `name`='薪酬批次-提交审批',
    `path`='/api/payroll/batches/*/submit-approval',
    `parent_id`=@menu_payroll_id,
    `order_num`=219,
    `props_json`=JSON_OBJECT('method','POST'),
    `status`='enabled',
    `update_time`=@now
WHERE `code`='api.payroll.batches.submit';

UPDATE sys_resource
SET `name`='薪酬批次-提交审批',
    `path`='/api/payroll/batches/*/submit-approval',
    `parent_id`=@menu_payroll_id,
    `order_num`=219,
    `props_json`=JSON_OBJECT('method','POST'),
    `status`='enabled',
    `update_time`=@now
WHERE `code`='api.payroll.batches.submit-approval';

-- 若已有角色授权了旧 code，同步授权到新 code，避免资源 code 规范化后非管理员失权。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT rr.role_id, newer.id, rr.actions_json, @now, @now
FROM sys_role_resource rr
JOIN sys_resource old ON old.id = rr.resource_id
JOIN sys_resource newer ON newer.code = 'api.payroll.batches.dry-run'
WHERE old.code = 'api.payroll.batches.dryrun'
ON DUPLICATE KEY UPDATE `actions_json`=VALUES(`actions_json`), `update_time`=@now;

INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT rr.role_id, newer.id, rr.actions_json, @now, @now
FROM sys_role_resource rr
JOIN sys_resource old ON old.id = rr.resource_id
JOIN sys_resource newer ON newer.code = 'api.payroll.batches.submit-approval'
WHERE old.code = 'api.payroll.batches.submit'
ON DUPLICATE KEY UPDATE `actions_json`=VALUES(`actions_json`), `update_time`=@now;

-- 财务角色延续既有“薪酬全量可用”策略，补齐新增 payroll API。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT r.id, res.id, NULL, @now, @now
FROM sys_role r
JOIN sys_resource res ON res.code IN (
    'api.payroll.batches.ledger',
    'api.payroll.batches.manager-review',
    'api.payroll.batches.submit-approval',
    'api.payroll.batches.retry-payment',
    'api.payroll.import.commit',
    'api.payroll.import.preview',
    'api.payroll.import.items',
    'api.payroll.import.item.manual',
    'api.payroll.import.item.update',
    'api.payroll.import.item.delete',
    'api.payroll.import.salary-items',
    'api.payroll.templates.version',
    'api.payroll.cycles.open',
    'api.payroll.cycles.advance',
    'api.payroll.cycles.delete',
    'api.payroll.distributions.list',
    'api.payroll.distributions.detail',
    'api.payroll.distributions.items',
    'api.payroll.distributions.reconciliation',
    'api.payroll.reconciliations.list',
    'api.payroll.reconciliations.detail',
    'api.payroll.payslips.list',
    'api.payroll.payslips.detail',
    'api.payroll.payslips.download',
    'api.payroll.reports.basic',
    'api.payroll.reports.basic-export'
)
WHERE r.code IN ('FINANCE', 'role.finance')
ON DUPLICATE KEY UPDATE `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
                        `update_time` = @now;

-- HR 保持周期/模板/导入配置相关权限，不扩大发放与支付操作面。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT r.id, res.id, NULL, @now, @now
FROM sys_role r
JOIN sys_resource res ON res.code IN (
    'api.payroll.import.preview',
    'api.payroll.import.items',
    'api.payroll.import.salary-items',
    'api.payroll.templates.version',
    'api.payroll.cycles.open',
    'api.payroll.cycles.advance'
)
WHERE r.code IN ('HR', 'role.hr')
ON DUPLICATE KEY UPDATE `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
                        `update_time` = @now;

-- 负责人只补主管审核视图；全量台账、发放单与工资条由方法安全限制为财务/管理员或员工本人。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT r.id, res.id, NULL, @now, @now
FROM sys_role r
JOIN sys_resource res ON res.code IN (
    'api.payroll.batches.manager-review'
)
WHERE r.code IN ('MANAGER', 'role.manager')
ON DUPLICATE KEY UPDATE `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
                        `update_time` = @now;

-- 组织同步管理接口只授权给管理员；普通部门树读取资源仍由初始化资源保持负责人可见。
INSERT INTO sys_role_resource (`role_id`,`resource_id`,`actions_json`,`create_time`,`update_time`)
SELECT r.id, res.id, CASE WHEN r.code IN ('ADMIN', 'role.admin.all') THEN JSON_ARRAY('*') ELSE NULL END, @now, @now
FROM sys_role r
JOIN sys_resource res ON res.code IN (
    'api.system.org.sync',
    'api.system.org.history'
)
WHERE r.code IN ('ADMIN', 'role.admin.all')
ON DUPLICATE KEY UPDATE `actions_json` = COALESCE(`sys_role_resource`.`actions_json`, VALUES(`actions_json`)),
                        `update_time` = @now;

-- 更新相关用户权限版本，避免缓存继续使用旧资源集合。
UPDATE sys_user u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM sys_user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE r.code IN ('ADMIN', 'FINANCE', 'HR', 'MANAGER', 'role.admin.all', 'role.finance', 'role.hr', 'role.manager')
) t ON t.user_id = u.id
SET u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_time = @now;

COMMIT;
