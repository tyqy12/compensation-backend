/**
 * ConfigDrawer 组件
 *
 * 配置抽屉组件，使用 Drawer 替代 Modal 提供更宽的编辑区域
 * 包含分步骤配置向导功能
 */

import React, { useEffect } from 'react';
import { Alert, Button, Drawer, Space, Steps, Typography } from 'antd';
import { ProForm, ProFormSwitch } from '@ant-design/pro-components';
import { CheckCircleOutlined, ExperimentOutlined, SaveOutlined } from '@ant-design/icons';
import {
  useIntegrationConfigQuery,
  useSaveIntegrationConfigMutation,
  useTestIntegrationMutation,
} from '../../../../services/queries/integration';
import type { Platform, SaveConfigRequest } from '../../../../types/api';
import { PLATFORM_INFO } from '../constants';
import StatusTag from './StatusTag';

// 平台表单组件
import WechatForm from './platform-forms/WechatForm';
import DingtalkForm from './platform-forms/DingtalkForm';
import FeishuForm from './platform-forms/FeishuForm';
import AlipayForm from './platform-forms/AlipayForm';
import YunzhanghuForm from './platform-forms/YunzhanghuForm';
import SmsForm from './platform-forms/SmsForm';
import EmailForm from './platform-forms/EmailForm';
import EncryptionForm from './platform-forms/EncryptionForm';

const { Text } = Typography;

interface ConfigDrawerProps {
  platform: Platform | null;
  open: boolean;
  onClose: () => void;
}

const ConfigDrawer: React.FC<ConfigDrawerProps> = ({ platform, open, onClose }) => {
  const [form] = ProForm.useForm<SaveConfigRequest>();
  const [currentStep, setCurrentStep] = React.useState(0);

  const configQuery = useIntegrationConfigQuery(platform || undefined);
  const saveMutation = useSaveIntegrationConfigMutation();
  const testMutation = useTestIntegrationMutation();

  // 当平台变化时重置表单
  useEffect(() => {
    if (open && platform) {
      form.resetFields();
      setCurrentStep(0);
      if (configQuery.data) {
        form.setFieldsValue({
          enabled: configQuery.data.enabled,
          [platform]: configQuery.data.config,
        } as Partial<SaveConfigRequest>);
      } else {
        form.setFieldsValue({ enabled: false } as Partial<SaveConfigRequest>);
      }
    }
  }, [open, platform, configQuery.data, form]);

  // 渲染平台表单
  const renderPlatformForm = () => {
    if (!platform) return null;

    switch (platform) {
      case 'wechat':
        return <WechatForm />;
      case 'dingtalk':
        return <DingtalkForm />;
      case 'feishu':
        return <FeishuForm />;
      case 'alipay':
        return <AlipayForm form={form} />;
      case 'yunzhanghu':
        return <YunzhanghuForm />;
      case 'sms':
        return <SmsForm />;
      case 'email':
        return <EmailForm />;
      case 'encryption':
        return <EncryptionForm form={form} />;
      default:
        return <Alert title="不支持的平台类型" type="error" />;
    }
  };

  // 处理保存
  const handleSave = async (values: SaveConfigRequest) => {
    if (!platform) return;

    const payload: SaveConfigRequest = { enabled: values.enabled };
    const platformValues = (values as unknown as Record<string, unknown>)[platform];
    if (platformValues) {
      (payload as unknown as Record<string, unknown>)[platform] = platformValues;
    }

    await saveMutation.mutateAsync({
      platformType: platform,
      config: payload,
    });
  };

  // 处理测试连接
  const handleTest = async () => {
    if (!platform) return;
    await testMutation.mutateAsync(platform);
  };

  // 判断是否为复杂配置（需要分步）
  const isComplexPlatform = platform === 'alipay' || platform === 'yunzhanghu';

  // 步骤配置
  const steps = isComplexPlatform
    ? [
        { title: '基础配置', description: '填写必要信息' },
        { title: '高级选项', description: '配置高级参数' },
        { title: '测试与启用', description: '验证并启用' },
      ]
    : [];

  const platformInfo = platform ? PLATFORM_INFO[platform] : null;
  const connectionStatus = configQuery.data?.connectionStatus ?? 'unknown';

  return (
    <Drawer
      rootClassName="integration-config-drawer"
      title={
        <div className="integration-drawer-title">
          <div className="integration-drawer-platform-icon">{platformInfo?.icon}</div>
          <div className="integration-drawer-title-copy">
            <Text className="integration-eyebrow">PLATFORM CONFIGURATION</Text>
            <Typography.Title level={4} className="integration-drawer-title-text">
              {platformInfo ? `配置 ${platformInfo.name}` : '配置平台'}
            </Typography.Title>
            <Text type="secondary">
              {platformInfo?.description ?? '填写并保存该平台的接入信息'}
            </Text>
          </div>
          {platformInfo && <StatusTag status={connectionStatus} />}
        </div>
      }
      open={open}
      onClose={onClose}
      size={800}
      destroyOnHidden
      footer={
        <div className="integration-drawer-footer">
          <Text type="secondary">保存前建议先测试连接，配置变更会记录审计日志。</Text>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button
              icon={<ExperimentOutlined />}
              onClick={handleTest}
              loading={testMutation.isPending}
              disabled={!platform}
            >
              测试连接
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={() => form.submit()}
              loading={saveMutation.isPending}
            >
              保存配置
            </Button>
          </Space>
        </div>
      }
    >
      <div className="integration-drawer-content">
        {configQuery.isError && (
          <Alert
            className="integration-drawer-alert"
            type="error"
            title="加载配置失败"
            description={
              configQuery.error instanceof Error ? configQuery.error.message : '请稍后重试'
            }
            showIcon
          />
        )}

        <ProForm<SaveConfigRequest>
          form={form}
          layout="vertical"
          className="integration-drawer-form"
          onFinish={async (values) => {
            await handleSave(values);
            onClose();
            return true;
          }}
          submitter={false}
          loading={configQuery.isLoading}
        >
          <section className="integration-drawer-status-card">
            <div className="integration-drawer-status-copy">
              <div className="integration-drawer-status-icon">
                <CheckCircleOutlined />
              </div>
              <div>
                <Text strong>运行设置</Text>
                <Text type="secondary">启用后，系统会在对应业务流程中调用该平台。</Text>
              </div>
            </div>
            <ProFormSwitch
              name="enabled"
              label="启用此配置"
              tooltip="是否启用此平台的集成功能"
              fieldProps={{
                onChange: () => {
                  if (platform === 'encryption') {
                    form.validateFields([
                      ['encryption', 'aesKey'],
                      ['encryption', 'sm4Key'],
                    ]);
                  }
                },
              }}
            />
          </section>

          {isComplexPlatform && steps.length > 0 && (
            <section className="integration-drawer-steps-section">
              <div className="integration-drawer-section-heading">
                <div>
                  <Text className="integration-eyebrow">CONFIGURATION FLOW</Text>
                  <Typography.Title level={5}>配置流程</Typography.Title>
                </div>
                <Text type="secondary">
                  {currentStep + 1}/{steps.length}
                </Text>
              </div>
              <Steps current={currentStep} items={steps} onChange={setCurrentStep} responsive />
            </section>
          )}

          <section className="integration-drawer-form-section">
            <div className="integration-drawer-section-heading">
              <div>
                <Text className="integration-eyebrow">CONFIGURATION</Text>
                <Typography.Title level={5}>配置信息</Typography.Title>
              </div>
              <Text type="secondary">带 * 的字段为必填项</Text>
            </div>

            <div className="integration-platform-form">{renderPlatformForm()}</div>
          </section>

          <section className="integration-drawer-help">
            <div className="integration-drawer-help-icon">?</div>
            <div>
              <Text strong>配置帮助</Text>
              <ul>
                <li>所有敏感信息将加密存储</li>
                <li>配置变更将记录审计日志</li>
                <li>建议定期更新密钥和凭证</li>
              </ul>
            </div>
          </section>
        </ProForm>
      </div>
    </Drawer>
  );
};

export default ConfigDrawer;
