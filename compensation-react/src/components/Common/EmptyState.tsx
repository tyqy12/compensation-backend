import React from 'react';
import { Empty } from 'antd';

const EmptyState: React.FC<{ description?: string }> = ({ description }) => (
  <Empty description={description ?? 'No Data'} />
);

export default EmptyState;
