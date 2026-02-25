/**
 * 角色列表页
 *
 * 设计原则：
 * - 卡片式布局（比表格更直观）
 * - 按角色类型分组（系统角色/业务角色/自定义角色）
 * - 每个卡片显示：角色名、描述、成员数、资源数、状态
 * - 系统角色带锁定标识（不可编辑）
 * - 操作按钮清晰（编辑/复制/禁用/删除）
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Button,
  Space,
  Tag,
  Badge,
  Typography,
  Row,
  Col,
  Statistic,
  Tooltip,
  Modal,
  message,
  Empty,
  Collapse,
  Input,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  StopOutlined,
  CheckOutlined,
  CopyOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  LockOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  useRolesQuery,
  useDeleteRoleMutation,
  useDisableRoleMutation,
  useEnableRoleMutation,
  useCopyRoleMutation,
} from '@services/queries/roles';
import type { RoleInfo } from '@types/api';

const { Text, Title } = Typography;

const RoleList: React.FC = () => {
  const navigate = useNavigate();

  // 查询数据
  const rolesQuery = useRolesQuery({});
  const deleteRoleMutation = useDeleteRoleMutation();
  const disableRoleMutation = useDisableRoleMutation();
  const enableRoleMutation = useEnableRoleMutation();
  const copyRoleMutation = useCopyRoleMutation();

  // 按角色类型分组
  const groupedRoles = useMemo(() => {
    const roles = rolesQuery.data || [];
    return {
      system: roles.filter((r: RoleInfo) => r.roleType === 'SYSTEM'),
      business: roles.filter((r: RoleInfo) => r.roleType === 'BUSINESS'),
      custom: roles.filter((r: RoleInfo) => r.roleType === 'CUSTOM'),
    };
  }, [rolesQuery.data]);

  // 删除角色
  const handleDelete = (role: RoleInfo) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除角色"${role.name}"吗？${
        role.isProtected
          ? '\n\n注意：系统保护角色不可删除！'
          : role.userCount
          ? `\n\n注意：该角色下有 ${role.userCount} 个关联用户，删除后用户将失去此角色权限。`
          : ''
      }`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteRoleMutation.mutateAsync(role.id);
          message.success('删除成功');
        } catch (e: any) {
          message.error(e?.message || '删除失败');
        }
      },
    });
  };

  // 禁用角色
  const handleDisable = (role: RoleInfo) => {
    Modal.confirm({
      title: '确认禁用',
      content: `确定要禁用角色"${role.name}"吗？禁用后该角色将无法使用。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await disableRoleMutation.mutateAsync(role.id);
          message.success('禁用成功');
        } catch (e: any) {
          message.error(e?.message || '禁用失败');
        }
      },
    });
  };

  // 启用角色
  const handleEnable = (role: RoleInfo) => {
    Modal.confirm({
      title: '确认启用',
      content: `确定要启用角色"${role.name}"吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await enableRoleMutation.mutateAsync(role.id);
          message.success('启用成功');
        } catch (e: any) {
          message.error(e?.message || '启用失败');
        }
      },
    });
  };

  // 复制角色
  const handleCopy = (role: RoleInfo) => {
    Modal.confirm({
      title: '复制角色',
      content: (
        <div>
          <p>确定要复制角色"{role.name}"吗？</p>
          <div style={{ marginTop: 16 }}>
            <label>新角色编码：</label>
            <Input id="copy-code" style={{ marginTop: 8 }} placeholder="英文字母开头" />
          </div>
          <div style={{ marginTop: 16 }}>
            <label>新角色名称：</label>
            <Input id="copy-name" style={{ marginTop: 8 }} placeholder="角色名称" defaultValue={`${role.name}-副本`} />
          </div>
        </div>
      ),
      okText: '确认复制',
      cancelText: '取消',
      width: 420,
      onOk: async () => {
        const newCode = (document.getElementById('copy-code') as HTMLInputElement)?.value?.trim();
        const newName = (document.getElementById('copy-name') as HTMLInputElement)?.value?.trim();
        if (!newCode || !newName) {
          message.error('请填写完整信息');
          return Promise.reject();
        }
        try {
          await copyRoleMutation.mutateAsync({ id: role.id, newCode, newName });
          message.success('复制成功');
        } catch (e: any) {
          message.error(e?.message || '复制失败');
        }
      },
    });
  };

  // 渲染角色卡片
  const renderRoleCard = (role: RoleInfo) => (
    <Col xs={24} sm={12} lg={8} xl={6} key={role.id}>
      <Card
        hoverable
        style={{ height: '100%' }}
        actions={[
          <Tooltip title="编辑" key="edit">
            <Button
              type="text"
              icon={<EditOutlined />}
              onClick={() => navigate(`/admin/auth-center/roles/${role.id}/edit`)}
              disabled={role.isProtected}
            />
          </Tooltip>,
          <Tooltip title="配置权限" key="permission">
            <Button
              type="text"
              icon={<SettingOutlined />}
              onClick={() => navigate(`/admin/auth-center/roles/${role.id}/permissions`)}
            />
          </Tooltip>,
          <Tooltip title="复制" key="copy">
            <Button
              type="text"
              icon={<CopyOutlined />}
              onClick={() => handleCopy(role)}
            />
          </Tooltip>,
          role.status === 'enabled' ? (
            <Tooltip title="禁用" key="disable">
              <Button
                type="text"
                danger
                icon={<StopOutlined />}
                onClick={() => handleDisable(role)}
                disabled={role.isProtected}
              />
            </Tooltip>
          ) : (
            <Tooltip title="启用" key="enable">
              <Button
                type="text"
                icon={<CheckOutlined />}
                onClick={() => handleEnable(role)}
              />
            </Tooltip>
          ),
        ]}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {/* 角色名称和类型 */}
          <Space>
            {role.isProtected && <LockOutlined style={{ color: '#ff4d4f' }} />}
            <Text strong style={{ fontSize: 16 }}>{role.name}</Text>
            <Tag
              color={
                role.roleType === 'SYSTEM'
                  ? 'red'
                  : role.roleType === 'BUSINESS'
                  ? 'blue'
                  : 'green'
              }
            >
              {role.roleTypeDisplayName}
            </Tag>
          </Space>

          {/* 角色编码 */}
          <Text type="secondary" style={{ fontSize: 12 }}>
            <code>{role.code}</code>
          </Text>

          {/* 角色描述 */}
          {role.description && (
            <Text type="secondary" style={{ fontSize: 12 }} ellipsis={{ tooltip: role.description }}>
              {role.description}
            </Text>
          )}

          {/* 统计信息 */}
          <Row gutter={16}>
            <Col span={12}>
              <Statistic
                title="成员数"
                value={role.userCount || 0}
                prefix={<UserOutlined />}
                valueStyle={{ fontSize: 16 }}
              />
            </Col>
            <Col span={12}>
              <Statistic
                title="资源数"
                value={role.resourceCount || 0}
                prefix={<SafetyCertificateOutlined />}
                valueStyle={{ fontSize: 16 }}
              />
            </Col>
          </Row>

          {/* 状态 */}
          <div>
            {role.status === 'enabled' ? (
              <Badge status="success" text="已启用" />
            ) : (
              <Badge status="default" text="已禁用" />
            )}
          </div>
        </Space>
      </Card>
    </Col>
  );

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
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* 系统角色 */}
        <Card
          title={
            <Space>
              <LockOutlined style={{ color: '#ff4d4f' }} />
              <Text strong>系统角色</Text>
              <Tag color="red">{groupedRoles.system.length}</Tag>
            </Space>
          }
        >
          {groupedRoles.system.length === 0 ? (
            <Empty description="暂无系统角色" />
          ) : (
            <Row gutter={[16, 16]}>
              {groupedRoles.system.map((role) => renderRoleCard(role))}
            </Row>
          )}
        </Card>

        {/* 业务角色 */}
        <Card
          title={
            <Space>
              <TeamOutlined style={{ color: '#1890ff' }} />
              <Text strong>业务角色</Text>
              <Tag color="blue">{groupedRoles.business.length}</Tag>
            </Space>
          }
        >
          {groupedRoles.business.length === 0 ? (
            <Empty description="暂无业务角色" />
          ) : (
            <Row gutter={[16, 16]}>
              {groupedRoles.business.map((role) => renderRoleCard(role))}
            </Row>
          )}
        </Card>

        {/* 自定义角色 */}
        <Collapse
          defaultActiveKey={['custom']}
          items={[
            {
              key: 'custom',
              label: (
                <Space>
                  <SettingOutlined style={{ color: '#52c41a' }} />
                  <Text strong>自定义角色</Text>
                  <Tag color="green">{groupedRoles.custom.length}</Tag>
                </Space>
              ),
              children: groupedRoles.custom.length === 0 ? (
                <Empty description="暂无自定义角色">
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => navigate('/admin/auth-center/roles/create')}
                  >
                    新建角色
                  </Button>
                </Empty>
              ) : (
                <Row gutter={[16, 16]}>
                  {groupedRoles.custom.map((role) => renderRoleCard(role))}
                </Row>
              ),
            },
          ]}
        />
      </Space>
    </PageContainer>
  );
};

export default RoleList;
