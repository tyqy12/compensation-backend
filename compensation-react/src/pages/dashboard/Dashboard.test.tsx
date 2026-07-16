import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { vi } from 'vitest';
import { store } from '../../services/stores/authSlice';
import Dashboard from './Dashboard';

const mockUseDashboardMetricsQuery = vi.fn();
const mockUseDashboardStatusQuery = vi.fn();
const mockUseDashboardTodosQuery = vi.fn();
const mockUseDashboardActivitiesQuery = vi.fn();

vi.mock('@services/queries/dashboard', () => ({
  useDashboardMetricsQuery: () => mockUseDashboardMetricsQuery(),
  useDashboardStatusQuery: () => mockUseDashboardStatusQuery(),
  useDashboardTodosQuery: () => mockUseDashboardTodosQuery(),
  useDashboardActivitiesQuery: () => mockUseDashboardActivitiesQuery(),
}));

// Mock localStorage
const mockLocalStorage = (() => {
  let store: { [key: string]: string } = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: mockLocalStorage,
});

// Mock matchMedia for Ant Design responsive components
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // deprecated
    removeListener: vi.fn(), // deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock window.innerWidth for responsive design
Object.defineProperty(window, 'innerWidth', {
  writable: true,
  configurable: true,
  value: 1024,
});

const mockMetrics = {
  employeeTotal: 1234,
  employeeGrowthRate: 8.2,
  monthlyPaymentAmount: 2680000,
  monthlyPaymentGrowthRate: 12.5,
  pendingBatchCount: 5,
  pendingBatchChangeRate: -2.1,
  userBindingRate: 89.6,
  userBindingGrowthRate: 1.8,
};

const mockSystemStatus = {
  overallStatus: '在线',
  components: [
    { name: '微信集成', runRate: 99.9, status: '同步中' },
    { name: '数据同步', runRate: 98.5, status: '在线' },
    { name: '支付服务', runRate: 99.7, status: '在线' },
    { name: '通知服务', runRate: 95.2, status: '警告' },
  ],
};

const mockTodos = [
  { title: '审核2024年1月薪资批次', priority: '高', due: '截止时间: 今天 18:00' },
  { title: '处理张三的用户绑定申请', priority: '中', due: '截止时间: 明天 12:00' },
  { title: '更新集成配置密钥', priority: '高', due: '截止时间: 2天后' },
  { title: '导入新员工信息', priority: '低', due: '截止时间: 本周内' },
];

const mockActivities = [
  { actor: '管理员', initial: '管', description: '创建了支付批次', timeAgo: '5分钟前' },
  { actor: '张三', initial: '张', description: '完成了用户绑定', timeAgo: '1小时前' },
  { actor: '系统', initial: '系', description: '同步了组织架构', timeAgo: '2小时前' },
  { actor: '李四', initial: '李', description: '更新了员工信息', timeAgo: '4小时前' },
];

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider store={store}>
    <BrowserRouter>{children}</BrowserRouter>
  </Provider>
);

describe('Dashboard 工作台', () => {
  beforeEach(() => {
    mockLocalStorage.clear();
    vi.clearAllMocks();

    mockUseDashboardMetricsQuery.mockReturnValue({
      data: mockMetrics,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseDashboardStatusQuery.mockReturnValue({
      data: mockSystemStatus,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseDashboardTodosQuery.mockReturnValue({
      data: mockTodos,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseDashboardActivitiesQuery.mockReturnValue({
      data: mockActivities,
      isLoading: false,
      isError: false,
      error: null,
    });
  });

  it('应该正确渲染工作台主要模块', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证欢迎信息
    expect(screen.getByText(/早上好，管理员/)).toBeInTheDocument();

    // 验证核心数据卡片
    expect(screen.getByText('员工总数')).toBeInTheDocument();
    expect(screen.getByText('本月支付')).toBeInTheDocument();
    expect(screen.getByText('待处理批次')).toBeInTheDocument();
    expect(screen.getByText('用户绑定率')).toBeInTheDocument();

    // 验证快捷入口
    expect(screen.getByText('快捷入口')).toBeInTheDocument();
    expect(screen.getByText('新建支付批次')).toBeInTheDocument();
    expect(screen.getByText('员工管理')).toBeInTheDocument();

    // 验证待办清单
    expect(screen.getByText('待办清单')).toBeInTheDocument();

    // 验证最近活动
    expect(screen.getByText('最近活动')).toBeInTheDocument();

    // 验证系统状态
    expect(screen.getByText('系统状态')).toBeInTheDocument();

    // 验证使用帮助
    expect(screen.getByText('使用帮助')).toBeInTheDocument();

    // 验证产品动态
    expect(screen.getByText('产品动态')).toBeInTheDocument();
  });

  it('新用户应该自动显示引导', async () => {
    // 模拟新用户（没有完成引导的标记）
    mockLocalStorage.getItem.mockReturnValue(null);

    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // Dashboard 延迟打开引导，直接等待弹窗正文，避免只命中页面上的触发按钮。
    await waitFor(
      () => {
        expect(screen.getByText('欢迎来到薪酬管理系统！')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
  });

  it('已完成引导的用户不应该自动显示引导', () => {
    // 模拟已完成引导的用户
    mockLocalStorage.getItem.mockReturnValue('true');

    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 引导弹窗不应该自动出现
    expect(screen.queryByText('欢迎来到薪酬管理系统！')).not.toBeInTheDocument();
  });

  it('应该能手动触发新手引导', async () => {
    mockLocalStorage.getItem.mockReturnValue('true');

    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 点击新手引导按钮
    const guideButton = screen.getByText('新手引导');
    fireEvent.click(guideButton);

    // 引导弹窗应该出现
    await waitFor(() => {
      expect(screen.getByText('欢迎来到薪酬管理系统！')).toBeInTheDocument();
    });
  });

  it('应该显示正确的统计数据', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证统计数字
    expect(screen.getByText('1,234')).toBeInTheDocument(); // 员工总数
    expect(screen.getByText(/2,680,000/)).toBeInTheDocument(); // 本月支付
    expect(screen.getByText('5')).toBeInTheDocument(); // 待处理批次
    expect(
      screen.getByText(
        (_, element) =>
          element?.classList.contains('ant-statistic-content-value') &&
          Boolean(element.textContent?.includes('89.6')),
      ),
    ).toBeInTheDocument(); // 用户绑定率

    // 验证趋势指示器
    expect(screen.getAllByText(/较上月/)).toHaveLength(4);
  });

  it('应该显示待办事项列表', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证待办事项
    expect(screen.getByText('审核2024年1月薪资批次')).toBeInTheDocument();
    expect(screen.getByText('处理张三的用户绑定申请')).toBeInTheDocument();
    expect(screen.getByText('更新集成配置密钥')).toBeInTheDocument();

    // 验证优先级标签
    expect(screen.getAllByText('高')).toHaveLength(2);
    expect(screen.getByText('中')).toBeInTheDocument();
    expect(screen.getByText('低')).toBeInTheDocument();
  });

  it('应该显示系统状态信息', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证系统服务状态
    expect(screen.getByText('微信集成')).toBeInTheDocument();
    expect(screen.getByText('数据同步')).toBeInTheDocument();
    expect(screen.getByText('支付服务')).toBeInTheDocument();
    expect(screen.getByText('通知服务')).toBeInTheDocument();

    // 验证状态标签
    expect(screen.getAllByText('在线')).toHaveLength(2);
    expect(screen.getByText('同步中')).toBeInTheDocument();
    expect(screen.getByText('警告')).toBeInTheDocument();

    // 验证运行率
    expect(screen.getByText('运行率 99.9%')).toBeInTheDocument();
    expect(screen.getByText('运行率 95.2%')).toBeInTheDocument();
  });

  it('快捷入口应该包含所有必要的功能', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证快捷入口功能
    const quickActions = [
      '新建支付批次',
      '员工管理',
      '用户绑定',
      '系统配置',
      '组织同步',
      '查看报告',
    ];

    quickActions.forEach((action) => {
      expect(screen.getByText(action)).toBeInTheDocument();
    });
  });

  it('快捷入口使用扁平操作项而不是嵌套卡片', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    expect(document.querySelectorAll('.dashboard-quick-action')).toHaveLength(6);
    expect(document.querySelector('.dashboard-quick-action .ant-card')).not.toBeInTheDocument();
  });

  it('应该显示最近活动记录', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证活动记录
    expect(screen.getByText(/创建了支付批次/)).toBeInTheDocument();
    expect(screen.getByText(/完成了用户绑定/)).toBeInTheDocument();
    expect(screen.getByText(/同步了组织架构/)).toBeInTheDocument();

    // 验证时间信息
    expect(screen.getByText('5分钟前')).toBeInTheDocument();
    expect(screen.getByText('1小时前')).toBeInTheDocument();
  });

  it('应该显示产品动态和运营内容', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证产品动态
    expect(screen.getByText('新功能上线')).toBeInTheDocument();
    expect(screen.getByText('系统优化')).toBeInTheDocument();
    expect(screen.getByText(/批量支付功能已上线/)).toBeInTheDocument();
    expect(screen.getByText(/支付处理速度提升50%/)).toBeInTheDocument();
  });
});

// 工作台模块功能测试
describe('Dashboard 模块功能', () => {
  it('应该符合 Ant Design 工作台设计规范', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证模块数量在5-9个之间（符合设计建议）
    const moduleCount = [
      screen.queryByText('快捷入口'),
      screen.queryByText('待办清单'),
      screen.queryByText('最近活动'),
      screen.queryByText('系统状态'),
      screen.queryByText('使用帮助'),
      screen.queryByText('产品动态'),
    ].filter(Boolean).length;

    expect(moduleCount).toBeGreaterThanOrEqual(5);
    expect(moduleCount).toBeLessThanOrEqual(9);
  });

  it('应该提供降低记忆负载的设计', () => {
    render(
      <TestWrapper>
        <Dashboard />
      </TestWrapper>,
    );

    // 验证提供了常用功能的快捷入口
    expect(screen.getByText('快捷入口')).toBeInTheDocument();

    // 验证提供了待办事项提醒
    expect(screen.getByText('待办清单')).toBeInTheDocument();

    // 验证提供了最近活动记录
    expect(screen.getByText('最近活动')).toBeInTheDocument();
  });
});
