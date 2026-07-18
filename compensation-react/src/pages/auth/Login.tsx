import React from 'react';
import { App as AntdApp, Button, Divider, Form, Input, Typography } from 'antd';
import {
  ArrowRightOutlined,
  LockOutlined,
  QrcodeOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { useLoginMutation } from '@services/queries/auth';
import { authorizeWecom } from '@services/auth';
import { isWeComEnv } from '@services/wecom';
import { toMessage } from '@utils/error';
import { appName } from '@app/theme';
import './Login.less';

const { Paragraph, Text, Title } = Typography;

const resolveWecomRedirectUri = () => {
  const configured = (import.meta as any).env.VITE_OAUTH_REDIRECT_URI_WECHAT as string | undefined;
  if (configured?.trim()) return configured.trim();
  return `${window.location.origin}/oauth/callback/wechat`;
};

const Login: React.FC = () => {
  const login = useLoginMutation();
  const { message } = AntdApp.useApp();
  const [authLoading, setAuthLoading] = React.useState(false);

  const onFinish = (values: { username: string; password: string }) => {
    login.mutate({ username: values.username, password: values.password });
  };

  const handleWecomLogin = async () => {
    try {
      setAuthLoading(true);
      const channel: 'wecom' | 'web' = isWeComEnv() ? 'wecom' : 'web';
      const { url } = await authorizeWecom(channel, resolveWecomRedirectUri());
      window.location.href = url;
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setAuthLoading(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-brand-panel" aria-label="产品介绍">
        <div className="login-brand-mark">
          <WalletOutlined />
        </div>
        <Text className="login-eyebrow">OPERATIONS CONSOLE</Text>
        <Title level={1}>{appName}</Title>
        <Paragraph className="login-brand-description">
          统一管理员工、组织、薪酬、支付与审批流程，让每一次人事与财务操作都清晰可追溯。
        </Paragraph>
        <div className="login-brand-meta">
          <span>
            <i /> 人事数据
          </span>
          <span>
            <i /> 薪酬流程
          </span>
          <span>
            <i /> 权限控制
          </span>
        </div>
      </section>

      <section className="login-form-panel">
        <div className="login-form-heading">
          <Text className="login-form-kicker">WELCOME BACK</Text>
          <Title level={2}>欢迎回来</Title>
          <Text type="secondary">登录后继续处理今日工作。</Text>
        </div>

        <Form layout="vertical" onFinish={onFinish} autoComplete="off" className="login-form">
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input autoFocus prefix={<LockOutlined />} placeholder="请输入用户名" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" size="large" />
          </Form.Item>
          <Button
            className="login-submit-button"
            type="primary"
            htmlType="submit"
            block
            size="large"
            icon={<ArrowRightOutlined />}
            loading={login.isPending}
          >
            登录工作台
          </Button>
        </Form>

        <Divider plain>或</Divider>

        <Button
          className="login-wecom-button"
          block
          size="large"
          icon={<QrcodeOutlined />}
          loading={authLoading}
          onClick={handleWecomLogin}
        >
          企业微信登录
        </Button>

        <Text className="login-security-note" type="secondary">
          <LockOutlined /> 你的登录信息将通过安全连接传输
        </Text>
      </section>
    </main>
  );
};

export default Login;
