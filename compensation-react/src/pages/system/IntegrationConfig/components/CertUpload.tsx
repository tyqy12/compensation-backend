/**
 * CertUpload 组件
 *
 * 证书上传组件，用于支付宝等平台证书上传
 * 提取为独立组件以便复用
 */

import React, { useState } from 'react';
import { Upload, Typography, App as AntdApp } from 'antd';
import { InboxOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { CERT_UPLOAD_CONFIG, STYLES } from '../constants';

const { Text } = Typography;

interface CertUploadProps {
  certType: 'appCert' | 'alipayCert' | 'alipayRootCert';
  label: string;
  value?: string;
  onChange?: (value: string) => void;
}

const CertUpload: React.FC<CertUploadProps> = ({
  certType,
  label,
  value,
  onChange,
}) => {
  const { message } = AntdApp.useApp();
  const [uploading, setUploading] = useState(false);

  const handleCertUpload = async (file: File): Promise<boolean> => {
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('certType', certType);

      const response = await fetch(CERT_UPLOAD_CONFIG.uploadEndpoint, {
        method: 'POST',
        body: formData,
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token') || ''}`,
        },
      });

      const result = await response.json();

      if (result.code === 0 && result.data) {
        onChange?.(result.data);
        message.success('证书上传成功');
        return true;
      } else {
        message.error(result.message || '证书上传失败');
        return false;
      }
    } catch (error) {
      message.error('证书上传失败: ' + (error as Error).message);
      return false;
    } finally {
      setUploading(false);
    }
  };

  const beforeUpload = (file: File): boolean => {
    // 验证文件类型
    const isValidType = CERT_UPLOAD_CONFIG.allowedTypes.some(type =>
      file.name.toLowerCase().endsWith(type)
    );
    if (!isValidType) {
      message.error('证书文件必须是 .crt 格式');
      return false;
    }

    // 验证文件大小
    if (file.size > CERT_UPLOAD_CONFIG.maxSize) {
      message.error('证书文件大小不能超过 1MB');
      return false;
    }

    // 开始上传
    handleCertUpload(file);
    return false; // 阻止自动上传
  };

  return (
    <div style={STYLES.certUploadContainer}>
      <div style={STYLES.certUploadLabel}>{label}</div>
      <Upload.Dragger
        name="file"
        multiple={false}
        showUploadList={false}
        beforeUpload={beforeUpload}
        disabled={uploading}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽上传证书文件</p>
        <p className="ant-upload-hint">
          支持 .crt 格式，文件大小不超过 1MB
        </p>
      </Upload.Dragger>
      {value && (
        <div style={STYLES.certUploaded}>
          <Text type="success" style={{ fontSize: 12 }}>
            <CheckCircleOutlined style={{ marginRight: 4 }} />
            已上传: {value}
          </Text>
        </div>
      )}
    </div>
  );
};

export default CertUpload;
