/**
 * 角色列表页
 *
 * 角色管理工作区：按角色类型筛选，集中处理角色信息、权限和成员入口。
 */

import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CheckOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  StopOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  useCopyRoleMutation,
  useDeleteRoleMutation,
  useDisableRoleMutation,
  useEnableRoleMutation,
  useRolesQuery,
} from '@services/queries/roles';
import type { RoleInfo } from '@types/api';
import './RoleList.less';

const { Text } = Typography;

type RoleListItem = RoleInfo & {
  userCount?: number;
  resourceCount?: number;
};

type RoleTypeFilter = 'ALL' | RoleInfo['roleType'];
type StatusFilter = 'all' | RoleInfo['status'];
type CopyFormValues = { newCode: string; newName: string };

const ROLE_TYPE_META: Record<
  RoleInfo['roleType'],
  { label: string; color: string; className: string }
> = {
  SYSTEM: { label: '系统角色', color: 'red', className: 'is-system' },
  BUSINESS: { label: '业务角色', color: 'blue', className: 'is-business' },
  CUSTOM: { label: '自定义角色', color: 'green', className: 'is-custom' },
};

const getRoleTypeMeta = (role: RoleListItem) => ROLE_TYPE_META[role.roleType];

const getRoleUpdatedLabel = (role: RoleListItem) => {
  const value = role.updateTime ?? role.createTime;
  if (!value) return '暂无更新时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '更新时间未知';
  return `更新于 ${date.toLocaleDateString('zh-CN')}`;
};

interface RoleCardProps {
  role: RoleListItem;
  onEdit: (role: RoleListItem) => void;
  onPermission: (role: RoleListItem) => void;
  onMembers: (role: RoleListItem) => void;
  onCopy: (role: RoleListItem) => void;
  onDelete: (role: RoleListItem) => void;
  onDisable: (role: RoleListItem) => void;
  onEnable: (role: RoleListItem) => void;
}

const RoleCard: React.FC<RoleCardProps> = ({
  role,
  onEdit,
  onPermission,
  onMembers,
  onCopy,
  onDelete,
  onDisable,
  onEnable,
}) => {
  const typeMeta = getRoleTypeMeta(role);
  const isLocked = role.isProtected || !role.isEditable;
  const userCount = role.userCount ?? 0;
  const resourceCount = role.resourceCount ?? 0;
  const menuItems = [
    {
      key: 'edit',
      icon: <EditOutlined />,
      label: '编辑角色',
      disabled: isLocked,
      onClick: () => onEdit(role),
    },
    {
      key: 'copy',
      icon: <CopyOutlined />,
      label: '复制角色',
      onClick: () => onCopy(role),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: '删除角色',
      danger: true,
      disabled: isLocked,
      onClick: () => onDelete(role),
    },
  ];

  return (
    <article
      className={`role-card ${typeMeta.className} ${role.status === 'disabled' ? 'is-disabled' : ''}`}
    >
      <div className="role-card-header">
        <div className="role-card-identity">
          <div className="role-card-icon">
            {role.isProtected ? <LockOutlined /> : <SafetyCertificateOutlined />}
          </div>
          <div className="role-card-title-group">
            <Text strong className="role-card-name">
              {role.name}
            </Text>
            <Text type="secondary" className="role-card-code">
              {role.code}
            </Text>
          </div>
        </div>
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <Button
            type="text"
            className="role-card-more"
            icon={<EllipsisOutlined />}
            aria-label={`${role.name} 更多操作`}
          />
        </Dropdown>
      </div>

      <div className="role-card-tags">
        <Tag color={typeMeta.color}>{role.roleTypeDisplayName || typeMeta.label}</Tag>
        <Tag color={role.status === 'enabled' ? 'success' : 'default'}>
          {role.statusDisplayName || (role.status === 'enabled' ? '已启用' : '已禁用')}
        </Tag>
        {role.isProtected && (
          <Tag icon={<LockOutlined />} color="default">
            系统保护
          </Tag>
        )}
      </div>

      <Text
        type="secondary"
        className="role-card-description"
        ellipsis={{ tooltip: role.description || '暂无角色描述' }}
      >
        {role.description || '暂无角色描述'}
      </Text>

      <div className="role-card-metrics">
        <div className="role-card-metric">
          <UserOutlined />
          <div>
            <Text type="secondary">成员</Text>
            <strong>{userCount}</strong>
          </div>
        </div>
        <div className="role-card-metric">
          <SafetyCertificateOutlined />
          <div>
            <Text type="secondary">资源</Text>
            <strong>{resourceCount}</strong>
          </div>
        </div>
      </div>

      <div className="role-card-meta">
        <Text type="secondary">{getRoleUpdatedLabel(role)}</Text>
      </div>

      <div className="role-card-actions">
        <Button
          type="primary"
          size="small"
          icon={<SettingOutlined />}
          onClick={() => onPermission(role)}
        >
          配置权限
        </Button>
        <Button size="small" icon={<TeamOutlined />} onClick={() => onMembers(role)}>
          成员{userCount > 0 ? ` ${userCount}` : ''}
        </Button>
        {role.status === 'enabled' ? (
          <Button
            type="text"
            danger
            size="small"
            icon={<StopOutlined />}
            onClick={() => onDisable(role)}
            disabled={isLocked}
            aria-label={`禁用${role.name}`}
          />
        ) : (
          <Button
            type="text"
            size="small"
            icon={<CheckOutlined />}
            onClick={() => onEnable(role)}
            aria-label={`启用${role.name}`}
          />
        )}
      </div>
    </article>
  );
};

interface RoleGridProps {
  roles: RoleListItem[];
  onEdit: (role: RoleListItem) => void;
  onPermission: (role: RoleListItem) => void;
  onMembers: (role: RoleListItem) => void;
  onCopy: (role: RoleListItem) => void;
  onDelete: (role: RoleListItem) => void;
  onDisable: (role: RoleListItem) => void;
  onEnable: (role: RoleListItem) => void;
  onReset: () => void;
}

const RoleGrid: React.FC<RoleGridProps> = ({ roles, onReset, ...handlers }) => {
  if (roles.length === 0) {
    return (
      <div className="role-empty-state">
        <Empty description="没有匹配的角色">
          <Button type="link" onClick={onReset}>
            清除筛选
          </Button>
        </Empty>
      </div>
    );
  }

  return (
    <div className="role-grid">
      {roles.map((role) => (
        <RoleCard key={role.id} role={role} {...handlers} />
      ))}
    </div>
  );
};

const RoleList: React.FC = () => {
  const navigate = useNavigate();
  const rolesQuery = useRolesQuery({});
  const deleteRoleMutation = useDeleteRoleMutation();
  const disableRoleMutation = useDisableRoleMutation();
  const enableRoleMutation = useEnableRoleMutation();
  const copyRoleMutation = useCopyRoleMutation();
  const [activeType, setActiveType] = useState<RoleTypeFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [keyword, setKeyword] = useState('');
  const [copyingRole, setCopyingRole] = useState<RoleListItem | null>(null);
  const [copyForm] = Form.useForm<CopyFormValues>();

  const allRoles = (rolesQuery.data ?? []) as RoleListItem[];
  const visibleRoles = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return allRoles.filter((role) => {
      const matchesType = activeType === 'ALL' || role.roleType === activeType;
      const matchesStatus = statusFilter === 'all' || role.status === statusFilter;
      const matchesKeyword =
        !normalizedKeyword ||
        [role.name, role.code, role.description]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      return matchesType && matchesStatus && matchesKeyword;
    });
  }, [activeType, allRoles, keyword, statusFilter]);

  const groupedCounts = useMemo(
    () => ({
      ALL: allRoles.length,
      SYSTEM: allRoles.filter((role) => role.roleType === 'SYSTEM').length,
      BUSINESS: allRoles.filter((role) => role.roleType === 'BUSINESS').length,
      CUSTOM: allRoles.filter((role) => role.roleType === 'CUSTOM').length,
    }),
    [allRoles],
  );

  const summaryItems = [
    {
      key: 'total',
      label: '角色总数',
      value: allRoles.length,
      suffix: '个',
      hint: '系统中的全部角色',
      icon: <SafetyCertificateOutlined />,
      className: 'is-blue',
    },
    {
      key: 'enabled',
      label: '已启用',
      value: allRoles.filter((role) => role.status === 'enabled').length,
      suffix: '个',
      hint: '当前可参与授权',
      icon: <CheckCircleOutlined />,
      className: 'is-green',
    },
    {
      key: 'custom',
      label: '自定义角色',
      value: groupedCounts.CUSTOM,
      suffix: '个',
      hint: '可按业务需要调整',
      icon: <SettingOutlined />,
      className: 'is-amber',
    },
    {
      key: 'protected',
      label: '系统保护',
      value: allRoles.filter((role) => role.isProtected).length,
      suffix: '个',
      hint: '不可直接修改的角色',
      icon: <LockOutlined />,
      className: 'is-red',
    },
  ];

  const resetFilters = () => {
    setActiveType('ALL');
    setStatusFilter('all');
    setKeyword('');
  };

  const handleDelete = (role: RoleListItem) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除角色“${role.name}”吗？${role.userCount ? `该角色下有 ${role.userCount} 个关联用户。` : ''}`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteRoleMutation.mutateAsync(role.id);
          message.success('删除成功');
        } catch (error: any) {
          message.error(error?.message || '删除失败');
        }
      },
    });
  };

  const handleDisable = (role: RoleListItem) => {
    Modal.confirm({
      title: '确认禁用',
      content: `确定要禁用角色“${role.name}”吗？禁用后该角色将无法使用。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await disableRoleMutation.mutateAsync(role.id);
          message.success('禁用成功');
        } catch (error: any) {
          message.error(error?.message || '禁用失败');
        }
      },
    });
  };

  const handleEnable = (role: RoleListItem) => {
    Modal.confirm({
      title: '确认启用',
      content: `确定要启用角色“${role.name}”吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await enableRoleMutation.mutateAsync(role.id);
          message.success('启用成功');
        } catch (error: any) {
          message.error(error?.message || '启用失败');
        }
      },
    });
  };

  const handleCopy = (role: RoleListItem) => {
    setCopyingRole(role);
    copyForm.setFieldsValue({ newCode: `${role.code}_COPY`, newName: `${role.name}-副本` });
  };

  const handleCloseCopy = () => {
    setCopyingRole(null);
    copyForm.resetFields();
  };

  const handleConfirmCopy = async () => {
    if (!copyingRole) return;
    try {
      const values = await copyForm.validateFields();
      await copyRoleMutation.mutateAsync({ id: copyingRole.id, ...values });
      message.success('复制成功');
      handleCloseCopy();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '复制失败');
    }
  };

  const roleTabs = (['ALL', 'SYSTEM', 'BUSINESS', 'CUSTOM'] as RoleTypeFilter[]).map((type) => ({
    key: type,
    label: (
      <div className="role-tab-label">
        <span>{type === 'ALL' ? '全部角色' : ROLE_TYPE_META[type].label}</span>
        <small>{groupedCounts[type]}</small>
      </div>
    ),
    children: (
      <RoleGrid
        roles={visibleRoles}
        onEdit={(role) => navigate(`/admin/auth-center/roles/${role.id}/edit`)}
        onPermission={(role) => navigate(`/admin/auth-center/roles/${role.id}/permissions`)}
        onMembers={(role) => navigate(`/admin/auth-center/roles/${role.id}/members`)}
        onCopy={handleCopy}
        onDelete={handleDelete}
        onDisable={handleDisable}
        onEnable={handleEnable}
        onReset={resetFilters}
      />
    ),
  }));

  if (rolesQuery.isError) {
    return (
      <PageContainer header={{ title: '角色管理' }}>
        <Alert
          title="角色列表加载失败"
          description={rolesQuery.error instanceof Error ? rolesQuery.error.message : '请稍后重试'}
          type="error"
          showIcon
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      header={{
        title: '角色管理',
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { title: '角色管理' },
          ],
        },
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => rolesQuery.refetch()}
          loading={rolesQuery.isLoading}
        >
          刷新
        </Button>,
        <Button
          key="create"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/admin/auth-center/roles/create')}
        >
          新建角色
        </Button>,
      ]}
    >
      <main className="role-management-page">
        <section className="role-summary-grid" aria-label="角色统计">
          {summaryItems.map((item) => (
            <div className={`role-summary-item ${item.className}`} key={item.key}>
              <div className="role-summary-icon">{item.icon}</div>
              <div className="role-summary-content">
                <Text className="role-summary-label">{item.label}</Text>
                <div className="role-summary-value">
                  <span>{item.value}</span>
                  <Text type="secondary">{item.suffix}</Text>
                </div>
                <Text type="secondary" className="role-summary-hint">
                  {item.hint}
                </Text>
              </div>
            </div>
          ))}
        </section>

        <section className="role-workspace" aria-labelledby="role-workspace-title">
          <div className="role-workspace-toolbar">
            <div className="role-workspace-heading">
              <Text className="role-eyebrow">ACCESS CONTROL</Text>
              <Typography.Title level={4} id="role-workspace-title">
                角色目录
              </Typography.Title>
              <Text type="secondary">按角色类型浏览并进入权限、成员管理。</Text>
            </div>
            <div className="role-filters">
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="搜索角色名称、编码或描述"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <Select
                value={statusFilter}
                onChange={setStatusFilter}
                options={[
                  { value: 'all', label: '全部状态' },
                  { value: 'enabled', label: '已启用' },
                  { value: 'disabled', label: '已禁用' },
                ]}
              />
            </div>
          </div>

          <Tabs
            className="role-type-tabs"
            activeKey={activeType}
            onChange={(key) => setActiveType(key as RoleTypeFilter)}
            items={roleTabs}
          />
        </section>
      </main>

      <Modal
        className="role-copy-modal"
        title="复制角色"
        open={Boolean(copyingRole)}
        onCancel={handleCloseCopy}
        onOk={handleConfirmCopy}
        okText="确认复制"
        cancelText="取消"
        confirmLoading={copyRoleMutation.isPending}
      >
        <Text type="secondary">
          将复制“{copyingRole?.name ?? ''}”的基本信息和权限配置，请填写新角色标识。
        </Text>
        <Form form={copyForm} layout="vertical" className="role-copy-form">
          <Form.Item
            name="newCode"
            label="新角色编码"
            rules={[
              { required: true, message: '请输入新角色编码' },
              {
                pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
                message: '编码必须以字母开头，只能包含字母、数字和下划线',
              },
            ]}
          >
            <Input placeholder="如：PAYROLL_REVIEWER" />
          </Form.Item>
          <Form.Item
            name="newName"
            label="新角色名称"
            rules={[{ required: true, message: '请输入新角色名称' }]}
          >
            <Input placeholder="如：薪酬审核员" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default RoleList;
