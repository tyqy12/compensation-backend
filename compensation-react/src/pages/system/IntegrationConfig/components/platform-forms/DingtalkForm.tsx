/**
 * DingtalkForm 组件
 *
 * 钉钉配置表单
 */

import React from 'react';
import { ProFormText, ProFormTextArea } from '@ant-design/pro-components';

const DingtalkForm: React.FC = () => {
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
};

export default DingtalkForm;
