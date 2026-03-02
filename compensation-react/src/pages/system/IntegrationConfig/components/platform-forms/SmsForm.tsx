/**
 * SmsForm 组件
 *
 * 短信服务配置表单
 */

import React from 'react';
import {
  ProFormSelect,
  ProFormText,
  ProFormDigit,
} from '@ant-design/pro-components';

const SmsForm: React.FC = () => {
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
};

export default SmsForm;
