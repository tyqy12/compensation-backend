import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { vi } from 'vitest';
import Forbidden from './Forbidden';
import NotFound from './NotFound';
import ServerError from './ServerError';

// Mock react-router-dom useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <BrowserRouter>{children}</BrowserRouter>
);

describe('异常页面', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  describe('403 Forbidden 页面', () => {
    it('应该正确渲染 403 页面内容', () => {
      render(
        <TestWrapper>
          <Forbidden />
        </TestWrapper>,
      );

      expect(screen.getByText('403 - 访问被拒绝')).toBeInTheDocument();
      expect(screen.getByText(/抱歉，您暂时没有权限访问此页面/)).toBeInTheDocument();
      expect(screen.getByText('返回首页')).toBeInTheDocument();
      expect(screen.getByText('返回上页')).toBeInTheDocument();
      expect(screen.getByText('重新登录')).toBeInTheDocument();
      expect(screen.getByText(/如果您认为这是一个错误/)).toBeInTheDocument();
    });
  });

  describe('404 NotFound 页面', () => {
    it('应该正确渲染 404 页面内容', () => {
      render(
        <TestWrapper>
          <NotFound />
        </TestWrapper>,
      );

      expect(screen.getByText('404 - 页面未找到')).toBeInTheDocument();
      expect(screen.getByText(/抱歉，您访问的页面不存在/)).toBeInTheDocument();
      expect(screen.getByText('返回首页')).toBeInTheDocument();
      expect(screen.getByText('返回上页')).toBeInTheDocument();
      expect(screen.getByText('刷新页面')).toBeInTheDocument();
      expect(screen.getByText('搜索内容')).toBeInTheDocument();
      expect(screen.getByText(/请检查网址是否正确/)).toBeInTheDocument();
    });
  });

  describe('500 ServerError 页面', () => {
    it('应该正确渲染 500 页面内容', () => {
      render(
        <TestWrapper>
          <ServerError />
        </TestWrapper>,
      );

      expect(screen.getByText('500 - 服务器错误')).toBeInTheDocument();
      expect(screen.getByText(/抱歉，服务器出现了一些问题/)).toBeInTheDocument();
      expect(screen.getByText('返回首页')).toBeInTheDocument();
      expect(screen.getByText('重新加载')).toBeInTheDocument();
      expect(screen.getByText('返回上页')).toBeInTheDocument();
      expect(screen.getByText('联系客服')).toBeInTheDocument();
      expect(screen.getByText(/您可以稍后再试/)).toBeInTheDocument();
    });
  });
});
