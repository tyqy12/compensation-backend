import React from 'react';
import { Spin, type SpinProps } from 'antd';

export const Loading: React.FC<SpinProps> = (props) => (
  <div className="app-loading">
    <Spin {...props} />
  </div>
);

export default Loading;
