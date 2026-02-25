-- phpMyAdmin SQL Dump
-- version 5.2.2
-- https://www.phpmyadmin.net/
--
-- 主机： mysql-container
-- 生成日期： 2025-10-01 07:04:20
-- 服务器版本： 8.0.41
-- PHP 版本： 8.2.27

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- 数据库： `s06`
--

-- --------------------------------------------------------

--
-- 表的结构 `approval_step`
--

CREATE TABLE `approval_step` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '流程ID',
  `step_no` int NOT NULL COMMENT '步骤序号',
  `step_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤名称',
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批人姓名',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '步骤状态(pending:待处理,approved:已通过,rejected:已拒绝,skipped:已跳过)',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `reject_reason` text COLLATE utf8mb4_unicode_ci COMMENT '拒绝原因',
  `approve_comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见',
  `timeout_hours` int DEFAULT '24' COMMENT '超时时间(小时)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批步骤表';

--
-- 转存表中的数据 `approval_step`
--

INSERT INTO `approval_step` (`id`, `workflow_id`, `step_no`, `step_name`, `approver_id`, `approver_name`, `status`, `approve_time`, `reject_reason`, `approve_comment`, `timeout_hours`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 1, 1, '部门负责人审批', 2, '张部长', 'APPROVED', '2025-09-27 13:38:08', NULL, 'OK step1', 24, '2025-09-27 13:38:08', '2025-09-27 13:38:08', 'alice', 'alice', 0, 1),
(2, 1, 2, '财务负责人审批', 3, '李财务', 'APPROVED', '2025-09-27 13:38:09', NULL, 'OK step2', 24, '2025-09-27 13:38:08', '2025-09-27 13:38:08', 'alice', 'alice', 0, 1),
(3, 1, 3, '总经理审批', 1, '王总', 'APPROVED', '2025-09-27 13:38:09', NULL, 'OK step3', 48, '2025-09-27 13:38:08', '2025-09-27 13:38:08', 'alice', 'alice', 0, 1),
(4, 2, 1, '部门负责人审批', 2, '张部长', 'PENDING', NULL, NULL, NULL, 24, '2025-09-27 13:44:14', '2025-09-27 13:44:14', 'alice', 'alice', 0, 0),
(5, 2, 2, '财务负责人审批', 3, '李财务', 'PENDING', NULL, NULL, NULL, 24, '2025-09-27 13:44:14', '2025-09-27 13:44:14', 'alice', 'alice', 0, 0),
(6, 2, 3, '总经理审批', 1, '王总', 'PENDING', NULL, NULL, NULL, 48, '2025-09-27 13:44:14', '2025-09-27 13:44:14', 'alice', 'alice', 0, 0),
(7, 3, 1, '部门负责人审批', 2, '张部长', 'PENDING', NULL, NULL, NULL, 24, '2025-09-27 14:00:01', '2025-09-27 14:00:01', 'alice', 'alice', 0, 0),
(8, 3, 2, '财务负责人审批', 3, '李财务', 'PENDING', NULL, NULL, NULL, 24, '2025-09-27 14:00:01', '2025-09-27 14:00:01', 'alice', 'alice', 0, 0),
(9, 3, 3, '总经理审批', 1, '王总', 'PENDING', NULL, NULL, NULL, 48, '2025-09-27 14:00:01', '2025-09-27 14:00:01', 'alice', 'alice', 0, 0),
(10, 4, 1, '部门负责人审批', 2, '张部长', 'APPROVED', '2025-09-27 14:03:06', NULL, 'OK step1', 24, '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 1),
(11, 4, 2, '财务负责人审批', 3, '李财务', 'APPROVED', '2025-09-27 14:03:07', NULL, 'OK step2', 24, '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 1),
(12, 4, 3, '总经理审批', 1, '王总', 'APPROVED', '2025-09-27 14:03:07', NULL, 'OK step3', 48, '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 1);

-- --------------------------------------------------------

--
-- 表的结构 `approval_workflow`
--

CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `workflow_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程名称',
  `workflow_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程类型(BATCH:批量支付,ADHOC:临时支付,OFFLINE:离线员工)',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务关键字(如batch_no)',
  `business_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型',
  `current_step` int DEFAULT '1' COMMENT '当前步骤',
  `total_steps` int NOT NULL COMMENT '总步骤数',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '流程状态(pending:待审批,approved:已通过,rejected:已拒绝,cancelled:已取消)',
  `initiator_id` bigint NOT NULL COMMENT '发起人ID',
  `current_approver_id` bigint DEFAULT NULL COMMENT '当前审批人ID',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `workflow_data` json DEFAULT NULL COMMENT '流程数据(JSON格式)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程表';

--
-- 转存表中的数据 `approval_workflow`
--

INSERT INTO `approval_workflow` (`id`, `workflow_name`, `workflow_type`, `business_key`, `business_type`, `current_step`, `total_steps`, `status`, `initiator_id`, `current_approver_id`, `submit_time`, `complete_time`, `workflow_data`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, '批量支付审批-BATCH_20250927_133806', 'BATCH', 'BATCH_20250927_133806', 'PAYMENT', 3, 3, 'APPROVED', 1, 1, '2025-09-27 13:38:08', '2025-09-27 13:38:09', '{\"batchNo\": \"BATCH_DEV\"}', '2025-09-27 13:38:08', '2025-09-27 13:38:08', 'alice', 'alice', 0, 3),
(2, '批量支付审批-BATCH_20250927_134412', 'BATCH', 'BATCH_20250927_134412', 'PAYMENT', 1, 3, 'PENDING', 1, 2, '2025-09-27 13:44:13', NULL, '{\"batchNo\": \"BATCH_DEV\"}', '2025-09-27 13:44:13', '2025-09-27 13:44:13', 'alice', 'alice', 0, 0),
(3, '批量支付审批-BATCH_20250927_140000', 'BATCH', 'BATCH_20250927_140000', 'PAYMENT', 1, 3, 'PENDING', 1, 2, '2025-09-27 14:00:01', NULL, '{\"batchNo\": \"BATCH_DEV\"}', '2025-09-27 14:00:01', '2025-09-27 14:00:01', 'alice', 'alice', 0, 0),
(4, '批量支付审批-BATCH_20250927_140305', 'BATCH', 'BATCH_20250927_140305', 'PAYMENT', 3, 3, 'APPROVED', 1, 1, '2025-09-27 14:03:06', '2025-09-27 14:03:07', '{\"batchNo\": \"BATCH_DEV\"}', '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 3);

-- --------------------------------------------------------

--
-- 表的结构 `audit_log`
--

CREATE TABLE `audit_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作用户名',
  `operation` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型',
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求方法(GET,POST,PUT,DELETE)',
  `request_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求URL',
  `request_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理',
  `request_params` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数',
  `response_result` text COLLATE utf8mb4_unicode_ci COMMENT '响应结果',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `execution_time` bigint DEFAULT NULL COMMENT '执行时间(毫秒)',
  `business_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务关键字',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表(5年保存期)';

--
-- 转存表中的数据 `audit_log`
--

INSERT INTO `audit_log` (`id`, `user_id`, `username`, `operation`, `method`, `request_url`, `request_ip`, `user_agent`, `request_params`, `response_result`, `error_msg`, `execution_time`, `business_type`, `business_key`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, NULL, 'alice', 'APPROVAL_START', 'POST', '/api/approval/workflows', '0:0:0:0:0:0:0:1', 'curl/8.5.0', NULL, 'WID=4', NULL, 388, 'APPROVAL', 'BATCH_20250927_140305', '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 0),
(2, NULL, 'alice', 'APPROVAL_APPROVE', 'POST', '/api/approval/workflows/4/approve', '0:0:0:0:0:0:0:1', 'curl/8.5.0', NULL, 'OK', NULL, 280, 'APPROVAL', '4', '2025-09-27 14:03:06', '2025-09-27 14:03:06', 'alice', 'alice', 0, 0),
(3, NULL, 'alice', 'APPROVAL_APPROVE', 'POST', '/api/approval/workflows/4/approve', '0:0:0:0:0:0:0:1', 'curl/8.5.0', NULL, 'OK', NULL, 197, 'APPROVAL', '4', '2025-09-27 14:03:07', '2025-09-27 14:03:07', 'alice', 'alice', 0, 0),
(4, NULL, 'alice', 'APPROVAL_APPROVE', 'POST', '/api/approval/workflows/4/approve', '0:0:0:0:0:0:0:1', 'curl/8.5.0', NULL, 'OK', NULL, 170, 'APPROVAL', '4', '2025-09-27 14:03:07', '2025-09-27 14:03:07', 'alice', 'alice', 0, 0),
(5, NULL, '1', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', 'class java.lang.Integer cannot be cast to class java.lang.Long (java.lang.Integer and java.lang.Long are in module java.base of loader \'bootstrap\')', 918, 'AUTH', '1', '2025-09-27 21:15:57', '2025-09-27 21:15:57', 'anonymousUser', 'anonymousUser', 0, 0),
(6, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 67, 'AUTH', 'admin', '2025-09-27 21:16:09', '2025-09-27 21:16:09', 'anonymousUser', 'anonymousUser', 0, 0),
(7, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 50, 'AUTH', 'admin', '2025-09-27 21:16:18', '2025-09-27 21:16:18', 'anonymousUser', 'anonymousUser', 0, 0),
(8, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 56, 'AUTH', 'admin', '2025-09-27 21:19:08', '2025-09-27 21:19:08', 'anonymousUser', 'anonymousUser', 0, 0),
(9, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', 'class java.lang.Integer cannot be cast to class java.lang.Long (java.lang.Integer and java.lang.Long are in module java.base of loader \'bootstrap\')', 1191, 'AUTH', 'admin', '2025-09-27 21:23:14', '2025-09-27 21:23:14', 'anonymousUser', 'anonymousUser', 0, 0),
(10, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', 'class java.lang.Integer cannot be cast to class java.lang.Long (java.lang.Integer and java.lang.Long are in module java.base of loader \'bootstrap\')', 237, 'AUTH', 'admin', '2025-09-27 21:23:18', '2025-09-27 21:23:18', 'anonymousUser', 'anonymousUser', 0, 0),
(11, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1138, 'AUTH', 'admin', '2025-09-27 21:27:44', '2025-09-27 21:27:44', 'anonymousUser', 'anonymousUser', 0, 0),
(12, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 247, 'AUTH', 'admin', '2025-09-27 21:28:04', '2025-09-27 21:28:04', 'anonymousUser', 'anonymousUser', 0, 0),
(13, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 419, 'AUTH', 'admin', '2025-09-27 21:32:07', '2025-09-27 21:32:07', 'anonymousUser', 'anonymousUser', 0, 0),
(14, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 192, 'AUTH', 'admin', '2025-09-27 22:12:54', '2025-09-27 22:12:54', 'anonymousUser', 'anonymousUser', 0, 0),
(15, NULL, 'admin', 'INTEGRATION_CONFIG_TEST', 'POST', '/api/system/integration/wecom/test-connection', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'INTEGRATION', 'wecom', '2025-09-27 22:13:07', '2025-09-27 22:13:07', 'admin', 'admin', 0, 0),
(16, NULL, 'admin', 'INTEGRATION_CONFIG_TEST', 'POST', '/api/system/integration/wecom/test-connection', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'INTEGRATION', 'wecom', '2025-09-27 22:13:14', '2025-09-27 22:13:14', 'admin', 'admin', 0, 0),
(17, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1818, 'AUTH', 'admin', '2025-09-28 12:59:32', '2025-09-28 12:59:32', 'anonymousUser', 'anonymousUser', 0, 0),
(18, NULL, 'admin', 'INTEGRATION_CONFIG_TEST', 'POST', '/api/system/integration/wecom/test-connection', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'INTEGRATION', 'wecom', '2025-09-28 13:51:30', '2025-09-28 13:51:30', 'admin', 'admin', 0, 0),
(19, NULL, 'admin', 'INTEGRATION_CONFIG_SAVE', 'PUT', '/api/system/integration/dingtalk', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'dingtalk', '2025-09-28 14:05:06', '2025-09-28 14:05:06', 'admin', 'admin', 0, 0),
(20, NULL, 'admin', 'INTEGRATION_CONFIG_SAVE', 'PUT', '/api/system/integration/dingtalk', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'dingtalk', '2025-09-28 14:05:14', '2025-09-28 14:05:14', 'admin', 'admin', 0, 0),
(21, NULL, 'admin', 'INTEGRATION_CONFIG_SAVE', 'PUT', '/api/system/integration/dingtalk', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'dingtalk', '2025-09-28 14:05:21', '2025-09-28 14:05:21', 'admin', 'admin', 0, 0),
(22, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-28 14:32:32', '2025-09-28 14:32:32', 'admin', 'admin', 0, 0),
(23, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 381, 'ORG', 'all', '2025-09-28 14:32:36', '2025-09-28 14:32:36', 'admin', 'admin', 0, 0),
(24, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 245, 'ORG', 'all', '2025-09-28 14:32:49', '2025-09-28 14:32:49', 'admin', 'admin', 0, 0),
(25, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 220, 'ORG', 'all', '2025-09-28 14:32:57', '2025-09-28 14:32:57', 'admin', 'admin', 0, 0),
(26, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/1/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 41, 'EMPLOYEE', '1', '2025-09-28 15:01:52', '2025-09-28 15:01:52', 'admin', 'admin', 0, 0),
(27, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/1/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 27, 'EMPLOYEE', '1', '2025-09-28 15:02:01', '2025-09-28 15:02:01', 'admin', 'admin', 0, 0),
(28, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-28 16:43:15', '2025-09-28 16:43:15', 'admin', 'admin', 0, 0),
(29, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:24:16', '2025-09-28 22:24:16', 'admin', 'admin', 0, 0),
(30, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'encryption', '2025-09-28 22:24:26', '2025-09-28 22:24:26', 'admin', 'admin', 0, 0),
(31, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'wechat', '2025-09-28 22:26:32', '2025-09-28 22:26:32', 'admin', 'admin', 0, 0),
(32, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'wechat', '2025-09-28 22:33:45', '2025-09-28 22:33:45', 'admin', 'admin', 0, 0),
(33, NULL, 'admin', 'INTEGRATION_CONFIG_SAVE', 'PUT', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'wechat', '2025-09-28 22:35:00', '2025-09-28 22:35:00', 'admin', 'admin', 0, 0),
(34, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'wechat', '2025-09-28 22:35:00', '2025-09-28 22:35:00', 'admin', 'admin', 0, 0),
(35, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:35:00', '2025-09-28 22:35:00', 'admin', 'admin', 0, 0),
(36, NULL, 'admin', 'INTEGRATION_CONFIG_TEST', 'POST', '/api/admin/integration-configs/wechat/test-connection', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 80, 'INTEGRATION', 'wechat', '2025-09-28 22:35:25', '2025-09-28 22:35:25', 'admin', 'admin', 0, 0),
(37, NULL, 'admin', 'INTEGRATION_CONFIG_DELETE', 'DELETE', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'wechat', '2025-09-28 22:36:15', '2025-09-28 22:36:15', 'admin', 'admin', 0, 0),
(38, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:36:16', '2025-09-28 22:36:16', 'admin', 'admin', 0, 0),
(39, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'wechat', '2025-09-28 22:36:20', '2025-09-28 22:36:20', 'admin', 'admin', 0, 0),
(40, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:36:38', '2025-09-28 22:36:38', 'admin', 'admin', 0, 0),
(41, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'wechat', '2025-09-28 22:36:41', '2025-09-28 22:36:41', 'admin', 'admin', 0, 0),
(42, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:37:31', '2025-09-28 22:37:31', 'admin', 'admin', 0, 0),
(43, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-28 22:37:44', '2025-09-28 22:37:44', 'admin', 'admin', 0, 0),
(44, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1858, 'ORG', 'all', '2025-09-28 22:37:50', '2025-09-28 22:37:50', 'admin', 'admin', 0, 0),
(45, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-28 22:38:07', '2025-09-28 22:38:07', 'admin', 'admin', 0, 0),
(46, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:38:57', '2025-09-28 22:38:57', 'admin', 'admin', 0, 0),
(47, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/alipay', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'alipay', '2025-09-28 22:39:05', '2025-09-28 22:39:05', 'admin', 'admin', 0, 0),
(48, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:54:42', '2025-09-28 22:54:42', 'admin', 'admin', 0, 0),
(49, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:55:23', '2025-09-28 22:55:23', 'admin', 'admin', 0, 0),
(50, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/feishu', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'feishu', '2025-09-28 22:56:14', '2025-09-28 22:56:14', 'admin', 'admin', 0, 0),
(51, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/dingtalk', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'dingtalk', '2025-09-28 22:56:22', '2025-09-28 22:56:22', 'admin', 'admin', 0, 0),
(52, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/sms', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'sms', '2025-09-28 22:56:33', '2025-09-28 22:56:33', 'admin', 'admin', 0, 0),
(53, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/email', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'email', '2025-09-28 22:56:49', '2025-09-28 22:56:49', 'admin', 'admin', 0, 0),
(54, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-28 22:57:01', '2025-09-28 22:57:01', 'admin', 'admin', 0, 0),
(55, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:58:20', '2025-09-28 22:58:20', 'admin', 'admin', 0, 0),
(56, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:58:28', '2025-09-28 22:58:28', 'admin', 'admin', 0, 0),
(57, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 22:59:41', '2025-09-28 22:59:41', 'admin', 'admin', 0, 0),
(58, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 23:01:19', '2025-09-28 23:01:19', 'admin', 'admin', 0, 0),
(59, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 23:01:55', '2025-09-28 23:01:55', 'admin', 'admin', 0, 0),
(60, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 23:11:19', '2025-09-28 23:11:19', 'admin', 'admin', 0, 0),
(61, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-28 23:11:25', '2025-09-28 23:11:25', 'admin', 'admin', 0, 0),
(62, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/2/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 49, 'EMPLOYEE', '2', '2025-09-29 00:12:00', '2025-09-29 00:12:00', 'admin', 'admin', 0, 0),
(63, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 00:12:33', '2025-09-29 00:12:33', 'admin', 'admin', 0, 0),
(64, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-29 00:12:35', '2025-09-29 00:12:35', 'admin', 'admin', 0, 0),
(65, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 12:13:43', '2025-09-29 12:13:43', 'admin', 'admin', 0, 0),
(66, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-29 12:17:16', '2025-09-29 12:17:16', 'admin', 'admin', 0, 0),
(67, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 0, 'ORG', 'wecom', '2025-09-29 12:18:17', '2025-09-29 12:18:17', 'admin', 'admin', 0, 0),
(68, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 2, 'ORG', 'all', '2025-09-29 12:22:54', '2025-09-29 12:22:54', 'admin', 'admin', 0, 0),
(69, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1430, 'ORG', 'all', '2025-09-29 12:23:08', '2025-09-29 12:23:08', 'admin', 'admin', 0, 0),
(70, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 356, 'ORG', 'all', '2025-09-29 12:23:23', '2025-09-29 12:23:23', 'admin', 'admin', 0, 0),
(71, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 12:23:23', '2025-09-29 12:23:23', 'admin', 'admin', 0, 0),
(72, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 97, 'ORG', 'wechat', '2025-09-29 12:28:42', '2025-09-29 12:28:42', 'admin', 'admin', 0, 0),
(73, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 567, 'ORG', 'all', '2025-09-29 12:28:56', '2025-09-29 12:28:56', 'admin', 'admin', 0, 0),
(74, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 12:30:20', '2025-09-29 12:30:20', 'admin', 'admin', 0, 0),
(75, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 12:32:28', '2025-09-29 12:32:28', 'admin', 'admin', 0, 0),
(76, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 86, 'ORG', 'wechat', '2025-09-29 12:33:19', '2025-09-29 12:33:19', 'admin', 'admin', 0, 0),
(77, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 63, 'ORG', 'wechat', '2025-09-29 12:34:26', '2025-09-29 12:34:26', 'admin', 'admin', 0, 0),
(78, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 458, 'ORG', 'all', '2025-09-29 12:36:21', '2025-09-29 12:36:21', 'admin', 'admin', 0, 0),
(79, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 12:36:23', '2025-09-29 12:36:23', 'admin', 'admin', 0, 0),
(80, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 57, 'ORG', 'wechat', '2025-09-29 12:39:04', '2025-09-29 12:39:04', 'admin', 'admin', 0, 0),
(81, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1, 'ORG', 'all', '2025-09-29 12:39:07', '2025-09-29 12:39:07', 'admin', 'admin', 0, 0),
(82, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1074, 'ORG', 'all', '2025-09-29 12:39:52', '2025-09-29 12:39:52', 'admin', 'admin', 0, 0),
(83, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 12:39:52', '2025-09-29 12:39:52', 'admin', 'admin', 0, 0),
(84, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 84, 'ORG', 'wechat', '2025-09-29 12:39:56', '2025-09-29 12:39:56', 'admin', 'admin', 0, 0),
(85, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 132, 'ORG', 'wechat', '2025-09-29 12:44:32', '2025-09-29 12:44:32', 'admin', 'admin', 0, 0),
(86, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 283, 'AUTH', 'admin', '2025-09-29 13:02:08', '2025-09-29 13:02:08', 'anonymousUser', 'anonymousUser', 0, 0),
(87, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 13:02:09', '2025-09-29 13:02:09', 'admin', 'admin', 0, 0),
(88, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/2/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 45, 'EMPLOYEE', '2', '2025-09-29 13:03:04', '2025-09-29 13:03:04', 'admin', 'admin', 0, 0),
(89, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/1/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 88, 'EMPLOYEE', '1', '2025-09-29 13:14:00', '2025-09-29 13:14:00', 'admin', 'admin', 0, 0),
(90, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 144, 'ORG', 'wechat', '2025-09-29 13:26:17', '2025-09-29 13:26:17', 'admin', 'admin', 0, 0),
(91, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 14:04:56', '2025-09-29 14:04:56', 'admin', 'admin', 0, 0),
(92, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 56, 'ORG', 'wechat', '2025-09-29 14:04:58', '2025-09-29 14:04:58', 'admin', 'admin', 0, 0),
(93, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 2125, 'ORG', 'all', '2025-09-29 14:05:03', '2025-09-29 14:05:03', 'admin', 'admin', 0, 0),
(94, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 14:05:03', '2025-09-29 14:05:03', 'admin', 'admin', 0, 0),
(95, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1963, 'ORG', 'all', '2025-09-29 14:05:07', '2025-09-29 14:05:07', 'admin', 'admin', 0, 0),
(96, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 14:05:07', '2025-09-29 14:05:07', 'admin', 'admin', 0, 0),
(97, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1748, 'ORG', 'all', '2025-09-29 14:05:11', '2025-09-29 14:05:11', 'admin', 'admin', 0, 0),
(98, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-29 14:05:11', '2025-09-29 14:05:11', 'admin', 'admin', 0, 0),
(99, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1813, 'ORG', 'all', '2025-09-29 14:05:14', '2025-09-29 14:05:14', 'admin', 'admin', 0, 0),
(100, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-29 15:08:07', '2025-09-29 15:08:07', 'admin', 'admin', 0, 0),
(101, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 105, 'ORG', 'wechat', '2025-09-29 15:08:11', '2025-09-29 15:08:11', 'admin', 'admin', 0, 0),
(102, NULL, '林语', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 266, 'AUTH', '林语', '2025-09-29 17:03:24', '2025-09-29 17:03:24', 'anonymousUser', 'anonymousUser', 0, 0),
(103, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 208, 'AUTH', 'admin', '2025-09-30 00:00:27', '2025-09-30 00:00:27', 'anonymousUser', 'anonymousUser', 0, 0),
(104, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 07:56:38', '2025-09-30 07:56:38', 'admin', 'admin', 0, 0),
(105, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 45, 'ORG', 'wechat', '2025-09-30 07:56:40', '2025-09-30 07:56:40', 'admin', 'admin', 0, 0),
(106, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 310, 'AUTH', 'admin', '2025-09-30 10:59:28', '2025-09-30 10:59:28', 'anonymousUser', 'anonymousUser', 0, 0),
(107, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 281, 'AUTH', 'admin', '2025-09-30 13:12:08', '2025-09-30 13:12:08', 'anonymousUser', 'anonymousUser', 0, 0),
(108, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 23, 'ORG', 'wechat', '2025-09-30 14:50:07', '2025-09-30 14:50:07', 'admin', 'admin', 0, 0),
(109, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 52, 'ORG', 'wechat', '2025-09-30 14:52:44', '2025-09-30 14:52:44', 'admin', 'admin', 0, 0),
(110, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 173, 'AUTH', 'admin', '2025-09-30 17:20:45', '2025-09-30 17:20:45', 'anonymousUser', 'anonymousUser', 0, 0),
(111, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 50, 'ORG', 'wechat', '2025-09-30 17:25:43', '2025-09-30 17:25:43', 'admin', 'admin', 0, 0),
(112, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:25:44', '2025-09-30 17:25:44', 'admin', 'admin', 0, 0),
(113, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:25:56', '2025-09-30 17:25:56', 'admin', 'admin', 0, 0),
(114, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:28:17', '2025-09-30 17:28:17', 'admin', 'admin', 0, 0),
(115, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:28:27', '2025-09-30 17:28:27', 'admin', 'admin', 0, 0),
(116, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 185, 'AUTH', 'admin', '2025-09-30 17:28:44', '2025-09-30 17:28:44', 'anonymousUser', 'anonymousUser', 0, 0),
(117, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:28:44', '2025-09-30 17:28:44', 'admin', 'admin', 0, 0),
(118, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:30:15', '2025-09-30 17:30:15', 'admin', 'admin', 0, 0),
(119, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:30:51', '2025-09-30 17:30:51', 'admin', 'admin', 0, 0),
(120, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:32:08', '2025-09-30 17:32:08', 'admin', 'admin', 0, 0),
(121, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:33:08', '2025-09-30 17:33:08', 'admin', 'admin', 0, 0),
(122, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 149, 'AUTH', 'admin', '2025-09-30 17:33:23', '2025-09-30 17:33:23', 'anonymousUser', 'anonymousUser', 0, 0),
(123, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:33:24', '2025-09-30 17:33:24', 'admin', 'admin', 0, 0),
(124, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:44:50', '2025-09-30 17:44:50', 'admin', 'admin', 0, 0),
(125, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 230, 'AUTH', 'admin', '2025-09-30 17:45:29', '2025-09-30 17:45:29', 'anonymousUser', 'anonymousUser', 0, 0),
(126, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:45:30', '2025-09-30 17:45:30', 'admin', 'admin', 0, 0),
(127, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 236, 'AUTH', 'admin', '2025-09-30 17:46:24', '2025-09-30 17:46:24', 'anonymousUser', 'anonymousUser', 0, 0),
(128, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:46:25', '2025-09-30 17:46:25', 'admin', 'admin', 0, 0),
(129, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:48:59', '2025-09-30 17:48:59', 'admin', 'admin', 0, 0),
(130, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:50:10', '2025-09-30 17:50:10', 'admin', 'admin', 0, 0),
(131, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 230, 'AUTH', 'admin', '2025-09-30 17:50:34', '2025-09-30 17:50:34', 'anonymousUser', 'anonymousUser', 0, 0),
(132, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:50:35', '2025-09-30 17:50:35', 'admin', 'admin', 0, 0),
(133, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 17:52:41', '2025-09-30 17:52:41', 'admin', 'admin', 0, 0),
(134, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0', NULL, 'OK', NULL, 157, 'AUTH', 'admin', '2025-09-30 17:53:50', '2025-09-30 17:53:50', 'anonymousUser', 'anonymousUser', 0, 0),
(135, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0', NULL, 'OK', NULL, 32, 'ORG', 'wechat', '2025-09-30 17:54:08', '2025-09-30 17:54:08', 'admin', 'admin', 0, 0),
(136, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0', NULL, 'OK', NULL, 3904, 'ORG', 'all', '2025-09-30 17:54:20', '2025-09-30 17:54:20', 'admin', 'admin', 0, 0),
(137, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 17:54:20', '2025-09-30 17:54:20', 'admin', 'admin', 0, 0),
(138, NULL, '林语', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 150, 'AUTH', '林语', '2025-09-30 17:56:07', '2025-09-30 17:56:07', 'anonymousUser', 'anonymousUser', 0, 0),
(139, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 141, 'AUTH', 'admin', '2025-09-30 17:56:38', '2025-09-30 17:56:38', 'anonymousUser', 'anonymousUser', 0, 0),
(140, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 18:05:14', '2025-09-30 18:05:14', 'admin', 'admin', 0, 0),
(141, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 37, 'ORG', 'wechat', '2025-09-30 18:14:22', '2025-09-30 18:14:22', 'admin', 'admin', 0, 0),
(142, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 18:14:24', '2025-09-30 18:14:24', 'admin', 'admin', 0, 0),
(143, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/5/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 33, 'EMPLOYEE', '5', '2025-09-30 19:10:49', '2025-09-30 19:10:49', 'admin', 'admin', 0, 0),
(144, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 19:11:58', '2025-09-30 19:11:58', 'admin', 'admin', 0, 0),
(145, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 56, 'ORG', 'wechat', '2025-09-30 19:12:00', '2025-09-30 19:12:00', 'admin', 'admin', 0, 0),
(146, NULL, '林语', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 175, 'AUTH', '林语', '2025-09-30 19:51:27', '2025-09-30 19:51:27', 'anonymousUser', 'anonymousUser', 0, 0),
(147, NULL, 'admin', 'LOGIN_PASSWORD', 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 128, 'AUTH', 'admin', '2025-09-30 19:51:53', '2025-09-30 19:51:53', 'anonymousUser', 'anonymousUser', 0, 0),
(148, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 42, 'ORG', 'wechat', '2025-09-30 19:51:58', '2025-09-30 19:51:58', 'admin', 'admin', 0, 0),
(149, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 17654, 'ORG', 'all', '2025-09-30 19:52:20', '2025-09-30 19:52:20', 'admin', 'admin', 0, 0),
(150, NULL, 'admin', 'ORG_SYNC_ALL', 'POST', '/api/system/org/sync', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 3926, 'ORG', 'all', '2025-09-30 19:53:09', '2025-09-30 19:53:09', 'admin', 'admin', 0, 0),
(151, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 45, 'ORG', 'wechat', '2025-09-30 19:59:23', '2025-09-30 19:59:23', 'admin', 'admin', 0, 0),
(152, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 19:59:24', '2025-09-30 19:59:24', 'admin', 'admin', 0, 0);
INSERT INTO `audit_log` (`id`, `user_id`, `username`, `operation`, `method`, `request_url`, `request_ip`, `user_agent`, `request_params`, `response_result`, `error_msg`, `execution_time`, `business_type`, `business_key`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(153, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 20:17:19', '2025-09-30 20:17:19', 'admin', 'admin', 0, 0),
(154, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/dingtalk', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'dingtalk', '2025-09-30 20:17:24', '2025-09-30 20:17:24', 'admin', 'admin', 0, 0),
(155, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'wechat', '2025-09-30 20:17:31', '2025-09-30 20:17:31', 'admin', 'admin', 0, 0),
(156, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 20:17:37', '2025-09-30 20:17:37', 'admin', 'admin', 0, 0),
(157, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/wechat', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'wechat', '2025-09-30 20:17:39', '2025-09-30 20:17:39', 'admin', 'admin', 0, 0),
(158, NULL, 'admin', 'INTEGRATION_CONFIG_TEST', 'POST', '/api/admin/integration-configs/wechat/test-connection', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 26, 'INTEGRATION', 'wechat', '2025-09-30 20:25:15', '2025-09-30 20:25:15', 'admin', 'admin', 0, 0),
(159, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 184, 'ORG', 'wechat', '2025-09-30 20:53:58', '2025-09-30 20:53:58', 'admin', 'admin', 0, 0),
(160, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 71, 'ORG', 'wechat', '2025-09-30 20:54:10', '2025-09-30 20:54:10', 'admin', 'admin', 0, 0),
(161, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 33, 'ORG', 'wechat', '2025-09-30 21:02:03', '2025-09-30 21:02:03', 'admin', 'admin', 0, 0),
(162, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 34, 'ORG', 'wechat', '2025-09-30 21:04:01', '2025-09-30 21:04:01', 'admin', 'admin', 0, 0),
(163, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 62, 'ORG', 'wechat', '2025-09-30 21:05:04', '2025-09-30 21:05:04', 'admin', 'admin', 0, 0),
(164, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-09-30 21:05:06', '2025-09-30 21:05:06', 'admin', 'admin', 0, 0),
(165, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 21:06:52', '2025-09-30 21:06:52', 'admin', 'admin', 0, 0),
(166, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 97, 'ORG', 'wechat', '2025-09-30 21:20:50', '2025-09-30 21:20:50', 'admin', 'admin', 0, 0),
(167, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 21:25:05', '2025-09-30 21:25:05', 'admin', 'admin', 0, 0),
(168, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 21:25:11', '2025-09-30 21:25:11', 'admin', 'admin', 0, 0),
(169, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 21:25:13', '2025-09-30 21:25:13', 'admin', 'admin', 0, 0),
(170, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 1, 'ORG', 'all', '2025-09-30 21:25:14', '2025-09-30 21:25:14', 'admin', 'admin', 0, 0),
(171, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'ORG', 'all', '2025-09-30 21:25:18', '2025-09-30 21:25:18', 'admin', 'admin', 0, 0),
(172, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 46, 'ORG', 'wechat', '2025-09-30 21:32:45', '2025-09-30 21:32:45', 'admin', 'admin', 0, 0),
(173, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 40, 'ORG', 'wechat', '2025-09-30 21:37:01', '2025-09-30 21:37:01', 'admin', 'admin', 0, 0),
(174, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 40, 'ORG', 'wechat', '2025-09-30 21:37:04', '2025-09-30 21:37:04', 'admin', 'admin', 0, 0),
(175, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 30, 'ORG', 'wechat', '2025-09-30 21:39:47', '2025-09-30 21:39:47', 'admin', 'admin', 0, 0),
(176, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 54, 'ORG', 'wechat', '2025-09-30 23:05:09', '2025-09-30 23:05:09', 'admin', 'admin', 0, 0),
(177, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 39, 'ORG', 'feishu', '2025-09-30 23:28:55', '2025-09-30 23:28:55', 'admin', 'admin', 0, 0),
(178, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 47, 'ORG', 'dingtalk', '2025-09-30 23:28:58', '2025-09-30 23:28:58', 'admin', 'admin', 0, 0),
(179, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 23, 'ORG', 'wechat', '2025-10-01 10:27:28', '2025-10-01 10:27:28', 'admin', 'admin', 0, 0),
(180, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 11:25:33', '2025-10-01 11:25:33', 'admin', 'admin', 0, 0),
(181, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 78, 'ORG', 'wechat', '2025-10-01 11:25:36', '2025-10-01 11:25:36', 'admin', 'admin', 0, 0),
(182, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 82, 'ORG', 'wechat', '2025-10-01 11:45:47', '2025-10-01 11:45:47', 'admin', 'admin', 0, 0),
(183, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 47, 'ORG', 'wechat', '2025-10-01 11:56:11', '2025-10-01 11:56:11', 'admin', 'admin', 0, 0),
(184, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 12:41:49', '2025-10-01 12:41:49', 'admin', 'admin', 0, 0),
(185, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'encryption', '2025-10-01 12:41:53', '2025-10-01 12:41:53', 'admin', 'admin', 0, 0),
(186, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 13:03:48', '2025-10-01 13:03:48', 'admin', 'admin', 0, 0),
(187, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 37, 'ORG', 'wechat', '2025-10-01 13:03:51', '2025-10-01 13:03:51', 'admin', 'admin', 0, 0),
(188, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 26, 'ORG', 'wechat', '2025-10-01 13:16:23', '2025-10-01 13:16:23', 'admin', 'admin', 0, 0),
(189, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 38, 'ORG', 'wechat', '2025-10-01 13:22:21', '2025-10-01 13:22:21', 'admin', 'admin', 0, 0),
(190, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 88, 'ORG', 'wechat', '2025-10-01 13:31:23', '2025-10-01 13:31:23', 'admin', 'admin', 0, 0),
(191, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'FAILED', NULL, 34, 'ORG', 'dingtalk', '2025-10-01 13:32:37', '2025-10-01 13:32:37', 'admin', 'admin', 0, 0),
(192, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 142, 'ORG', 'wechat', '2025-10-01 13:32:37', '2025-10-01 13:32:37', 'admin', 'admin', 0, 0),
(193, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 42, 'ORG', 'wechat', '2025-10-01 13:59:06', '2025-10-01 13:59:06', 'admin', 'admin', 0, 0),
(194, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 44, 'ORG', 'wechat', '2025-10-01 14:00:13', '2025-10-01 14:00:13', 'admin', 'admin', 0, 0),
(195, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 39, 'ORG', 'wechat', '2025-10-01 14:01:43', '2025-10-01 14:01:43', 'admin', 'admin', 0, 0),
(196, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 106, 'ORG', 'wechat', '2025-10-01 14:08:50', '2025-10-01 14:08:50', 'admin', 'admin', 0, 0),
(197, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 45, 'ORG', 'wechat', '2025-10-01 14:09:06', '2025-10-01 14:09:06', 'admin', 'admin', 0, 0),
(198, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 54, 'ORG', 'wechat', '2025-10-01 14:14:59', '2025-10-01 14:14:59', 'admin', 'admin', 0, 0),
(199, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 32, 'ORG', 'wechat', '2025-10-01 14:16:22', '2025-10-01 14:16:22', 'admin', 'admin', 0, 0),
(200, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 57, 'ORG', 'wechat', '2025-10-01 14:18:36', '2025-10-01 14:18:36', 'admin', 'admin', 0, 0),
(201, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 41, 'ORG', 'wechat', '2025-10-01 14:19:59', '2025-10-01 14:19:59', 'admin', 'admin', 0, 0),
(202, NULL, 'admin', 'ORG_CHECK', 'GET', '/api/system/org/check', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 21, 'ORG', 'wechat', '2025-10-01 14:41:44', '2025-10-01 14:41:44', 'admin', 'admin', 0, 0),
(203, NULL, 'admin', 'DECRYPT_ID_CARD', 'GET', '/api/employee/7/id-card', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'NOT_FOUND', NULL, 22, 'EMPLOYEE', '7', '2025-10-01 14:44:00', '2025-10-01 14:44:00', 'admin', 'admin', 0, 0),
(204, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 14:44:08', '2025-10-01 14:44:08', 'admin', 'admin', 0, 0),
(205, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'encryption', '2025-10-01 14:44:14', '2025-10-01 14:44:14', 'admin', 'admin', 0, 0),
(206, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'encryption', '2025-10-01 14:48:47', '2025-10-01 14:48:47', 'admin', 'admin', 0, 0),
(207, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 14:48:47', '2025-10-01 14:48:47', 'admin', 'admin', 0, 0),
(208, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 15:02:15', '2025-10-01 15:02:15', 'admin', 'admin', 0, 0),
(209, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', '配置不存在', 0, 'INTEGRATION', 'encryption', '2025-10-01 15:02:17', '2025-10-01 15:02:17', 'admin', 'admin', 0, 0),
(210, NULL, 'admin', 'INTEGRATION_CONFIG_SAVE', 'PUT', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'encryption', '2025-10-01 15:02:37', '2025-10-01 15:02:37', 'admin', 'admin', 0, 0),
(211, NULL, 'admin', 'INTEGRATION_CONFIG_READ', 'GET', '/api/admin/integration-configs/encryption', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'encryption', '2025-10-01 15:02:38', '2025-10-01 15:02:38', 'admin', 'admin', 0, 0),
(212, NULL, 'admin', 'INTEGRATION_CONFIG_LIST', 'GET', '/api/admin/integration-configs', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36', NULL, 'OK', NULL, 0, 'INTEGRATION', 'ALL', '2025-10-01 15:02:38', '2025-10-01 15:02:38', 'admin', 'admin', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `employee`
--

CREATE TABLE `employee` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `employee_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工工号',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工姓名',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号码',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱地址',
  `encrypted_id_card` text COLLATE utf8mb4_unicode_ci COMMENT '加密后的身份证号(SM4+AES双重加密)',
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门',
  `position` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职位',
  `employment_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'full_time' COMMENT '用工类型(full_time/part_time)',
  `platform_user_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台用户ID(企微/钉钉/飞书)',
  `platform_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',
  `is_offline` tinyint(1) DEFAULT '0' COMMENT '是否离线员工(0:否,1:是)',
  `manager_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'active' COMMENT '员工状态(active:在职,inactive:离职,suspended:停职)',
  `bank_account` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行卡号(加密存储)',
  `bank_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户银行',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工信息表';

--
-- 转存表中的数据 `employee`
--

INSERT INTO `employee` (`id`, `employee_id`, `name`, `phone`, `email`, `encrypted_id_card`, `department`, `position`, `employment_type`, `platform_user_id`, `platform_type`, `is_offline`, `manager_id`, `hire_date`, `status`, `bank_account`, `bank_name`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, '00057', '张三', '18582000504', NULL, NULL, NULL, NULL, 'full_time', '11', 'wechat', 0, NULL, NULL, 'active', NULL, NULL, '2025-09-28 15:00:56', '2025-09-29 04:37:55', 'admin', 'admin', 0, 3),
(2, 'EMP002', '李四', '13987654321', 'lisi@company.com', NULL, '产品部', '产品经理', 'full_time', '11', 'dingtalk', 0, NULL, NULL, 'active', '6225681234567890', '建设银行', '2025-09-28 07:31:23', '2025-09-29 05:12:57', NULL, NULL, 0, 0),
(3, 'EMP003', '王五', '13611223344', 'wangwu@company.com', NULL, '技术部', '前端工程师', 'full_time', NULL, NULL, 0, NULL, NULL, 'active', '6227001234567890', '招商银行', '2025-09-28 07:31:23', '2025-09-28 07:31:23', NULL, NULL, 0, 0),
(4, '林语', '蒋飘', NULL, NULL, NULL, '公会', '', 'full_time', 'Yang', 'wechat', 0, NULL, NULL, 'active', NULL, NULL, '2025-09-29 14:00:00', '2025-09-29 14:00:00', 'system', 'system', 0, 164),
(5, 'XieLiJuan', '谢丽娟', NULL, NULL, NULL, '总部', '', 'full_time', 'XieLiJuan', 'wechat', 0, NULL, NULL, 'active', NULL, NULL, '2025-09-29 14:00:01', '2025-09-29 09:05:58', 'system', 'system', 0, 83),
(6, '11104', '莉莉', NULL, NULL, NULL, NULL, NULL, 'part_time', NULL, NULL, 0, NULL, NULL, 'active', NULL, NULL, '2025-09-30 19:43:03', '2025-09-30 19:43:03', 'admin', 'admin', 0, 0),
(7, '00145', 'all out', NULL, NULL, NULL, '公会', 'HR', 'full_time', 'allout', 'wechat', 0, NULL, NULL, 'active', NULL, NULL, '2025-10-01 14:43:24', '2025-10-01 14:43:24', 'admin', 'admin', 0, 2);

-- --------------------------------------------------------

--
-- 表的结构 `employee_department`
--

CREATE TABLE `employee_department` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `platform_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台类型',
  `platform_dept_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台部门ID',
  `local_dept_id` bigint DEFAULT NULL COMMENT '本地部门ID',
  `dept_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `is_primary` tinyint(1) DEFAULT '0' COMMENT '是否主部门',
  `order_num` int DEFAULT '0' COMMENT '顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工-部门多对多关联';

--
-- 转存表中的数据 `employee_department`
--

INSERT INTO `employee_department` (`id`, `employee_id`, `platform_type`, `platform_dept_id`, `local_dept_id`, `dept_name`, `is_primary`, `order_num`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 7, 'wechat', NULL, NULL, '总部', 1, 1, '2025-10-01 14:43:25', '2025-10-01 14:43:25', 'admin', 'admin', 0, 0),
(2, 7, 'wechat', NULL, NULL, '公会', 0, 2, '2025-10-01 14:43:25', '2025-10-01 14:43:25', 'admin', 'admin', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `integration_config`
--

CREATE TABLE `integration_config` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `platform_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay)',
  `config_json` text COLLATE utf8mb4_unicode_ci COMMENT '配置JSON',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台集成配置表';

--
-- 转存表中的数据 `integration_config`
--

INSERT INTO `integration_config` (`id`, `platform_type`, `config_json`, `enabled`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'dingtalk', 'HcvaHjURv9I1KgUtSWiajW/d/taPkM0cS1a/sxOk+3U=', 0, '2025-09-28 14:05:06', '2025-09-28 14:05:06', 'admin', 'admin', 0, 2),
(2, 'wechat', 'sSwPJONzV7B0K3Md00lw/JvQkFISV66jnt8oQCFnMcfVDAafY8VLp+hETmuTv+O/81rcpud3OwL22cc+jrpAgEWGxYOtLgAivskDOtWHb3OGBFz8FCZqBtXs8v42H21mxyNVPZ5OTA89QMlR7iEuQXfyAoA43AUIOOM8aDvtIcSw5fQ8/bKFlaVzgIAzXvduDHGmRDo69oA/D5TdrDjK+cHPz7LxqNZZwV+BdUKdxFjHdW9lt1CA2YaSTqRC5F/v', 1, '2025-09-28 22:34:59', '2025-09-28 14:37:20', 'admin', 'admin', 0, 1),
(3, 'encryption', 'ULf8rLysghe2tXA5P3paKGJkplNKg6tY/qtVG123DixIkYW4ORsem24zHQerFUXqgcViHbiFGAZ7xa9k2JbWgSiRrwW1ZnCQGxdFm4l4UMHgVsWEa9vBP8vJGx+p9E2PTVbRLcXvNGI0RIffQdPrUCwhyn1qkl8cHa7iBGUk6kEBDTlEnhIHhVa24CT0ami1RCcHEprGsGVz8eCvdms75WqslZ4VrR4niWljnbiTc1s/AADTsl+i8jtY1IVrXDJxxgkjpH2efeTes79j5SfAow==', 1, '2025-10-01 15:02:37', '2025-10-01 15:02:37', 'admin', 'admin', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `notification_record`
--

CREATE TABLE `notification_record` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `notification_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知渠道',
  `recipient_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接收人ID',
  `recipient_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接收人姓名',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通知标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '通知内容',
  `template_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板ID',
  `template_params` text COLLATE utf8mb4_unicode_ci COMMENT '模板参数(JSON)',
  `business_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务类型',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务标识',
  `status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '通知状态',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `max_retry` int DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `send_time` datetime DEFAULT NULL COMMENT '实际发送时间',
  `response_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应码',
  `response_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应消息',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `fallback_channels` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败回退渠道(JSON数组)',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发送记录表';

--
-- 转存表中的数据 `notification_record`
--

INSERT INTO `notification_record` (`id`, `notification_type`, `channel`, `recipient_id`, `recipient_name`, `title`, `content`, `template_id`, `template_params`, `business_type`, `business_key`, `status`, `retry_count`, `max_retry`, `next_retry_time`, `send_time`, `response_code`, `response_message`, `error_message`, `priority`, `fallback_channels`, `is_read`, `read_time`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'APPROVAL_RESULT', 'wechat', 'wx_10001', '李四', '审批结果通知', '您的审批请求已通过，请登录\r\n  系统查看详情。', NULL, NULL, 'APPROVAL', 'FLOW20250928015', 'success', 0, 3, NULL, '2025-09-28 14:21:21', '200', '发送成功', NULL, 3, NULL, 1, '2025-09-28 14:21:21', '2025-09-28 14:21:21', '2025-09-28 14:21:21', 'system', 'system', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `org_department`
--

CREATE TABLE `org_department` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `platform_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',
  `platform_dept_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台部门ID',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `parent_platform_dept_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台父部门ID',
  `parent_id` bigint DEFAULT NULL COMMENT '本地父部门ID',
  `order_num` int DEFAULT NULL COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';

--
-- 转存表中的数据 `org_department`
--

INSERT INTO `org_department` (`id`, `platform_type`, `platform_dept_id`, `name`, `parent_platform_dept_id`, `parent_id`, `order_num`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'wechat', '5', '总部', NULL, NULL, NULL, '2025-09-29 16:00:01', '2025-09-29 16:00:01', 'system', 'system', 0, 0),
(2, 'wechat', '6', '公会', '5', NULL, NULL, '2025-09-29 16:00:01', '2025-09-29 16:00:01', 'system', 'system', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `payment_batch`
--

CREATE TABLE `payment_batch` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `batch_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次号',
  `batch_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次名称',
  `payment_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付类型',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '总金额',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总笔数',
  `success_count` int DEFAULT '0' COMMENT '成功笔数',
  `failed_count` int DEFAULT '0' COMMENT '失败笔数',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'draft' COMMENT '批次状态(draft:草稿,submitted:已提交,approved:已审批,processing:处理中,completed:已完成,failed:失败)',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `process_start_time` datetime DEFAULT NULL COMMENT '开始处理时间',
  `process_end_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `processor_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `remark` text COLLATE utf8mb4_unicode_ci COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付批次表';

--
-- 转存表中的数据 `payment_batch`
--

INSERT INTO `payment_batch` (`id`, `batch_no`, `batch_name`, `payment_type`, `total_amount`, `total_count`, `success_count`, `failed_count`, `status`, `submit_time`, `approve_time`, `process_start_time`, `process_end_time`, `approver_id`, `processor_id`, `remark`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'BATCH_20240115001', '2024年1月工资发放', 'salary', 28500.00, 3, 2, 1, 'processing', '2024-01-15 09:00:00', '2024-01-15 10:30:00', '2024-01-15 14:00:00', NULL, 1, 1, '月度工资批量发放，包含技术部和产品部员工', '2025-09-28 07:32:19', '2025-09-28 07:32:19', NULL, NULL, 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `payment_record`
--

CREATE TABLE `payment_record` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `batch_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次号',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `payment_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付类型(salary:工资,bonus:奖金,reimbursement:报销)',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'CNY' COMMENT '币种',
  `payment_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'alipay' COMMENT '支付方式(alipay:支付宝)',
  `recipient_account` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收款账户',
  `recipient_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收款人姓名',
  `payment_desc` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付描述',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '支付状态(pending:待处理,processing:处理中,success:成功,failed:失败,cancelled:已取消)',
  `alipay_order_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付宝订单号',
  `alipay_trade_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付宝交易号',
  `error_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_msg` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `payment_time` datetime DEFAULT NULL COMMENT '实际支付时间',
  `notification_time` datetime DEFAULT NULL COMMENT '支付宝通知时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

--
-- 转存表中的数据 `payment_record`
--

INSERT INTO `payment_record` (`id`, `batch_no`, `employee_id`, `payment_type`, `amount`, `currency`, `payment_method`, `recipient_account`, `recipient_name`, `payment_desc`, `status`, `alipay_order_no`, `alipay_trade_no`, `error_code`, `error_msg`, `payment_time`, `notification_time`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'BATCH_20240115001', 1, 'salary', 12000.00, 'CNY', 'alipay', '13812345678', '张三', '2024年1月工资', 'success', 'COMP_20240115_001', '2024011522000123456789', NULL, NULL, '2024-01-15 14:05:23', '2024-01-15 14:05:25', '2025-09-28 07:34:59', '2025-09-28 07:34:59', NULL, NULL, 0, 0),
(2, 'BATCH_20240115001', 2, 'salary', 8500.00, 'CNY', 'alipay', '13987654321', '李四', '2024年1月工资', 'success', 'COMP_20240115_002', '2024011522000123456790', NULL, NULL, '2024-01-15 14:06:12', '2024-01-15 14:06:14', '2025-09-28 07:34:59', '2025-09-28 07:34:59', NULL, NULL, 0, 0),
(3, 'BATCH_20240115001', 3, 'salary', 8000.00, 'CNY', 'alipay', '13611223344', '王五', '2024年1月工资', 'success', 'COMP_1759046029800_C9ECE6AA', 'MOCK_1759046030981', 'PAYEE_NOT_EXIST', '收款方账户不存在', '2025-09-28 15:53:51', NULL, '2025-09-28 07:35:27', '2025-09-28 07:53:51', NULL, NULL, 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `resource_snapshot`
--

CREATE TABLE `resource_snapshot` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '审批流程ID',
  `before_json` json DEFAULT NULL COMMENT '变更前快照',
  `after_json` json DEFAULT NULL COMMENT '变更后快照(拟变更)',
  `actor_id` bigint DEFAULT NULL COMMENT '发起人',
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源授权变更快照';

-- --------------------------------------------------------

--
-- 表的结构 `sys_config`
--

CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` text COLLATE utf8mb4_unicode_ci COMMENT '配置值',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置备注',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'string' COMMENT '配置类型(string,number,boolean,json)',
  `config_desc` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
  `is_encrypted` tinyint(1) DEFAULT '0' COMMENT '是否加密(0:否,1:是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

--
-- 转存表中的数据 `sys_config`
--

INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`, `config_type`, `config_desc`, `is_encrypted`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'system.name', '薪酬助手系统', NULL, 'string', '系统名称', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0),
(2, 'payment.daily.limit', '10000.00', NULL, 'number', '单人单日支付限额(元)', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0),
(3, 'payment.batch.max.size', '1000', NULL, 'number', '批量支付最大笔数', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0),
(4, 'approval.timeout.hours', '24', NULL, 'number', '审批超时时间(小时)', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0),
(5, 'notification.retry.max', '3', NULL, 'number', '通知重试最大次数', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0),
(6, 'audit.log.retention.days', '1825', NULL, 'number', '审计日志保留天数(5年)', 0, '2025-09-27 05:26:14', '2025-09-27 05:26:14', NULL, NULL, 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `sys_resource`
--

CREATE TABLE `sys_resource` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型: MENU/VIEW/ACTION/API',
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '全局唯一编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源名称',
  `path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端路由或后端接口路径',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端组件',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `parent_id` bigint DEFAULT NULL COMMENT '父资源ID',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `props_json` json DEFAULT NULL COMMENT '扩展元信息(JSON)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统资源表';

--
-- 转存表中的数据 `sys_resource`
--

INSERT INTO `sys_resource` (`id`, `type`, `code`, `name`, `path`, `component`, `icon`, `parent_id`, `order_num`, `props_json`, `status`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'MENU', 'dashboard', '工作台', '/', 'dashboard/Dashboard', 'dashboard', NULL, 1, '{\"affix\": true, \"keepAlive\": true}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(2, 'MENU', 'employees', '员工管理', '/employees', 'employees/List', 'team', 15, 11, '{\"keepAlive\": true}', 'enabled', '2025-09-29 16:18:42', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(3, 'VIEW', 'employees.detail', '员工详情', '/employees/:id', 'employees/Detail', NULL, NULL, 11, '{}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(4, 'MENU', 'payments.batches', '支付批次', '/payments/batches', 'payments/Batches', 'wallet', 16, 21, '{\"keepAlive\": true}', 'enabled', '2025-09-29 16:18:42', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(5, 'VIEW', 'payments.batch.detail', '批次详情', '/payments/batches/:batchNo', 'payments/BatchDetail', NULL, NULL, 21, '{}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(6, 'MENU', 'admin.user-binding', '用户绑定', '/admin/user-binding', 'admin/UserBinding', 'user-switch', 17, 81, '{}', 'enabled', '2025-09-29 16:18:42', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(7, 'MENU', 'system.integration', '集成配置', '/system/integration', 'system/IntegrationConfig', 'global', 18, 91, '{}', 'enabled', '2025-09-29 16:18:42', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(8, 'MENU', 'system.org-sync', '组织同步', '/system/org-sync', 'system/OrgSync', 'sync', 18, 92, '{}', 'enabled', '2025-09-29 16:18:42', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(9, 'API', 'api.payment.batch.start', '启动支付批次', '/api/payment/batch/{batchNo}/start', NULL, NULL, NULL, 0, '{\"method\": \"POST\"}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(10, 'API', 'api.payment.record.retry', '重试失败记录', '/api/payment/record/{recordId}/retry', NULL, NULL, NULL, 0, '{\"method\": \"POST\"}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(11, 'API', 'api.system.org.sync', '组织同步触发', '/api/system/org/sync', NULL, NULL, NULL, 0, '{\"method\": \"POST\"}', 'enabled', '2025-09-29 16:18:42', '2025-09-29 16:18:42', NULL, NULL, 0, 0),
(12, 'MENU', 'admin.resources.v2', '菜单管理', '/admin/resources-v2', 'admin/ResourcesV2', 'setting', 17, 82, '{\"roles\": [\"ADMIN\"], \"keepAlive\": true}', 'enabled', '2025-09-30 10:13:50', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(13, 'MENU', 'admin.role-auth', '角色授权', '/admin/role-auth', 'admin/RoleAuthorization', 'team', 17, 83, '{\"roles\": [\"ADMIN\"]}', 'enabled', '2025-09-30 10:13:50', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(14, 'MENU', 'admin.user-auth', '用户授权', '/admin/user-auth', 'admin/UserAuthorization', 'user-switch', 17, 84, '{\"roles\": [\"ADMIN\"]}', 'enabled', '2025-09-30 10:13:50', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(15, 'MENU', 'business', '业务管理', NULL, NULL, 'appstore', NULL, 10, '{}', 'enabled', '2025-09-30 11:50:26', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(16, 'MENU', 'payments', '支付管理', NULL, NULL, 'wallet', 15, 20, '{}', 'enabled', '2025-09-30 11:50:26', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(17, 'MENU', 'admin', '系统管理', NULL, NULL, 'setting', NULL, 80, '{\"roles\": [\"ADMIN\"]}', 'enabled', '2025-09-30 11:50:26', '2025-09-30 11:50:26', NULL, NULL, 0, 0),
(18, 'MENU', 'system', '系统配置', NULL, NULL, 'control', NULL, 90, '{}', 'enabled', '2025-09-30 11:50:26', '2025-09-30 11:50:26', NULL, NULL, 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `sys_role`
--

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- --------------------------------------------------------

--
-- 表的结构 `sys_role_resource`
--

CREATE TABLE `sys_role_resource` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `role_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源授权关系';

-- --------------------------------------------------------

--
-- 表的结构 `sys_user`
--

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码(BCrypt加密)',
  `real_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '真实姓名',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'active' COMMENT '用户状态(active:激活,inactive:禁用)',
  `roles` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色列表(逗号分隔)',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `platform_user_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台用户ID',
  `platform_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台类型',
  `permission_version` int DEFAULT '0' COMMENT '权限版本',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

--
-- 转存表中的数据 `sys_user`
--

INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `avatar`, `status`, `roles`, `employee_id`, `platform_user_id`, `platform_type`, `permission_version`, `last_login_time`, `last_login_ip`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`) VALUES
(1, 'admin', '$2b$10$EmMZfKZ/qXmJjgacNWQRrOSgdALZDWnF6zhN4GFpxbP/Iwd./7QIe', '系统管理员', 'admin@yiyundao.com', NULL, NULL, 'active', 'ROLE_ADMIN', NULL, NULL, NULL, 0, NULL, NULL, '2025-09-27 05:26:14', '2025-09-27 13:26:35', 'system', NULL, 0, 0),
(2, '林语', '$2b$10$EmMZfKZ/qXmJjgacNWQRrOSgdALZDWnF6zhN4GFpxbP/Iwd./7QIe', '蒋飘', NULL, NULL, NULL, 'active', 'ROLE_USER', 4, 'Yang', 'wechat', 0, NULL, NULL, '2025-09-29 16:00:03', '2025-09-29 09:03:05', 'system', 'system', 0, 1),
(3, 'XieLiJuan', '$2a$10$KDv9UwiEEdPcApAxaNbLW.e1UMsVDA65I5KQCYHHwleCaZShz.24W', '谢丽娟', NULL, NULL, NULL, 'active', 'ROLE_USER', 5, 'XieLiJuan', 'wechat', 0, NULL, NULL, '2025-09-29 16:00:04', '2025-09-29 16:00:04', 'system', 'system', 0, 1),
(4, 'wblili', '$2a$10$KRKalNVfgcr8UWq2Jam/v.m9hQSKUaffzAMswQJMRRuLBCuTURuGK', '莉莉', NULL, NULL, NULL, 'active', 'ROLE_USER', 6, NULL, NULL, 0, NULL, NULL, '2025-09-30 19:43:04', '2025-09-30 19:43:04', 'admin', 'admin', 0, 0),
(5, 'pengjunhua', '$2a$10$aR8vhhPvq4B97Zw41n9dW.8/jPUVWyoZIo9k84ajge.PecSMJgJPy', '彭俊华', NULL, NULL, NULL, 'active', 'ROLE_USER', 7, 'allout', 'wechat', 0, NULL, NULL, '2025-10-01 14:43:25', '2025-10-01 14:43:25', 'admin', 'admin', 0, 0);

-- --------------------------------------------------------

--
-- 表的结构 `sys_user_resource`
--

CREATE TABLE `sys_user_resource` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源个性授权';

-- --------------------------------------------------------

--
-- 表的结构 `sys_user_role`
--

CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关系';

--
-- 转储表的索引
--

--
-- 表的索引 `approval_step`
--
ALTER TABLE `approval_step`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_workflow_step` (`workflow_id`,`step_no`),
  ADD KEY `idx_approver` (`approver_id`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `approval_workflow`
--
ALTER TABLE `approval_workflow`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_business_key` (`business_key`,`business_type`),
  ADD KEY `idx_workflow_type` (`workflow_type`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_initiator` (`initiator_id`),
  ADD KEY `idx_current_approver` (`current_approver_id`),
  ADD KEY `idx_create_time` (`create_time`),
  ADD KEY `idx_workflow_type_status` (`workflow_type`,`status`),
  ADD KEY `idx_workflow_approver_time` (`current_approver_id`,`submit_time`);

--
-- 表的索引 `audit_log`
--
ALTER TABLE `audit_log`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_user` (`user_id`),
  ADD KEY `idx_operation` (`operation`),
  ADD KEY `idx_business` (`business_type`,`business_key`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `employee`
--
ALTER TABLE `employee`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_employee_id` (`employee_id`),
  ADD KEY `idx_platform_user` (`platform_user_id`,`platform_type`),
  ADD KEY `idx_manager` (`manager_id`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_offline` (`is_offline`),
  ADD KEY `idx_create_time` (`create_time`),
  ADD KEY `idx_employee_platform_status` (`platform_type`,`status`,`is_offline`),
  ADD KEY `idx_employee_dept_position` (`department`,`position`);

--
-- 表的索引 `employee_department`
--
ALTER TABLE `employee_department`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_emp` (`employee_id`),
  ADD KEY `idx_platform_dept` (`platform_type`,`platform_dept_id`);

--
-- 表的索引 `integration_config`
--
ALTER TABLE `integration_config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_platform_type` (`platform_type`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `notification_record`
--
ALTER TABLE `notification_record`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_status_next_retry` (`status`,`next_retry_time`),
  ADD KEY `idx_business_type_key` (`business_type`,`business_key`),
  ADD KEY `idx_recipient_channel` (`recipient_id`,`channel`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `org_department`
--
ALTER TABLE `org_department`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_platform_dept` (`platform_type`,`platform_dept_id`),
  ADD KEY `idx_parent` (`parent_platform_dept_id`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `payment_batch`
--
ALTER TABLE `payment_batch`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_batch_no` (`batch_no`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_payment_type` (`payment_type`),
  ADD KEY `idx_create_time` (`create_time`),
  ADD KEY `idx_submit_time` (`submit_time`);

--
-- 表的索引 `payment_record`
--
ALTER TABLE `payment_record`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_alipay_order` (`alipay_order_no`),
  ADD KEY `idx_batch_no` (`batch_no`),
  ADD KEY `idx_employee` (`employee_id`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_payment_type` (`payment_type`),
  ADD KEY `idx_create_time` (`create_time`),
  ADD KEY `idx_payment_time` (`payment_time`),
  ADD KEY `idx_payment_batch_status` (`batch_no`,`status`),
  ADD KEY `idx_payment_time_range` (`create_time`,`payment_time`);

--
-- 表的索引 `resource_snapshot`
--
ALTER TABLE `resource_snapshot`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_snapshot_workflow` (`workflow_id`);

--
-- 表的索引 `sys_config`
--
ALTER TABLE `sys_config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_config_key` (`config_key`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `sys_resource`
--
ALTER TABLE `sys_resource`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_resource_code` (`code`),
  ADD KEY `idx_type` (`type`),
  ADD KEY `idx_parent_order` (`parent_id`,`order_num`);

--
-- 表的索引 `sys_role`
--
ALTER TABLE `sys_role`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_role_code` (`code`);

--
-- 表的索引 `sys_role_resource`
--
ALTER TABLE `sys_role_resource`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_role_resource` (`role_id`,`resource_id`),
  ADD KEY `idx_role` (`role_id`),
  ADD KEY `idx_resource` (`resource_id`);

--
-- 表的索引 `sys_user`
--
ALTER TABLE `sys_user`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_username` (`username`),
  ADD KEY `idx_employee` (`employee_id`),
  ADD KEY `idx_platform_user` (`platform_user_id`,`platform_type`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_create_time` (`create_time`);

--
-- 表的索引 `sys_user_resource`
--
ALTER TABLE `sys_user_resource`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_user_resource` (`user_id`,`resource_id`),
  ADD KEY `idx_user` (`user_id`),
  ADD KEY `idx_resource` (`resource_id`);

--
-- 表的索引 `sys_user_role`
--
ALTER TABLE `sys_user_role`
  ADD UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  ADD KEY `idx_role` (`role_id`);

--
-- 在导出的表使用AUTO_INCREMENT
--

--
-- 使用表AUTO_INCREMENT `approval_step`
--
ALTER TABLE `approval_step`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=13;

--
-- 使用表AUTO_INCREMENT `approval_workflow`
--
ALTER TABLE `approval_workflow`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=5;

--
-- 使用表AUTO_INCREMENT `audit_log`
--
ALTER TABLE `audit_log`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=213;

--
-- 使用表AUTO_INCREMENT `employee`
--
ALTER TABLE `employee`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=8;

--
-- 使用表AUTO_INCREMENT `employee_department`
--
ALTER TABLE `employee_department`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=3;

--
-- 使用表AUTO_INCREMENT `integration_config`
--
ALTER TABLE `integration_config`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=4;

--
-- 使用表AUTO_INCREMENT `notification_record`
--
ALTER TABLE `notification_record`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=2;

--
-- 使用表AUTO_INCREMENT `org_department`
--
ALTER TABLE `org_department`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=3;

--
-- 使用表AUTO_INCREMENT `payment_batch`
--
ALTER TABLE `payment_batch`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=2;

--
-- 使用表AUTO_INCREMENT `payment_record`
--
ALTER TABLE `payment_record`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=4;

--
-- 使用表AUTO_INCREMENT `resource_snapshot`
--
ALTER TABLE `resource_snapshot`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

--
-- 使用表AUTO_INCREMENT `sys_config`
--
ALTER TABLE `sys_config`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=7;

--
-- 使用表AUTO_INCREMENT `sys_resource`
--
ALTER TABLE `sys_resource`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=19;

--
-- 使用表AUTO_INCREMENT `sys_role`
--
ALTER TABLE `sys_role`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

--
-- 使用表AUTO_INCREMENT `sys_role_resource`
--
ALTER TABLE `sys_role_resource`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

--
-- 使用表AUTO_INCREMENT `sys_user`
--
ALTER TABLE `sys_user`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', AUTO_INCREMENT=6;

--
-- 使用表AUTO_INCREMENT `sys_user_resource`
--
ALTER TABLE `sys_user_resource`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

--
-- 限制导出的表
--

--
-- 限制表 `approval_step`
--
ALTER TABLE `approval_step`
  ADD CONSTRAINT `fk_step_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`);

--
-- 限制表 `employee_department`
--
ALTER TABLE `employee_department`
  ADD CONSTRAINT `fk_emp_dept_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);

--
-- 限制表 `payment_record`
--
ALTER TABLE `payment_record`
  ADD CONSTRAINT `fk_payment_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);

--
-- 限制表 `sys_user`
--
ALTER TABLE `sys_user`
  ADD CONSTRAINT `fk_user_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
