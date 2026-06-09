-- Persist compensation records for approval callbacks that fail after approval transaction commit.

CREATE TABLE IF NOT EXISTS `payroll_payment_failure` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '审批工作流ID',
  `payroll_batch_id` bigint DEFAULT NULL COMMENT '薪资批次ID',
  `business_key` varchar(100) DEFAULT NULL COMMENT '审批业务Key',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '最近失败原因',
  `status` varchar(20) NOT NULL DEFAULT 'unresolved' COMMENT 'unresolved/retrying/resolved',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `last_failed_time` datetime DEFAULT NULL COMMENT '最近失败时间',
  `last_retry_time` datetime DEFAULT NULL COMMENT '最近重试时间',
  `resolved_time` datetime DEFAULT NULL COMMENT '解决时间',
  `payment_batch_no` varchar(64) DEFAULT NULL COMMENT '关联支付批次号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow` (`workflow_id`),
  KEY `idx_status_failed_time` (`status`, `last_failed_time`),
  KEY `idx_payroll_batch` (`payroll_batch_id`),
  KEY `idx_payment_batch_no` (`payment_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资审批后置支付失败补偿表';

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
SELECT 'API','api.admin.payment.failures.list','支付补偿-失败列表','/api/admin/payment/batch/failures',NULL,NULL,
       (SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),120,JSON_OBJECT('method','GET','roles',JSON_ARRAY('ADMIN')),'enabled',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'sys_resource')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=NOW();

INSERT INTO `sys_resource`(`type`,`code`,`name`,`path`,`component`,`icon`,`parent_id`,`order_num`,`props_json`,`status`,`create_time`,`update_time`)
SELECT 'API','api.admin.payment.failures.retry','支付补偿-重试','/api/admin/payment/batch/failures/*/retry',NULL,NULL,
       (SELECT id FROM sys_resource WHERE code='payments.api' LIMIT 1),121,JSON_OBJECT('method','POST','roles',JSON_ARRAY('ADMIN')),'enabled',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'sys_resource')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`props_json`=VALUES(`props_json`),`status`='enabled',`update_time`=NOW();
