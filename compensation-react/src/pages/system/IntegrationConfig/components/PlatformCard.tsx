/**
 * PlatformCard 组件
 *
 * 平台卡片组件，展示单个平台的配置状态和操作按钮
 * 遵循单一职责原则，专注于卡片展示和交互
 */

import React from 'react';
import { Card, Space, Tag, Typography, Switch, Descriptions, Button } from 'antd';
import {
  SettingOutlined,
  ExperimentOutlined,
  GlobalOutlined,
} from '@ant-design/icons';
import type { IntegrationConfigListItem, Platform } from '../../../../types/api';
import { PLATFORM_INFO, STYLES } from '../constants';
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
    <Card
      size="small"
      style={STYLES.platformCard}
      title={
        <Space>
          {platformInfo.icon}
          <span>{platformInfo.name}</span>
        </Space>
      }
      extra={<StatusTag status={item.connectionStatus} />}
      actions={[
        <Button
          key="config"
          type="link"
          size="small"
          icon={<SettingOutlined />}
          onClick={() => onConfig(item.platformType)}
        >
          配置
        </Button>,
        <Button
          key="test"
          type="link"
          size="small"
          icon={<ExperimentOutlined />}
          loading={testLoading}
          disabled={!item.configured}
          onClick={() => onTest(item.platformType)}
        >
          测试
        </Button>,
        <Button
          key="disable"
          type="link"
          size="small"
          danger={item.enabled}
          disabled={!item.configured}
          onClick={handleDisableClick}
        >
          {item.enabled ? '禁用' : '启用'}
        </Button>,
      ]}
    >
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 13 }}>
          {platformInfo.description}
        </Text>
      </div>

      <Descriptions size="small" column={1}>
        <Descriptions.Item label="配置状态">
          <Tag color={item.configured ? 'success' : 'default'} size="small">
            {item.configured ? '已配置' : '未配置'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="启用状态">
          <Space size={4}>
            <Switch size="small" checked={item.enabled} disabled />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {item.enabled ? '已启用' : '未启用'}
            </Text>
          </Space>
        </Descriptions.Item>
        {item.lastModified && (
          <Descriptions.Item label="更新时间">
            <Text type="secondary" style={{ fontSize: 12 }}>
              {new Date(item.lastModified).toLocaleDateString('zh-CN')}
            </Text>
          </Descriptions.Item>
        )}
      </Descriptions>
    </Card>
  );
};

export default PlatformCard;
