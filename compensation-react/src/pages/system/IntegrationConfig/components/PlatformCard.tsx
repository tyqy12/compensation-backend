/**
 * PlatformCard 组件
 *
 * 平台条目组件，展示单个平台的配置状态和操作按钮
 * 遵循单一职责原则，专注于平台展示和交互
 */

import React from 'react';
import { Button, Switch, Tag, Typography } from 'antd';
import {
  ExperimentOutlined,
  GlobalOutlined,
  PoweroffOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { IntegrationConfigListItem, Platform } from '../../../../types/api';
import { PLATFORM_INFO } from '../constants';
import StatusTag from './StatusTag';

const { Text } = Typography;

interface PlatformCardProps {
  item: IntegrationConfigListItem;
  onConfig: (platform: Platform) => void;
  onTest: (platform: Platform) => void;
  onDisable: (platform: Platform) => void;
  onEnable: (platform: Platform) => void;
  testLoading?: boolean;
}

const PlatformCard: React.FC<PlatformCardProps> = ({
  item,
  onConfig,
  onTest,
  onDisable,
  onEnable,
  testLoading = false,
}) => {
  const platformInfo = PLATFORM_INFO[item.platformType] ?? {
    name: item.platformName ?? item.platformType,
    icon: <GlobalOutlined style={{ color: '#595959' }} />,
    description: item.platformName ?? '第三方平台',
    color: 'default',
  };

  const handleDisableClick = () => {
    if (item.enabled) {
      onDisable(item.platformType);
    } else {
      onEnable(item.platformType);
    }
  };

  return (
    <article
      className={`integration-platform-card ${item.configured ? 'is-configured' : 'is-unconfigured'}`}
    >
      <div className="integration-platform-card-header">
        <div className="integration-platform-identity">
          <div className="integration-platform-icon">{platformInfo.icon}</div>
          <div className="integration-platform-copy">
            <Text strong className="integration-platform-name">
              {platformInfo.name}
            </Text>
            <Text type="secondary" className="integration-platform-description">
              {platformInfo.description}
            </Text>
          </div>
        </div>
        <StatusTag status={item.connectionStatus} />
      </div>

      <div className="integration-platform-status-grid">
        <div className="integration-platform-status-item">
          <Text type="secondary">配置状态</Text>
          <Tag color={item.configured ? 'success' : 'default'}>
            {item.configured ? '已配置' : '未配置'}
          </Tag>
        </div>
        <div className="integration-platform-status-item">
          <Text type="secondary">启用状态</Text>
          <div className="integration-platform-enabled">
            <Switch size="small" checked={item.enabled} disabled />
            <Text type="secondary">{item.enabled ? '已启用' : '未启用'}</Text>
          </div>
        </div>
      </div>

      <div className="integration-platform-meta">
        <Text type="secondary">
          {item.lastModified
            ? `最近更新 ${new Date(item.lastModified).toLocaleDateString('zh-CN')}`
            : '尚未记录更新时间'}
        </Text>
      </div>

      <div className="integration-platform-actions">
        <Button
          type="primary"
          size="small"
          icon={<SettingOutlined />}
          onClick={() => onConfig(item.platformType)}
        >
          配置
        </Button>
        <Button
          size="small"
          icon={<ExperimentOutlined />}
          loading={testLoading}
          disabled={!item.configured}
          onClick={() => onTest(item.platformType)}
        >
          测试
        </Button>
        <Button
          type="text"
          size="small"
          icon={<PoweroffOutlined />}
          danger={item.enabled}
          disabled={!item.configured}
          onClick={handleDisableClick}
        >
          {item.enabled ? '禁用' : '启用'}
        </Button>
      </div>
    </article>
  );
};

export default PlatformCard;
