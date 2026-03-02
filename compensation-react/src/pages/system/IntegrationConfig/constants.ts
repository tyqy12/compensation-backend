/**
 * IntegrationConfig 模块常量定义
 *
 * 集中管理平台信息、样式常量和分组配置
 */

import React from 'react';
import {
  WechatOutlined,
  DingdingOutlined,
  MailOutlined,
  MessageOutlined,
  PayCircleOutlined,
  SecurityScanOutlined,
  CloudServerOutlined,
  SettingOutlined,
  TeamOutlined,
  NotificationOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { Platform } from '../../types/api';
import type { PlatformGroup } from './types';

/**
 * 平台信息配置
 */
export const PLATFORM_INFO: Record<Platform, {
  name: string;
  icon: React.ReactNode;
  description: string;
  color: string;
}> = {
  wechat: {
    name: '企业微信',
    icon: React.createElement(WechatOutlined, { style: { color: '#07c160' } }),
    description: '企业内部通讯和消息推送',
    color: 'green',
  },
  dingtalk: {
    name: '钉钉',
    icon: React.createElement(DingdingOutlined, { style: { color: '#1890ff' } }),
    description: '企业协作和组织管理',
    color: 'blue',
  },
  feishu: {
    name: '飞书',
    icon: React.createElement(SettingOutlined, { style: { color: '#722ed1' } }),
    description: '团队协作和文档管理',
    color: 'purple',
  },
  alipay: {
    name: '支付宝',
    icon: React.createElement(PayCircleOutlined, { style: { color: '#1890ff' } }),
    description: '支付接口和转账功能',
    color: 'blue',
  },
  yunzhanghu: {
    name: '云账户',
    icon: React.createElement(CloudServerOutlined, { style: { color: '#13c2c2' } }),
    description: '灵活用工结算与代发',
    color: 'cyan',
  },
  sms: {
    name: '短信服务',
    icon: React.createElement(MessageOutlined, { style: { color: '#52c41a' } }),
    description: '短信验证和通知推送',
    color: 'green',
  },
  email: {
    name: '邮件服务',
    icon: React.createElement(MailOutlined, { style: { color: '#fa8c16' } }),
    description: '邮件发送和通知服务',
    color: 'orange',
  },
  encryption: {
    name: '加密配置',
    icon: React.createElement(SecurityScanOutlined, { style: { color: '#f5222d' } }),
    description: '数据加密和安全配置',
    color: 'red',
  },
};

/**
 * 平台分组配置
 */
export const PLATFORM_GROUPS: PlatformGroup[] = [
  {
    key: 'communication',
    name: '企业通讯平台',
    icon: React.createElement(TeamOutlined),
    platforms: ['wechat', 'dingtalk', 'feishu'],
  },
  {
    key: 'payment',
    name: '支付与结算',
    icon: React.createElement(PayCircleOutlined),
    platforms: ['alipay', 'yunzhanghu'],
  },
  {
    key: 'notification',
    name: '通知服务',
    icon: React.createElement(NotificationOutlined),
    platforms: ['sms', 'email'],
  },
  {
    key: 'security',
    name: '安全与加密',
    icon: React.createElement(SafetyCertificateOutlined),
    platforms: ['encryption'],
  },
];

/**
 * 样式常量
 * 遵循项目现有模式，使用内联样式对象
 */
export const STYLES = {
  pageContainer: {
    padding: 24,
  },
  groupSection: {
    marginBottom: 24,
  },
  groupHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    marginBottom: 16,
    padding: '8px 0',
    borderBottom: '1px solid #f0f0f0',
  },
  groupTitle: {
    fontSize: 16,
    fontWeight: 500,
  },
  groupCount: {
    marginLeft: 'auto',
  },
  platformCard: {
    height: '100%',
    transition: 'all 0.3s ease',
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  cardContent: {
    marginTop: 8,
  },
  statusTag: {
    minWidth: 60,
    textAlign: 'center' as const,
  },
  infoRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
    padding: '4px 0',
  },
  actionRow: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 12,
    paddingTop: 12,
    borderTop: '1px solid #f0f0f0',
  },
  drawerContent: {
    padding: '0 24px',
  },
  formSection: {
    marginBottom: 24,
  },
  helpSection: {
    backgroundColor: '#f6ffed',
    border: '1px solid #b7eb8f',
    borderRadius: 4,
    padding: 12,
    marginTop: 16,
  },
  certUploadContainer: {
    marginBottom: 24,
  },
  certUploadLabel: {
    marginBottom: 8,
    fontWeight: 500,
  },
  certUploaded: {
    marginTop: 8,
    padding: 8,
    backgroundColor: '#f6ffed',
    borderRadius: 4,
    border: '1px solid #b7eb8f',
  },
} as const;

/**
 * 平台颜色映射
 */
export const PLATFORM_COLORS: Record<Platform, string> = {
  wechat: '#07c160',
  dingtalk: '#1890ff',
  feishu: '#722ed1',
  alipay: '#1677ff',
  yunzhanghu: '#13c2c2',
  sms: '#52c41a',
  email: '#fa8c16',
  encryption: '#f5222d',
};

/**
 * 连接状态显示配置
 */
export const CONNECTION_STATUS_MAP: Record<string, {
  text: string;
  color: string;
}> = {
  connected: {
    text: '已连接',
    color: 'success',
  },
  disconnected: {
    text: '未连接',
    color: 'default',
  },
  unknown: {
    text: '未知',
    color: 'warning',
  },
};

/**
 * 证书上传配置
 */
export const CERT_UPLOAD_CONFIG = {
  maxSize: 1024 * 1024, // 1MB
  allowedTypes: ['.crt'],
  uploadEndpoint: '/api/admin/integration-configs/alipay/cert-upload',
};
