/**
 * IntegrationConfig 页面
 *
 * 集成配置管理主页面
 * 采用分组卡片布局，参考 RoleList 设计模式
 *
 * 优化点：
 * 1. 组件拆分 - 将单文件拆分为多个职责单一的组件
 * 2. 分组展示 - 按功能分组展示平台卡片
 * 3. Drawer替代Modal - 提供更宽的编辑区域
 * 4. 提取常量 - 集中管理平台信息和样式
 */

import React, { useState } from 'react';
import { Button, Space, Card, Typography, App as AntdApp } from 'antd';
import {
  PageContainer,
} from '@ant-design/pro-components';
import {
  ReloadOutlined,
  SafetyCertificateOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import {
  useIntegrationListQuery,
  useDisableIntegrationMutation,
  useEnableIntegrationMutation,
  useTestIntegrationMutation,
} from '../../../services/queries/integration';
import type { Platform } from '../../../types/api';
import { PLATFORM_GROUPS, STYLES } from './constants';
import PlatformGroup from './components/PlatformGroup';
import ConfigDrawer from './components/ConfigDrawer';

const { Text } = Typography;

const IntegrationConfigPage: React.FC = () => {
  const [selectedPlatform, setSelectedPlatform] = useState<Platform | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const { message, modal } = AntdApp.useApp();

  // 查询 hooks
  const listQuery = useIntegrationListQuery();
  const disableMutation = useDisableIntegrationMutation();
  const enableMutation = useEnableIntegrationMutation();
  const testMutation = useTestIntegrationMutation();

  // 处理配置按钮点击
  const handleConfig = (platform: Platform) => {
    setSelectedPlatform(platform);
    setDrawerOpen(true);
  };

  // 处理关闭抽屉
  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setSelectedPlatform(null);
  };

  // 处理禁用配置
  const handleDisable = (platform: Platform) => {
    const platformName = getPlatformName(platform);
    modal.confirm({
      title: '确认禁用',
      content: `确定要禁用 ${platformName} 的配置吗？`,
      onOk: async () => {
        try {
          await disableMutation.mutateAsync(platform);
          message.success('配置已禁用');
        } catch (error: any) {
          message.error(`禁用失败：${error.message}`);
        }
      },
    });
  };

  // 处理启用配置
  const handleEnable = (platform: Platform) => {
    const platformName = getPlatformName(platform);
    modal.confirm({
      title: '确认启用',
      content: `确定要启用 ${platformName} 的配置吗？`,
      onOk: async () => {
        try {
          await enableMutation.mutateAsync(platform);
          message.success('配置已启用');
        } catch (error: any) {
          message.error(`启用失败：${error.message}`);
        }
      },
    });
  };

  // 处理测试连接
  const handleTest = async (platform: Platform) => {
    try {
      const result = await testMutation.mutateAsync(platform);
      const success = result?.success ?? false;
      const detailMessage = result?.message ?? result?.detail;

      if (success) {
        message.success(detailMessage || '连接测试成功！');
      } else {
        message.warning(detailMessage || '连接测试失败，请检查配置');
      }
    } catch (error: any) {
      message.error(`测试失败：${error.message || '网络错误'}`);
    }
  };

  // 获取平台名称
  const getPlatformName = (platform: Platform): string => {
    for (const group of PLATFORM_GROUPS) {
      if (group.platforms.includes(platform)) {
        // 从 PLATFORM_INFO 中查找名称
        const platformInfo = require('./constants').PLATFORM_INFO[platform];
        return platformInfo?.name || platform;
      }
    }
    return platform;
  };

  return (
    <PageContainer
      header={{
        title: '集成配置管理',
        subTitle: '管理第三方平台集成配置',
        extra: [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => listQuery.refetch()}
            loading={listQuery.isLoading}
          >
            刷新
          </Button>,
        ],
      }}
      loading={listQuery.isLoading}
    >
      {/* 使用说明 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          backgroundColor: '#e6f7ff',
          borderRadius: 4,
          padding: '12px 16px',
          marginBottom: 16,
        }}
      >
        <InfoCircleOutlined
          style={{ fontSize: 16, color: '#1890ff', marginRight: 8 }}
        />
        <Text style={{ fontSize: 14, color: '#1890ff' }}>
          集成配置说明：配置各种第三方平台的接入信息，包括企业通讯平台、支付接口、通知服务等。配置完成后请务必进行连接测试以确保正常工作。
        </Text>
      </div>

      {/* 平台分组展示 */}
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {PLATFORM_GROUPS.map((group) => (
          <PlatformGroup
            key={group.key}
            group={group}
            items={listQuery.data || []}
            onConfig={handleConfig}
            onTest={handleTest}
            onDisable={handleDisable}
            onEnable={handleEnable}
            testLoading={testMutation.isPending}
          />
        ))}

        {/* 安全提示 */}
        <Card
          size="small"
          title={
            <Space>
              <SafetyCertificateOutlined />
              <span>安全提示</span>
            </Space>
          }
        >
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            <li>所有敏感配置信息在数据库中均已加密存储</li>
            <li>配置操作会记录详细的审计日志</li>
            <li>建议定期更换密钥和凭证</li>
            <li>生产环境必须使用HTTPS传输</li>
          </ul>
        </Card>
      </Space>

      {/* 配置抽屉 */}
      <ConfigDrawer
        platform={selectedPlatform}
        open={drawerOpen}
        onClose={handleCloseDrawer}
      />
    </PageContainer>
  );
};

export default IntegrationConfigPage;
