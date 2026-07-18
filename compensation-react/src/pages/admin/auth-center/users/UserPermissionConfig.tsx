/**
 * 用户权限配置页
 *
 * 设计原则：
 * - 左侧资源树 + 右侧操作配置，提高信息密度
 * - 支持关键词搜索、类型筛选、仅看已选
 * - 支持批量设置操作权限
 * - 继承权限只读展示，不会提交为个性化权限
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
  Input,
  Select,
  Divider,
  Empty,
  Switch,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { ArrowLeftOutlined, SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import {
  useUserAggregateSearchQuery,
  useUserResourcesQuery,
  useUserAggregateResourcesQuery,
  usePutUserResourcesMutation,
} from '@services/queries/adminAuth';
import { useRolesQuery } from '@services/queries/roles';
import type { SysResource } from '@types/api';

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

const parseActions = (actions?: string[] | null, actionsJson?: string | null): string[] => {
  if (Array.isArray(actions)) {
    return actions;
  }
  if (!actionsJson) {
    return [];
  }
  try {
    const parsed = JSON.parse(actionsJson);
    if (Array.isArray(parsed)) {
      return parsed;
    }
    return Array.isArray(parsed?.actions) ? parsed.actions : [];
  } catch {
    return [];
  }
};

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

const UserPermissionConfig: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const userIdNum = userId ? parseInt(userId) : 0;

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const userQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const userResourcesQuery = useUserResourcesQuery(userIdNum);
  const userAggregateResourcesQuery = useUserAggregateResourcesQuery(userIdNum);
  const putUserResourcesMutation = usePutUserResourcesMutation(userIdNum);
  const rolesQuery = useRolesQuery({});

  // 状态
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [actionConfigs, setActionConfigs] = useState<Record<number, string[]>>({});
  const [resourceKeyword, setResourceKeyword] = useState('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState<ResourceTypeOption>('ALL');
  const [onlySelected, setOnlySelected] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [leafOnlyCheckable, setLeafOnlyCheckable] = useState(true);
  const [bulkActions, setBulkActions] = useState<string[]>([]);

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

  const personalizedResourceIdSet = useMemo(() => {
    const resources = userResourcesQuery.data || [];
    return new Set(resources.map((r) => r.resourceId).filter((id): id is number => id != null));
  }, [userResourcesQuery.data]);

  const inheritedResourceIds = useMemo(() => {
    const aggregateResources = userAggregateResourcesQuery.data || [];
    const aggregateIds = new Set(
      aggregateResources.map((r) => r.resourceId).filter((id): id is number => id != null),
    );
    return Array.from(aggregateIds).filter((id) => !personalizedResourceIdSet.has(id));
  }, [userAggregateResourcesQuery.data, personalizedResourceIdSet]);

  const inheritedResourceIdSet = useMemo(
    () => new Set(inheritedResourceIds),
    [inheritedResourceIds],
  );

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
        (r.name || '').toLowerCase().includes(keyword) ||
        (r.code || '').toLowerCase().includes(keyword) ||
        (r.path || '').toLowerCase().includes(keyword)
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
            <Text strong style={{ fontSize: 13 }}>
              {r.name}
            </Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              ({r.code})
            </Text>
            <Tag
              color={RESOURCE_TYPE_COLOR[r.type] || 'default'}
              style={{ margin: 0, fontSize: 10 }}
            >
              {RESOURCE_TYPE_LABEL[r.type] || r.type}
            </Tag>
            {inheritedResourceIdSet.has(r.id) && (
              <Tag color="cyan" style={{ margin: 0, fontSize: 10 }}>
                继承
              </Tag>
            )}
            {r.path && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                {r.path}
              </Text>
            )}
          </Space>
        ),
        children: [],
        disableCheckbox:
          inheritedResourceIdSet.has(r.id) ||
          (leafOnlyCheckable && nonLeafIdSet.has(r.id) && !checkedKeySet.has(r.id)),
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
  }, [filteredResources, inheritedResourceIdSet, leafOnlyCheckable, nonLeafIdSet, checkedKeySet]);

  const allTreeKeys = useMemo(() => collectTreeKeys(treeData), [treeData]);

  // 筛选条件变化时自动展开可见树
  useEffect(() => {
    setExpandedKeys(allTreeKeys);
  }, [allTreeKeys]);

  // 加载用户当前有效权限（角色继承 + 个性化）
  useEffect(() => {
    if (userAggregateResourcesQuery.data) {
      const resources = userAggregateResourcesQuery.data as any[];
      const idSet = new Set<number>();

      const configs: Record<number, string[]> = {};
      resources.forEach((r) => {
        const resourceId = r.resourceId;
        if (resourceId == null) return;

        idSet.add(resourceId);
        const parsedActions = parseActions(r.actions, r.actionsJson);
        if (parsedActions.length > 0) {
          configs[resourceId] = parsedActions;
        }
      });

      setCheckedKeys(Array.from(idSet));
      setActionConfigs(configs);
    }
  }, [userAggregateResourcesQuery.data]);

  const selectedResources = useMemo(() => {
    return checkedKeys
      .map((key) => resourceMap.get(Number(key)))
      .filter((r): r is SysResource => !!r)
      .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
  }, [checkedKeys, resourceMap]);

  const editableSelectedResources = useMemo(() => {
    return selectedResources.filter((r) => !inheritedResourceIdSet.has(r.id));
  }, [selectedResources, inheritedResourceIdSet]);

  // Tree 勾选变化
  const handleCheck = (
    checked: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] },
  ) => {
    const keys = Array.isArray(checked) ? checked : checked.checked;
    const merged = Array.from(new Set([...keys.map((k) => Number(k)), ...inheritedResourceIds]));
    setCheckedKeys(merged);

    // 移除未勾选资源的操作配置
    const keySet = new Set(merged.map(Number));
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

  const handleSelectAllVisible = () => {
    const visibleIds = filteredResources.map((r) => r.id);
    const merged = new Set<number>([
      ...checkedKeys.map((k) => Number(k)),
      ...visibleIds,
      ...inheritedResourceIds,
    ]);
    setCheckedKeys(Array.from(merged));
  };

  const handleClearSelected = () => {
    setCheckedKeys(Array.from(inheritedResourceIdSet));
    setActionConfigs((prev) => {
      const next = { ...prev };
      Object.keys(next).forEach((key) => {
        if (!inheritedResourceIdSet.has(Number(key))) {
          delete next[Number(key)];
        }
      });
      return next;
    });
  };

  const handleExpandAll = () => {
    setExpandedKeys(allTreeKeys);
  };

  const handleCollapseAll = () => {
    setExpandedKeys([]);
  };

  const applyBulkActionsToSelected = useCallback(() => {
    if (editableSelectedResources.length === 0) {
      message.warning('没有可编辑的资源');
      return;
    }

    setActionConfigs((prev) => {
      const next = { ...prev };
      editableSelectedResources.forEach((res) => {
        next[res.id] = bulkActions;
      });
      return next;
    });
    message.success(
      `已将 ${bulkActions.length} 个操作应用到 ${editableSelectedResources.length} 项资源`,
    );
  }, [editableSelectedResources, bulkActions]);

  const clearActionsForSelected = useCallback(() => {
    if (editableSelectedResources.length === 0) {
      message.warning('没有可编辑的资源');
      return;
    }

    setActionConfigs((prev) => {
      const next = { ...prev };
      editableSelectedResources.forEach((res) => {
        delete next[res.id];
      });
      return next;
    });
    message.success(`已清空 ${editableSelectedResources.length} 项资源的操作配置`);
  }, [editableSelectedResources]);

  // 保存权限配置
  const handleSave = async () => {
    try {
      const resourceIds = checkedKeys.map(Number).filter((id) => !inheritedResourceIdSet.has(id));

      const actions: Record<string, string[]> = {};
      Object.entries(actionConfigs).forEach(([key, value]) => {
        if (value.length > 0 && !inheritedResourceIdSet.has(Number(key))) {
          actions[key] = value;
        }
      });

      const result = await putUserResourcesMutation.mutateAsync({
        resourceIds,
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

  if (
    resourcesQuery.isLoading ||
    userQuery.isLoading ||
    userResourcesQuery.isLoading ||
    userAggregateResourcesQuery.isLoading
  ) {
    return (
      <PageContainer>
        <Card>
          <Spin description="加载中..." />
        </Card>
      </PageContainer>
    );
  }

  if (!currentUser) {
    return (
      <PageContainer>
        <Card>
          <Alert title="用户不存在" description="未找到指定的用户信息" type="error" showIcon />
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
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {/* 用户信息 */}
        <Card title="用户信息" size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label="用户名">{currentUser.username}</Descriptions.Item>
            <Descriptions.Item label="当前角色">
              <Space size={[0, 4]} wrap>
                {currentUser.roles ? (
                  currentUser.roles
                    .split(',')
                    .filter(Boolean)
                    .map((role: string, index: number) => (
                      <Tag key={index} color="blue">
                        {getRoleDisplayName(role)}
                      </Tag>
                    ))
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Alert
          title="个性化授权说明"
          description={`当前有 ${inheritedResourceIds.length} 项权限来自角色继承（标记为“继承”且不可取消/编辑），提交时仅会保存个性化权限。`}
          type="info"
          showIcon
        />

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={14}>
            <Card
              title="资源选择"
              extra={
                <Space>
                  <Text type="secondary">已选 {checkedKeys.length} 项</Text>
                  <Button
                    icon={<ReloadOutlined />}
                    size="small"
                    onClick={() => {
                      userResourcesQuery.refetch();
                      userAggregateResourcesQuery.refetch();
                    }}
                    loading={
                      userResourcesQuery.isFetching || userAggregateResourcesQuery.isFetching
                    }
                  >
                    刷新
                  </Button>
                </Space>
              }
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
                <Button
                  size="small"
                  onClick={handleSelectAllVisible}
                  disabled={filteredResources.length === 0}
                >
                  全选当前结果
                </Button>
                <Button
                  size="small"
                  onClick={handleClearSelected}
                  disabled={checkedKeys.length === inheritedResourceIds.length}
                >
                  清空个性化已选
                </Button>
                <Button size="small" onClick={handleExpandAll} disabled={treeData.length === 0}>
                  展开全部
                </Button>
                <Button size="small" onClick={handleCollapseAll} disabled={treeData.length === 0}>
                  折叠全部
                </Button>
              </Space>

              <div
                style={{
                  border: '1px solid #f0f0f0',
                  borderRadius: 4,
                  padding: 12,
                  minHeight: 520,
                }}
              >
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
            <Card
              title="操作权限配置"
              extra={<Text type="secondary">资源 {selectedResources.length} 项</Text>}
            >
              {selectedResources.length === 0 ? (
                <Empty description="请先在左侧选择资源" />
              ) : (
                <>
                  <Alert
                    title="为个性化资源配置操作"
                    description={`可编辑资源 ${editableSelectedResources.length} 项，继承资源仅展示不可编辑。`}
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                  />

                  <Space
                    orientation="vertical"
                    size={8}
                    style={{ width: '100%', marginBottom: 12 }}
                  >
                    <Text type="secondary">批量设置操作（仅作用于可编辑资源）</Text>
                    <Checkbox.Group
                      options={ACTION_OPTIONS}
                      value={bulkActions}
                      onChange={(values) => setBulkActions(values as string[])}
                    />
                    <Space>
                      <Button size="small" onClick={applyBulkActionsToSelected}>
                        应用到全部可编辑
                      </Button>
                      <Button size="small" onClick={clearActionsForSelected}>
                        清空全部可编辑动作
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
                        const isInheritedOnly = inheritedResourceIdSet.has(res.id);
                        return {
                          key: String(res.id),
                          label: (
                            <Space wrap>
                              <Text strong>{res.name}</Text>
                              <Tag color={RESOURCE_TYPE_COLOR[res.type] || 'default'}>
                                {RESOURCE_TYPE_LABEL[res.type] || res.type}
                              </Tag>
                              {isInheritedOnly && <Tag color="cyan">继承</Tag>}
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
                              disabled={isInheritedOnly}
                              onChange={(checkedValues) =>
                                updateActionConfig(res.id, checkedValues as string[])
                              }
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

        <Card title="权限统计" size="small">
          <Row gutter={16}>
            <Col xs={12} sm={6}>
              <Statistic title="菜单权限" value={permissionStats.menuCount} suffix="项" />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic title="API权限" value={permissionStats.apiCount} suffix="项" />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic title="操作权限" value={permissionStats.actionCount} suffix="项" />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic title="总计" value={permissionStats.total} suffix="项" />
            </Col>
          </Row>
        </Card>

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
