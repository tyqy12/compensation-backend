/**
 * 用户列表页 - 授权中心
 *
 * 设计原则：
 * - 简洁的表格布局
 * - 强大的搜索和筛选功能
 * - 快速操作入口（分配角色、配置权限、查看权限）
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Table, Input, Button, Space, Dropdown, Typography, Form, Tag, Badge } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  ExportOutlined,
  DownOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { useUserAggregateSearchQuery } from '@services/queries/adminAuth';
import { useRolesQuery } from '@services/queries/roles';
import type { AdminUserAggregateItem } from '@services/adminAuth';

const { Text } = Typography;

type UserRecord = AdminUserAggregateItem;

const PLATFORM_NAME_MAP: Record<string, string> = {
  wechat: '企业微信',
  dingtalk: '钉钉',
  feishu: '飞书',
};

const normalizeProvider = (value?: string | null) => {
  if (!value) return null;
  const normalized = String(value).trim().toLowerCase();
  if (!normalized) return null;
  if (normalized === 'wecom' || normalized === 'qywx' || normalized === 'wx') return 'wechat';
  if (normalized === 'dingding' || normalized === 'dd') return 'dingtalk';
  if (normalized === 'lark') return 'feishu';
  return normalized;
};

const UserList: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();

  // 搜索状态
  const [searchParams, setSearchParams] = useState({
    q: '',
    page: 1,
    size: 20,
  });

  // 查询用户数据
  const usersQuery = useUserAggregateSearchQuery(searchParams);
  const users = usersQuery.data?.records || [];
  const total = usersQuery.data?.total || 0;

  // 查询角色列表（用于角色名称映射）
  const rolesQuery = useRolesQuery({});

  // 动态创建角色代码到名称的映射表
  const roleNameMap = useMemo(() => {
    const roles = rolesQuery.data || [];
    const map: Record<string, string> = {};
    roles.forEach((role) => {
      map[role.code] = role.name;
    });
    return map;
  }, [rolesQuery.data]);

  // 获取角色显示名称
  const getRoleDisplayName = (roleCode: string): string => {
    return roleNameMap[roleCode.trim()] || roleCode;
  };

  const getRecordProvider = (record: UserRecord) => normalizeProvider(record.provider);

  const getRecordSubjectId = (record: UserRecord) => record.subjectId ?? null;

  // 搜索处理
  const handleSearch = (values: any) => {
    setSearchParams((prev) => ({
      ...prev,
      q: values.searchText || '',
      page: 1,
    }));
  };

  // 重置搜索
  const handleReset = () => {
    form.resetFields();
    setSearchParams({
      q: '',
      page: 1,
      size: 20,
    });
  };

  // 分页变化
  const handleTableChange = (pagination: any) => {
    setSearchParams((prev) => ({
      ...prev,
      page: pagination.current,
      size: pagination.pageSize,
    }));
  };

  // 操作菜单
  const getActionMenu = (record: UserRecord) => ({
    items: [
      {
        key: 'assign-roles',
        icon: <TeamOutlined />,
        label: '分配角色',
        onClick: () => navigate(`/admin/auth-center/users/${record.userId}/roles`),
      },
      {
        key: 'config-permissions',
        icon: <SafetyCertificateOutlined />,
        label: '配置权限',
        onClick: () => navigate(`/admin/auth-center/users/${record.userId}/permissions`),
      },
      {
        key: 'view-permissions',
        icon: <EyeOutlined />,
        label: '查看权限',
        onClick: () => navigate(`/admin/auth-center/users/${record.userId}/view`),
      },
    ],
  });

  // 表格列定义
  const columns: ColumnsType<UserRecord> = [
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      width: 180,
      render: (text, record) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{text}</Text>
          {record.realName && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.realName}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '员工信息',
      key: 'employee',
      width: 200,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          {record.employeeName && <Text style={{ fontSize: 12 }}>{record.employeeName}</Text>}
          {record.employeeNo && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              工号: {record.employeeNo}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '角色',
      dataIndex: 'roles',
      key: 'roles',
      width: 200,
      render: (roles: string) => {
        if (!roles) return <Text type="secondary">-</Text>;
        const roleList = roles.split(',').filter(Boolean);
        return (
          <Space size={[0, 4]} wrap>
            {roleList.map((role, index) => (
              <Tag key={index} color="blue" style={{ fontSize: 11 }}>
                {getRoleDisplayName(role)}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '平台绑定',
      dataIndex: 'provider',
      key: 'provider',
      width: 180,
      render: (_, record) => {
        const provider = getRecordProvider(record);
        if (!provider) {
          return <Badge status="default" text="未绑定" />;
        }
        const subjectId = getRecordSubjectId(record);
        return (
          <Space orientation="vertical" size={0}>
            <Badge status="success" text={PLATFORM_NAME_MAP[provider] ?? provider} />
            {subjectId ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {subjectId}
              </Text>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Dropdown menu={getActionMenu(record)} trigger={['click']}>
          <Button type="link" size="small">
            操作 <DownOutlined />
          </Button>
        </Dropdown>
      ),
    },
  ];

  return (
    <PageContainer
      header={{
        title: '用户授权',
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { title: '用户授权' },
          ],
        },
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => usersQuery.refetch()}
          loading={usersQuery.isLoading}
        >
          刷新
        </Button>,
        <Button key="export" icon={<ExportOutlined />}>
          导出
        </Button>,
      ]}
    >
      <Card>
        {/* 搜索区域 */}
        <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16 }}>
          <Form.Item name="searchText" style={{ width: 400 }}>
            <Input
              placeholder="搜索用户、员工姓名、工号..."
              prefix={<SearchOutlined />}
              allowClear
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={usersQuery.isLoading}>
                搜索
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        {/* 表格 */}
        <Table
          columns={columns}
          dataSource={users}
          rowKey="userId"
          loading={usersQuery.isLoading}
          pagination={{
            current: searchParams.page,
            pageSize: searchParams.size,
            total: total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          onChange={handleTableChange}
          scroll={{ x: 1000 }}
        />
      </Card>
    </PageContainer>
  );
};

export default UserList;
