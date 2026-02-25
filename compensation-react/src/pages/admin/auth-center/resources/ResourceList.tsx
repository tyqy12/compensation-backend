/**
 * 资源列表页（树形结构）
 *
 * 设计原则：
 * - 使用 Tree 组件展示资源树形结构
 * - 支持拖拽排序
 * - 简洁的操作按钮（编辑、删除）
 *  - 支持新建资源
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Tree,
  Button,
  Space,
  Tag,
  Typography,
  Modal,
  message,
  Spin,
  Tooltip,
  Dropdown,
  Popconfirm,
  Row,
  Col,
  Alert,
  Empty,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ExportOutlined,
  ImportOutlined,
  MoreOutlined,
  FolderOutlined,
  FileOutlined,
  ApiOutlined,
  PlayCircleOutlined,
  MenuOutlined,
  DragOutlined,
} from '@ant-design/icons';
import { useResourcesQuery, useDeleteResourceMutation } from '@services/queries/resources';
import type { SysResource } from '@types/api';

const { Text } = Typography;

// 资源类型图标映射
const resourceTypeIcons: Record<string, React.ReactNode> = {
  MENU: <MenuOutlined />,
  VIEW: <FileOutlined />,
  ACTION: <PlayCircleOutlined />,
  API: <ApiOutlined />,
};

// 资源类型颜色映射
const resourceTypeColors: Record<string, string> = {
  MENU: 'blue',
  VIEW: 'green',
  ACTION: 'orange',
  API: 'purple',
};

const ResourceList: React.FC = () => {
  const navigate = useNavigate();

  // 状态
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [dragMode, setDragMode] = useState(false);

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const deleteResourceMutation = useDeleteResourceMutation();

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
          <Space size={8}>
            {resourceTypeIcons[r.type] || <FileOutlined />}
            <Text strong style={{ fontSize: 14 }}>{r.name}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>({r.code})</Text>
            <Tag color={resourceTypeColors[r.type] || 'default'} style={{ margin: 0, fontSize: 10 }}>
              {r.type}
            </Tag>
            {r.status === 'disabled' && (
              <Tag color="red" style={{ margin: 0, fontSize: 10 }}>已禁用</Tag>
            )}
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

  // 删除资源
  const handleDelete = (resourceId: number) => {
    const resource = resourceMap.get(resourceId);
    if (!resource) return;

    Modal.confirm({
      title: '确认删除',
      content: `确定要删除资源 "${resource.name}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteResourceMutation.mutateAsync(resourceId);
          message.success('删除成功');
        } catch (error: any) {
          message.error(error?.message || '删除失败');
        }
      },
    });
  };

  // 获取操作菜单
  const getActionMenu = (resourceId: number) => ({
    items: [
      {
        key: 'edit',
        icon: <EditOutlined />,
        label: '编辑',
        onClick: () => navigate(`/admin/auth-center/resources/${resourceId}/edit`),
      },
      {
        key: 'add-child',
        icon: <PlusOutlined />,
        label: '添加子资源',
        onClick: () => navigate(`/admin/auth-center/resources/create?parentId=${resourceId}`),
      },
      {
        type: 'divider',
      },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        onClick: () => handleDelete(resourceId),
      },
    ],
  });

  // 渲染树节点标题（包含操作按钮）
  const renderTreeNode = (node: DataNode) => {
    const resourceId = node.key as number;

    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          width: '100%',
          paddingRight: 16,
        }}
      >
        <span>{node.title}</span>
        {!dragMode && (
          <Space size={4} onClick={(e) => e.stopPropagation()}>
            <Tooltip title="编辑">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                onClick={() => navigate(`/admin/auth-center/resources/${resourceId}/edit`)}
              />
            </Tooltip>
            <Tooltip title="添加子资源">
              <Button
                type="text"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => navigate(`/admin/auth-center/resources/create?parentId=${resourceId}`)}
              />
            </Tooltip>
            <Dropdown menu={getActionMenu(resourceId)} trigger={['click']}>
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          </Space>
        )}
      </div>
    );
  };

  // 递归渲染树节点
  const renderTreeData = (data: DataNode[]): DataNode[] => {
    return data.map((node) => ({
      ...node,
      title: renderTreeNode(node),
      children: node.children ? renderTreeData(node.children) : undefined,
    }));
  };

  // 展开/收起所有节点
  const handleExpandAll = () => {
    if (expandedKeys.length > 0) {
      setExpandedKeys([]);
    } else {
      const allKeys = resourcesQuery.data?.map((r: SysResource) => r.id) || [];
      setExpandedKeys(allKeys);
    }
  };

  if (resourcesQuery.isLoading) {
    return (
      <PageContainer>
        <Card>
          <Spin tip="加载中..." />
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer
      header={{
        title: '资源管理',
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { title: '资源管理' },
          ],
        },
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => resourcesQuery.refetch()}
          loading={resourcesQuery.isLoading}
        >
          刷新
        </Button>,
        <Button key="export" icon={<ExportOutlined />}>
          导出
        </Button>,
        <Button key="import" icon={<ImportOutlined />}>
          导入
        </Button>,
      ]}
    >
      <Row gutter={16}>
        <Col span={24}>
          <Card
            title={
              <Space>
                <FolderOutlined />
                <Text strong>资源树</Text>
                <Tag color="blue">{resourcesQuery.data?.length || 0} 项</Tag>
              </Space>
            }
            extra={
              <Space>
                <Button
                  type={dragMode ? 'primary' : 'default'}
                  icon={<DragOutlined />}
                  onClick={() => setDragMode(!dragMode)}
                >
                  {dragMode ? '完成排序' : '排序模式'}
                </Button>
                <Button onClick={handleExpandAll}>
                  {expandedKeys.length > 0 ? '收起全部' : '展开全部'}
                </Button>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => navigate('/admin/auth-center/resources/create')}
                >
                  新建资源
                </Button>
              </Space>
            }
          >
            {treeData.length === 0 ? (
              <Empty description="暂无资源">
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => navigate('/admin/auth-center/resources/create')}
                >
                  新建资源
                </Button>
              </Empty>
            ) : (
              <div style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 16 }}>
                <Tree
                  showLine
                  showIcon={false}
                  defaultExpandAll
                  expandedKeys={expandedKeys}
                  onExpand={setExpandedKeys}
                  selectedKeys={selectedKeys}
                  onSelect={setSelectedKeys}
                  treeData={renderTreeData(treeData)}
                  draggable={dragMode}
                  onDrop={(info) => {
                    // TODO: 实现拖拽排序逻辑
                    console.log('Drop:', info);
                  }}
                />
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 提示信息 */}
      <Card style={{ marginTop: 16 }} size="small">
        <Alert
          message="资源类型说明"
          description={
            <Space direction="vertical">
              <Space>
                <Tag color="blue">MENU</Tag>
                <Text>菜单资源 - 系统导航菜单</Text>
              </Space>
              <Space>
                <Tag color="green">VIEW</Tag>
                <Text>页面资源 - 页面访问权限</Text>
              </Space>
              <Space>
                <Tag color="orange">ACTION</Tag>
                <Text>操作资源 - 按钮级权限</Text>
              </Space>
              <Space>
                <Tag color="purple">API</Tag>
                <Text>API资源 - 接口访问权限</Text>
              </Space>
            </Space>
          }
          type="info"
          showIcon
        />
      </Card>
    </PageContainer>
  );
};

export default ResourceList;
