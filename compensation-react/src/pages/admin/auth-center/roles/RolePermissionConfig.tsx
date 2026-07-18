import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Empty,
  Input,
  Select,
  Spin,
  Switch,
  Tag,
  Tree,
  Typography,
  message,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  CheckOutlined,
  ClearOutlined,
  CompressOutlined,
  ExpandOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import { useRolesQuery } from '@services/queries/roles';
import {
  usePermissionActionsQuery,
  useRoleResourcesQuery,
  usePutRoleResourcesMutation,
} from '@services/queries/adminAuth';
import type { RoleInfo, SysResource } from '@types/api';
import './RolePages.less';

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

const ROLE_TYPE_META: Record<RoleInfo['roleType'], { label: string; color: string }> = {
  SYSTEM: { label: '系统角色', color: 'red' },
  BUSINESS: { label: '业务角色', color: 'blue' },
  CUSTOM: { label: '自定义角色', color: 'green' },
};

const parseActions = (resource: { actions?: string[]; actionsJson?: string | null }) => {
  if (Array.isArray(resource.actions)) return resource.actions;
  if (!resource.actionsJson) return [];
  try {
    const parsed = JSON.parse(resource.actionsJson);
    return Array.isArray(parsed) ? parsed : Array.isArray(parsed?.actions) ? parsed.actions : [];
  } catch {
    return [];
  }
};

const collectTreeKeys = (nodes: DataNode[]): React.Key[] => {
  const keys: React.Key[] = [];
  const walk = (items: DataNode[]) => {
    items.forEach((item) => {
      keys.push(item.key);
      if (item.children?.length) walk(item.children);
    });
  };
  walk(nodes);
  return keys;
};

const RolePermissionConfig: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const roleIdNum = roleId ? Number(roleId) : 0;

  const resourcesQuery = useResourcesQuery();
  const rolesQuery = useRolesQuery({});
  const roleResourcesQuery = useRoleResourcesQuery(roleIdNum);
  const putRoleResourcesMutation = usePutRoleResourcesMutation(roleIdNum);
  const permissionActionsQuery = usePermissionActionsQuery();

  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [actionConfigs, setActionConfigs] = useState<Record<number, string[]>>({});
  const [resourceKeyword, setResourceKeyword] = useState('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState<ResourceTypeOption>('ALL');
  const [onlySelected, setOnlySelected] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [leafOnlyCheckable, setLeafOnlyCheckable] = useState(true);
  const [bulkActions, setBulkActions] = useState<string[]>([]);

  const currentRole: RoleInfo | undefined = useMemo(
    () => rolesQuery.data?.find((role) => role.id === roleIdNum),
    [roleIdNum, rolesQuery.data],
  );
  const resourceMap = useMemo(
    () => new Map((resourcesQuery.data ?? []).map((resource) => [resource.id, resource])),
    [resourcesQuery.data],
  );
  const checkedKeySet = useMemo(
    () => new Set(checkedKeys.map((key) => Number(key))),
    [checkedKeys],
  );
  const actionCatalog = permissionActionsQuery.data ?? [];
  const actionOptions = useMemo(
    () => actionCatalog.map((action) => ({ label: action.name || action.code, value: action.code })),
    [actionCatalog],
  );
  const actionOptionsForResource = useCallback(
    (resourceId: number) => actionCatalog
      .filter((action) => action.resourceIds?.includes(resourceId))
      .map((action) => ({ label: action.name || action.code, value: action.code })),
    [actionCatalog],
  );
  const bulkActionOptions = useMemo(() => {
    const selectedIds = checkedKeys.map(Number);
    if (!selectedIds.length) return actionOptions;
    return actionCatalog
      .filter((action) => selectedIds.every((id) => action.resourceIds?.includes(id)))
      .map((action) => ({ label: action.name || action.code, value: action.code }));
  }, [actionCatalog, actionOptions, checkedKeys]);

  const filteredResources = useMemo(() => {
    const list = resourcesQuery.data ?? [];
    const keyword = resourceKeyword.trim().toLowerCase();
    if (!keyword && resourceTypeFilter === 'ALL' && !onlySelected) return list;

    const byId = new Map<number, SysResource>(list.map((resource) => [resource.id, resource]));
    const matches = (resource: SysResource) => {
      if (resourceTypeFilter !== 'ALL' && resource.type !== resourceTypeFilter) return false;
      if (onlySelected && !checkedKeySet.has(resource.id)) return false;
      if (!keyword) return true;
      return [resource.name, resource.code, resource.path]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword));
    };

    const visibleIds = new Set<number>();
    list.forEach((resource) => {
      if (!matches(resource)) return;
      let current: SysResource | undefined = resource;
      while (current) {
        if (visibleIds.has(current.id)) break;
        visibleIds.add(current.id);
        current = current.parentId != null ? byId.get(current.parentId) : undefined;
      }
    });
    return list.filter((resource) => visibleIds.has(resource.id));
  }, [checkedKeySet, onlySelected, resourceKeyword, resourceTypeFilter, resourcesQuery.data]);

  const nonLeafIdSet = useMemo(() => {
    const ids = new Set<number>();
    filteredResources.forEach((resource) => {
      if (resource.parentId != null) ids.add(resource.parentId);
    });
    return ids;
  }, [filteredResources]);

  const treeData: DataNode[] = useMemo(() => {
    const nodeMap = new Map<number, DataNode>();
    filteredResources.forEach((resource) => {
      nodeMap.set(resource.id, {
        key: resource.id,
        title: (
          <div className="permission-tree-label">
            <Text strong>{resource.name}</Text>
            <Text type="secondary" className="permission-tree-code">
              {resource.code}
            </Text>
            <Tag color={RESOURCE_TYPE_COLOR[resource.type] || 'default'}>
              {RESOURCE_TYPE_LABEL[resource.type] || resource.type}
            </Tag>
            {resource.path && <Text type="secondary">{resource.path}</Text>}
          </div>
        ),
        children: [],
        disableCheckbox:
          leafOnlyCheckable && nonLeafIdSet.has(resource.id) && !checkedKeySet.has(resource.id),
      });
    });

    const roots: DataNode[] = [];
    filteredResources.forEach((resource) => {
      const node = nodeMap.get(resource.id);
      const parentId = resource.parentId ?? null;
      if (parentId != null && nodeMap.has(parentId)) {
        nodeMap.get(parentId)?.children?.push(node!);
      } else if (node) {
        roots.push(node);
      }
    });
    return roots;
  }, [checkedKeySet, filteredResources, leafOnlyCheckable, nonLeafIdSet]);

  const allTreeKeys = useMemo(() => collectTreeKeys(treeData), [treeData]);

  useEffect(() => {
    setExpandedKeys(allTreeKeys);
  }, [allTreeKeys]);

  useEffect(() => {
    if (!roleResourcesQuery.data) return;
    const ids = new Set<number>();
    const configs: Record<number, string[]> = {};
    roleResourcesQuery.data.forEach((resource) => {
      const resourceId = resource.id;
      ids.add(resourceId);
      const actions = parseActions(resource);
      if (actions.length) configs[resourceId] = actions;
    });
    setCheckedKeys(Array.from(ids));
    setActionConfigs(configs);
  }, [roleResourcesQuery.data]);

  const selectedResources = useMemo(
    () =>
      checkedKeys
        .map((key) => resourceMap.get(Number(key)))
        .filter((resource): resource is SysResource => Boolean(resource))
        .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0)),
    [checkedKeys, resourceMap],
  );

  const handleCheck = (
    checked: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] },
  ) => {
    const keys = Array.isArray(checked) ? checked : checked.checked;
    const numericKeys = keys.map((key) => Number(key));
    setCheckedKeys(numericKeys);
    const keySet = new Set(numericKeys);
    setActionConfigs((previous) =>
      Object.fromEntries(Object.entries(previous).filter(([key]) => keySet.has(Number(key)))),
    );
  };

  const updateActionConfig = useCallback((resourceId: number, actions: string[]) => {
    setActionConfigs((previous) => ({ ...previous, [resourceId]: actions }));
  }, []);

  const applyBulkActionsToSelected = useCallback(() => {
    if (!checkedKeys.length) {
      message.warning('请先选择资源');
      return;
    }
    setActionConfigs((previous) => {
      const next = { ...previous };
      checkedKeys.forEach((key) => {
        next[Number(key)] = bulkActions;
      });
      return next;
    });
    message.success(`已将 ${bulkActions.length} 个操作应用到 ${checkedKeys.length} 项资源`);
  }, [bulkActions, checkedKeys]);

  const clearActionsForSelected = useCallback(() => {
    if (!checkedKeys.length) {
      message.warning('请先选择资源');
      return;
    }
    setActionConfigs((previous) => {
      const next = { ...previous };
      checkedKeys.forEach((key) => delete next[Number(key)]);
      return next;
    });
    message.success(`已清空 ${checkedKeys.length} 项资源的操作配置`);
  }, [checkedKeys]);

  const handleSelectAllVisible = () => {
    const visibleIds = filteredResources.map((resource) => resource.id);
    setCheckedKeys(Array.from(new Set([...checkedKeys.map(Number), ...visibleIds])));
  };

  const handleClearSelected = () => {
    setCheckedKeys([]);
    setActionConfigs({});
  };

  const handleSave = async () => {
    try {
      const actions: Record<string, string[]> = {};
      Object.entries(actionConfigs).forEach(([key, values]) => {
        if (values.length) actions[key] = values;
      });
      const result = await putRoleResourcesMutation.mutateAsync({
        resourceIds: checkedKeys.map(Number),
        actions,
      });
      if (result?.workflowId) {
        message.success(`已提交审批，workflowId=${result.workflowId}`);
      } else {
        message.success('权限配置已生效');
      }
      navigate('/admin/auth-center/roles');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  const permissionStats = useMemo(() => {
    const countByType = (type: string) =>
      checkedKeys.filter((key) => resourceMap.get(Number(key))?.type === type).length;
    return {
      total: checkedKeys.length,
      menu: countByType('MENU'),
      view: countByType('VIEW'),
      action: countByType('ACTION'),
      api: countByType('API'),
      configured: Object.values(actionConfigs).filter((actions) => actions.length > 0).length,
    };
  }, [actionConfigs, checkedKeys, resourceMap]);

  const pageHeader = {
    title: '权限配置',
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
  };

  if (resourcesQuery.isLoading || rolesQuery.isLoading || roleResourcesQuery.isLoading || permissionActionsQuery.isLoading) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Spin size="large" description="加载权限资源..." />
        </main>
      </PageContainer>
    );
  }

  if (resourcesQuery.isError || rolesQuery.isError || roleResourcesQuery.isError || permissionActionsQuery.isError) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert title="权限配置加载失败" description="请刷新页面后重试" type="error" showIcon />
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
  const readOnly = currentRole.isProtected || !currentRole.isEditable;

  return (
    <PageContainer header={pageHeader}>
      <main className="role-permission-page">
        <section className="role-context-panel">
          <div className="role-context-identity">
            <div className="role-context-icon is-permission">
              <SafetyCertificateOutlined />
            </div>
            <div className="role-context-copy">
              <Text className="role-page-eyebrow">ACCESS CONTROL</Text>
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
            {currentRole.description || '为该角色选择可访问的资源，并细化到具体操作权限。'}
          </Text>
        </section>

        <section className="role-stat-strip" aria-label="权限统计">
          <div className="role-stat-item is-blue">
            <Text className="role-stat-label">已选资源</Text>
            <div className="role-stat-value">
              {permissionStats.total}
              <Text> 项</Text>
            </div>
            <Text type="secondary">当前授权范围</Text>
          </div>
          <div className="role-stat-item is-green">
            <Text className="role-stat-label">菜单 / 页面</Text>
            <div className="role-stat-value">
              {permissionStats.menu + permissionStats.view}
              <Text> 项</Text>
            </div>
            <Text type="secondary">导航和页面访问</Text>
          </div>
          <div className="role-stat-item is-amber">
            <Text className="role-stat-label">操作 / API</Text>
            <div className="role-stat-value">
              {permissionStats.action + permissionStats.api}
              <Text> 项</Text>
            </div>
            <Text type="secondary">动作和接口资源</Text>
          </div>
          <div className="role-stat-item is-red">
            <Text className="role-stat-label">已细化动作</Text>
            <div className="role-stat-value">
              {permissionStats.configured}
              <Text> 项</Text>
            </div>
            <Text type="secondary">已配置具体操作</Text>
          </div>
        </section>

        <section className="role-permission-workspace">
          <div className="role-workspace-header">
            <div>
              <Text className="role-section-kicker">RESOURCE POLICY</Text>
              <Typography.Title level={4}>资源权限目录</Typography.Title>
              <Text type="secondary">从左侧选择资源，再在右侧配置动作范围。</Text>
            </div>
            <div className="role-workspace-actions">
              <Text type="secondary">已选 {checkedKeys.length} 项</Text>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => roleResourcesQuery.refetch()}
                loading={roleResourcesQuery.isFetching}
              >
                刷新
              </Button>
            </div>
          </div>

          <div className="permission-filterbar">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              value={resourceKeyword}
              placeholder="搜索资源名称、编码或路径"
              onChange={(event) => setResourceKeyword(event.target.value)}
            />
            <Select<ResourceTypeOption>
              value={resourceTypeFilter}
              options={RESOURCE_TYPE_OPTIONS}
              onChange={setResourceTypeFilter}
            />
            <label className="permission-switch-control">
              <Switch size="small" checked={onlySelected} onChange={setOnlySelected} />
              <span>仅看已选</span>
            </label>
            <label className="permission-switch-control">
              <Switch size="small" checked={leafOnlyCheckable} onChange={setLeafOnlyCheckable} />
              <span>仅叶子可勾选</span>
            </label>
          </div>

          {readOnly && (
            <Alert
              className="permission-readonly-alert"
              title="该角色为保护角色，当前仅可查看权限"
              type="warning"
              showIcon
            />
          )}

          <div className="role-permission-grid">
            <section className="permission-resource-panel">
              <div className="permission-panel-heading">
                <div>
                  <Text strong>资源树</Text>
                  <Text type="secondary">共 {filteredResources.length} 项可见资源</Text>
                </div>
                <div className="permission-panel-tools">
                  <Button
                    size="small"
                    icon={<CheckOutlined />}
                    onClick={handleSelectAllVisible}
                    disabled={!filteredResources.length || readOnly}
                  >
                    全选结果
                  </Button>
                  <Button
                    size="small"
                    icon={<ClearOutlined />}
                    onClick={handleClearSelected}
                    disabled={!checkedKeys.length || readOnly}
                  >
                    清空
                  </Button>
                  <Button
                    size="small"
                    icon={<ExpandOutlined />}
                    onClick={() => setExpandedKeys(allTreeKeys)}
                    disabled={!treeData.length}
                    aria-label="展开全部"
                  />
                  <Button
                    size="small"
                    icon={<CompressOutlined />}
                    onClick={() => setExpandedKeys([])}
                    disabled={!treeData.length}
                    aria-label="折叠全部"
                  />
                </div>
              </div>
              <div className="permission-tree-shell">
                {treeData.length ? (
                  <Tree
                    checkable
                    showLine
                    blockNode
                    checkedKeys={checkedKeys}
                    expandedKeys={expandedKeys}
                    onExpand={(keys) => setExpandedKeys(keys)}
                    onCheck={readOnly ? undefined : handleCheck}
                    treeData={treeData}
                    height={530}
                  />
                ) : (
                  <Empty description="没有匹配的资源，请调整筛选条件" />
                )}
              </div>
            </section>

            <section className="permission-action-panel">
              <div className="permission-panel-heading">
                <div>
                  <Text strong>操作权限</Text>
                  <Text type="secondary">已选资源的动作细分</Text>
                </div>
                <Tag color="blue">{selectedResources.length} 项</Tag>
              </div>

              {selectedResources.length === 0 ? (
                <div className="permission-empty-state">
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先从左侧选择资源" />
                </div>
              ) : (
                <>
                  <div className="permission-bulk-editor">
                    <div className="permission-bulk-heading">
                      <Text strong>批量设置</Text>
                      <Text type="secondary">应用到当前全部已选资源</Text>
                    </div>
                    <Checkbox.Group
                      disabled={readOnly}
                      options={bulkActionOptions}
                      value={bulkActions}
                      onChange={(values) => setBulkActions(values as string[])}
                    />
                    <div className="permission-bulk-actions">
                      <Button
                        size="small"
                        type="primary"
                        ghost
                        onClick={applyBulkActionsToSelected}
                        disabled={readOnly}
                      >
                        应用到已选
                      </Button>
                      <Button size="small" onClick={clearActionsForSelected} disabled={readOnly}>
                        清空动作
                      </Button>
                    </div>
                  </div>

                  <div className="permission-action-list">
                    {selectedResources.map((resource) => {
                      const actions = actionConfigs[resource.id] || [];
                      return (
                        <article className="permission-resource-item" key={resource.id}>
                          <div className="permission-resource-item-heading">
                            <div className="permission-resource-item-name">
                              <Text strong>{resource.name}</Text>
                              <Tag color={RESOURCE_TYPE_COLOR[resource.type] || 'default'}>
                                {RESOURCE_TYPE_LABEL[resource.type] || resource.type}
                              </Tag>
                              <Text type="secondary">{resource.code}</Text>
                            </div>
                            <Text type="secondary">
                              {actions.length ? `已选 ${actions.length} 项` : '默认范围'}
                            </Text>
                          </div>
                          <Checkbox.Group
                            disabled={readOnly}
                            options={actionOptionsForResource(resource.id)}
                            value={actions}
                            onChange={(values) =>
                              updateActionConfig(resource.id, values as string[])
                            }
                          />
                        </article>
                      );
                    })}
                  </div>
                </>
              )}
            </section>
          </div>
        </section>

        <div className="role-page-actions">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/auth-center/roles')}>
            取消
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            onClick={handleSave}
            loading={putRoleResourcesMutation.isPending}
            disabled={readOnly}
          >
            保存并应用
          </Button>
        </div>
      </main>
    </PageContainer>
  );
};

export default RolePermissionConfig;
