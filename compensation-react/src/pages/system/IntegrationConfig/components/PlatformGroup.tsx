/**
 * PlatformGroup 组件
 *
 * 平台分组组件，在分类工作区中展示同一功能域的平台
 */

import React from 'react';
import { Typography } from 'antd';
import type { IntegrationConfigListItem, Platform } from '../../../../types/api';
import type { PlatformGroup as PlatformGroupType } from '../types';
import PlatformCard from './PlatformCard';

const { Text } = Typography;

interface PlatformGroupProps {
  group: PlatformGroupType;
  items: IntegrationConfigListItem[];
  onConfig: (platform: Platform) => void;
  onTest: (platform: Platform) => void;
  onDisable: (platform: Platform) => void;
  onEnable: (platform: Platform) => void;
  testLoading?: boolean;
}

const PlatformGroup: React.FC<PlatformGroupProps> = ({
  group,
  items,
  onConfig,
  onTest,
  onDisable,
  onEnable,
  testLoading = false,
}) => {
  // 过滤出该分组下的平台
  const groupItems = items.filter((item) => group.platforms.includes(item.platformType));

  // 如果没有该平台分组的数据，不渲染
  if (groupItems.length === 0) {
    return null;
  }

  // 计算已配置的平台数量
  const configuredCount = groupItems.filter((item) => item.configured).length;
  const pendingCount = groupItems.length - configuredCount;

  return (
    <section className="integration-group" aria-labelledby={`integration-group-${group.key}`}>
      <div className="integration-group-header">
        <div className="integration-group-identity">
          <div className="integration-group-icon">{group.icon}</div>
          <div>
            <Typography.Title
              level={4}
              id={`integration-group-${group.key}`}
              className="integration-group-title"
            >
              {group.name}
            </Typography.Title>
            <Text type="secondary">
              {pendingCount > 0 ? `${pendingCount} 个平台等待配置` : '本组平台已全部配置'}
            </Text>
          </div>
        </div>
        <div className="integration-group-progress">
          <span>
            {configuredCount}/{groupItems.length}
          </span>
          <Text type="secondary">已配置</Text>
        </div>
      </div>

      <div className="integration-platform-grid">
        {groupItems.map((item) => (
          <div key={item.platformType}>
            <PlatformCard
              item={item}
              onConfig={onConfig}
              onTest={onTest}
              onDisable={onDisable}
              onEnable={onEnable}
              testLoading={testLoading}
            />
          </div>
        ))}
      </div>
    </section>
  );
};

export default PlatformGroup;
