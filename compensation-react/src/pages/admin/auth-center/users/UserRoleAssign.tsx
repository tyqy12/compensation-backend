/**
 * 用户角色分配页
 *
 * 设计目标：
 * - 穿梭框快速分配角色
 * - 右侧实时展示分配结果与变更差异
 * - 支持一键恢复初始分配
 */

import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Transfer,
  Button,
  Space,
  Descriptions,
  Statistic,
  Row,
  Col,
  message,
  Spin,
  Tag,
  Alert,
  Empty,
  Divider,
  Badge,
  Typography,
} from 'antd';
import type { TransferDirection } from 'antd/es/transfer';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useRolesQuery } from '@services/queries/roles';
import { useUserAggregateSearchQuery, useUserRolesQuery, useSetUserRolesMutation } from '@services/queries/adminAuth';
import type { RoleInfo } from '@types/api';

interface TransferItem {
  key: string;
  title: string;
  description: string;
  roleType: 'SYSTEM' | 'BUSINESS' | 'CUSTOM';
  status: string;
  isProtected: boolean;
  disabled?: boolean;
}

const ROLE_TYPE_COLOR: Record<string, string> = {
  SYSTEM: 'red',
  BUSINESS: 'blue',
  CUSTOM: 'green',
};

const { Text } = Typography;

const UserRoleAssign: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const userIdNum = userId ? parseInt(userId) : 0;

  // 查询数据
  const rolesQuery = useRolesQuery({});
  const userQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const userRolesQuery = useUserRolesQuery(userIdNum); // 获取用户已有的角色ID列表
  const setUserRolesMutation = useSetUserRolesMutation(userIdNum);

  // 状态
  const [targetKeys, setTargetKeys] = useState<string[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);

  // 当前用户信息
  const currentUser = useMemo(() => {
    return userQuery.data?.records?.find((u) => u.userId === userIdNum);
  }, [userQuery.data, userIdNum]);

  const originalTargetKeys = useMemo(
    () => (userRolesQuery.data || []).map(String),
    [userRolesQuery.data],
  );
  const originalTargetSet = useMemo(() => new Set(originalTargetKeys), [originalTargetKeys]);

  // 角色数据转换为 Transfer 数据源
  const transferData: TransferItem[] = useMemo(() => {
    const roles = rolesQuery.data || [];
    return roles.map((role: RoleInfo) => ({
      key: String(role.id),
      title: role.name,
      description: `${role.code} · ${role.roleTypeDisplayName}`,
      roleType: role.roleType,
      status: role.status,
      isProtected: role.isProtected,
      // 禁用角色不允许新分配，但若用户已拥有则允许移除
      disabled: role.status !== 'enabled' && !originalTargetSet.has(String(role.id)),
    }));
  }, [rolesQuery.data, originalTargetSet]);

  const roleMap = useMemo(() => {
    return new Map(transferData.map((item) => [item.key, item]));
  }, [transferData]);

  // 初始化用户当前角色（从API获取角色ID列表）
  useEffect(() => {
    if (userRolesQuery.data) {
      const roleIds = userRolesQuery.data.map(String);
      setTargetKeys(roleIds);
    }
  }, [userRolesQuery.data]);

  // Transfer 变化处理
  const handleChange = (newTargetKeys: string[], _direction: TransferDirection, _moveKeys: string[]) => {
    setTargetKeys(newTargetKeys);
  };

  const handleSelectChange = (sourceSelectedKeys: string[], targetSelectedKeys: string[]) => {
    setSelectedKeys([...sourceSelectedKeys, ...targetSelectedKeys]);
  };

  const handleReset = () => {
    setTargetKeys(originalTargetKeys);
    setSelectedKeys([]);
  };

  const selectedRoles = useMemo(() => {
    return targetKeys
      .map((key) => roleMap.get(key))
      .filter((r): r is TransferItem => !!r);
  }, [targetKeys, roleMap]);

  const addedRoleKeys = useMemo(() => {
    return targetKeys.filter((key) => !originalTargetSet.has(key));
  }, [targetKeys, originalTargetSet]);

  const removedRoleKeys = useMemo(() => {
    const current = new Set(targetKeys);
    return originalTargetKeys.filter((key) => !current.has(key));
  }, [targetKeys, originalTargetKeys]);

  // 角色统计
  const roleStats = useMemo(() => {
    const systemCount = selectedRoles.filter((r) => r.roleType === 'SYSTEM').length;
    const businessCount = selectedRoles.filter((r) => r.roleType === 'BUSINESS').length;
    const customCount = selectedRoles.filter((r) => r.roleType === 'CUSTOM').length;
    const protectedCount = selectedRoles.filter((r) => r.isProtected).length;
    const disabledCount = selectedRoles.filter((r) => r.status !== 'enabled').length;
    return {
      total: selectedRoles.length,
      systemCount,
      businessCount,
      customCount,
      protectedCount,
      disabledCount,
      addedCount: addedRoleKeys.length,
      removedCount: removedRoleKeys.length,
    };
  }, [selectedRoles, addedRoleKeys, removedRoleKeys]);

  const hasChanges = useMemo(() => {
    if (targetKeys.length !== originalTargetKeys.length) return true;
    const currentSet = new Set(targetKeys);
    return originalTargetKeys.some((key) => !currentSet.has(key));
  }, [targetKeys, originalTargetKeys]);

  // 保存角色分配
  const handleSave = async () => {
    if (!hasChanges) {
      message.info('角色未发生变化');
      return;
    }

    try {
      const roleIds = targetKeys.map(Number);
      await setUserRolesMutation.mutateAsync(roleIds);
      message.success('角色分配成功');
      navigate('/admin/auth-center/users');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  if (rolesQuery.isLoading || userQuery.isLoading || userRolesQuery.isLoading) {
    return (
      <PageContainer>
        <Card>
          <Spin tip="加载中..." />
        </Card>
      </PageContainer>
    );
  }

  if (!currentUser) {
    return (
      <PageContainer>
        <Card>
          <Alert
            message="用户不存在"
            description="未找到指定的用户信息"
            type="error"
            showIcon
          />
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer
      header={{
        title: `分配角色 - ${currentUser.realName || currentUser.username}`,
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/users', title: '用户授权' },
            { title: '分配角色' },
          ],
        },
        onBack: () => navigate('/admin/auth-center/users'),
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* 用户信息 */}
        <Card title="用户信息" size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label="用户名">{currentUser.username}</Descriptions.Item>
            <Descriptions.Item label="真实姓名">{currentUser.realName || '-'}</Descriptions.Item>
            <Descriptions.Item label="邮箱">{currentUser.email || '-'}</Descriptions.Item>
            <Descriptions.Item label="手机">{currentUser.phone || '-'}</Descriptions.Item>
            <Descriptions.Item label="员工工号">{currentUser.employeeNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="部门">{currentUser.departmentName || '-'}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            <Card
              title="角色分配"
              extra={(
                <Space>
                  <Text type="secondary">已选 {targetKeys.length} 项</Text>
                  <Button
                    size="small"
                    icon={<ReloadOutlined />}
                    onClick={handleReset}
                    disabled={!hasChanges}
                  >
                    恢复初始
                  </Button>
                </Space>
              )}
            >
              <Transfer
                dataSource={transferData}
                titles={['可选角色', '当前角色']}
                targetKeys={targetKeys}
                selectedKeys={selectedKeys}
                onChange={handleChange}
                onSelectChange={handleSelectChange}
                render={(item) => (
                  <Space size={6} wrap>
                    <Text strong>{item.title}</Text>
                    <Tag color={ROLE_TYPE_COLOR[item.roleType] || 'default'}>
                      {item.roleType}
                    </Tag>
                    {item.status !== 'enabled' && <Tag color="default">已禁用</Tag>}
                    {item.isProtected && <Tag color="red">保护角色</Tag>}
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {item.description}
                    </Text>
                  </Space>
                )}
                listStyle={{
                  width: 340,
                  height: 460,
                }}
                showSearch
                filterOption={(inputValue, item) =>
                  item.title.toLowerCase().includes(inputValue.toLowerCase())
                  || item.description.toLowerCase().includes(inputValue.toLowerCase())
                }
              />
              <Alert
                message="分配规则"
                description="禁用角色不允许新分配，但如果用户当前已拥有该角色，仍允许移除；保存为覆盖式更新。"
                type="info"
                showIcon
                style={{ marginTop: 12 }}
              />
            </Card>
          </Col>

          <Col xs={24} lg={8}>
            <Card
              title="分配预览"
              extra={<Badge count={selectedRoles.length} style={{ backgroundColor: '#1890ff' }} />}
            >
              {selectedRoles.length > 0 ? (
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Space wrap>
                    {selectedRoles.map((role) => (
                      <Tag key={role.key} color={ROLE_TYPE_COLOR[role.roleType] || 'default'}>
                        {role.title}
                      </Tag>
                    ))}
                  </Space>
                  <Divider style={{ margin: '8px 0' }} />
                  <Row gutter={[8, 8]}>
                    <Col span={12}>
                      <Statistic title="系统角色" value={roleStats.systemCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="业务角色" value={roleStats.businessCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="自定义角色" value={roleStats.customCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="保护角色" value={roleStats.protectedCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="新增" value={roleStats.addedCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="移除" value={roleStats.removedCount} />
                    </Col>
                  </Row>
                  {roleStats.disabledCount > 0 && (
                    <Alert
                      type="warning"
                      showIcon
                      message={`当前角色中包含 ${roleStats.disabledCount} 个已禁用角色`}
                    />
                  )}
                </Space>
              ) : (
                <Empty description="未分配任何角色" />
              )}
            </Card>
          </Col>
        </Row>

        <Card>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/admin/auth-center/users')}
            >
              取消
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={setUserRolesMutation.isPending}
              disabled={!hasChanges}
            >
              保存并应用
            </Button>
          </Space>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default UserRoleAssign;
