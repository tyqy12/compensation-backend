/**
 * 角色权限配置页
 *
 * 设计原则：
 * - 独立页面（不再是标签页内的左右分栏）
 * - 上下布局：上方是资源树，下方是操作权限配置
 * - 选中资源节点后，下方展开操作权限配置区域
 * - 支持批量勾选和全选
 * - 实时显示已选资源数量
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
  Badge,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import { useRolesQuery } from '@services/queries/roles';
import { useRoleResourcesQuery, usePutRoleResourcesMutation } from '@services/queries/adminAuth';
import type { SysResource, RoleInfo } from '@types/api';

const { Text } = Typography;

const RolePermissionConfig: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const roleIdNum = roleId ? parseInt(roleId) : 0;

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const rolesQuery = useRolesQuery({});
  const roleResourcesQuery = useRoleResourcesQuery(roleIdNum);
  const putRoleResourcesMutation = usePutRoleResourcesMutation(roleIdNum);

  // 状态
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [actionConfigs, setActionConfigs] = useState<Record<number, string[]>>({});

  // 当前角色信息
  const currentRole: RoleInfo | undefined = useMemo(() => {
    return rolesQuery.data?.find((r: RoleInfo) => r.id === roleIdNum);
  }, [rolesQuery.data, roleIdNum]);

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

  // 加载角色当前权限
  useEffect(() => {
    if (roleResourcesQuery.data) {
      const resources = roleResourcesQuery.data as any[];
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
  }, [roleResourcesQuery.data]);

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

      const result = await putRoleResourcesMutation.mutateAsync({
        resourceIds: checkedKeys.map(Number),
        actions,
      });

      const workflowId = result?.workflowId;
      if (workflowId) {
        message.success(`已提交审批，workflowId=${workflowId}`);
      } else {
        message.success('已直接生效（管理员操作）');
      }

      navigate('/admin/auth-center/roles');
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

  if (resourcesQuery.isLoading || rolesQuery.isLoading) {
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
        title: `角色权限配置 - ${currentRole.name}`,
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/roles', title: '角色管理' },
            { title: '权限配置' },
          ],
        },
        onBack: () => navigate('/admin/auth-center/roles'),
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* 角色信息 */}
        <Card
          title={
            <Space>
              <SafetyCertificateOutlined />
              <Text strong>角色信息</Text>
            </Space>
          }
          size="small"
        >
          <Descriptions column={2} size="small">
            <Descriptions.Item label="角色名称">{currentRole.name}</Descriptions.Item>
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
            <Descriptions.Item label="关联用户" span={2}>
              {currentRole.userCount || 0} 人
            </Descriptions.Item>
          </Descriptions>
        </Card>

        {/* 资源树 */}
        <Card
          title="资源选择"
          extra={
            <Space>
              <Text type="secondary">已选 {checkedKeys.length} 项</Text>
              <Button
                icon={<ReloadOutlined />}
                size="small"
                onClick={() => roleResourcesQuery.refetch()}
                loading={roleResourcesQuery.isFetching}
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
            <Alert
              message="为选中的资源配置具体的操作权限"
              description="可以为每个资源配置不同的操作权限，如：read（读取）、write（写入）、delete（删除）等"
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
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
                      <Text type="secondary" style={{ fontSize: 11 }}>{res.code}</Text>
                      {actions.length > 0 && (
                        <Space size={4}>
                          {actions.slice(0, 3).map((action) => (
                            <Tag key={action} color="blue" style={{ margin: 0, fontSize: 11 }}>
                              {action}
                            </Tag>
                          ))}
                          {actions.length > 3 && (
                            <Text type="secondary" style={{ fontSize: 11 }}>
                              +{actions.length - 3}
                            </Text>
                          )}
                        </Space>
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
              onClick={() => navigate('/admin/auth-center/roles')}
            >
              取消
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={putRoleResourcesMutation.isPending}
              disabled={currentRole.isProtected}
            >
              保存并应用
            </Button>
          </Space>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default RolePermissionConfig;
