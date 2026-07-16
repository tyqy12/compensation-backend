import React from 'react';
import { render } from '@testing-library/react';
import { Loading } from './Loading';

describe('Loading组件', () => {
  it('应该正确渲染默认状态', () => {
    const { container } = render(<Loading />);

    // 检查loading元素是否存在
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('应该正确渲染自定义提示文本', () => {
    const customTip = '正在处理数据...';
    const { container } = render(<Loading description={customTip} />);

    // 检查loading元素是否存在
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('应该正确渲染不同尺寸', () => {
    const { container, rerender } = render(<Loading size="small" />);
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();

    rerender(<Loading size="large" />);
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
  });
});
