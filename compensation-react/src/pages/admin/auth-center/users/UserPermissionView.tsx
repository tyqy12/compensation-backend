/**
 * 用户权限查看页
 *
 * 设计原则：
 * - 只读展示页面，用于查看用户的完整权限
 * - 展示用户的基本信息、角色、个性化权限
 * - 使用树形结构展示权限，直观清晰
 * - 支持导出权限报告
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Descriptions,
  Tag,
  Space,
  Tree,
  Button,
  Spin,
  Alert,
  Typography,
  Row,
  Col,
  Statistic,
  Badge,
  Divider,
  Empty,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  ExportOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import { useUserAggregateSearchQuery, useUserAggregateResourcesQuery } from '@services/queries/adminAuth';
import { useRolesQuery } from '@services/queries/roles';
import type { SysResource } from '@types/api';

const { Text, Title } = Typography;

// 操作权限中文映射
const ACTION_LABELS: Record<string, string> = {
  read: '查看',
  write: '编辑',
  delete: '删除',
  admin: '管理',
  export: '导出',
  import: '导入',
};

const UserPermissionView: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const userIdNum = userId ? parseInt(userId) : 0;

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const userQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const userResourcesQuery = useUserAggregateResourcesQuery(userIdNum);
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

  // 当前用户信息
  const currentUser = useMemo(() => {
    return userQuery.data?.records?.find((u) => u.userId === userIdNum);
  }, [userQuery.data, userIdNum]);

  // 资源映射
  const resourceMap = useMemo(() => {
    const list = resourcesQuery.data || [];
    return new Map(list.map((r) => [r.id, r]));
  }, [resourcesQuery.data]);

  // 用户权限资源ID列表（使用聚合权限接口）
  const userResourceIds = useMemo(() => {
    const resources = userResourcesQuery.data || [];
    return resources.map((r) => r.resourceId).filter((id): id is number => id != null);
  }, [userResourcesQuery.data]);

  // 用户权限操作配置（使用聚合权限接口，直接有 actions 字段）
  const userActionConfigs = useMemo(() => {
    const resources = userResourcesQuery.data || [];
    const configs: Record<number, string[]> = {};
    resources.forEach((r) => {
      if (r.actions && r.actions.length > 0) {
        configs[r.resourceId] = r.actions;
      } else if (r.actionsJson) {
        // 兼容旧格式
        try {
          const parsedActions = JSON.parse(r.actionsJson);
          configs[r.resourceId] = Array.isArray(parsedActions)
            ? parsedActions
            : parsedActions.actions || [];
        } catch {
          configs[r.resourceId] = [];
        }
      }
    });
    return configs;
  }, [userResourcesQuery.data]);

  // 构建权限树（只显示用户有权限的资源）
  const permissionTreeData: DataNode[] = useMemo(() => {
    const list = resourcesQuery.data || [];
    const map = new Map<number, DataNode>();

    // 只处理用户有权限的资源
    list
      .filter((r: SysResource) => userResourceIds.includes(r.id))
      .forEach((r: SysResource) => {
        const actions = userActionConfigs[r.id] || [];
        map.set(r.id, {
          key: r.id,
          title: (
            <Space size={4}>
              <Text strong style={{ fontSize: 13 }}>{r.name}</Text>
              <Text type="secondary" style={{ fontSize: 11 }}>({r.code})</Text>
              {actions.length > 0 && (
                <Space size={2}>
                  {actions.map((action) => (
                    <Tag key={action} color="blue" style={{ margin: 0, fontSize: 10 }}>
                      {ACTION_LABELS[action] || action}
                    </Tag>
                  ))}
                </Space>
              )}
            </Space>
          ),
          children: [],
        });
      });

    const roots: DataNode[] = [];
    map.forEach((node, id) => {
      const res = resourceMap.get(id);
      if (res) {
        const pid = res.parentId ?? null;
        if (pid && map.has(pid)) {
          map.get(pid)!.children!.push(node);
        } else {
          roots.push(node);
        }
      }
    });

    return roots;
  }, [resourcesQuery.data, userResourceIds, userActionConfigs, resourceMap]);

  // 权限统计
  const permissionStats = useMemo(() => {
    const menuCount = userResourceIds.filter((id) => {
      const res = resourceMap.get(id);
      return res?.type === 'MENU';
    }).length;

    const apiCount = userResourceIds.filter((id) => {
      const res = resourceMap.get(id);
      return res?.type === 'API';
    }).length;

    const actionCount = userResourceIds.filter((id) => {
      const res = resourceMap.get(id);
      return res?.type === 'ACTION';
    }).length;

    return {
      menuCount,
      apiCount,
      actionCount,
      total: userResourceIds.length,
    };
  }, [userResourceIds, resourceMap]);

  // 导出权限报告
  const handleExport = () => {
    const report = {
      user: currentUser,
      permissions: {
        resources: userResourceIds.map((id) => {
          const res = resourceMap.get(id);
          return {
            id,
            name: res?.name,
            code: res?.code,
            type: res?.type,
            actions: userActionConfigs[id] || [],
          };
        }),
        stats: permissionStats,
      },
      exportTime: new Date().toISOString(),
    };

    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `user-permissions-${currentUser?.username}-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (resourcesQuery.isLoading || userQuery.isLoading) {
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
        title: `用户权限查看 - ${currentUser.realName || currentUser.username}`,
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/users', title: '用户授权' },
            { title: '查看权限' },
          ],
        },
        onBack: () => navigate('/admin/auth-center/users'),
      }}
      extra={[
        <Button
          key="export"
          icon={<ExportOutlined />}
          onClick={handleExport}
        >
          导出报告
        </Button>,
      ]}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* 用户信息卡片 */}
        <Card
          title={
            <Space>
              <UserOutlined />
              <Text strong>基本信息</Text>
            </Space>
          }
        >
          <Descriptions column={2}>
            <Descriptions.Item label="用户名">{currentUser.username}</Descriptions.Item>
            <Descriptions.Item label="真实姓名">{currentUser.realName || '-'}</Descriptions.Item>
            <Descriptions.Item label="邮箱">{currentUser.email || '-'}</Descriptions.Item>
            <Descriptions.Item label="手机">{currentUser.phone || '-'}</Descriptions.Item>
            <Descriptions.Item label="员工工号">{currentUser.employeeNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="部门">{currentUser.departmentName || '-'}</Descriptions.Item>
          </Descriptions>
        </Card>

        {/* 角色信息 */}
        <Card
          title={
            <Space>
              <TeamOutlined />
              <Text strong>角色分配</Text>
            </Space>
          }
        >
          {currentUser.roles ? (
            <Space size={[0, 8]} wrap>
              {currentUser.roles.split(',').filter(Boolean).map((role: string, index: number) => (
                <Tag key={index} color="blue" style={{ fontSize: 14, padding: '4px 12px' }}>
                  {getRoleDisplayName(role)}
                </Tag>
              ))}
            </Space>
          ) : (
            <Empty description="未分配角色" />
          )}
        </Card>

        {/* 权限统计 */}
        <Card
          title={
            <Space>
              <SafetyCertificateOutlined />
              <Text strong>权限统计</Text>
            </Space>
          }
        >
          <Row gutter={16}>
            <Col span={6}>
              <Statistic
                title="菜单权限"
                value={permissionStats.menuCount}
                suffix="项"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="API权限"
                value={permissionStats.apiCount}
                suffix="项"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="操作权限"
                value={permissionStats.actionCount}
                suffix="项"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="总计"
                value={permissionStats.total}
                suffix="项"
              />
            </Col>
          </Row>
        </Card>

        {/* 权限详情 */}
        <Card
          title={
            <Space>
              <FileTextOutlined />
              <Text strong>权限详情</Text>
              <Badge count={permissionStats.total} style={{ backgroundColor: '#1890ff' }} />
            </Space>
          }
        >
          {permissionTreeData.length > 0 ? (
            <div style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 16 }}>
              <Tree
                defaultExpandAll
                treeData={permissionTreeData}
                selectable={false}
                showLine
              />
            </div>
          ) : (
            <Empty description="暂无权限配置" />
          )}

          <Divider />

          <Alert
            message="权限说明"
            description="
              • 角色权限：通过角色继承的系统权限
              • 个性化权限：为用户单独配置的权限（如上所示）
              • 实际权限 = 角色权限 + 个性化权限
            "
            type="info"
            showIcon
          />
        </Card>

        {/* 操作按钮 */}
        <Card>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/admin/auth-center/users')}
            >
              返回
            </Button>
          </Space>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default UserPermissionView;
