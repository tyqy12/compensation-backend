import React from 'react';
import { FloatButton } from 'antd';

interface BackTopProps {
  target?: () => HTMLElement | Window | Document;
  visibilityHeight?: number;
}

export const BackTop: React.FC<BackTopProps> = ({ target, visibilityHeight = 400 }) => {
  return (
    <FloatButton.BackTop
      className="app-back-top"
      target={target}
      visibilityHeight={visibilityHeight}
    />
  );
};

export default BackTop;
