/**
 * 用户权限查看页（只读）
 *
 * 设计目标：
 * - 支持按类型/来源/关键词快速筛选
 * - 左侧树形结构、右侧明细列表
 * - 明确区分继承权限与个性化权限
 */

import React, { useMemo, useState, useEffect } from 'react';
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
  Input,
  Select,
  Collapse,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  ExportOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
  FileTextOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useResourcesQuery } from '@services/queries/resources';
import {
  useUserAggregateSearchQuery,
  useUserAggregateResourcesQuery,
  useUserResourcesQuery,
} from '@services/queries/adminAuth';
import { useRolesQuery } from '@services/queries/roles';
import type { SysResource } from '@types/api';

const { Text } = Typography;

type ResourceTypeOption = 'ALL' | 'MENU' | 'VIEW' | 'ACTION' | 'API';
type SourceType = 'ALL' | 'PERSONALIZED' | 'INHERITED';

const RESOURCE_TYPE_OPTIONS: Array<{ label: string; value: ResourceTypeOption }> = [
  { label: '全部类型', value: 'ALL' },
  { label: '菜单', value: 'MENU' },
  { label: '页面', value: 'VIEW' },
  { label: '操作', value: 'ACTION' },
  { label: 'API', value: 'API' },
];

const SOURCE_OPTIONS: Array<{ label: string; value: SourceType }> = [
  { label: '全部来源', value: 'ALL' },
  { label: '个性化', value: 'PERSONALIZED' },
  { label: '角色继承', value: 'INHERITED' },
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

const SOURCE_COLOR: Record<Exclude<SourceType, 'ALL'>, string> = {
  PERSONALIZED: 'gold',
  INHERITED: 'cyan',
};

const SOURCE_LABEL: Record<Exclude<SourceType, 'ALL'>, string> = {
  PERSONALIZED: '个性化',
  INHERITED: '继承',
};

// 操作权限中文映射
const ACTION_LABELS: Record<string, string> = {
  read: '查看',
  write: '编辑',
  delete: '删除',
  admin: '管理',
  export: '导出',
  import: '导入',
};

interface PermissionDetailItem {
  id: number;
  source: Exclude<SourceType, 'ALL'>;
  resource: SysResource;
  actions: string[];
}

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

const UserPermissionView: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const userIdNum = userId ? parseInt(userId) : 0;

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const userQuery = useUserAggregateSearchQuery({ q: '', page: 1, size: 100 });
  const userAggregateResourcesQuery = useUserAggregateResourcesQuery(userIdNum);
  const userPersonalizedResourcesQuery = useUserResourcesQuery(userIdNum);
  const rolesQuery = useRolesQuery({});

  const [resourceKeyword, setResourceKeyword] = useState('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState<ResourceTypeOption>('ALL');
  const [sourceFilter, setSourceFilter] = useState<SourceType>('ALL');
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);

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

  const personalizedSet = useMemo(() => {
    const resources = userPersonalizedResourcesQuery.data || [];
    return new Set(resources.map((r) => r.resourceId).filter((id): id is number => id != null));
  }, [userPersonalizedResourcesQuery.data]);

  // 聚合权限明细（含来源）
  const permissionDetails = useMemo(() => {
    const resources = userAggregateResourcesQuery.data || [];
    const details: PermissionDetailItem[] = [];
    resources.forEach((item) => {
      const resourceId = item.resourceId;
      const resource = resourceMap.get(resourceId);
      if (!resource) return;

      const source: Exclude<SourceType, 'ALL'> = personalizedSet.has(resourceId)
        ? 'PERSONALIZED'
        : 'INHERITED';
      const actions = Array.isArray(item.actions)
        ? item.actions
        : item.actionsJson
          ? (() => {
              try {
                const parsed = JSON.parse(item.actionsJson);
                return Array.isArray(parsed) ? parsed : [];
              } catch {
                return [];
              }
            })()
          : [];

      details.push({
        id: resourceId,
        source,
        resource,
        actions,
      });
    });
    return details;
  }, [userAggregateResourcesQuery.data, resourceMap, personalizedSet]);

  const filteredPermissionDetails = useMemo(() => {
    const keyword = resourceKeyword.trim().toLowerCase();
    return permissionDetails.filter((item) => {
      if (resourceTypeFilter !== 'ALL' && item.resource.type !== resourceTypeFilter) return false;
      if (sourceFilter !== 'ALL' && item.source !== sourceFilter) return false;
      if (!keyword) return true;
      return (
        (item.resource.name || '').toLowerCase().includes(keyword) ||
        (item.resource.code || '').toLowerCase().includes(keyword) ||
        (item.resource.path || '').toLowerCase().includes(keyword)
      );
    });
  }, [permissionDetails, resourceKeyword, resourceTypeFilter, sourceFilter]);

  const filteredPermissionIdSet = useMemo(
    () => new Set(filteredPermissionDetails.map((item) => item.id)),
    [filteredPermissionDetails],
  );

  // 构建权限树（仅展示符合筛选条件的权限）
  const permissionTreeData: DataNode[] = useMemo(() => {
    if (filteredPermissionDetails.length === 0) return [];

    const nodeMap = new Map<number, DataNode>();
    filteredPermissionDetails.forEach((item) => {
      nodeMap.set(item.id, {
        key: item.id,
        title: (
          <Space size={4} wrap>
            <Text strong style={{ fontSize: 13 }}>
              {item.resource.name}
            </Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              ({item.resource.code})
            </Text>
            <Tag
              color={RESOURCE_TYPE_COLOR[item.resource.type] || 'default'}
              style={{ margin: 0, fontSize: 10 }}
            >
              {RESOURCE_TYPE_LABEL[item.resource.type] || item.resource.type}
            </Tag>
            <Tag color={SOURCE_COLOR[item.source]} style={{ margin: 0, fontSize: 10 }}>
              {SOURCE_LABEL[item.source]}
            </Tag>
            {item.actions.length > 0 && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                动作 {item.actions.length}
              </Text>
            )}
          </Space>
        ),
        children: [],
      });
    });

    const roots: DataNode[] = [];
    filteredPermissionDetails.forEach((item) => {
      const currentNode = nodeMap.get(item.id)!;
      const parentId = item.resource.parentId ?? null;
      if (parentId != null && filteredPermissionIdSet.has(parentId) && nodeMap.has(parentId)) {
        nodeMap.get(parentId)!.children!.push(currentNode);
      } else {
        roots.push(currentNode);
      }
    });

    return roots;
  }, [filteredPermissionDetails, filteredPermissionIdSet]);

  const allTreeKeys = useMemo(() => collectTreeKeys(permissionTreeData), [permissionTreeData]);

  useEffect(() => {
    setExpandedKeys(allTreeKeys);
  }, [allTreeKeys]);

  // 权限统计
  const permissionStats = useMemo(() => {
    const menuCount = permissionDetails.filter((item) => item.resource.type === 'MENU').length;
    const apiCount = permissionDetails.filter((item) => item.resource.type === 'API').length;
    const actionCount = permissionDetails.filter((item) => item.resource.type === 'ACTION').length;
    const personalizedCount = permissionDetails.filter(
      (item) => item.source === 'PERSONALIZED',
    ).length;
    const inheritedCount = permissionDetails.filter((item) => item.source === 'INHERITED').length;
    return {
      menuCount,
      apiCount,
      actionCount,
      personalizedCount,
      inheritedCount,
      total: permissionDetails.length,
    };
  }, [permissionDetails]);

  // 导出权限报告
  const handleExport = () => {
    const report = {
      user: currentUser,
      permissions: {
        resources: permissionDetails.map((item) => ({
          id: item.id,
          name: item.resource.name,
          code: item.resource.code,
          type: item.resource.type,
          source: item.source,
          actions: item.actions,
        })),
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

  if (
    resourcesQuery.isLoading ||
    userQuery.isLoading ||
    userAggregateResourcesQuery.isLoading ||
    userPersonalizedResourcesQuery.isLoading
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
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => {
            userAggregateResourcesQuery.refetch();
            userPersonalizedResourcesQuery.refetch();
          }}
          loading={
            userAggregateResourcesQuery.isFetching || userPersonalizedResourcesQuery.isFetching
          }
        >
          刷新
        </Button>,
        <Button key="export" icon={<ExportOutlined />} onClick={handleExport}>
          导出报告
        </Button>,
      ]}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {/* 用户信息卡片 */}
        <Card
          title={
            <Space>
              <UserOutlined />
              <Text strong>基本信息</Text>
            </Space>
          }
          size="small"
        >
          <Descriptions column={2} size="small">
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
          size="small"
        >
          {currentUser.roles ? (
            <Space size={[0, 8]} wrap>
              {currentUser.roles
                .split(',')
                .filter(Boolean)
                .map((role: string, index: number) => (
                  <Tag key={index} color="blue">
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
          size="small"
        >
          <Row gutter={16}>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="菜单权限" value={permissionStats.menuCount} suffix="项" />
            </Col>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="API权限" value={permissionStats.apiCount} suffix="项" />
            </Col>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="操作权限" value={permissionStats.actionCount} suffix="项" />
            </Col>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="个性化" value={permissionStats.personalizedCount} suffix="项" />
            </Col>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="继承" value={permissionStats.inheritedCount} suffix="项" />
            </Col>
            <Col xs={12} sm={8} lg={4}>
              <Statistic title="总计" value={permissionStats.total} suffix="项" />
            </Col>
          </Row>
        </Card>

        {/* 权限详情 */}
        <Card
          title={
            <Space>
              <FileTextOutlined />
              <Text strong>权限详情</Text>
              <Badge
                count={filteredPermissionDetails.length}
                style={{ backgroundColor: '#1890ff' }}
              />
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
            <Select<SourceType>
              value={sourceFilter}
              style={{ width: 160 }}
              options={SOURCE_OPTIONS}
              onChange={setSourceFilter}
            />
            <Button
              size="small"
              onClick={() => setExpandedKeys(allTreeKeys)}
              disabled={permissionTreeData.length === 0}
            >
              展开全部
            </Button>
            <Button
              size="small"
              onClick={() => setExpandedKeys([])}
              disabled={permissionTreeData.length === 0}
            >
              折叠全部
            </Button>
          </Space>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <div
                style={{
                  border: '1px solid #f0f0f0',
                  borderRadius: 4,
                  padding: 12,
                  minHeight: 460,
                }}
              >
                {permissionTreeData.length > 0 ? (
                  <Tree
                    showLine
                    selectable={false}
                    expandedKeys={expandedKeys}
                    onExpand={(keys) => setExpandedKeys(keys)}
                    treeData={permissionTreeData}
                    height={440}
                  />
                ) : (
                  <Empty description="暂无匹配的权限项" />
                )}
              </div>
            </Col>
            <Col xs={24} lg={10}>
              {filteredPermissionDetails.length > 0 ? (
                <div style={{ maxHeight: 460, overflowY: 'auto', paddingRight: 4 }}>
                  <Collapse
                    accordion
                    items={filteredPermissionDetails
                      .sort((a, b) => (a.resource.orderNum ?? 0) - (b.resource.orderNum ?? 0))
                      .map((item) => ({
                        key: String(item.id),
                        label: (
                          <Space wrap>
                            <Text strong>{item.resource.name}</Text>
                            <Tag color={RESOURCE_TYPE_COLOR[item.resource.type] || 'default'}>
                              {RESOURCE_TYPE_LABEL[item.resource.type] || item.resource.type}
                            </Tag>
                            <Tag color={SOURCE_COLOR[item.source]}>{SOURCE_LABEL[item.source]}</Tag>
                          </Space>
                        ),
                        children: (
                          <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                            <Text type="secondary">{item.resource.code}</Text>
                            <Text type="secondary">{item.resource.path || '-'}</Text>
                            {item.actions.length > 0 ? (
                              <Space wrap>
                                {item.actions.map((action) => (
                                  <Tag key={action} color="blue">
                                    {ACTION_LABELS[action] || action}
                                  </Tag>
                                ))}
                              </Space>
                            ) : (
                              <Text type="secondary">未配置动作细项</Text>
                            )}
                          </Space>
                        ),
                      }))}
                  />
                </div>
              ) : (
                <Empty description="暂无匹配的权限明细" />
              )}
            </Col>
          </Row>

          <Divider />

          <Alert
            title="权限说明"
            description="角色权限来自角色继承；个性化权限为对该用户单独配置；实际权限为两者并集。"
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
