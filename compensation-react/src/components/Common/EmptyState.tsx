import React from 'react';
import { Empty } from 'antd';

const EmptyState: React.FC<{ description?: string }> = ({ description }) => (
  <div className="app-empty-state">
    <Empty description={description ?? '暂无数据'} />
  </div>
);

export default EmptyState;
