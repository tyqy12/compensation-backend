/**
 * YunzhanghuForm 组件
 *
 * 云账户配置表单
 */

import React from 'react';
import {
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
  ProFormSwitch,
} from '@ant-design/pro-components';
import { Alert } from 'antd';

const YunzhanghuForm: React.FC = () => {
  return (
    <>
      <Alert
        title="云账户沙箱接入说明"
        description={
          <div>
            <p>
              1. 当前建议先接入沙箱：<code>https://api-service.yunzhanghu.com/sandbox</code>
            </p>
            <p>2. 必填参数由开放平台提供，互联网平台名称需填写实际收入来源平台。</p>
            <p>
              3. 签名类型推荐 <code>rsa</code>，与 SDK 配置保持一致。
            </p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <ProFormText
        name={['yunzhanghu', 'dealerId']}
        label="平台企业ID (dealerId)"
        placeholder="请输入 dealerId"
        rules={[{ required: true, message: '请输入 dealerId' }]}
      />
      <ProFormText
        name={['yunzhanghu', 'brokerId']}
        label="综合服务主体ID (brokerId)"
        placeholder="请输入 brokerId"
        rules={[{ required: true, message: '请输入 brokerId' }]}
      />
      <ProFormText
        name={['yunzhanghu', 'appKey']}
        label="App Key"
        placeholder="请输入 appKey"
        rules={[{ required: true, message: '请输入 appKey' }]}
      />
      <ProFormText.Password
        name={['yunzhanghu', 'des3Key']}
        label="3DES Key"
        placeholder="请输入 3DES Key"
        rules={[{ required: true, message: '请输入 3DES Key' }]}
      />
      <ProFormSelect
        name={['yunzhanghu', 'signType']}
        label="签名类型"
        valueEnum={{
          rsa: 'RSA',
          sha256: 'SHA256',
        }}
        initialValue="rsa"
        rules={[{ required: true, message: '请选择签名类型' }]}
      />
      <ProFormTextArea
        name={['yunzhanghu', 'rsaPrivateKey']}
        label="RSA 私钥"
        placeholder="请输入 RSA 私钥"
        rules={[{ required: true, message: '请输入 RSA 私钥' }]}
        fieldProps={{ rows: 4 }}
      />
      <ProFormTextArea
        name={['yunzhanghu', 'rsaPublicKey']}
        label="云账户公钥"
        placeholder="请输入云账户公钥"
        rules={[{ required: true, message: '请输入云账户公钥' }]}
        fieldProps={{ rows: 4 }}
      />
      <ProFormText
        name={['yunzhanghu', 'url']}
        label="请求地址"
        placeholder="https://api-service.yunzhanghu.com/sandbox"
        initialValue="https://api-service.yunzhanghu.com/sandbox"
        rules={[{ required: true, message: '请输入请求地址' }]}
      />
      <ProFormText
        name={['yunzhanghu', 'notifyUrl']}
        label="回调地址"
        placeholder="请输入云账户支付回调地址（可选）"
      />
      <ProFormText
        name={['yunzhanghu', 'projectId']}
        label="业务线标识"
        placeholder="可选，用于平台侧区分业务线"
      />
      <ProFormText
        name={['yunzhanghu', 'dealerPlatformName']}
        label="互联网平台名称"
        placeholder="请输入实际收入来源平台名称，如：薪酬助手"
        rules={[{ required: true, message: '请输入互联网平台名称' }]}
      />
      <ProFormSelect
        name={['yunzhanghu', 'checkName']}
        label="姓名校验策略"
        valueEnum={{
          Check: 'Check（校验）',
        }}
        placeholder="可选，默认不传"
      />
      <ProFormSwitch name={['yunzhanghu', 'isDebug']} label="开启SDK调试日志" initialValue={true} />
    </>
  );
};

export default YunzhanghuForm;
