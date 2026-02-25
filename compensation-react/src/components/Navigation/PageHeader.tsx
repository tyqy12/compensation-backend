import React from 'react';
import { Button, Space } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { AppBreadcrumb } from './Breadcrumb';

interface PageHeaderProps {
  title?: string;
  subTitle?: string;
  extra?: React.ReactNode;
  showBack?: boolean;
  onBack?: () => void;
  children?: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  subTitle,
  extra,
  showBack = false,
  onBack,
  children,
  className,
  style,
}) => {
  const navigate = useNavigate();

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      navigate(-1);
    }
  };

  return (
    <div
      className={className}
      style={{
        backgroundColor: '#fff',
        padding: '16px 24px',
        marginBottom: '16px',
        borderRadius: '6px',
        boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
        border: '1px solid #f0f0f0',
        ...style,
      }}
    >
      {/* 面包屑导航 */}
      <AppBreadcrumb />

      {/* 页面标题和操作 */}
      {(title || extra || showBack) && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            marginTop: title ? '8px' : 0,
          }}
        >
          <div style={{ flex: 1, minWidth: 0 }}>
            {showBack && (
              <Button
                type="text"
                icon={<ArrowLeftOutlined />}
                onClick={handleBack}
                style={{ marginBottom: '8px' }}
              >
                返回
              </Button>
            )}
            {title && (
              <h1
                style={{
                  fontSize: '24px',
                  fontWeight: 600,
                  margin: 0,
                  marginBottom: subTitle ? '4px' : 0,
                  color: '#262626',
                }}
              >
                {title}
              </h1>
            )}
            {subTitle && (
              <p
                style={{
                  fontSize: '14px',
                  color: '#8c8c8c',
                  margin: 0,
                }}
              >
                {subTitle}
              </p>
            )}
          </div>

          {extra && (
            <div style={{ marginLeft: '16px' }}>
              {Array.isArray(extra) ? <Space>{extra}</Space> : extra}
            </div>
          )}
        </div>
      )}

      {/* 页面内容 */}
      {children}
    </div>
  );
};

export default PageHeader;
