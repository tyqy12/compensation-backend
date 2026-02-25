import React from 'react';
import { FloatButton } from 'antd';

interface BackTopProps {
  target?: () => HTMLElement | Window | Document;
  visibilityHeight?: number;
}

export const BackTop: React.FC<BackTopProps> = ({ target, visibilityHeight = 400 }) => {
  return (
    <FloatButton.BackTop
      target={target}
      visibilityHeight={visibilityHeight}
      style={{
        right: 24,
        bottom: 80,
      }}
    />
  );
};

export default BackTop;
