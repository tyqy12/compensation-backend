import React, { useEffect } from 'react';
import { Button, App as AntdApp, Space, Typography, Alert, Descriptions, Tag } from 'antd';
import {
  PageContainer,
  ProCard,
  ProForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { CheckCircleOutlined, ExclamationCircleOutlined, LoadingOutlined } from '@ant-design/icons';
import {
  useIntegrationQuery,
  useSaveIntegrationMutation,
  useTestConnectionMutation,
} from '@services/queries/integration';
import type { Platform } from '@types/api';

const { Text } = Typography;

const IntegrationConfigPage: React.FC = () => {
  const [platform, setPlatform] = React.useState<Platform>('wecom');
  const [form] = ProForm.useForm();

  // Query hooks
  const integrationQuery = useIntegrationQuery(platform);
  const saveIntegration = useSaveIntegrationMutation(platform);
  const testConnection = useTestConnectionMutation(platform);

  const { message } = AntdApp.useApp();

  // Load form data when platform changes or query data updates
  useEffect(() => {
    if (integrationQuery.data) {
      form.setFieldsValue(integrationQuery.data);
    }
  }, [integrationQuery.data, form]);

  const onFinish = async (values: any) => {
    try {
      await saveIntegration.mutateAsync(values);
      message.success('配置保存成功！');
      return true;
    } catch (error: any) {
      message.error(`保存失败：${error.message || '请检查配置信息'}`);
      return false;
    }
  };

  const handleTestConnection = async () => {
    try {
      const result = await testConnection.mutateAsync();
      if (result.success) {
        message.success('连接测试成功！');
      } else {
        message.error(`连接测试失败：${result.message || '请检查配置'}`);
      }
    } catch (error: any) {
      message.error(`连接测试失败：${error.message || '网络错误'}`);
    }
  };

  const validateUrl = (rule: any, value: string) => {
    if (!value) return Promise.resolve();
    try {
      new URL(value);
      return Promise.resolve();
    } catch {
      return Promise.reject(new Error('请输入有效的URL地址'));
    }
  };

  const getStatusInfo = () => {
    if (integrationQuery.isLoading) {
      return { icon: <LoadingOutlined />, text: '加载中...', color: 'processing' };
    }
    if (integrationQuery.data?.enabled) {
      return { icon: <CheckCircleOutlined />, text: '已启用', color: 'success' };
    }
    return { icon: <ExclamationCircleOutlined />, text: '未配置', color: 'warning' };
  };

  const statusInfo = getStatusInfo();

  return (
    <PageContainer
      header={{
        title: '集成配置',
        subTitle: (
          <Space>
            <Tag icon={statusInfo.icon} color={statusInfo.color}>
              {statusInfo.text}
            </Tag>
          </Space>
        ),
      }}
      extra={[
        <Button
          key="test"
          onClick={handleTestConnection}
          loading={testConnection.isPending}
          disabled={!form.getFieldValue('clientId') || !form.getFieldValue('clientSecret')}
        >
          测试连接
        </Button>,
      ]}
      loading={integrationQuery.isLoading}
    >
      <Space size="middle" style={{ width: '100%' }}>
        {/* 配置说明 */}
        <Alert
          message="集成配置说明"
          description="配置第三方平台接入信息，完成后可进行组织同步和用户绑定。请确保所有信息准确无误。"
          type="info"
          showIcon
          style={{ marginBottom: '16px' }}
        />

        {/* 配置表单 */}
        <ProCard title="配置信息">
          <ProForm
            form={form}
            layout="vertical"
            onFinish={onFinish}
            loading={saveIntegration.isPending}
            submitter={{
              searchConfig: { submitText: '保存配置' },
              resetButtonProps: {
                style: { display: 'none' },
              },
              submitButtonProps: {
                loading: saveIntegration.isPending,
              },
            }}
          >
            <ProFormSelect
              name="platform"
              label="集成平台"
              tooltip="选择要集成的第三方平台"
              valueEnum={{
                wecom: '企业微信',
                dingtalk: '钉钉',
                feishu: '飞书',
              }}
              fieldProps={{
                onChange: (value: Platform) => {
                  setPlatform(value);
                  // 清空表单数据，准备加载新平台的配置
                  form.resetFields(['clientId', 'clientSecret', 'callbackUrl']);
                },
              }}
              rules={[{ required: true, message: '请选择集成平台' }]}
              initialValue="wecom"
            />

            <ProFormText
              name="clientId"
              label="应用ID/Client ID"
              tooltip="在第三方平台申请的应用标识"
              placeholder="请输入应用ID"
              rules={[
                { required: true, message: '请输入应用ID' },
                { min: 3, message: '应用ID长度不能少于3位' },
                {
                  pattern: /^[a-zA-Z0-9_-]+$/,
                  message: '应用ID只能包含字母、数字、下划线和连字符',
                },
              ]}
            />

            <ProFormText.Password
              name="clientSecret"
              label="应用密钥/Client Secret"
              tooltip="在第三方平台申请的应用密钥，请妥善保管"
              placeholder="请输入应用密钥"
              rules={[
                { required: true, message: '请输入应用密钥' },
                { min: 8, message: '应用密钥长度不能少于8位' },
              ]}
            />

            <ProFormTextArea
              name="callbackUrl"
              label="回调地址"
              tooltip="OAuth认证完成后的回调地址，请确保可以正常访问"
              placeholder={`https://yourdomain.com/oauth/callback/${platform}`}
              rules={[{ required: true, message: '请输入回调地址' }, { validator: validateUrl }]}
              fieldProps={{
                autoSize: { minRows: 2, maxRows: 4 },
              }}
            />
          </ProForm>
        </ProCard>

        {/* 配置状态展示 */}
        {integrationQuery.data && (
          <ProCard title="当前配置状态" size="small">
            <Descriptions column={2} size="small">
              <Descriptions.Item label="平台">
                {integrationQuery.data.platform === 'wecom'
                  ? '企业微信'
                  : integrationQuery.data.platform === 'dingtalk'
                    ? '钉钉'
                    : integrationQuery.data.platform === 'feishu'
                      ? '飞书'
                      : integrationQuery.data.platform}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={integrationQuery.data.enabled ? 'success' : 'default'}>
                  {integrationQuery.data.enabled ? '已启用' : '未启用'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="应用ID">
                <Text code>{integrationQuery.data.clientId || '未设置'}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="最后测试">
                {integrationQuery.data.lastTestedAt
                  ? new Date(integrationQuery.data.lastTestedAt).toLocaleString('zh-CN')
                  : '未测试'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {integrationQuery.data.createdAt
                  ? new Date(integrationQuery.data.createdAt).toLocaleString('zh-CN')
                  : '未知'}
              </Descriptions.Item>
            </Descriptions>
          </ProCard>
        )}

        {/* 操作说明 */}
        <Alert
          message="操作提示"
          description={
            <div>
              <p>1. 选择需要集成的平台类型</p>
              <p>2. 填写从第三方平台获取的应用凭证</p>
              <p>3. 点击&quot;测试连接&quot;验证配置是否正确</p>
              <p>4. 测试成功后点击&quot;保存配置&quot;完成设置</p>
            </div>
          }
          type="success"
          showIcon
        />
      </Space>
    </PageContainer>
  );
};

export default IntegrationConfigPage;
