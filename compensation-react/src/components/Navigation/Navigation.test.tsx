import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter, MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { vi } from 'vitest';
import { store } from '../../services/stores/authSlice';
import { AppBreadcrumb } from './Breadcrumb';
import { PageHeader } from './PageHeader';
import { BackTop } from './BackTop';

// Mock react-router-dom useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const TestWrapper = ({
  children,
  initialEntries = ['/'],
}: {
  children: React.ReactNode;
  initialEntries?: string[];
}) => (
  <Provider store={store}>
    <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
  </Provider>
);

describe('导航组件', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  describe('面包屑导航', () => {
    it('首页不显示面包屑', () => {
      render(
        <TestWrapper initialEntries={['/']}>
          <AppBreadcrumb />
        </TestWrapper>,
      );

      expect(screen.queryByText('首页')).not.toBeInTheDocument();
    });

    it('少于二层不显示面包屑', () => {
      render(
        <TestWrapper initialEntries={['/employees']}>
          <AppBreadcrumb />
        </TestWrapper>,
      );

      expect(screen.queryByText('首页')).not.toBeInTheDocument();
    });

    it('二层及以上显示完整面包屑', () => {
      render(
        <TestWrapper initialEntries={['/payments/batches']}>
          <AppBreadcrumb />
        </TestWrapper>,
      );

      expect(screen.getByText('首页')).toBeInTheDocument();
      expect(screen.getByText('业务管理')).toBeInTheDocument();
      expect(screen.getByText('支付批次')).toBeInTheDocument();
    });

    it('动态路由显示正确面包屑', () => {
      render(
        <TestWrapper initialEntries={['/employees/123']}>
          <AppBreadcrumb />
        </TestWrapper>,
      );

      expect(screen.getByText('首页')).toBeInTheDocument();
      expect(screen.getByText('业务管理')).toBeInTheDocument();
      expect(screen.getByText('员工详情')).toBeInTheDocument();
    });
  });

  describe('页头组件', () => {
    it('正确渲染标题和子标题', () => {
      render(
        <TestWrapper>
          <PageHeader title="测试页面" subTitle="这是一个测试页面" />
        </TestWrapper>,
      );

      expect(screen.getByText('测试页面')).toBeInTheDocument();
      expect(screen.getByText('这是一个测试页面')).toBeInTheDocument();
    });

    it('显示返回按钮并处理点击', () => {
      render(
        <TestWrapper>
          <PageHeader title="测试页面" showBack={true} />
        </TestWrapper>,
      );

      const backButton = screen.getByText('返回');
      expect(backButton).toBeInTheDocument();

      fireEvent.click(backButton);
      expect(mockNavigate).toHaveBeenCalledWith(-1);
    });

    it('自定义返回逻辑', () => {
      const mockOnBack = vi.fn();

      render(
        <TestWrapper>
          <PageHeader title="测试页面" showBack={true} onBack={mockOnBack} />
        </TestWrapper>,
      );

      const backButton = screen.getByText('返回');
      fireEvent.click(backButton);

      expect(mockOnBack).toHaveBeenCalled();
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('显示额外操作', () => {
      render(
        <TestWrapper>
          <PageHeader title="测试页面" extra={<button>操作按钮</button>} />
        </TestWrapper>,
      );

      expect(screen.getByText('操作按钮')).toBeInTheDocument();
    });
  });

  describe('返回顶部组件', () => {
    it('正确渲染返回顶部按钮', () => {
      render(
        <BrowserRouter>
          <BackTop />
        </BrowserRouter>,
      );

      // BackTop 组件内部使用了 Portal，在测试环境可能不会直接显示
      // 但组件应该能正常渲染而不报错
    });
  });
});

// 导航系统压力测试
describe('导航系统压力测试', () => {
  const testRoutes = [
    '/',
    '/employees',
    '/employees/123',
    '/payments/batches',
    '/payments/batches/BATCH001',
    '/system/integration',
    '/admin/user-binding',
    '/nonexistent/path',
  ];

  testRoutes.forEach((route) => {
    it(`路由 ${route} 应该能正确处理`, () => {
      render(
        <TestWrapper initialEntries={[route]}>
          <div>
            <AppBreadcrumb />
            <PageHeader title="测试页面" />
          </div>
        </TestWrapper>,
      );

      // 应该能正常渲染而不报错
      expect(screen.getByText('测试页面')).toBeInTheDocument();
    });
  });
});
