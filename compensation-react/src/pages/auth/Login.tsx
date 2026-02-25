import React from 'react';
import { Button, Card, Form, Input, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useLoginMutation } from '@services/queries/auth';
import { authorizeWecom } from '@services/auth';
import { isWeComEnv } from '@services/wecom';
import { QrcodeOutlined } from '@ant-design/icons';
import { App as AntdApp } from 'antd';
import { toMessage } from '@utils/error';

const Login: React.FC = () => {
  const login = useLoginMutation();
  const { message } = AntdApp.useApp();
  const [authLoading, setAuthLoading] = React.useState(false);

  const onFinish = (values: any) => {
    login.mutate({ username: values.username, password: values.password });
  };

  return (
    <div style={{ display: 'grid', placeItems: 'center', height: '100vh' }}>
      <Card title={<Typography.Title level={3}>登录</Typography.Title>} style={{ width: 360 }}>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={login.isPending}>
              登录
            </Button>
          </Form.Item>
          <Form.Item>
            <Button
              block
              icon={<QrcodeOutlined />}
              loading={authLoading}
              onClick={async () => {
                try {
                  setAuthLoading(true);
                  const channel: 'wecom' | 'web' = isWeComEnv() ? 'wecom' : 'web';
                  const redirectUri = `${window.location.origin}/oauth/callback/wecom`;
                  const { url } = await authorizeWecom(channel, redirectUri);
                  window.location.href = url;
                } catch (e) {
                  message.error(toMessage(e));
                } finally {
                  setAuthLoading(false);
                }
              }}
            >
              企业微信登录
            </Button>
          </Form.Item>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            或使用 OAuth 登录 → <Link to="/oauth/callback/wecom">企业微信（模拟）</Link>
          </Typography.Paragraph>
        </Form>
      </Card>
    </div>
  );
};

export default Login;
