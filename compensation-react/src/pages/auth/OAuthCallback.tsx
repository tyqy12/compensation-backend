import React, { useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Typography, App as AntdApp } from 'antd';
import { useOAuthCallbackMutation } from '@services/queries/auth';
import type { Platform } from '@types/api';

const OAuthCallback: React.FC = () => {
  const { platform } = useParams();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const code = sp.get('code') || '';
  const state = sp.get('state') || undefined;
  const mutation = useOAuthCallbackMutation((platform as Platform) || 'wecom');
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (!platform || !code) {
      message.error('回调参数缺失');
      return;
    }
    mutation.mutate(
      { code, state },
      {
        onError: (e: any) => {
          const status = e?.response?.status ?? e?.status;
          if (status === 403) {
            message.warning(e?.message || '需要先完成账号绑定');
            setTimeout(() => navigate('/admin/user-binding', { replace: true }), 800);
          } else {
            message.error(e?.message || '登录失败');
            setTimeout(() => navigate('/login', { replace: true }), 800);
          }
        },
        onSuccess: () => {
          navigate('/', { replace: true });
        },
      },
    );
  }, [platform, code, state]);

  return (
    <Typography.Paragraph>
      处理 {platform} OAuth 回调中，正在跳转…
    </Typography.Paragraph>
  );
};

export default OAuthCallback;
