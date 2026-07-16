import React, { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Empty,
  Input,
  Modal,
  Spin,
  Table,
  Tag,
  Transfer,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CloseCircleOutlined, PlusOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { useRolesQuery } from '@services/queries/roles';
import { getUserRoles, setUserRoles, type AdminUserAggregateItem } from '@services/adminAuth';
import { useUserAggregateSearchQuery } from '@services/queries/adminAuth';
import type { RoleInfo } from '@types/api';
import './RolePages.less';

const { Text } = Typography;

const ROLE_TYPE_META: Record<RoleInfo['roleType'], { label: string; color: string }> = {
  SYSTEM: { label: '系统角色', color: 'red' },
  BUSINESS: { label: '业务角色', color: 'blue' },
  CUSTOM: { label: '自定义角色', color: 'green' },
};

const getRoleCodes = (roles?: string) =>
  new Set(
    (roles ?? '')
      .split(',')
      .map((role) => role.trim())
      .filter(Boolean),
  );

const getUserSearchText = (user: AdminUserAggregateItem) =>
  [
    user.username,
    user.realName,
    user.employeeName,
    user.employeeNo,
    user.email,
    user.phone,
    user.provider,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

const RoleMembers: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const roleIdNum = roleId ? Number(roleId) : 0;
  const [memberKeyword, setMemberKeyword] = useState('');
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const [memberActionLoading, setMemberActionLoading] = useState<string | null>(null);

  const rolesQuery = useRolesQuery({});
  const usersQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const currentRole: RoleInfo | undefined = useMemo(
    () => rolesQuery.data?.find((role) => role.id === roleIdNum),
    [roleIdNum, rolesQuery.data],
  );
  const allUsers = useMemo(
    () => usersQuery.data?.records ?? usersQuery.data?.list ?? [],
    [usersQuery.data],
  );

  const roleMembers = useMemo(() => {
    if (!currentRole) return [];
    return allUsers.filter((user) => getRoleCodes(user.roles).has(currentRole.code));
  }, [allUsers, currentRole]);

  const visibleMembers = useMemo(() => {
    const keyword = memberKeyword.trim().toLowerCase();
    if (!keyword) return roleMembers;
    return roleMembers.filter((user) => getUserSearchText(user).includes(keyword));
  }, [memberKeyword, roleMembers]);

  const availableUsers = useMemo(() => {
    const memberIds = new Set(roleMembers.map((member) => member.userId));
    return allUsers
      .filter((user) => !memberIds.has(user.userId))
      .map((user) => ({
        key: String(user.userId),
        title: user.realName ? `${user.realName} · ${user.username}` : user.username,
        description:
          [user.employeeName, user.employeeNo].filter(Boolean).join(' · ') || '暂无员工信息',
      }));
  }, [allUsers, roleMembers]);

  const refreshMembers = async () => {
    await Promise.all([usersQuery.refetch(), rolesQuery.refetch()]);
  };

  const updateRoleMembership = async (userId: number, shouldInclude: boolean) => {
    const roleIds = await getUserRoles(userId);
    const nextRoleIds = new Set(roleIds.map(Number));
    if (shouldInclude) {
      nextRoleIds.add(roleIdNum);
    } else {
      nextRoleIds.delete(roleIdNum);
    }
    await setUserRoles(userId, Array.from(nextRoleIds));
  };

  const handleRemoveMember = (user: AdminUserAggregateItem) => {
    Modal.confirm({
      title: '移除角色成员',
      content: `确定将“${user.realName || user.username}”从“${currentRole?.name}”中移除吗？`,
      okText: '确认移除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setMemberActionLoading(String(user.userId));
        try {
          await updateRoleMembership(user.userId, false);
          await refreshMembers();
          message.success('成员已移除');
        } catch (error: any) {
          message.error(error?.message || '移除失败');
          throw error;
        } finally {
          setMemberActionLoading(null);
        }
      },
    });
  };

  const handleAddMembers = async () => {
    if (selectedUserIds.length === 0) return;
    setMemberActionLoading('add');
    try {
      await Promise.all(
        selectedUserIds.map((userId) => updateRoleMembership(Number(userId), true)),
      );
      await refreshMembers();
      message.success(`已添加 ${selectedUserIds.length} 名成员`);
      setAddModalVisible(false);
      setSelectedUserIds([]);
    } catch (error: any) {
      message.error(error?.message || '添加失败');
    } finally {
      setMemberActionLoading(null);
    }
  };

  const columns: ColumnsType<AdminUserAggregateItem> = [
    {
      title: '成员',
      dataIndex: 'username',
      key: 'member',
      width: 230,
      render: (username, user) => (
        <div className="role-user-cell">
          <Avatar size={34} icon={<UserOutlined />} />
          <div className="role-user-cell-copy">
            <Text strong>{user.realName || username}</Text>
            <Text type="secondary">{username}</Text>
          </div>
        </div>
      ),
    },
    {
      title: '员工信息',
      key: 'employee',
      render: (_, user) => (
        <div className="role-table-stack">
          <Text>{user.employeeName || '未关联员工'}</Text>
          <Text type="secondary">{user.employeeNo || '暂无工号信息'}</Text>
        </div>
      ),
    },
    {
      title: '联系方式',
      key: 'contact',
      render: (_, user) => (
        <div className="role-table-stack">
          <Text>{user.email || '暂无邮箱'}</Text>
          <Text type="secondary">{user.phone || '暂无手机号'}</Text>
        </div>
      ),
    },
    {
      title: '登录来源',
      dataIndex: 'provider',
      key: 'provider',
      width: 120,
      render: (provider) =>
        provider ? <Tag color="blue">{provider}</Tag> : <Badge status="default" text="本地账号" />,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, user) => (
        <Button
          type="text"
          danger
          icon={<CloseCircleOutlined />}
          loading={memberActionLoading === String(user.userId)}
          onClick={() => handleRemoveMember(user)}
        >
          移除
        </Button>
      ),
    },
  ];

  const pageHeader = {
    title: '角色成员',
    breadcrumb: {
      items: [
        { path: '/', title: '首页' },
        { path: '/admin', title: '管理端' },
        { path: '/admin/auth-center', title: '授权中心' },
        { path: '/admin/auth-center/roles', title: '角色管理' },
        { title: '角色成员' },
      ],
    },
    onBack: () => navigate('/admin/auth-center/roles'),
  };

  if (rolesQuery.isLoading || usersQuery.isLoading) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Spin size="large" description="加载成员信息..." />
        </main>
      </PageContainer>
    );
  }

  if (rolesQuery.isError || usersQuery.isError) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert title="成员信息加载失败" description="请刷新页面后重试" type="error" showIcon />
        </main>
      </PageContainer>
    );
  }

  if (!currentRole) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert title="角色不存在" description="未找到指定的角色信息" type="error" showIcon />
        </main>
      </PageContainer>
    );
  }

  const roleMeta = ROLE_TYPE_META[currentRole.roleType];

  return (
    <PageContainer header={pageHeader}>
      <main className="role-members-page">
        <section className="role-context-panel">
          <div className="role-context-identity">
            <div className="role-context-icon is-members">
              <TeamOutlined />
            </div>
            <div className="role-context-copy">
              <Text className="role-page-eyebrow">ROLE MEMBERS</Text>
              <Typography.Title level={3}>{currentRole.name}</Typography.Title>
              <div className="role-context-meta">
                <Text code>{currentRole.code}</Text>
                <Tag color={roleMeta.color}>{roleMeta.label}</Tag>
                <Badge
                  status={currentRole.status === 'enabled' ? 'success' : 'default'}
                  text={currentRole.status === 'enabled' ? '已启用' : '已禁用'}
                />
              </div>
            </div>
          </div>
          <Text type="secondary" className="role-context-description">
            {currentRole.description || '管理可以使用该角色权限的用户。'}
          </Text>
        </section>

        <section className="role-stat-strip" aria-label="成员统计">
          <div className="role-stat-item is-blue">
            <Text className="role-stat-label">当前成员</Text>
            <div className="role-stat-value">
              {roleMembers.length}
              <Text> 人</Text>
            </div>
            <Text type="secondary">已匹配角色编码</Text>
          </div>
          <div className="role-stat-item is-green">
            <Text className="role-stat-label">可添加用户</Text>
            <div className="role-stat-value">
              {availableUsers.length}
              <Text> 人</Text>
            </div>
            <Text type="secondary">当前用户目录</Text>
          </div>
          <div className="role-stat-item is-amber">
            <Text className="role-stat-label">当前筛选</Text>
            <div className="role-stat-value">
              {visibleMembers.length}
              <Text> 人</Text>
            </div>
            <Text type="secondary">搜索结果</Text>
          </div>
        </section>

        <section className="role-members-workspace">
          <div className="role-workspace-header">
            <div>
              <Text className="role-section-kicker">DIRECTORY</Text>
              <Typography.Title level={4}>成员目录</Typography.Title>
              <Text type="secondary">查看成员账号、员工信息和登录来源。</Text>
            </div>
            <div className="role-workspace-actions">
              <Input
                allowClear
                value={memberKeyword}
                prefix={<UserOutlined />}
                placeholder="搜索姓名、账号、工号或部门"
                onChange={(event) => setMemberKeyword(event.target.value)}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setAddModalVisible(true)}
              >
                添加成员
              </Button>
            </div>
          </div>

          <Table
            className="role-members-table"
            columns={columns}
            dataSource={visibleMembers}
            rowKey="userId"
            loading={usersQuery.isFetching}
            scroll={{ x: 780 }}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 人`,
            }}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={memberKeyword ? '没有匹配的成员' : '暂无成员'}
                />
              ),
            }}
          />
        </section>
      </main>

      <Modal
        className="role-members-modal"
        title={
          <div className="role-modal-title">
            <TeamOutlined />
            <span>添加成员到 {currentRole.name}</span>
          </div>
        }
        open={addModalVisible}
        onOk={handleAddMembers}
        onCancel={() => {
          setAddModalVisible(false);
          setSelectedUserIds([]);
        }}
        width={760}
        centered
        okText="确认添加"
        cancelText="取消"
        okButtonProps={{
          disabled: selectedUserIds.length === 0,
          loading: memberActionLoading === 'add',
        }}
      >
        <div className="role-modal-intro">
          <Text type="secondary">选择用户后，系统会保留其现有角色，并追加当前角色。</Text>
          <Text type="secondary">可使用两侧搜索快速定位账号。</Text>
        </div>
        <Transfer
          className="role-members-transfer"
          dataSource={availableUsers}
          titles={['可选用户', '待添加']}
          targetKeys={selectedUserIds}
          onChange={(keys) => setSelectedUserIds(keys.map(String))}
          render={(item) => (
            <div className="role-transfer-item">
              <Text strong>{item.title}</Text>
              <Text type="secondary">{item.description}</Text>
            </div>
          )}
          showSearch
          filterOption={(inputValue, item) =>
            item.title.toLowerCase().includes(inputValue.toLowerCase()) ||
            item.description.toLowerCase().includes(inputValue.toLowerCase())
          }
        />
      </Modal>
    </PageContainer>
  );
};

export default RoleMembers;
