import React, { useEffect } from 'react';
import { App as AntdApp } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { setApiErrorHandler } from '@services/errors';
import { logout, store } from '@services/stores/authSlice';

export const AuthErrorHandler: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    setApiErrorHandler(({ status, message: apiMessage }) => {
      if (status === 401) {
        store.dispatch(logout());
        message.warning('登录已过期，请重新登录');
        if (location.pathname !== '/login') {
          navigate('/login', { replace: true, state: { from: location } });
        }
        return;
      }

      if (status === 403) {
        message.warning('没有权限执行该操作');
        return;
      }

      message.error(apiMessage);
    });
  }, [location, message, navigate]);

  return null;
};

export default AuthErrorHandler;
