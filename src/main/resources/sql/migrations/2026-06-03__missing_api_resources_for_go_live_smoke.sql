-- 补齐后端联调中发现缺失的 API 资源定义
-- 目标：
-- 1) 已有数据库增量补齐 sys_resource
-- 2) 让 fail-closed 的 ApiResourceAuthorizationFilter 能正确匹配这些接口

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();
SET @payments_api_parent_id := (SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1);
SET @system_org_sync_parent_id := (SELECT id FROM sys_resource WHERE code='system.org-sync' LIMIT 1);
SET @admin_parent_id := (SELECT id FROM sys_resource WHERE code='admin' LIMIT 1);

INSERT INTO sys_resource (`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
VALUES
('API','api.payment.batch.precheck','支付批次-发放预校验','/api/payment/batch/{batchNo}/precheck',NULL,NULL,@payments_api_parent_id,55,JSON_OBJECT('method','GET'),'enabled',@now,@now),
('API','api.payment.batch.retry-failed','支付批次-重试失败记录','/api/payment/batch/{batchNo}/retry-failed',NULL,NULL,@payments_api_parent_id,85,JSON_OBJECT('method','POST'),'enabled',@now,@now),
('API','api.system.org.sync-task-detail','组织同步-异步任务详情','/api/system/org/sync-task/{id}',NULL,NULL,@system_org_sync_parent_id,75,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN','MANAGER')),'enabled',@now,@now),
('API','api.admin.app-registry.detail','开放应用-详情','/api/admin/app-registry/{id}',NULL,NULL,@admin_parent_id,150,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now),
('API','api.admin.integration-config.enable','集成配置-启用','/api/admin/integration-configs/{platformType}/enable',NULL,NULL,(SELECT id FROM sys_resource WHERE code='system.integration' LIMIT 1),210,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',@now,@now)
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),
  `path`=VALUES(`path`),
  `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`),
  `props_json`=VALUES(`props_json`),
  `status`='enabled',
  `update_time`=@now;

COMMIT;
