import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { ConfigProvider } from 'antd';
import EmptyState from './EmptyState';

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ConfigProvider>{children}</ConfigProvider>
);

describe('EmptyState', () => {
  it('should render with default message', () => {
    render(
      <TestWrapper>
        <EmptyState />
      </TestWrapper>,
    );

    expect(screen.getByText(/暂无数据/)).toBeInTheDocument();
  });

  it('should render with custom message', () => {
    render(
      <TestWrapper>
        <EmptyState message="没有找到相关内容" />
      </TestWrapper>,
    );

    expect(screen.getByText('没有找到相关内容')).toBeInTheDocument();
  });

  it('should render with custom description', () => {
    render(
      <TestWrapper>
        <EmptyState message="暂无数据" description="请尝试其他搜索条件或稍后再试" />
      </TestWrapper>,
    );

    expect(screen.getByText('暂无数据')).toBeInTheDocument();
    expect(screen.getByText('请尝试其他搜索条件或稍后再试')).toBeInTheDocument();
  });

  it('should render with action button', () => {
    const mockAction = vi.fn();

    render(
      <TestWrapper>
        <EmptyState
          message="暂无数据"
          action={{
            text: '创建新项目',
            onClick: mockAction,
          }}
        />
      </TestWrapper>,
    );

    const actionButton = screen.getByText('创建新项目');
    expect(actionButton).toBeInTheDocument();

    fireEvent.click(actionButton);
    expect(mockAction).toHaveBeenCalledTimes(1);
  });

  it('should render with custom image', () => {
    render(
      <TestWrapper>
        <EmptyState message="暂无数据" image="/custom-empty.svg" />
      </TestWrapper>,
    );

    expect(screen.getByText('暂无数据')).toBeInTheDocument();
    // Image would be rendered by Ant Design Empty component
  });

  it('should render without description when not provided', () => {
    render(
      <TestWrapper>
        <EmptyState message="暂无数据" />
      </TestWrapper>,
    );

    expect(screen.getByText('暂无数据')).toBeInTheDocument();
    // Should not have any description text
  });

  it('should render with loading action button', () => {
    const mockAction = vi.fn();

    render(
      <TestWrapper>
        <EmptyState
          message="暂无数据"
          action={{
            text: '重新加载',
            onClick: mockAction,
            loading: true,
          }}
        />
      </TestWrapper>,
    );

    const actionButton = screen.getByText('重新加载');
    expect(actionButton).toBeInTheDocument();

    // Button should be in loading state
    fireEvent.click(actionButton);
    expect(mockAction).toHaveBeenCalledTimes(1);
  });

  it('should render with different button types', () => {
    const mockAction = vi.fn();

    render(
      <TestWrapper>
        <EmptyState
          message="暂无数据"
          action={{
            text: '主要操作',
            onClick: mockAction,
            type: 'primary',
          }}
        />
      </TestWrapper>,
    );

    expect(screen.getByText('主要操作')).toBeInTheDocument();
  });

  it('should handle action button with icon', () => {
    const mockAction = vi.fn();

    render(
      <TestWrapper>
        <EmptyState
          message="暂无数据"
          action={{
            text: '添加内容',
            onClick: mockAction,
            icon: 'plus',
          }}
        />
      </TestWrapper>,
    );

    expect(screen.getByText('添加内容')).toBeInTheDocument();
  });

  it('should render multiple actions', () => {
    const mockAction1 = vi.fn();
    const mockAction2 = vi.fn();

    render(
      <TestWrapper>
        <EmptyState
          message="暂无数据"
          actions={[
            {
              text: '创建',
              onClick: mockAction1,
              type: 'primary',
            },
            {
              text: '导入',
              onClick: mockAction2,
            },
          ]}
        />
      </TestWrapper>,
    );

    const createButton = screen.getByText('创建');
    const importButton = screen.getByText('导入');

    expect(createButton).toBeInTheDocument();
    expect(importButton).toBeInTheDocument();

    fireEvent.click(createButton);
    fireEvent.click(importButton);

    expect(mockAction1).toHaveBeenCalledTimes(1);
    expect(mockAction2).toHaveBeenCalledTimes(1);
  });

  it('should apply custom className', () => {
    const { container } = render(
      <TestWrapper>
        <EmptyState message="暂无数据" className="custom-empty-state" />
      </TestWrapper>,
    );

    expect(container.querySelector('.custom-empty-state')).toBeInTheDocument();
  });

  it('should apply custom styles', () => {
    const customStyle = { marginTop: '50px' };

    const { container } = render(
      <TestWrapper>
        <EmptyState message="暂无数据" style={customStyle} />
      </TestWrapper>,
    );

    const emptyElement = container.firstChild as HTMLElement;
    expect(emptyElement).toHaveStyle('margin-top: 50px');
  });

  it('should handle different empty state scenarios', () => {
    const scenarios = [
      { message: '搜索无结果', description: '请尝试其他关键词' },
      { message: '网络错误', description: '请检查网络连接' },
      { message: '权限不足', description: '请联系管理员' },
    ];

    scenarios.forEach((scenario) => {
      const { unmount } = render(
        <TestWrapper>
          <EmptyState message={scenario.message} description={scenario.description} />
        </TestWrapper>,
      );

      expect(screen.getByText(scenario.message)).toBeInTheDocument();
      expect(screen.getByText(scenario.description)).toBeInTheDocument();

      unmount();
    });
  });
});
