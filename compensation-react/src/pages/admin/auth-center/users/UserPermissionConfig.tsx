/**
 * 用户权限配置页
 *
 * 设计原则：
 * - 使用 Tree 组件展示资源树
 * - 支持勾选资源节点
 * - 选中资源后，下方展开操作权限配置区域
 * - 提示信息：非管理员需要审批
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Tree,
  Button,
  Space,
  Descriptions,
  Checkbox,
  message,
  Spin,
  Alert,
  Tag,
  Typography,
  Collapse,
  Row,
  Col,
  Statistic,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import { useUserAggregateSearchQuery, useUserResourcesQuery, usePutUserResourcesMutation } from '@services/queries/adminAuth';
import { useRolesQuery } from '@services/queries/roles';
import type { SysResource } from '@types/api';

const { Text } = Typography;

const UserPermissionConfig: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const userIdNum = userId ? parseInt(userId) : 0;

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const userQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const userResourcesQuery = useUserResourcesQuery(userIdNum);
  const putUserResourcesMutation = usePutUserResourcesMutation(userIdNum);
  const rolesQuery = useRolesQuery({});

  // 状态
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [actionConfigs, setActionConfigs] = useState<Record<number, string[]>>({});

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

  // 构建资源树
  const treeData: DataNode[] = useMemo(() => {
    const list = resourcesQuery.data || [];
    const map = new Map<number, DataNode>();

    list.forEach((r: SysResource) => {
      map.set(r.id, {
        key: r.id,
        title: (
          <Space size={4}>
            <Text strong style={{ fontSize: 13 }}>{r.name}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>({r.code})</Text>
            {r.type === 'API' && <Tag color="purple" style={{ margin: 0, fontSize: 10 }}>API</Tag>}
            {r.type === 'ACTION' && <Tag color="orange" style={{ margin: 0, fontSize: 10 }}>操作</Tag>}
            {r.type === 'MENU' && <Tag color="blue" style={{ margin: 0, fontSize: 10 }}>菜单</Tag>}
          </Space>
        ),
        children: [],
      });
    });

    const roots: DataNode[] = [];
    list.forEach((r: SysResource) => {
      const node = map.get(r.id);
      const pid = r.parentId ?? null;
      if (pid && map.has(pid)) {
        map.get(pid)!.children!.push(node!);
      } else {
        roots.push(node!);
      }
    });

    return roots;
  }, [resourcesQuery.data]);

  // 加载用户当前权限
  useEffect(() => {
    if (userResourcesQuery.data) {
      const resources = userResourcesQuery.data as any[];
      const ids = resources.map((r) => r.resourceId).filter((id): id is number => id != null);
      setCheckedKeys(ids);

      const configs: Record<number, string[]> = {};
      resources.forEach((r) => {
        if (r.actionsJson) {
          try {
            const parsedActions = JSON.parse(r.actionsJson);
            configs[r.resourceId] = Array.isArray(parsedActions) ? parsedActions : parsedActions.actions || [];
          } catch {}
        }
      });
      setActionConfigs(configs);
    }
  }, [userResourcesQuery.data]);

  // Tree 勾选变化
  const handleCheck = (checked: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] }) => {
    const keys = Array.isArray(checked) ? checked : checked.checked;
    setCheckedKeys(keys);

    // 移除未勾选资源的操作配置
    const keySet = new Set(keys.map(Number));
    setActionConfigs((prev) => {
      const newConfigs: Record<number, string[]> = {};
      Object.keys(prev).forEach((key) => {
        const numKey = Number(key);
        if (keySet.has(numKey)) {
          newConfigs[numKey] = prev[numKey];
        }
      });
      return newConfigs;
    });
  };

  // 更新操作权限配置
  const updateActionConfig = useCallback((resourceId: number, actions: string[]) => {
    setActionConfigs((prev) => ({
      ...prev,
      [resourceId]: actions,
    }));
  }, []);

  // 保存权限配置
  const handleSave = async () => {
    try {
      const actions: Record<string, string[]> = {};
      Object.entries(actionConfigs).forEach(([key, value]) => {
        if (value.length > 0) {
          actions[key] = value;
        }
      });

      const result = await putUserResourcesMutation.mutateAsync({
        resourceIds: checkedKeys.map(Number),
        actions,
      });

      const workflowId = result?.workflowId;
      if (workflowId) {
        message.success(`已提交审批，workflowId=${workflowId}`);
      } else {
        message.success('已直接生效（管理员操作）');
      }

      navigate('/admin/auth-center/users');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  // 权限统计
  const permissionStats = useMemo(() => {
    const menuCount = checkedKeys.filter((key) => {
      const res = resourceMap.get(Number(key));
      return res?.type === 'MENU';
    }).length;

    const apiCount = checkedKeys.filter((key) => {
      const res = resourceMap.get(Number(key));
      return res?.type === 'API';
    }).length;

    const actionCount = checkedKeys.filter((key) => {
      const res = resourceMap.get(Number(key));
      return res?.type === 'ACTION';
    }).length;

    return { menuCount, apiCount, actionCount, total: checkedKeys.length };
  }, [checkedKeys, resourceMap]);

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
        title: `个性化权限配置 - ${currentUser.realName || currentUser.username}`,
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/users', title: '用户授权' },
            { title: '配置权限' },
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
            <Descriptions.Item label="当前角色">
              <Space size={[0, 4]} wrap>
                {currentUser.roles ? (
                  currentUser.roles.split(',').filter(Boolean).map((role: string, index: number) => (
                    <Tag key={index} color="blue">{getRoleDisplayName(role)}</Tag>
                  ))
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        </Card>

        {/* 提示信息 */}
        <Alert
          message="个性化授权说明"
          description="个性化授权是为用户单独授予的资源权限，不通过角色分配。非管理员操作需要提交审批，管理员操作直接生效。"
          type="info"
          showIcon
        />

        {/* 资源树 */}
        <Card
          title="资源选择"
          extra={
            <Space>
              <Text type="secondary">已选 {checkedKeys.length} 项</Text>
              <Button
                icon={<ReloadOutlined />}
                size="small"
                onClick={() => userResourcesQuery.refetch()}
                loading={userResourcesQuery.isFetching}
              >
                刷新
              </Button>
            </Space>
          }
        >
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 16, maxHeight: 500, overflowY: 'auto' }}>
            <Tree
              checkable
              defaultExpandAll
              checkedKeys={checkedKeys}
              onCheck={handleCheck}
              treeData={treeData}
              height={450}
            />
          </div>
        </Card>

        {/* 操作权限配置 */}
        {checkedKeys.length > 0 && (
          <Card title="操作权限配置">
            <Collapse
              accordion
              items={checkedKeys.map((key) => {
                const res = resourceMap.get(Number(key));
                if (!res) return null;

                const actions = actionConfigs[Number(key)] || [];

                return {
                  key: String(key),
                  label: (
                    <Space>
                      <Text strong>{res.name}</Text>
                      <Tag color={res.type === 'API' ? 'purple' : res.type === 'ACTION' ? 'orange' : 'blue'}>
                        {res.type}
                      </Tag>
                      {actions.length > 0 && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          已配置 {actions.length} 个操作
                        </Text>
                      )}
                    </Space>
                  ),
                  children: (
                    <Checkbox.Group
                      options={[
                        { label: '查看数据', value: 'read' },
                        { label: '新增/编辑', value: 'write' },
                        { label: '删除数据', value: 'delete' },
                        { label: '全部权限', value: 'admin' },
                        { label: '导出Excel', value: 'export' },
                        { label: '导入数据', value: 'import' },
                      ]}
                      value={actions}
                      onChange={(checkedValues) => updateActionConfig(Number(key), checkedValues as string[])}
                    />
                  ),
                };
              }).filter(Boolean)}
            />
          </Card>
        )}

        {/* 权限统计 */}
        <Card title="权限统计" size="small">
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="菜单权限" value={permissionStats.menuCount} suffix="项" />
            </Col>
            <Col span={6}>
              <Statistic title="API权限" value={permissionStats.apiCount} suffix="项" />
            </Col>
            <Col span={6}>
              <Statistic title="操作权限" value={permissionStats.actionCount} suffix="项" />
            </Col>
            <Col span={6}>
              <Statistic title="总计" value={permissionStats.total} suffix="项" />
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
              loading={putUserResourcesMutation.isPending}
            >
              提交审批
            </Button>
          </Space>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default UserPermissionConfig;
