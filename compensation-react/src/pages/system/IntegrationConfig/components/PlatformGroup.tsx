/**
 * PlatformGroup 组件
 *
 * 平台分组组件，将平台按功能分组展示
 * 参考 RoleList 的分组卡片布局设计
 */

import React from 'react';
import { Card, Row, Col, Space, Typography, Badge } from 'antd';
import type { IntegrationConfigListItem, Platform } from '../../../../types/api';
import type { PlatformGroup as PlatformGroupType } from '../types';
import { STYLES } from '../constants';
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
  const groupItems = items.filter(item =>
    group.platforms.includes(item.platformType)
  );

  // 如果没有该平台分组的数据，不渲染
  if (groupItems.length === 0) {
    return null;
  }

  // 计算已配置的平台数量
  const configuredCount = groupItems.filter(item => item.configured).length;

  return (
    <Card
      style={STYLES.groupSection}
      styles={{ body: { padding: 16 } }}
    >
      {/* 分组标题 */}
      <div style={STYLES.groupHeader}>
        <Space>
          {group.icon}
          <Text strong style={STYLES.groupTitle}>
            {group.name}
          </Text>
        </Space>
        <Badge
          count={`${configuredCount}/${groupItems.length}`}
          style={{ backgroundColor: configuredCount === groupItems.length ? '#52c41a' : '#1890ff' }}
          showZero
        />
      </div>

      {/* 平台卡片网格 */}
      <Row gutter={[16, 16]}>
        {groupItems.map(item => (
          <Col xs={24} sm={12} lg={8} xl={6} key={item.platformType}>
            <PlatformCard
              item={item}
              onConfig={onConfig}
              onTest={onTest}
              onDisable={onDisable}
              onEnable={onEnable}
              testLoading={testLoading}
            />
          </Col>
        ))}
      </Row>
    </Card>
  );
};

export default PlatformGroup;
