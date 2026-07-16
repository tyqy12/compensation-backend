/**
 * IntegrationConfig 页面
 *
 * 集成配置管理主页面
 * 采用分类工作区布局，按平台类型切换配置场景
 *
 * 优化点：
 * 1. 组件拆分 - 将单文件拆分为多个职责单一的组件
 * 2. 分类工作区 - 按功能切换展示平台卡片
 * 3. Drawer替代Modal - 提供更宽的编辑区域
 * 4. 提取常量 - 集中管理平台信息和样式
 */

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Tabs, Typography, App as AntdApp } from 'antd';
import { PageContainer } from '@ant-design/pro-components';
import {
  CheckCircleOutlined,
  GlobalOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SettingOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  useIntegrationListQuery,
  useDisableIntegrationMutation,
  useEnableIntegrationMutation,
  useTestIntegrationMutation,
} from '../../../services/queries/integration';
import type { Platform } from '../../../types/api';
import { PLATFORM_GROUPS, PLATFORM_INFO } from './constants';
import PlatformGroup from './components/PlatformGroup';
import ConfigDrawer from './components/ConfigDrawer';
import './IntegrationConfig.less';

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

  const getPlatformName = (platform: Platform): string => PLATFORM_INFO[platform]?.name || platform;
  const integrationItems = listQuery.data ?? [];
  const [activeGroupKey, setActiveGroupKey] = useState(PLATFORM_GROUPS[0]?.key ?? '');
  const availableGroups = useMemo(
    () =>
      PLATFORM_GROUPS.filter((group) =>
        integrationItems.some((item) => group.platforms.includes(item.platformType)),
      ),
    [integrationItems],
  );

  useEffect(() => {
    if (
      availableGroups.length > 0 &&
      !availableGroups.some((group) => group.key === activeGroupKey)
    ) {
      setActiveGroupKey(availableGroups[0].key);
    }
  }, [activeGroupKey, availableGroups]);

  const configuredCount = integrationItems.filter((item) => item.configured).length;
  const enabledCount = integrationItems.filter((item) => item.enabled).length;
  const connectedCount = integrationItems.filter(
    (item) => item.connectionStatus === 'connected',
  ).length;
  const summaryItems = [
    {
      key: 'total',
      label: '接入平台',
      value: integrationItems.length,
      suffix: '个',
      hint: '系统当前支持的服务',
      icon: <GlobalOutlined />,
      className: 'is-blue',
    },
    {
      key: 'configured',
      label: '已完成配置',
      value: configuredCount,
      suffix: '个',
      hint: '已填写必要接入信息',
      icon: <CheckCircleOutlined />,
      className: 'is-green',
    },
    {
      key: 'enabled',
      label: '已启用服务',
      value: enabledCount,
      suffix: '个',
      hint: '当前参与业务调用',
      icon: <PoweroffOutlined />,
      className: 'is-amber',
    },
    {
      key: 'connected',
      label: '连接正常',
      value: connectedCount,
      suffix: '个',
      hint: '最近一次测试通过',
      icon: <SettingOutlined />,
      className: 'is-teal',
    },
  ];

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
      <main className="integration-config-page">
        {listQuery.isError && (
          <Alert
            className="integration-page-alert"
            type="error"
            showIcon
            title="集成配置加载失败"
            description={listQuery.error instanceof Error ? listQuery.error.message : '请稍后重试'}
          />
        )}

        <section className="integration-summary-grid" aria-label="集成状态统计">
          {summaryItems.map((item) => (
            <div className={`integration-summary-item ${item.className}`} key={item.key}>
              <div className="integration-summary-icon">{item.icon}</div>
              <div className="integration-summary-content">
                <Text className="integration-summary-label">{item.label}</Text>
                <div className="integration-summary-value">
                  <span>{item.value}</span>
                  <Text type="secondary">{item.suffix}</Text>
                </div>
                <Text type="secondary" className="integration-summary-hint">
                  {item.hint}
                </Text>
              </div>
            </div>
          ))}
        </section>

        {!listQuery.isLoading && (integrationItems.length === 0 || availableGroups.length === 0) ? (
          <section className="integration-empty-state">
            <Empty description="暂无可配置的集成平台" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            <Button icon={<ReloadOutlined />} onClick={() => listQuery.refetch()}>
              重新加载
            </Button>
          </section>
        ) : (
          <div className="integration-workspace">
            <Tabs
              className="integration-group-tabs"
              activeKey={activeGroupKey}
              onChange={setActiveGroupKey}
              items={availableGroups.map((group) => {
                const groupItems = integrationItems.filter((item) =>
                  group.platforms.includes(item.platformType),
                );
                const groupConfiguredCount = groupItems.filter((item) => item.configured).length;

                return {
                  key: group.key,
                  label: (
                    <div className="integration-group-tab-label">
                      <span className="integration-group-tab-icon">{group.icon}</span>
                      <span className="integration-group-tab-copy">
                        <span>{group.name}</span>
                        <small>
                          {groupConfiguredCount}/{groupItems.length} 已配置
                        </small>
                      </span>
                    </div>
                  ),
                  children: (
                    <PlatformGroup
                      group={group}
                      items={integrationItems}
                      onConfig={handleConfig}
                      onTest={handleTest}
                      onDisable={handleDisable}
                      onEnable={handleEnable}
                      testLoading={testMutation.isPending}
                    />
                  ),
                };
              })}
            />
          </div>
        )}

        <section
          className="integration-security-strip"
          aria-labelledby="integration-security-title"
        >
          <div className="integration-security-icon">
            <SafetyCertificateOutlined />
          </div>
          <div className="integration-security-copy">
            <Text strong id="integration-security-title">
              安全基线
            </Text>
            <Text type="secondary">敏感信息会加密存储，所有配置变更都会进入审计日志。</Text>
          </div>
          <ul className="integration-security-list">
            <li>定期更换密钥</li>
            <li>生产环境使用 HTTPS</li>
            <li>启用前先测试连接</li>
          </ul>
        </section>
      </main>

      {/* 配置抽屉 */}
      <ConfigDrawer platform={selectedPlatform} open={drawerOpen} onClose={handleCloseDrawer} />
    </PageContainer>
  );
};

export default IntegrationConfigPage;
