/**
 * ConfigDrawer 组件
 *
 * 配置抽屉组件，使用 Drawer 替代 Modal 提供更宽的编辑区域
 * 包含分步骤配置向导功能
 */

import React, { useEffect } from 'react';
import {
  Drawer,
  Space,
  Button,
  Divider,
  Steps,
  Alert,
  Typography,
} from 'antd';
import { ProForm, ProFormSwitch } from '@ant-design/pro-components';
import {
  useIntegrationConfigQuery,
  useSaveIntegrationConfigMutation,
  useTestIntegrationMutation,
} from '../../../../services/queries/integration';
import type {
  Platform,
  SaveConfigRequest,
} from '../../../../types/api';
import { PLATFORM_INFO, STYLES } from '../constants';

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

const ConfigDrawer: React.FC<ConfigDrawerProps> = ({
  platform,
  open,
  onClose,
}) => {
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
        return <Alert message="不支持的平台类型" type="error" />;
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

  return (
    <Drawer
      title={
        platformInfo ? (
          <Space>
            {platformInfo.icon}
            <span>配置 {platformInfo.name}</span>
          </Space>
        ) : (
          '配置平台'
        )
      }
      open={open}
      onClose={onClose}
      width={800}
      destroyOnClose
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            onClick={handleTest}
            loading={testMutation.isPending}
            disabled={!platform}
          >
            测试连接
          </Button>
          <Button
            type="primary"
            onClick={() => form.submit()}
            loading={saveMutation.isPending}
          >
            保存配置
          </Button>
        </div>
      }
    >
      {configQuery.isError && (
        <Alert
          type="error"
          message="加载配置失败"
          description={
            configQuery.error instanceof Error
              ? configQuery.error.message
              : '请稍后重试'
          }
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* 复杂配置显示步骤条 */}
      {isComplexPlatform && steps.length > 0 && (
        <Steps
          current={currentStep}
          items={steps}
          onChange={setCurrentStep}
          style={{ marginBottom: 24 }}
        />
      )}

      <ProForm<SaveConfigRequest>
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          await handleSave(values);
          onClose();
          return true;
        }}
        submitter={false}
        loading={configQuery.isLoading}
      >
        {/* 启用开关 */}
        <div style={STYLES.formSection}>
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
        </div>

        <Divider orientation="left" style={{ marginLeft: 0 }}>
          配置信息
        </Divider>

        {/* 平台专属表单 */}
        {renderPlatformForm()}

        {/* 配置帮助 */}
        <div style={STYLES.helpSection}>
          <Text strong>配置帮助</Text>
          <ul style={{ margin: '8px 0 0 0', paddingLeft: 20, fontSize: 13 }}>
            <li>所有敏感信息将加密存储</li>
            <li>配置变更将记录审计日志</li>
            <li>建议定期更新密钥和凭证</li>
          </ul>
        </div>
      </ProForm>
    </Drawer>
  );
};

export default ConfigDrawer;
