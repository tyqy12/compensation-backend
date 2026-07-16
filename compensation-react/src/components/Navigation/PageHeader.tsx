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
    <div className={`app-navigation-page-header ${className || ''}`} style={style}>
      {/* 面包屑导航 */}
      <AppBreadcrumb />

      {/* 页面标题和操作 */}
      {(title || extra || showBack) && (
        <div className="app-navigation-page-header-row">
          <div className="app-navigation-page-header-copy">
            {showBack && (
              <Button type="text" icon={<ArrowLeftOutlined />} onClick={handleBack}>
                返回
              </Button>
            )}
            {title && <h1 className="app-navigation-page-header-title">{title}</h1>}
            {subTitle && <p className="app-navigation-page-header-subtitle">{subTitle}</p>}
          </div>

          {extra && (
            <div className="app-navigation-page-header-extra">
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
