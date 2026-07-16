/**
 * AlipayForm 组件
 *
 * 支付宝配置表单
 */

import React from 'react';
import {
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
  ProFormDependency,
} from '@ant-design/pro-components';
import { Alert, Divider } from 'antd';
import CertUpload from '../CertUpload';

interface AlipayFormProps {
  form: any;
}

const AlipayForm: React.FC<AlipayFormProps> = ({ form }) => {
  return (
    <>
      <Alert
        title="支付宝配置说明"
        description={
          <div>
            <p>
              <strong>重要提示：</strong>转账功能必须使用证书模式！
            </p>
            <p>1. 在支付宝开放平台下载三个证书文件</p>
            <p>2. 上传证书到服务器并填写绝对路径</p>
            <p>3. 密钥模式仅支持基础功能，不支持转账</p>
          </div>
        }
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <ProFormSelect
        name={['alipay', 'certMode']}
        label="加签模式"
        placeholder="请选择加签模式"
        valueEnum={{
          publicKey: '公钥模式（基础功能）',
          cert: '证书模式（支持转账，推荐）',
        }}
        initialValue="cert"
        rules={[{ required: true, message: '请选择加签模式' }]}
        fieldProps={{
          onChange: (value) => {
            if (value === 'cert') {
              form.setFieldsValue({
                alipay: { publicKey: undefined },
              });
            }
          },
        }}
      />

      <ProFormText
        name={['alipay', 'appId']}
        label="应用ID"
        placeholder="请输入支付宝应用APPID"
        rules={[{ required: true, message: '请输入应用ID' }]}
      />

      <ProFormTextArea
        name={['alipay', 'privateKey']}
        label="应用私钥"
        placeholder="请输入RSA应用私钥（PKCS8格式）"
        rules={[{ required: true, message: '请输入应用私钥' }]}
        fieldProps={{ rows: 4 }}
      />

      <ProFormDependency name={['alipay', 'certMode']}>
        {({ alipay }) => {
          const certMode = alipay?.certMode || 'cert';
          if (certMode === 'cert') {
            return (
              <>
                <Divider orientation="left">证书上传（推荐）</Divider>
                <Alert
                  title="证书上传说明"
                  description="请直接上传从支付宝开放平台下载的三个证书文件，系统将自动保存到服务器并填写路径。"
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                />

                <ProFormDependency name={['alipay', 'appCertPath']}>
                  {({ alipay: { appCertPath } }) => (
                    <CertUpload
                      certType="appCert"
                      label="应用公钥证书"
                      value={appCertPath}
                      onChange={(value) => form.setFieldValue(['alipay', 'appCertPath'], value)}
                    />
                  )}
                </ProFormDependency>

                <ProFormDependency name={['alipay', 'alipayCertPath']}>
                  {({ alipay: { alipayCertPath } }) => (
                    <CertUpload
                      certType="alipayCert"
                      label="支付宝公钥证书"
                      value={alipayCertPath}
                      onChange={(value) => form.setFieldValue(['alipay', 'alipayCertPath'], value)}
                    />
                  )}
                </ProFormDependency>

                <ProFormDependency name={['alipay', 'alipayRootCertPath']}>
                  {({ alipay: { alipayRootCertPath } }) => (
                    <CertUpload
                      certType="alipayRootCert"
                      label="支付宝根证书"
                      value={alipayRootCertPath}
                      onChange={(value) =>
                        form.setFieldValue(['alipay', 'alipayRootCertPath'], value)
                      }
                    />
                  )}
                </ProFormDependency>

                <Divider orientation="left">或手动填写路径</Divider>
                <ProFormText
                  name={['alipay', 'appCertPath']}
                  label="应用公钥证书路径"
                  placeholder="/path/to/appCertPublicKey.crt"
                  rules={[{ required: true, message: '请输入应用公钥证书路径' }]}
                  tooltip="支付宝开放平台下载的应用公钥证书"
                />
                <ProFormText
                  name={['alipay', 'alipayCertPath']}
                  label="支付宝公钥证书路径"
                  placeholder="/path/to/alipayCertPublicKey.crt"
                  rules={[{ required: true, message: '请输入支付宝公钥证书路径' }]}
                  tooltip="支付宝开放平台下载的支付宝公钥证书"
                />
                <ProFormText
                  name={['alipay', 'alipayRootCertPath']}
                  label="支付宝根证书路径"
                  placeholder="/path/to/alipayRootCert.crt"
                  rules={[{ required: true, message: '请输入支付宝根证书路径' }]}
                  tooltip="支付宝开放平台下载的支付宝根证书"
                />
              </>
            );
          }
          return (
            <ProFormTextArea
              name={['alipay', 'publicKey']}
              label="支付宝公钥"
              placeholder="请输入支付宝平台公钥"
              rules={[{ required: true, message: '公钥模式需要输入支付宝公钥' }]}
              fieldProps={{ rows: 4 }}
            />
          );
        }}
      </ProFormDependency>

      <ProFormText
        name={['alipay', 'serverUrl']}
        label="服务器地址"
        placeholder="https://openapi.alipay.com/gateway.do"
        initialValue="https://openapi.alipay.com/gateway.do"
      />
      <ProFormText
        name={['alipay', 'notifyUrl']}
        label="异步通知地址"
        placeholder="请输入支付结果通知地址"
        tooltip="用于接收支付宝异步通知，需要外网可访问"
      />
    </>
  );
};

export default AlipayForm;
