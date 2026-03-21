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
  Input,
  Select,
  Divider,
  Empty,
  Switch,
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

type ResourceTypeOption = 'ALL' | 'MENU' | 'VIEW' | 'ACTION' | 'API';

const RESOURCE_TYPE_OPTIONS: Array<{ label: string; value: ResourceTypeOption }> = [
  { label: '全部类型', value: 'ALL' },
  { label: '菜单', value: 'MENU' },
  { label: '页面', value: 'VIEW' },
  { label: '操作', value: 'ACTION' },
  { label: 'API', value: 'API' },
];

const RESOURCE_TYPE_LABEL: Record<string, string> = {
  MENU: '菜单',
  VIEW: '页面',
  ACTION: '操作',
  API: 'API',
};

const RESOURCE_TYPE_COLOR: Record<string, string> = {
  MENU: 'blue',
  VIEW: 'green',
  ACTION: 'orange',
  API: 'purple',
};

const ACTION_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '查看数据', value: 'read' },
  { label: '新增/编辑', value: 'write' },
  { label: '删除数据', value: 'delete' },
  { label: '全部权限', value: 'admin' },
  { label: '导出Excel', value: 'export' },
  { label: '导入数据', value: 'import' },
];

const collectTreeKeys = (nodes: DataNode[]): React.Key[] => {
  const keys: React.Key[] = [];
  const walk = (items: DataNode[]) => {
    items.forEach((item) => {
      keys.push(item.key);
      if (item.children && item.children.length > 0) {
        walk(item.children);
      }
    });
  };
  walk(nodes);
  return keys;
};

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
  const [resourceKeyword, setResourceKeyword] = useState('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState<ResourceTypeOption>('ALL');
  const [onlySelected, setOnlySelected] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [leafOnlyCheckable, setLeafOnlyCheckable] = useState(true);
  const [bulkActions, setBulkActions] = useState<string[]>([]);

  // 当前角色信息
  const currentRole: RoleInfo | undefined = useMemo(() => {
    return rolesQuery.data?.find((r: RoleInfo) => r.id === roleIdNum);
  }, [rolesQuery.data, roleIdNum]);

  // 资源映射
  const resourceMap = useMemo(() => {
    const list = resourcesQuery.data || [];
    return new Map(list.map((r) => [r.id, r]));
  }, [resourcesQuery.data]);

  const checkedKeySet = useMemo(() => {
    return new Set(checkedKeys.map((key) => Number(key)));
  }, [checkedKeys]);

  // 根据关键词、类型、已选状态过滤资源，同时补全父节点保证层级可读
  const filteredResources = useMemo(() => {
    const list = resourcesQuery.data || [];
    if (list.length === 0) return [];

    const keyword = resourceKeyword.trim().toLowerCase();
    const noFilter = keyword.length === 0 && resourceTypeFilter === 'ALL' && !onlySelected;
    if (noFilter) return list;

    const byId = new Map<number, SysResource>(list.map((r) => [r.id, r]));
    const match = (r: SysResource) => {
      if (resourceTypeFilter !== 'ALL' && r.type !== resourceTypeFilter) return false;
      if (onlySelected && !checkedKeySet.has(r.id)) return false;
      if (!keyword) return true;
      return (
        (r.name || '').toLowerCase().includes(keyword)
        || (r.code || '').toLowerCase().includes(keyword)
        || (r.path || '').toLowerCase().includes(keyword)
      );
    };

    const visibleIds = new Set<number>();
    list.forEach((r) => {
      if (!match(r)) return;

      let current: SysResource | undefined = r;
      while (current) {
        if (visibleIds.has(current.id)) break;
        visibleIds.add(current.id);
        const parentId = current.parentId ?? null;
        current = parentId != null ? byId.get(parentId) : undefined;
      }
    });

    return list.filter((r) => visibleIds.has(r.id));
  }, [resourcesQuery.data, resourceKeyword, resourceTypeFilter, onlySelected, checkedKeySet]);

  const nonLeafIdSet = useMemo(() => {
    const set = new Set<number>();
    filteredResources.forEach((r) => {
      if (r.parentId != null) {
        set.add(r.parentId);
      }
    });
    return set;
  }, [filteredResources]);

  // 构建资源树
  const treeData: DataNode[] = useMemo(() => {
    const list = filteredResources || [];
    const map = new Map<number, DataNode>();

    list.forEach((r: SysResource) => {
      map.set(r.id, {
        key: r.id,
        title: (
          <Space size={4} wrap>
            <Text strong style={{ fontSize: 13 }}>{r.name}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>({r.code})</Text>
            <Tag color={RESOURCE_TYPE_COLOR[r.type] || 'default'} style={{ margin: 0, fontSize: 10 }}>
              {RESOURCE_TYPE_LABEL[r.type] || r.type}
            </Tag>
            {r.path && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                {r.path}
              </Text>
            )}
          </Space>
        ),
        children: [],
        disableCheckbox: leafOnlyCheckable && nonLeafIdSet.has(r.id) && !checkedKeySet.has(r.id),
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
  }, [filteredResources, leafOnlyCheckable, nonLeafIdSet, checkedKeySet]);

  const allTreeKeys = useMemo(() => collectTreeKeys(treeData), [treeData]);

  // 筛选条件变化时自动展开可见树，减少用户额外点击
  useEffect(() => {
    setExpandedKeys(allTreeKeys);
  }, [allTreeKeys]);

  // 加载角色当前权限
  useEffect(() => {
    if (roleResourcesQuery.data) {
      const resources = roleResourcesQuery.data as Array<{
        id?: number;
        resourceId?: number;
        actions?: string[];
        actionsJson?: string | null;
      }>;
      const ids = new Set<number>();
      const configs: Record<number, string[]> = {};

      resources.forEach((r) => {
        const resourceId = r.id ?? r.resourceId;
        if (resourceId == null) return;

        ids.add(resourceId);

        if (Array.isArray(r.actions)) {
          configs[resourceId] = r.actions;
          return;
        }

        if (r.actionsJson) {
          try {
            const parsedActions = JSON.parse(r.actionsJson);
            configs[resourceId] = Array.isArray(parsedActions) ? parsedActions : parsedActions.actions || [];
          } catch {}
        }
      });

      setCheckedKeys(Array.from(ids));
      setActionConfigs(configs);
    }
  }, [roleResourcesQuery.data]);

  const selectedResources = useMemo(() => {
    return checkedKeys
      .map((key) => resourceMap.get(Number(key)))
      .filter((r): r is SysResource => !!r)
      .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
  }, [checkedKeys, resourceMap]);

  // Tree 勾选变化
  const handleCheck = (checked: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] }) => {
    const keys = Array.isArray(checked) ? checked : checked.checked;
    const numericKeys = keys.map((key) => Number(key));
    setCheckedKeys(numericKeys);

    // 移除未勾选资源的操作配置
    const keySet = new Set(numericKeys);
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

  const applyBulkActionsToSelected = useCallback(() => {
    if (checkedKeys.length === 0) {
      message.warning('请先选择资源');
      return;
    }

    setActionConfigs((prev) => {
      const next = { ...prev };
      checkedKeys.forEach((key) => {
        next[Number(key)] = bulkActions;
      });
      return next;
    });
    message.success(`已将 ${bulkActions.length} 个操作应用到 ${checkedKeys.length} 项资源`);
  }, [checkedKeys, bulkActions]);

  const clearActionsForSelected = useCallback(() => {
    if (checkedKeys.length === 0) {
      message.warning('请先选择资源');
      return;
    }

    setActionConfigs((prev) => {
      const next = { ...prev };
      checkedKeys.forEach((key) => {
        delete next[Number(key)];
      });
      return next;
    });
    message.success(`已清空 ${checkedKeys.length} 项资源的操作配置`);
  }, [checkedKeys]);

  const handleSelectAllVisible = () => {
    const visibleIds = filteredResources.map((r) => r.id);
    const merged = new Set<number>([...checkedKeys.map((k) => Number(k)), ...visibleIds]);
    setCheckedKeys(Array.from(merged));
  };

  const handleClearSelected = () => {
    setCheckedKeys([]);
    setActionConfigs({});
  };

  const handleExpandAll = () => {
    setExpandedKeys(allTreeKeys);
  };

  const handleCollapseAll = () => {
    setExpandedKeys([]);
  };

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

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={14}>
            <Card
              title="资源选择"
              extra={(
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
              )}
            >
              <Space wrap style={{ marginBottom: 12 }}>
                <Input.Search
                  allowClear
                  placeholder="按名称 / 编码 / 路径搜索"
                  style={{ width: 260 }}
                  value={resourceKeyword}
                  onChange={(e) => setResourceKeyword(e.target.value)}
                />
                <Select<ResourceTypeOption>
                  value={resourceTypeFilter}
                  style={{ width: 140 }}
                  options={RESOURCE_TYPE_OPTIONS}
                  onChange={setResourceTypeFilter}
                />
                <Checkbox
                  checked={onlySelected}
                  onChange={(e) => setOnlySelected(e.target.checked)}
                >
                  仅看已选
                </Checkbox>
                <Space size={4}>
                  <Text type="secondary">仅叶子可勾选</Text>
                  <Switch
                    size="small"
                    checked={leafOnlyCheckable}
                    onChange={setLeafOnlyCheckable}
                  />
                </Space>
                <Divider type="vertical" />
                <Button size="small" onClick={handleSelectAllVisible} disabled={filteredResources.length === 0}>
                  全选当前结果
                </Button>
                <Button size="small" onClick={handleClearSelected} disabled={checkedKeys.length === 0}>
                  清空已选
                </Button>
                <Button size="small" onClick={handleExpandAll} disabled={treeData.length === 0}>
                  展开全部
                </Button>
                <Button size="small" onClick={handleCollapseAll} disabled={treeData.length === 0}>
                  折叠全部
                </Button>
              </Space>
              <div style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 12, minHeight: 520 }}>
                {treeData.length > 0 ? (
                  <Tree
                    checkable
                    showLine
                    checkedKeys={checkedKeys}
                    expandedKeys={expandedKeys}
                    onExpand={(keys) => setExpandedKeys(keys)}
                    onCheck={handleCheck}
                    treeData={treeData}
                    height={500}
                  />
                ) : (
                  <Empty description="没有匹配的资源，请调整筛选条件" />
                )}
              </div>
            </Card>
          </Col>

          <Col xs={24} lg={10}>
            <Card title="操作权限配置" extra={<Text type="secondary">资源 {selectedResources.length} 项</Text>}>
              {selectedResources.length === 0 ? (
                <Empty description="请先在左侧选择资源" />
              ) : (
                <>
                  <Alert
                    message="为选中的资源配置具体操作权限"
                    description="未勾选任何操作表示默认权限范围，勾选后将按你配置的动作细分控制。"
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                  />
                  <Space direction="vertical" size={8} style={{ width: '100%', marginBottom: 12 }}>
                    <Text type="secondary">批量设置操作（应用到当前全部已选资源）</Text>
                    <Checkbox.Group
                      options={ACTION_OPTIONS}
                      value={bulkActions}
                      onChange={(values) => setBulkActions(values as string[])}
                    />
                    <Space>
                      <Button size="small" onClick={applyBulkActionsToSelected}>
                        应用到全部已选
                      </Button>
                      <Button size="small" onClick={clearActionsForSelected}>
                        清空全部动作
                      </Button>
                    </Space>
                  </Space>
                  <Space wrap style={{ marginBottom: 12 }}>
                    {selectedResources.slice(0, 12).map((res) => (
                      <Tag key={res.id} color={RESOURCE_TYPE_COLOR[res.type] || 'default'}>
                        {res.name}
                      </Tag>
                    ))}
                    {selectedResources.length > 12 && (
                      <Text type="secondary">+{selectedResources.length - 12} 项</Text>
                    )}
                  </Space>
                  <div style={{ maxHeight: 430, overflowY: 'auto', paddingRight: 4 }}>
                    <Collapse
                      accordion
                      items={selectedResources.map((res) => {
                        const actions = actionConfigs[res.id] || [];
                        return {
                          key: String(res.id),
                          label: (
                            <Space wrap>
                              <Text strong>{res.name}</Text>
                              <Tag color={RESOURCE_TYPE_COLOR[res.type] || 'default'}>
                                {RESOURCE_TYPE_LABEL[res.type] || res.type}
                              </Tag>
                              <Text type="secondary" style={{ fontSize: 11 }}>
                                {res.code}
                              </Text>
                              {actions.length > 0 && (
                                <Text type="secondary" style={{ fontSize: 11 }}>
                                  已配置 {actions.length} 个操作
                                </Text>
                              )}
                            </Space>
                          ),
                          children: (
                            <Checkbox.Group
                              options={ACTION_OPTIONS}
                              value={actions}
                              onChange={(checkedValues) => updateActionConfig(res.id, checkedValues as string[])}
                            />
                          ),
                        };
                      })}
                    />
                  </div>
                </>
              )}
            </Card>
          </Col>
        </Row>

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
