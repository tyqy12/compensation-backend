/**
 * FeishuForm 组件
 *
 * 飞书配置表单
 */

import React from 'react';
import { ProFormText, ProFormTextArea } from '@ant-design/pro-components';

const FeishuForm: React.FC = () => {
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
};

export default FeishuForm;
