/**
 * 用户角色分配页
 *
 * 设计原则：
 * - 使用 Transfer 穿梭框组件（Ant Design 标准组件）
 * - 左侧显示"当前角色"，右侧显示"可选角色"
 * - 清晰的左右对比，操作直观
 * - 底部显示权限预览（分配后的权限统计）
 *
 * 遵循 Ant Design 设计规范
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
} from 'antd';
import type { TransferDirection } from 'antd/es/transfer';
import {
  ArrowLeftOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { useRolesQuery } from '@services/queries/roles';
import { useUserAggregateSearchQuery, useUserRolesQuery, useSetUserRolesMutation } from '@services/queries/adminAuth';
import type { RoleInfo } from '@types/api';

interface TransferItem {
  key: string;
  title: string;
  description: string;
  roleType: string;
  disabled?: boolean;
}

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

  // 角色数据转换为 Transfer 数据源
  const transferData: TransferItem[] = useMemo(() => {
    const roles = rolesQuery.data || [];
    return roles.map((role: RoleInfo) => ({
      key: String(role.id),
      title: role.name,
      description: `${role.code} - ${role.roleTypeDisplayName}`,
      roleType: role.roleType,
      disabled: role.status !== 'enabled', // 禁用的角色不可选
    }));
  }, [rolesQuery.data]);

  // 初始化用户当前角色（从API获取角色ID列表）
  useEffect(() => {
    if (userRolesQuery.data) {
      const roleIds = userRolesQuery.data.map(String);
      setTargetKeys(roleIds);
    }
  }, [userRolesQuery.data]);

  // Transfer 变化处理
  const handleChange = (newTargetKeys: string[], direction: TransferDirection, moveKeys: string[]) => {
    setTargetKeys(newTargetKeys);
  };

  const handleSelectChange = (sourceSelectedKeys: string[], targetSelectedKeys: string[]) => {
    setSelectedKeys([...sourceSelectedKeys, ...targetSelectedKeys]);
  };

  // 保存角色分配
  const handleSave = async () => {
    try {
      const roleIds = targetKeys.map(Number);
      await setUserRolesMutation.mutateAsync(roleIds);
      message.success('角色分配成功');
      navigate('/admin/auth-center/users');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  // 权限统计
  const permissionStats = useMemo(() => {
    // TODO: 根据选中的角色计算权限统计
    // 这里需要查询每个角色的资源数量
    return {
      menuCount: 0,
      apiCount: 0,
      actionCount: 0,
    };
  }, [targetKeys]);

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

        {/* 角色分配 */}
        <Card title="角色分配">
          <Transfer
            dataSource={transferData}
            titles={['可选角色', '当前角色']}
            targetKeys={targetKeys}
            selectedKeys={selectedKeys}
            onChange={handleChange}
            onSelectChange={handleSelectChange}
            render={(item) => (
              <div>
                <div style={{ fontWeight: 500 }}>{item.title}</div>
                <div style={{ fontSize: 12, color: '#999' }}>
                  {item.description}
                  {item.disabled && <Tag color="red" style={{ marginLeft: 8 }}>已禁用</Tag>}
                </div>
              </div>
            )}
            listStyle={{
              width: 400,
              height: 500,
            }}
            showSearch
            filterOption={(inputValue, item) =>
              item.title.toLowerCase().indexOf(inputValue.toLowerCase()) !== -1 ||
              item.description.toLowerCase().indexOf(inputValue.toLowerCase()) !== -1
            }
          />
        </Card>

        {/* 权限预览 */}
        <Card title="权限预览" size="small">
          <Alert
            message="分配后该用户将拥有以下权限"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
          <Row gutter={16}>
            <Col span={8}>
              <Statistic
                title="菜单资源"
                value={permissionStats.menuCount}
                suffix="项"
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="API资源"
                value={permissionStats.apiCount}
                suffix="项"
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="操作权限"
                value={permissionStats.actionCount}
                suffix="项"
              />
            </Col>
          </Row>
        </Card>

        {/* 操作按钮 */}
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
