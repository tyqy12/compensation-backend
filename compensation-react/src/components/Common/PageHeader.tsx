import React from 'react';
import { Typography, Space } from 'antd';

type Props = {
  title: string;
  extra?: React.ReactNode;
  children?: React.ReactNode;
};

export const PageHeader: React.FC<Props> = ({ title, extra, children }) => {
  return (
    <div className="app-page-header-simple">
      <Space className="app-page-header-simple-row">
        <Typography.Title level={3}>{title}</Typography.Title>
        {extra}
      </Space>
      {children}
    </div>
  );
};

export default PageHeader;
