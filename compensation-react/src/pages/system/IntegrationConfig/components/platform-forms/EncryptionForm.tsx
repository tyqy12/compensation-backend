/**
 * EncryptionForm 组件
 *
 * 加密配置表单
 */

import React from 'react';
import { ProFormText, ProFormSelect, ProFormDigit } from '@ant-design/pro-components';
import { Alert } from 'antd';

interface EncryptionFormProps {
  form: any;
}

const EncryptionForm: React.FC<EncryptionFormProps> = ({ form }) => {
  const validateEncryptionField =
    (field: 'aesKey' | 'sm4Key') => async (_: unknown, value?: string) => {
      const otherField = field === 'aesKey' ? 'sm4Key' : 'aesKey';
      const otherValue = form.getFieldValue(['encryption', otherField]);
      const enabled = form.getFieldValue('enabled');

      if (!enabled) {
        return Promise.resolve();
      }

      if (!value && !otherValue) {
        return Promise.reject(new Error('至少填写 AES 或 SM4 密钥'));
      }

      if (value && value.length < 16) {
        return Promise.reject(
          new Error(`${field === 'aesKey' ? 'AES' : 'SM4'}密钥长度不能少于16字符`),
        );
      }

      return Promise.resolve();
    };

  return (
    <>
      <Alert
        title="加密配置说明"
        description="至少需要配置AES密钥或SM4密钥中的一种，密钥长度不能少于16个字符。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <ProFormText.Password
        name={['encryption', 'aesKey']}
        label="AES加密密钥"
        placeholder="请输入AES密钥（至少16字符）"
        rules={[{ validator: validateEncryptionField('aesKey') }]}
        fieldProps={{
          onChange: () => form.validateFields([['encryption', 'sm4Key']]),
        }}
      />
      <ProFormText.Password
        name={['encryption', 'sm4Key']}
        label="SM4加密密钥"
        placeholder="请输入SM4密钥（至少16字符）"
        rules={[{ validator: validateEncryptionField('sm4Key') }]}
        fieldProps={{
          onChange: () => form.validateFields([['encryption', 'aesKey']]),
        }}
      />
      <ProFormSelect
        name={['encryption', 'algorithm']}
        label="加密算法"
        placeholder="请选择加密算法"
        valueEnum={{
          AES: 'AES',
          SM4: 'SM4',
          'SM4+AES': 'SM4+AES',
        }}
        initialValue="SM4+AES"
      />
      <ProFormSelect
        name={['encryption', 'keyDerivation']}
        label="密钥派生算法"
        placeholder="请选择密钥派生算法"
        valueEnum={{
          'SHA-256': 'SHA-256',
          'SHA-512': 'SHA-512',
          PBKDF2: 'PBKDF2',
        }}
        initialValue="SHA-256"
      />
      <ProFormDigit
        name={['encryption', 'keyRotationDays']}
        label="密钥轮换周期（天）"
        placeholder="90"
        min={1}
        max={365}
        initialValue={90}
      />
    </>
  );
};

export default EncryptionForm;
