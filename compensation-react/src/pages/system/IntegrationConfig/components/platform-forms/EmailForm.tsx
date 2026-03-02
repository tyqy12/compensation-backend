/**
 * EmailForm 组件
 *
 * 邮件服务配置表单
 */

import React from 'react';
import {
  ProFormText,
  ProFormDigit,
  ProFormSwitch,
} from '@ant-design/pro-components';

const EmailForm: React.FC = () => {
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
};

export default EmailForm;
