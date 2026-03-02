/**
 * WechatForm 组件
 *
 * 企业微信配置表单
 */

import React from 'react';
import { ProFormText, ProFormTextArea } from '@ant-design/pro-components';

const WechatForm: React.FC = () => {
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
};

export default WechatForm;
