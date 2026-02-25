import React, { useEffect, useState, useMemo } from 'react';
import { Drawer, Button, Tree, Checkbox, Space, message, Spin, Divider, Alert, Modal } from 'antd';
import type { CheckedInfo } from 'antd/es/tree/Tree';
import {
  SaveOutlined,
  ReloadOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useRoleResourcesQuery, useAssignRoleResourcesMutation, useRevokeRoleResourcesMutation } from '@services/queries/roles';
import { useResourcesQuery } from '@services/queries/resources';
import type { SysResource, ResourceType, RoleResourceBrief } from '@types/api';

// 操作权限列表
const ACTION_OPTIONS = ['read', 'write', 'delete', 'execute', 'export', 'import'];

// 操作权限中文映射
const ACTION_LABELS: Record<string, string> = {
  read: '读取',
  write: '写入',
  delete: '删除',
  execute: '执行',
  export: '导出',
  import: '导入',
};

interface RoleResourceAssignDrawerProps {
  visible: boolean;
  roleId: number | null;
  onClose: () => void;
}

interface ResourceNode extends SysResource {
  children?: ResourceNode[];
  checked?: boolean;
  halfChecked?: boolean;
  actions?: string[];
}

/**
 * 构建资源树
 */
const buildResourceTree = (
  resources: SysResource[],
  roleResources: RoleResourceBrief[],
): ResourceNode[] => {
  // 创建资源ID到角色的映射
  const resourceIdToActions = new Map<number, string[]>();
  roleResources.forEach((r) => {
    resourceIdToActions.set(r.id, r.actions || []);
  });

  // 构建树形结构
  const rootNodes: ResourceNode[] = [];
  const nodeMap = new Map<number, ResourceNode>();

  // 第一遍：创建所有节点
  resources.forEach((res) => {
    const node: ResourceNode = {
      ...res,
      checked: resourceIdToActions.has(res.id),
      actions: resourceIdToActions.get(res.id) || [],
    };
    nodeMap.set(res.id, node);
  });

  // 第二遍：建立父子关系
  resources.forEach((res) => {
    const node = nodeMap.get(res.id)!;
    if (res.parentId) {
      const parent = nodeMap.get(res.parentId);
      if (parent) {
        parent.children = parent.children || [];
        parent.children.push(node);
      } else {
        // 父节点不存在，作为根节点
        rootNodes.push(node);
      }
    } else {
      rootNodes.push(node);
    }
  });

  // 递归排序
  const sortNodes = (nodes: ResourceNode[]): ResourceNode[] => {
    return nodes
      .sort((a, b) => (a.orderNum || 0) - (b.orderNum || 0))
      .map((node) => ({
        ...node,
        children: node.children ? sortNodes(node.children) : undefined,
      }));
  };

  return sortNodes(rootNodes);
};

/**
 * 获取所有叶子节点的ID
 */
const getAllLeafNodeIds = (nodes: ResourceNode[]): number[] => {
  const ids: number[] = [];
  const traverse = (nodeList: ResourceNode[]) => {
    nodeList.forEach((node) => {
      if (!node.children || node.children.length === 0) {
        ids.push(node.id);
      } else {
        traverse(node.children);
      }
    });
  };
  traverse(nodes);
  return ids;
};

const RoleResourceAssignDrawer: React.FC<RoleResourceAssignDrawerProps> = ({
  visible,
  roleId,
  onClose,
}) => {
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [resourceActions, setResourceActions] = useState<Record<number, string[]>>({});

  // 获取角色资源权限
  const { data: roleResources, isLoading: loadingRoleResources } = useRoleResourcesQuery(
    roleId || 0,
  );

  // 获取所有资源（用于构建树）
  const { data: allResources, isLoading: loadingResources } = useResourcesQuery({});

  // 分配资源权限突变
  const assignMutation = useAssignRoleResourcesMutation();
  // 撤销资源权限突变
  const revokeMutation = useRevokeRoleResourcesMutation();

  // 构建资源树
  const treeData = useMemo(() => {
    if (!allResources) return [];
    return buildResourceTree(allResources, roleResources || []);
  }, [allResources, roleResources]);

  // 加载完成后展开所有节点
  useEffect(() => {
    if (treeData.length > 0) {
      const allKeys = treeData.map((node) => node.id.toString());
      setExpandedKeys(allKeys);
    }
  }, [treeData]);

  // 处理树节点勾选
  const handleCheck = (checkedInfo: CheckedInfo, node: ResourceNode) => {
    const { checked } = checkedInfo;
    const nodeId = node.id;

    // 更新选中状态
    if (checked) {
      if (!selectedKeys.includes(nodeId)) {
        setSelectedKeys([...selectedKeys, nodeId]);
      }
    } else {
      setSelectedKeys(selectedKeys.filter((key) => key !== nodeId));
    }

    // 更新操作权限
    if (checked) {
      setResourceActions((prev) => ({
        ...prev,
        [nodeId]: node.actions?.length ? node.actions : ['read'], // 默认选中读取权限
      }));
    } else {
      const { [nodeId]: _, ...rest } = resourceActions;
      setResourceActions(rest);
    }
  };

  // 处理全选/取消全选
  const handleCheckAll = (checked: boolean) => {
    if (checked) {
      const leafIds = getAllLeafNodeIds(treeData);
      setSelectedKeys(leafIds.map((id) => id));

      // 默认所有权限
      const actions: Record<number, string[]> = {};
      leafIds.forEach((id) => {
        actions[id] = ['read', 'write', 'delete'];
      });
      setResourceActions(actions);
    } else {
      setSelectedKeys([]);
      setResourceActions({});
    }
  };

  // 展开/折叠所有
  const handleExpandAll = (expanded: boolean) => {
    if (expanded) {
      const allKeys = treeData.map((node) => node.id.toString());
      setExpandedKeys(allKeys);
    } else {
      setExpandedKeys([]);
    }
  };

  // 保存分配结果
  const handleSave = async () => {
    if (!roleId) return;

    // 构建资源分配列表
    const resources = selectedKeys.map((key) => {
      const id = Number(key);
      return {
        resourceId: id,
        actions: resourceActions[id]?.length ? resourceActions[id] : ['*'],
      };
    });

    try {
      await assignMutation.mutateAsync({
        id: roleId,
        request: {
          resources,
          replaceExisting: true,
        },
      });
      message.success('权限分配成功');
      onClose();
    } catch (e: any) {
      message.error(e?.message || '权限分配失败');
    }
  };

  // 清空所有权限
  const handleClearAll = () => {
    Modal.confirm({
      title: '确认清空',
      content: '确定要清空该角色的所有资源权限吗？',
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        try {
          await revokeMutation.mutateAsync({ id: roleId!, resourceIds: undefined });
          message.success('已清空所有权限');
          setSelectedKeys([]);
          setResourceActions({});
        } catch (e: any) {
          message.error(e?.message || '操作失败');
        }
      },
    });
  };

  // 渲染操作权限选择
  const renderActionCheckbox = (node: ResourceNode) => {
    const nodeId = node.id;
    const isChecked = selectedKeys.includes(nodeId);
    const currentActions = resourceActions[nodeId] || [];

    if (!isChecked) return null;

    return (
      <div style={{ marginLeft: 24, marginTop: 8, marginBottom: 8 }}>
        <span style={{ color: '#888', marginRight: 8 }}>操作权限：</span>
        <Checkbox.Group
          value={currentActions}
          onChange={(values) => {
            setResourceActions((prev) => ({
              ...prev,
              [nodeId]: values as string[],
            }));
          }}
        >
          <Space size={16}>
            {ACTION_OPTIONS.map((action) => (
              <Checkbox key={action} value={action}>
                {ACTION_LABELS[action]}
              </Checkbox>
            ))}
          </Space>
        </Checkbox.Group>
      </div>
    );
  };

  // 渲染树节点
  const renderTreeNode = (node: ResourceNode) => {
    const isChecked = selectedKeys.includes(node.id);

    return (
      <div key={node.id}>
        <span style={{ display: 'inline-block', width: '100%' }}>
          <Checkbox
            checked={isChecked}
            onChange={(e) => handleCheck({ checked: e.target.checked, node }, node)}
          >
            <span style={{ fontWeight: node.type === 'MENU' ? 500 : 400 }}>
              {node.name}
            </span>
            <span style={{ color: '#999', marginLeft: 8, fontSize: 12 }}>
              ({node.type})
            </span>
          </Checkbox>
        </span>
        {renderActionCheckbox(node)}
        {node.children && node.children.length > 0 && (
          <div style={{ marginLeft: 20 }}>
            {node.children.map(renderTreeNode)}
          </div>
        )}
      </div>
    );
  };

  const loading = loadingRoleResources || loadingResources;

  return (
    <Drawer
      title="角色权限分配"
      open={visible}
      onClose={onClose}
      width={640}
      footer={
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button onClick={handleClearAll} danger>
              清空权限
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={assignMutation.isPending}
              onClick={handleSave}
            >
              保存配置
            </Button>
          </Space>
        </div>
      }
      extra={
        <Space>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => {
              setSelectedKeys([]);
              setResourceActions({});
            }}
          >
            重置
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {roleId && (
          <Alert
            message="权限说明"
            description={
              <div>
                <p>1. 勾选资源表示授予该角色访问权限</p>
                <p>2. 选中资源后可配置具体操作权限（读取、写入、删除等）</p>
                <p>3. 保存后，拥有该角色的用户将立即获得/失去相应权限</p>
              </div>
            }
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        {/* 批量操作 */}
        <div style={{ marginBottom: 16 }}>
          <Space>
            <Button size="small" onClick={() => handleCheckAll(true)}>
              全选
            </Button>
            <Button size="small" onClick={() => handleCheckAll(false)}>
              取消全选
            </Button>
            <Divider type="vertical" />
            <Button size="small" onClick={() => handleExpandAll(expandedKeys.length === 0)}>
              {expandedKeys.length === 0 ? '展开全部' : '折叠全部'}
            </Button>
          </Space>
          <span style={{ marginLeft: 16, color: '#666' }}>
            已选择 {selectedKeys.length} 个资源
          </span>
        </div>

        {/* 资源树 */}
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 16, maxHeight: 500, overflow: 'auto' }}>
          {treeData.length === 0 ? (
            <div style={{ textAlign: 'center', color: '#999', padding: 40 }}>
              暂无可用资源，请先在资源管理中添加资源
            </div>
          ) : (
            treeData.map(renderTreeNode)
          )}
        </div>
      </Spin>
    </Drawer>
  );
};

export default RoleResourceAssignDrawer;
