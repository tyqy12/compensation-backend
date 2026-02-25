import React from 'react';
import { Typography, Space } from 'antd';

type Props = {
  title: string;
  extra?: React.ReactNode;
  children?: React.ReactNode;
};

export const PageHeader: React.FC<Props> = ({ title, extra, children }) => {
  return (
    <div style={{ marginBottom: 16 }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {extra}
      </Space>
      {children}
    </div>
  );
};

export default PageHeader;
