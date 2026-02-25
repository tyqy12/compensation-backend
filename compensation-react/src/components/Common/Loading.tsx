import React from 'react';
import { Spin, type SpinProps } from 'antd';

export const Loading: React.FC<SpinProps> = (props) => (
  <div style={{ padding: 24, textAlign: 'center' }}>
    <Spin {...props} />
  </div>
);

export default Loading;
