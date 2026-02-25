/**
 * 角色成员管理页
 *
 * 设计原则：
 * - 展示角色的所有成员用户
 * - 支持从角色中移除用户
 * - 支持添加用户到角色
 * - 清晰的统计信息展示
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Badge,
  Modal,
  message,
  Spin,
  Alert,
  Descriptions,
  Transfer,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  TeamOutlined,
  UserOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useRolesQuery } from '@services/queries/roles';
import { useUserAggregateSearchQuery } from '@services/queries/adminAuth';
import type { RoleInfo } from '@types/api';

const { Text } = Typography;

interface UserRecord {
  userId: number;
  username: string;
  realName?: string;
  email?: string;
  phone?: string;
  employeeName?: string;
  employeeNo?: string;
  departmentName?: string;
  platformBinding?: string;
}

const RoleMembers: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const roleIdNum = roleId ? parseInt(roleId) : 0;

  // 状态
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);

  // 查询数据
  const rolesQuery = useRolesQuery({});
  const usersQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 1000 });

  // 当前角色信息
  const currentRole: RoleInfo | undefined = useMemo(() => {
    return rolesQuery.data?.find((r: RoleInfo) => r.id === roleIdNum);
  }, [rolesQuery.data, roleIdNum]);

  // 角色成员（模拟数据，实际应从API获取）
  const roleMembers: UserRecord[] = useMemo(() => {
    // TODO: 调用API获取角色成员列表
    // 这里使用模拟数据
    return usersQuery.data?.list
      ?.filter((u: any) => u.roles?.includes(currentRole?.name))
      .map((u: any) => ({
        userId: u.userId,
        username: u.username,
        realName: u.realName,
        email: u.email,
        phone: u.phone,
        employeeName: u.employeeName,
        employeeNo: u.employeeNo,
        departmentName: u.departmentName,
        platformBinding: u.platformBinding,
      })) || [];
  }, [usersQuery.data, currentRole?.name]);

  // 可选用户（不在当前角色中的用户）
  const availableUsers = useMemo(() => {
    const memberIds = new Set(roleMembers.map((m) => m.userId));
    return usersQuery.data?.list
      ?.filter((u: any) => !memberIds.has(u.userId))
      .map((u: any) => ({
        key: String(u.userId),
        title: `${u.username}${u.realName ? ` (${u.realName})` : ''}`,
        description: `${u.employeeName || '-'} | ${u.departmentName || '-'}`,
        disabled: false,
      })) || [];
  }, [usersQuery.data, roleMembers]);

  // 表格列定义
  const columns: ColumnsType<UserRecord> = [
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      render: (text, record) => (
        <Space direction="vertical" size={0}>
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
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          {record.employeeName && (
            <Text style={{ fontSize: 12 }}>{record.employeeName}</Text>
          )}
          {record.departmentName && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.departmentName}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '联系方式',
      key: 'contact',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          {record.email && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.email}
            </Text>
          )}
          {record.phone && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.phone}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '平台绑定',
      dataIndex: 'platformBinding',
      key: 'platformBinding',
      width: 120,
      render: (platform) => {
        if (!platform || platform === '未绑定') {
          return <Badge status="default" text="未绑定" />;
        }
        return <Badge status="success" text={platform} />;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button
          type="text"
          danger
          icon={<MinusCircleOutlined />}
          onClick={() => handleRemoveMember(record)}
        >
          移除
        </Button>
      ),
    },
  ];

  // 移除成员
  const handleRemoveMember = (user: UserRecord) => {
    Modal.confirm({
      title: '确认移除',
      content: `确定要将用户 "${user.username}" 从角色 "${currentRole?.name}" 中移除吗？`,
      okText: '确认移除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          // TODO: 调用API移除用户
          // await removeUserFromRole(roleIdNum, user.userId);
          message.success('移除成功');
          usersQuery.refetch();
        } catch (error: any) {
          message.error(error?.message || '移除失败');
        }
      },
    });
  };

  // 添加成员
  const handleAddMembers = async () => {
    try {
      // TODO: 调用API添加用户到角色
      // await addUsersToRole(roleIdNum, selectedUserIds.map(Number));
      message.success(`成功添加 ${selectedUserIds.length} 个用户`);
      setAddModalVisible(false);
      setSelectedUserIds([]);
      usersQuery.refetch();
    } catch (error: any) {
      message.error(error?.message || '添加失败');
    }
  };

  if (rolesQuery.isLoading || usersQuery.isLoading) {
    return (
      <PageContainer>
        <Card>
          <Spin tip="加载中..." />
        </Card>
      </PageContainer>
    );
  }

  if (!currentRole) {
    return (
      <PageContainer>
        <Card>
          <Alert
            message="角色不存在"
            description="未找到指定的角色信息"
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
        title: `角色成员 - ${currentRole.name}`,
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
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* 角色信息 */}
        <Card size="small">
          <Descriptions column={3} size="small">
            <Descriptions.Item label="角色编码">{currentRole.code}</Descriptions.Item>
            <Descriptions.Item label="角色类型">
              <Tag
                color={
                  currentRole.roleType === 'SYSTEM'
                    ? 'red'
                    : currentRole.roleType === 'BUSINESS'
                    ? 'blue'
                    : 'green'
                }
              >
                {currentRole.roleTypeDisplayName}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {currentRole.status === 'enabled' ? (
                <Badge status="success" text="已启用" />
              ) : (
                <Badge status="default" text="已禁用" />
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        {/* 成员列表 */}
        <Card
          title={
            <Space>
              <TeamOutlined />
              <Text strong>成员列表</Text>
              <Tag color="blue">{roleMembers.length} 人</Tag>
            </Space>
          }
          extra={
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setAddModalVisible(true)}
            >
              添加成员
            </Button>
          }
        >
          <Table
            columns={columns}
            dataSource={roleMembers}
            rowKey="userId"
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 人`,
            }}
            locale={{
              emptyText: '暂无成员',
            }}
          />
        </Card>

        {/* 操作按钮 */}
        <Card>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/auth-center/roles')}>
              返回
            </Button>
          </Space>
        </Card>
      </Space>

      {/* 添加成员弹窗 */}
      <Modal
        title="添加成员"
        open={addModalVisible}
        onOk={handleAddMembers}
        onCancel={() => {
          setAddModalVisible(false);
          setSelectedUserIds([]);
        }}
        width={700}
        okText="确认添加"
        cancelText="取消"
        okButtonProps={{ disabled: selectedUserIds.length === 0 }}
      >
        <Alert
          message="选择要添加到该角色的用户"
          description="左侧显示可选用户，右侧显示已选用户"
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Transfer
          dataSource={availableUsers}
          titles={['可选用户', '已选用户']}
          targetKeys={selectedUserIds}
          onChange={setSelectedUserIds}
          render={(item) => (
            <div>
              <div style={{ fontWeight: 500 }}>{item.title}</div>
              <div style={{ fontSize: 12, color: '#999' }}>{item.description}</div>
            </div>
          )}
          listStyle={{
            width: 300,
            height: 400,
          }}
          showSearch
          filterOption={(inputValue, item) =>
            item.title.toLowerCase().indexOf(inputValue.toLowerCase()) !== -1 ||
            item.description.toLowerCase().indexOf(inputValue.toLowerCase()) !== -1
          }
        />
      </Modal>
    </PageContainer>
  );
};

export default RoleMembers;
