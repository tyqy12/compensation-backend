-- Migration: Align audit_log with BaseEntity fields used by MyBatis-Plus
ALTER TABLE `audit_log`
  ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

ALTER TABLE `audit_log`
  ADD COLUMN `update_by` varchar(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`;

ALTER TABLE `audit_log`
  ADD COLUMN `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`;

ALTER TABLE `audit_log`
  ADD COLUMN `version` int DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`;

