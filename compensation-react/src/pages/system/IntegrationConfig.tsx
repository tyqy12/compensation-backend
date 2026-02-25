import React, { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Space,
  Button,
  Typography,
  Tag,
  Divider,
  Modal,
  Switch,
  Descriptions,
  Alert,
  App as AntdApp,
  Upload,
} from 'antd';
import {
  PageContainer,
  ProForm,
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
  ProFormDigit,
  ProFormSwitch,
  ProFormDependency,
} from '@ant-design/pro-components';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  DisconnectOutlined,
  SettingOutlined,
  ExperimentOutlined,
  SafetyCertificateOutlined,
  WechatOutlined,
  DingdingOutlined,
  MailOutlined,
  MessageOutlined,
  PayCircleOutlined,
  SecurityScanOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  InboxOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  useIntegrationListQuery,
  useIntegrationConfigQuery,
  useSaveIntegrationConfigMutation,
  useDisableIntegrationMutation,
  useTestIntegrationMutation,
} from '@services/queries/integration';
import type {
  Platform,
  IntegrationConfigListItem,
  SaveConfigRequest,
  WechatConfig,
  DingtalkConfig,
  FeishuConfig,
  AlipayConfig,
  SmsConfig,
  EmailConfig,
  EncryptionConfig,
} from '../../types/api';

const { Text } = Typography;

// 平台信息配置
const PLATFORM_INFO = {
  wechat: {
    name: '企业微信',
    icon: <WechatOutlined style={{ color: '#07c160' }} />,
    description: '企业内部通讯和消息推送',
    color: 'green',
  },
  dingtalk: {
    name: '钉钉',
    icon: <DingdingOutlined style={{ color: '#1890ff' }} />,
    description: '企业协作和组织管理',
    color: 'blue',
  },
  feishu: {
    name: '飞书',
    icon: <SettingOutlined style={{ color: '#722ed1' }} />,
    description: '团队协作和文档管理',
    color: 'purple',
  },
  alipay: {
    name: '支付宝',
    icon: <PayCircleOutlined style={{ color: '#1890ff' }} />,
    description: '支付接口和转账功能',
    color: 'blue',
  },
  sms: {
    name: '短信服务',
    icon: <MessageOutlined style={{ color: '#52c41a' }} />,
    description: '短信验证和通知推送',
    color: 'green',
  },
  email: {
    name: '邮件服务',
    icon: <MailOutlined style={{ color: '#fa8c16' }} />,
    description: '邮件发送和通知服务',
    color: 'orange',
  },
  encryption: {
    name: '加密配置',
    icon: <SecurityScanOutlined style={{ color: '#f5222d' }} />,
    description: '数据加密和安全配置',
    color: 'red',
  },
} as const;

// 连接状态显示
const getStatusDisplay = (status: string) => {
  switch (status) {
    case 'connected':
      return { icon: <CheckCircleOutlined />, text: '已连接', color: 'success' };
    case 'disconnected':
      return { icon: <DisconnectOutlined />, text: '未连接', color: 'default' };
    default:
      return { icon: <ExclamationCircleOutlined />, text: '未知', color: 'warning' };
  }
};

// 平台配置表单组件
const PlatformConfigForm: React.FC<{
  platform: Platform;
  onSave: (config: SaveConfigRequest) => Promise<void>;
  loading?: boolean;
}> = ({ platform, onSave, loading }) => {
  const configQuery = useIntegrationConfigQuery(platform);
  const [form] = ProForm.useForm<SaveConfigRequest>();
  const { message } = AntdApp.useApp();

  // 证书上传状态
  const [certUploading, setCertUploading] = useState<{
    appCert: boolean;
    alipayCert: boolean;
    alipayRootCert: boolean;
  }>({ appCert: false, alipayCert: false, alipayRootCert: false });

  useEffect(() => {
    form.resetFields();
    if (configQuery.data) {
      form.setFieldsValue({
        enabled: configQuery.data.enabled,
        [platform]: configQuery.data.config,
      } as Partial<SaveConfigRequest>);
    } else {
      form.setFieldsValue({ enabled: false } as Partial<SaveConfigRequest>);
    }
  }, [configQuery.data, form, platform]);

  // 处理证书上传
  const handleCertUpload = async (file: File, certType: 'appCert' | 'alipayCert' | 'alipayRootCert') => {
    setCertUploading(prev => ({ ...prev, [certType]: true }));
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('certType', certType);

      const response = await fetch('/api/admin/integration-configs/alipay/cert-upload', {
        method: 'POST',
        body: formData,
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token') || ''}`,
        },
      });

      const result = await response.json();

      if (result.code === 0 && result.data) {
        // 上传成功，更新表单字段
        const fieldName = certType === 'appCert' ? 'appCertPath' :
                         certType === 'alipayCert' ? 'alipayCertPath' : 'alipayRootCertPath';
        form.setFieldValue(['alipay', fieldName], result.data);
        message.success('证书上传成功');
        return true;
      } else {
        message.error(result.message || '证书上传失败');
        return false;
      }
    } catch (error) {
      message.error('证书上传失败: ' + (error as Error).message);
      return false;
    } finally {
      setCertUploading(prev => ({ ...prev, [certType]: false }));
    }
  };

  // 证书上传组件
  const CertUpload: React.FC<{
    certType: 'appCert' | 'alipayCert' | 'alipayRootCert';
    label: string;
  }> = ({ certType, label }) => {
    const fieldName = certType === 'appCert' ? 'appCertPath' :
                     certType === 'alipayCert' ? 'alipayCertPath' : 'alipayRootCertPath';
    const currentPath = form.getFieldValue(['alipay', fieldName]);

    return (
      <div style={{ marginBottom: 24 }}>
        <div style={{ marginBottom: 8, fontWeight: 500 }}>{label}</div>
        <Upload.Dragger
          name="file"
          multiple={false}
          showUploadList={false}
          beforeUpload={(file) => {
            // 验证文件类型
            if (!file.name.endsWith('.crt')) {
              message.error('证书文件必须是 .crt 格式');
              return Upload.LIST_IGNORE;
            }
            // 验证文件大小 (1MB)
            if (file.size > 1024 * 1024) {
              message.error('证书文件大小不能超过 1MB');
              return Upload.LIST_IGNORE;
            }
            // 开始上传
            handleCertUpload(file, certType);
            return false; // 阻止自动上传，使用自定义上传逻辑
          }}
          disabled={certUploading[certType]}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽上传证书文件</p>
          <p className="ant-upload-hint">支持 .crt 格式，文件大小不超过 1MB</p>
        </Upload.Dragger>
        {currentPath && (
          <div style={{ marginTop: 8, padding: 8, backgroundColor: '#f6ffed', borderRadius: 4, border: '1px solid #b7eb8f' }}>
            <Text type="success" style={{ fontSize: 12 }}>
              <CheckCircleOutlined style={{ marginRight: 4 }} />
              已上传: {currentPath}
            </Text>
          </div>
        )}
      </div>
    );
  };

  const validateEncryptionField = (field: 'aesKey' | 'sm4Key') => async (_: unknown, value?: string) => {
    const otherField = field === 'aesKey' ? 'sm4Key' : 'aesKey';
    const otherValue = form.getFieldValue(['encryption', otherField]);
    const enabled = form.getFieldValue('enabled');

    if (!enabled) {
      return Promise.resolve();
    }

    if (!value && !otherValue) {
      return Promise.reject(new Error('至少填写 AES 或 SM4 密钥'));
    }

    if (value && value.length < 16) {
      return Promise.reject(new Error(`${field === 'aesKey' ? 'AES' : 'SM4'}密钥长度不能少于16字符`));
    }

    return Promise.resolve();
  };

  const renderConfigFields = () => {
    switch (platform) {
      case 'wechat':
        return (
          <>
            <ProFormText
              name={['wechat', 'corpId']}
              label="企业ID"
              placeholder="请输入企业微信的CorpId"
              rules={[{ required: true, message: '请输入企业ID' }]}
              tooltip="在企业微信管理后台的'我的企业'页面可查看"
            />
            <ProFormText.Password
              name={['wechat', 'corpSecret']}
              label="应用密钥"
              placeholder="请输入应用Secret"
              rules={[{ required: true, message: '请输入应用密钥' }]}
              tooltip="在企业微信应用管理页面可查看和重置"
            />
            <ProFormText
              name={['wechat', 'agentId']}
              label="应用ID"
              placeholder="请输入AgentId（可选）"
              tooltip="应用的AgentId，用于消息推送"
            />
          </>
        );

      case 'dingtalk':
        return (
          <>
            <ProFormText
              name={['dingtalk', 'appKey']}
              label="应用AppKey"
              placeholder="请输入钉钉应用的AppKey"
              rules={[{ required: true, message: '请输入应用AppKey' }]}
            />
            <ProFormText.Password
              name={['dingtalk', 'appSecret']}
              label="应用AppSecret"
              placeholder="请输入应用密钥"
              rules={[{ required: true, message: '请输入应用密钥' }]}
            />
          </>
        );

      case 'feishu':
        return (
          <>
            <ProFormText
              name={['feishu', 'appId']}
              label="应用ID"
              placeholder="请输入飞书应用ID"
              rules={[{ required: true, message: '请输入应用ID' }]}
            />
            <ProFormText.Password
              name={['feishu', 'appSecret']}
              label="应用密钥"
              placeholder="请输入应用密钥"
              rules={[{ required: true, message: '请输入应用密钥' }]}
            />
          </>
        );

      case 'alipay':
        return (
          <>
            <Alert
              message="支付宝配置说明"
              description={
                <div>
                  <p><strong>重要提示：</strong>转账功能必须使用证书模式！</p>
                  <p>1. 在支付宝开放平台下载三个证书文件</p>
                  <p>2. 上传证书到服务器并填写绝对路径</p>
                  <p>3. 密钥模式仅支持基础功能，不支持转账</p>
                </div>
              }
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
            />
            <ProFormSelect
              name={['alipay', 'certMode']}
              label="加签模式"
              placeholder="请选择加签模式"
              valueEnum={{
                publicKey: '公钥模式（基础功能）',
                cert: '证书模式（支持转账，推荐）',
              }}
              initialValue="cert"
              rules={[{ required: true, message: '请选择加签模式' }]}
              fieldProps={{
                onChange: (value) => {
                  // 强制切换到证书模式时清空相关字段
                  if (value === 'cert') {
                    form.setFieldsValue({
                      alipay: { publicKey: undefined }
                    });
                  }
                }
              }}
            />
            <ProFormText
              name={['alipay', 'appId']}
              label="应用ID"
              placeholder="请输入支付宝应用APPID"
              rules={[{ required: true, message: '请输入应用ID' }]}
            />
            <ProFormTextArea
              name={['alipay', 'privateKey']}
              label="应用私钥"
              placeholder="请输入RSA应用私钥（PKCS8格式）"
              rules={[{ required: true, message: '请输入应用私钥' }]}
              fieldProps={{ rows: 4 }}
            />
            <ProFormDependency name={['alipay', 'certMode']}>
              {({ alipay }) => {
                const certMode = alipay?.certMode || 'cert';
                if (certMode === 'cert') {
                  return (
                    <>
                      <Divider orientation="left">证书上传（推荐）</Divider>
                      <Alert
                        message="证书上传说明"
                        description="请直接上传从支付宝开放平台下载的三个证书文件，系统将自动保存到服务器并填写路径。"
                        type="info"
                        showIcon
                        style={{ marginBottom: 16 }}
                      />
                      <CertUpload certType="appCert" label="应用公钥证书" />
                      <CertUpload certType="alipayCert" label="支付宝公钥证书" />
                      <CertUpload certType="alipayRootCert" label="支付宝根证书" />

                      <Divider orientation="left">或手动填写路径</Divider>
                      <ProFormText
                        name={['alipay', 'appCertPath']}
                        label="应用公钥证书路径"
                        placeholder="/path/to/appCertPublicKey.crt"
                        rules={[{ required: true, message: '请输入应用公钥证书路径' }]}
                        tooltip="支付宝开放平台下载的应用公钥证书"
                      />
                      <ProFormText
                        name={['alipay', 'alipayCertPath']}
                        label="支付宝公钥证书路径"
                        placeholder="/path/to/alipayCertPublicKey.crt"
                        rules={[{ required: true, message: '请输入支付宝公钥证书路径' }]}
                        tooltip="支付宝开放平台下载的支付宝公钥证书"
                      />
                      <ProFormText
                        name={['alipay', 'alipayRootCertPath']}
                        label="支付宝根证书路径"
                        placeholder="/path/to/alipayRootCert.crt"
                        rules={[{ required: true, message: '请输入支付宝根证书路径' }]}
                        tooltip="支付宝开放平台下载的支付宝根证书"
                      />
                    </>
                  );
                }
                return (
                  <ProFormTextArea
                    name={['alipay', 'publicKey']}
                    label="支付宝公钥"
                    placeholder="请输入支付宝平台公钥"
                    rules={[{ required: true, message: '公钥模式需要输入支付宝公钥' }]}
                    fieldProps={{ rows: 4 }}
                  />
                );
              }}
            </ProFormDependency>
            <ProFormText
              name={['alipay', 'serverUrl']}
              label="服务器地址"
              placeholder="https://openapi.alipay.com/gateway.do"
              initialValue="https://openapi.alipay.com/gateway.do"
            />
            <ProFormText
              name={['alipay', 'notifyUrl']}
              label="异步通知地址"
              placeholder="请输入支付结果通知地址"
              tooltip="用于接收支付宝异步通知，需要外网可访问"
            />
          </>
        );

      case 'sms':
        return (
          <>
            <ProFormSelect
              name={['sms', 'provider']}
              label="服务提供商"
              placeholder="请选择短信服务商"
              valueEnum={{
                aliyun: '阿里云',
                tencent: '腾讯云',
                huawei: '华为云',
                mock: '测试环境',
              }}
              rules={[{ required: true, message: '请选择服务提供商' }]}
            />
            <ProFormText
              name={['sms', 'accessKeyId']}
              label="AccessKey ID"
              placeholder="请输入AccessKey ID"
            />
            <ProFormText.Password
              name={['sms', 'accessKeySecret']}
              label="AccessKey Secret"
              placeholder="请输入AccessKey Secret"
            />
            <ProFormText
              name={['sms', 'signName']}
              label="短信签名"
              placeholder="请输入短信签名"
            />
            <ProFormText
              name={['sms', 'templateCode']}
              label="默认模板代码"
              placeholder="请输入短信模板代码"
            />
            <ProFormDigit
              name={['sms', 'dailyLimit']}
              label="日发送限制"
              placeholder="10000"
              min={1}
              initialValue={10000}
            />
            <ProFormDigit
              name={['sms', 'rateLimitPerMinute']}
              label="每分钟限制"
              placeholder="60"
              min={1}
              initialValue={60}
            />
          </>
        );

      case 'email':
        return (
          <>
            <ProFormText
              name={['email', 'host']}
              label="SMTP服务器"
              placeholder="smtp.example.com"
              rules={[{ required: true, message: '请输入SMTP服务器地址' }]}
            />
            <ProFormDigit
              name={['email', 'port']}
              label="端口"
              placeholder="587"
              min={1}
              max={65535}
              initialValue={587}
            />
            <ProFormText
              name={['email', 'username']}
              label="用户名"
              placeholder="请输入邮箱用户名"
              rules={[{ required: true, message: '请输入用户名' }]}
            />
            <ProFormText.Password
              name={['email', 'password']}
              label="密码"
              placeholder="请输入邮箱密码或授权码"
            />
            <ProFormText
              name={['email', 'fromAddress']}
              label="发件人邮箱"
              placeholder="noreply@example.com"
            />
            <ProFormText
              name={['email', 'fromName']}
              label="发件人名称"
              placeholder="系统通知"
            />
            <ProFormSwitch
              name={['email', 'ssl']}
              label="启用SSL"
              tooltip="是否使用SSL加密连接"
            />
            <ProFormSwitch
              name={['email', 'tls']}
              label="启用TLS"
              tooltip="是否使用TLS加密连接"
              initialValue={true}
            />
          </>
        );

      case 'encryption':
        return (
          <>
            <Alert
              message="加密配置说明"
              description="至少需要配置AES密钥或SM4密钥中的一种，密钥长度不能少于16个字符。"
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
            <ProFormText.Password
              name={['encryption', 'aesKey']}
              label="AES加密密钥"
              placeholder="请输入AES密钥（至少16字符）"
              rules={[{ validator: validateEncryptionField('aesKey') }]}
              fieldProps={{
                onChange: () => form.validateFields([['encryption', 'sm4Key']]),
              }}
            />
            <ProFormText.Password
              name={['encryption', 'sm4Key']}
              label="SM4加密密钥"
              placeholder="请输入SM4密钥（至少16字符）"
              rules={[{ validator: validateEncryptionField('sm4Key') }]}
              fieldProps={{
                onChange: () => form.validateFields([['encryption', 'aesKey']]),
              }}
            />
            <ProFormSelect
              name={['encryption', 'algorithm']}
              label="加密算法"
              placeholder="请选择加密算法"
              valueEnum={{
                'AES': 'AES',
                'SM4': 'SM4',
                'SM4+AES': 'SM4+AES',
              }}
              initialValue="SM4+AES"
            />
            <ProFormSelect
              name={['encryption', 'keyDerivation']}
              label="密钥派生算法"
              placeholder="请选择密钥派生算法"
              valueEnum={{
                'SHA-256': 'SHA-256',
                'SHA-512': 'SHA-512',
                'PBKDF2': 'PBKDF2',
              }}
              initialValue="SHA-256"
            />
            <ProFormDigit
              name={['encryption', 'keyRotationDays']}
              label="密钥轮换周期（天）"
              placeholder="90"
              min={1}
              max={365}
              initialValue={90}
            />
          </>
        );

      default:
        return <Alert message="不支持的平台类型" type="error" />;
    }
  };

  const handleSubmit = async (values: SaveConfigRequest) => {
    const payload: SaveConfigRequest = { enabled: values.enabled };
    const platformValues = (values as unknown as Record<string, unknown>)[platform];
    if (platformValues) {
      (payload as unknown as Record<string, unknown>)[platform] = platformValues;
    }
    await onSave(payload);
  };

  return (
    <Space size="large" style={{ width: '100%' }}>
      {configQuery.isError && (
        <Alert
          type="error"
          message="加载配置失败"
          description={configQuery.error instanceof Error ? configQuery.error.message : '请稍后重试'}
          showIcon
        />
      )}
      <ProForm<SaveConfigRequest>
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          await handleSubmit(values);
          return true;
        }}
        loading={configQuery.isLoading || loading}
        submitter={{
          searchConfig: { submitText: '保存配置' },
          resetButtonProps: { style: { display: 'none' } },
          submitButtonProps: {
            loading: loading,
            disabled: configQuery.isLoading,
          },
        }}
      >
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

        <Divider orientation="left" style={{ marginLeft: 0 }}>配置信息</Divider>

        {renderConfigFields()}
      </ProForm>
    </Space>
  );
};

const IntegrationConfigPage: React.FC = () => {
  const [selectedPlatform, setSelectedPlatform] = useState<Platform | null>(null);
  const [configModalVisible, setConfigModalVisible] = useState(false);

  const { message, modal } = AntdApp.useApp();

  // 查询hooks
  const listQuery = useIntegrationListQuery();
  const saveConfigMutation = useSaveIntegrationConfigMutation();
  const disableMutation = useDisableIntegrationMutation();
  const testMutation = useTestIntegrationMutation();

  // 处理配置保存
  const handleSaveConfig = async (config: SaveConfigRequest) => {
    if (!selectedPlatform) return;

    try {
      await saveConfigMutation.mutateAsync({
        platformType: selectedPlatform,
        config,
      });
      message.success('配置保存成功！');
      setConfigModalVisible(false);
      setSelectedPlatform(null);
    } catch (error: any) {
      message.error(`保存失败：${error.message || '请检查配置信息'}`);
    }
  };

  // 处理禁用配置
  const handleDisable = (platform: Platform) => {
    modal.confirm({
      title: '确认禁用',
      content: `确定要禁用 ${PLATFORM_INFO[platform].name} 的配置吗？`,
      icon: <ExclamationCircleOutlined />,
      okButtonProps: { danger: true },
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

  // 处理测试连接
  const handleTestConnection = async (platform: Platform) => {
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

  // 渲染平台卡片
  const renderPlatformCard = (item: IntegrationConfigListItem) => {
    const platformInfo = PLATFORM_INFO[item.platformType] ?? {
      name: item.platformName ?? item.platformType,
      icon: <GlobalOutlined style={{ color: '#595959' }} />,
      description: item.platformName ?? '第三方平台',
      color: 'default' as const,
    };
    const statusDisplay = getStatusDisplay(item.connectionStatus);

    return (
      <Col xs={24} sm={12} lg={8} xl={6} key={item.platformType}>
        <Card
          size="small"
          title={
            <Space>
              {platformInfo.icon}
              <span>{platformInfo.name}</span>
            </Space>
          }
          extra={
            <Tag icon={statusDisplay.icon} color={statusDisplay.color}>
              {statusDisplay.text}
            </Tag>
          }
          actions={[
            <Button
              key="config"
              type="link"
              icon={<SettingOutlined />}
              onClick={() => {
                setSelectedPlatform(item.platformType);
                setConfigModalVisible(true);
              }}
            >
              配置
            </Button>,
            <Button
              key="test"
              type="link"
              icon={<ExperimentOutlined />}
              loading={testMutation.isPending}
              disabled={!item.configured}
              onClick={() => handleTestConnection(item.platformType)}
            >
              测试
            </Button>,
            <Button
              key="disable"
              type="link"
              danger
              disabled={!item.enabled}
              onClick={() => handleDisable(item.platformType)}
            >
              禁用
            </Button>,
          ]}
        >
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary">{platformInfo.description}</Text>
          </div>

          <Descriptions size="small" column={1}>
            <Descriptions.Item label="状态">
              <Space>
                <Switch
                  size="small"
                  checked={item.enabled}
                  disabled
                />
                <Text type="secondary">
                  {item.enabled ? '已启用' : '未启用'}
                </Text>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="配置">
              <Text type={item.configured ? 'success' : 'secondary'}>
                {item.configured ? '已配置' : '未配置'}
              </Text>
            </Descriptions.Item>
            {item.lastModified && (
              <Descriptions.Item label="更新时间">
                <Text type="secondary">
                  {new Date(item.lastModified).toLocaleDateString('zh-CN')}
                </Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>
      </Col>
    );
  };

  return (
    <PageContainer
      header={{
        title: '集成配置管理',
        subTitle: '管理第三方平台集成配置',
        extra: [
          <Button
            key="refresh"
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
        <InfoCircleOutlined style={{ fontSize: 16, color: '#1890ff', marginRight: 8 }} />
        <span style={{ fontSize: 14, color: '#1890ff' }}>
          集成配置说明：配置各种第三方平台的接入信息，包括企业通讯平台、支付接口、通知服务等。配置完成后请务必进行连接测试以确保正常工作。
        </span>
      </div>

      <Space size="large" style={{ width: '100%' }}>
        {/* 平台配置卡片网格 */}
        <Row gutter={[16, 16]}>
          {listQuery.data?.map(renderPlatformCard)}
        </Row>

        {/* 操作提示 */}
        <Card size="small" title={<><SafetyCertificateOutlined style={{ marginRight: 8 }} />安全提示</>}>
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            <li>所有敏感配置信息在数据库中均已加密存储</li>
            <li>配置操作会记录详细的审计日志</li>
            <li>建议定期更换密钥和凭证</li>
            <li>生产环境必须使用HTTPS传输</li>
          </ul>
        </Card>
      </Space>

      {/* 配置表单弹窗 */}
      <Modal
        title={
          selectedPlatform ? (
            <Space>
              {PLATFORM_INFO[selectedPlatform].icon}
              <span>配置 {PLATFORM_INFO[selectedPlatform].name}</span>
            </Space>
          ) : '配置平台'
        }
        open={configModalVisible}
        onCancel={() => {
          setConfigModalVisible(false);
          setSelectedPlatform(null);
        }}
        width={800}
        footer={null}
        destroyOnHidden
      >
        {selectedPlatform && (
          <PlatformConfigForm
            platform={selectedPlatform}
            onSave={handleSaveConfig}
            loading={saveConfigMutation.isPending}
          />
        )}
      </Modal>
    </PageContainer>
  );
};

export default IntegrationConfigPage;
