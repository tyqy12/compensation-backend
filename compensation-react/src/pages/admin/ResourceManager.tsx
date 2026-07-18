import React, { useMemo, useRef, useState } from 'react';
import {
  Button, Space, Tag, Modal, Form, Input, Select, TreeSelect, InputNumber,
  App as AntdApp, Upload, Tabs, Tree, Card, Descriptions, Dropdown, Tooltip, Badge
} from 'antd';
import {
  PageContainer, ProTable, type ProColumns, type ActionType
} from '@ant-design/pro-components';
import {
  DownloadOutlined, UploadOutlined, PlusOutlined, DeleteOutlined,
  EditOutlined, ArrowUpOutlined, ArrowDownOutlined, ReloadOutlined,
  MoreOutlined, SearchOutlined, DragOutlined, InfoCircleOutlined
} from '@ant-design/icons';
import type { SysResource } from '@/types/api';
import { buildResourceTree } from '@utils/permissions';
import {
  useResourcesV2Query,
  useCreateResourceV2Mutation,
  useUpdateResourceV2Mutation,
  useDeleteResourceV2Mutation,
  useSortResourcesV2Mutation,
  useImportResourcesV2Mutation,
} from '@services/queries/resourcesV2';
import { useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import { broadcastMenuRefresh } from '@hooks/useMenuRefresh';

type ResourceType = 'MENU' | 'VIEW' | 'ACTION' | 'API';

const typeOptions: { label: string; value: ResourceType; color: string }[] = [
  { label: '菜单', value: 'MENU', color: 'blue' },
  { label: '页面', value: 'VIEW', color: 'green' },
  { label: '动作', value: 'ACTION', color: 'purple' },
  { label: '接口', value: 'API', color: 'orange' },
];

const statusOptions = [
  { label: '启用', value: 'enabled' },
  { label: '禁用', value: 'disabled' },
];

const ResourceManager: React.FC = () => {
  const [filterType, setFilterType] = useState<ResourceType | undefined>(undefined);
  const [searchText, setSearchText] = useState('');
  const { message, modal } = AntdApp.useApp();
  const actionRef = useRef<ActionType>();

  const listQuery = useResourcesV2Query(filterType);
  const qc = useQueryClient();
  const invalidatePerms = () => {
    qc.invalidateQueries({ queryKey: ['me', 'resources'] });
    qc.invalidateQueries({ queryKey: ['me', 'actions'] });
  };
  const createMut = useCreateResourceV2Mutation();
  const updateMut = useUpdateResourceV2Mutation();
  const deleteMut = useDeleteResourceV2Mutation();
  const sortMut = useSortResourcesV2Mutation();
  const importMut = useImportResourcesV2Mutation();

  const [form] = Form.useForm();
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<SysResource | null>(null);

  // 构建树数据
  const treeData = useMemo(() => {
    const all = listQuery.data || [];
    // 搜索过滤
    if (searchText.trim()) {
      const keyword = searchText.toLowerCase();
      const filtered = all.filter(r =>
        r.name?.toLowerCase().includes(keyword) ||
        r.code?.toLowerCase().includes(keyword)
      );
      return buildResourceTree(filtered, {
        includeDisabled: true,
        includeHidden: true,
        includeRouteParams: true,
      });
    }
    return buildResourceTree(all, {
      includeDisabled: true,
      includeHidden: true,
      includeRouteParams: true,
    });
  }, [listQuery.data, searchText]);

  const flatList = useMemo(() => {
    const collect = (nodes: any[]): any[] => {
      const result: any[] = [];
      nodes.forEach(n => {
        result.push(n);
        if (n.children?.length) result.push(...collect(n.children));
      });
      return result;
    };
    return collect(treeData);
  }, [treeData]);

  const parentTree = useMemo(() => {
    const map = (nodes: any[]): any[] => nodes.map((n) => ({
      value: n.id,
      label: `${n.name}`,
      children: n.children ? map(n.children) : undefined
    }));
    return map(treeData);
  }, [treeData]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ type: 'MENU', status: 'enabled', orderNum: 0 });
    setEditOpen(true);
  };

  const openEdit = (record: SysResource) => {
    setEditing(record);
    form.resetFields();
    const meta = (record as any).meta || {};
    form.setFieldsValue({
      ...record,
      keepAlive: meta.keepAlive ? true : undefined,
      affix: meta.affix ? true : undefined,
      hidden: typeof meta.hidden === 'boolean' ? meta.hidden : undefined,
      method: meta.method,
    });
    setEditOpen(true);
  };

  const handleSubmit = async () => {
    const v = await form.validateFields();
    const meta: Record<string, any> = {};
    if (v.keepAlive) meta.keepAlive = true;
    if (v.affix) meta.affix = true;
    if (typeof v.hidden === 'boolean') meta.hidden = v.hidden;
    if (v.method) meta.method = v.method;

    const payload = {
      type: v.type,
      code: v.code,
      name: v.name,
      path: v.path || null,
      component: v.component || null,
      icon: v.icon || null,
      parentId: v.parentId ?? null,
      orderNum: v.orderNum ?? 0,
      meta,
      status: v.status,
    } as any;

    try {
      if (editing) {
        await updateMut.mutateAsync({ id: editing.id, payload });
        message.success('更新成功');
      } else {
        await createMut.mutateAsync(payload);
        message.success('创建成功');
      }
      setEditOpen(false);
      actionRef.current?.reload();
      invalidatePerms();
      broadcastMenuRefresh();
    } catch (e: any) {
      message.error(e?.message || '保存失败');
    }
  };

  const handleDelete = (record: SysResource) => {
    modal.confirm({
      title: '确认删除',
      content: `确定要删除资源「${record.name}」吗？删除后不可恢复。`,
      onOk: async () => {
        try {
          await deleteMut.mutateAsync(record.id);
          message.success('删除成功');
          actionRef.current?.reload();
          invalidatePerms();
          broadcastMenuRefresh();
        } catch (e: any) {
          message.error(e?.message || '删除失败');
        }
      },
      okText: '确认删除',
      cancelText: '取消',
    });
  };

  const swapOrderInSiblings = async (record: SysResource, dir: 'up' | 'down') => {
    const siblings = flatList
      .filter((r) => (r.parentId ?? null) === (record.parentId ?? null))
      .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
    const idx = siblings.findIndex((s) => s.id === record.id);
    const targetIdx = dir === 'up' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= siblings.length) return;
    const a = siblings[idx];
    const b = siblings[targetIdx];
    const body = [
      { id: a.id, orderNum: b.orderNum ?? 0 },
      { id: b.id, orderNum: a.orderNum ?? 0 },
    ];
    try {
      await sortMut.mutateAsync(body);
      message.success('排序已更新');
      actionRef.current?.reload();
      invalidatePerms();
      broadcastMenuRefresh();
    } catch (e: any) {
      message.error(e?.message || '排序失败');
    }
  };

  // 获取类型的颜色
  const getTypeColor = (type: string) => {
    return typeOptions.find(t => t.value === type)?.color || 'default';
  };

  // 表格列配置
  const columns: ProColumns<SysResource>[] = [
    {
      title: '资源名称',
      dataIndex: 'name',
      width: 200,
      render: (_, record) => (
        <Space>
          <span>{record.name}</span>
          {(record as any)._children?.length > 0 && (
            <Badge count={(record as any)._children.length} style={{ backgroundColor: '#f0f0f0' }} />
          )}
        </Space>
      ),
    },
    {
      title: '资源编码',
      dataIndex: 'code',
      width: 220,
      copyable: true,
      render: (_, record) => (
        <code style={{ color: '#666', fontSize: 12 }}>{record.code}</code>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (_, record) => (
        <Tag color={getTypeColor(record.type || '')} style={{ marginRight: 0 }}>
          {record.type}
        </Tag>
      ),
    },
    {
      title: '路径/接口',
      dataIndex: 'path',
      width: 200,
      ellipsis: true,
      render: (_, record) => record.path ? (
        <code style={{ color: '#13c2c2', fontSize: 12 }}>{record.path}</code>
      ) : '-',
    },
    {
      title: '组件',
      dataIndex: 'component',
      width: 160,
      ellipsis: true,
      render: (_, record) => record.component || '-',
    },
    {
      title: '排序',
      dataIndex: 'orderNum',
      width: 70,
      align: 'center',
      render: (_, record) => (
        <Tag color="default" style={{ marginRight: 0 }}>{record.orderNum ?? 0}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      align: 'center',
      render: (_, record) => (
        <Badge
          status={record.status === 'enabled' ? 'success' : 'default'}
          text={record.status === 'enabled' ? '启用' : '禁用'}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="上移">
            <Button
              type="text"
              size="small"
              icon={<ArrowUpOutlined />}
              onClick={() => swapOrderInSiblings(record, 'up')}
            />
          </Tooltip>
          <Tooltip title="下移">
            <Button
              type="text"
              size="small"
              icon={<ArrowDownOutlined />}
              onClick={() => swapOrderInSiblings(record, 'down')}
            />
          </Tooltip>
          <Dropdown
            menu={{
              items: [
                {
                  key: 'edit',
                  label: '编辑',
                  icon: <EditOutlined />,
                  onClick: () => openEdit(record),
                },
                {
                  key: 'delete',
                  label: '删除',
                  icon: <DeleteOutlined />,
                  danger: true,
                  onClick: () => handleDelete(record),
                },
              ],
            }}
          >
            <Button type="text" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      ),
    },
  ];

  // 树数据转换
  const dataSource = useMemo(() => {
    const mapNode = (n: any): any => {
      return {
        ...n,
        key: n.id,
        children: n.children ? n.children.map(mapNode) : undefined,
      };
    };
    return treeData.map(mapNode);
  }, [treeData]);

  // 导入处理
  const beforeUpload = async (file: File) => {
    try {
      const text = await file.text();
      const json = JSON.parse(text);
      const result = await importMut.mutateAsync(json);
      const { created, updated, errors } = result;
      let msg = `导入成功: 新增 ${created}, 更新 ${updated}`;
      if (errors?.length) {
        msg += `, 失败 ${errors.length} 条`;
        console.warn('导入错误:', errors);
      }
      message.success(msg);
      actionRef.current?.reload();
      invalidatePerms();
      broadcastMenuRefresh();
    } catch (e: any) {
      message.error(e?.message || '导入失败');
    }
    return false;
  };

  // 拖拽树数据
  const treeDraggableData = useMemo(() => {
    const toTreeNode = (n: any): any => {
      return {
        key: String(n.id),
        title: (
          <Space>
            <Tag color={getTypeColor(n.type || '')} style={{ marginRight: 0 }}>
              {n.type}
            </Tag>
            <span style={{ fontWeight: 500 }}>{n.name}</span>
            <code style={{ color: '#999', fontSize: 11 }}>{n.code}</code>
          </Space>
        ),
        children: (n.children || []).map(toTreeNode),
      };
    };
    return dataSource.map(toTreeNode);
  }, [dataSource]);

  // 拖拽处理
  const onDropTree = async (info: any) => {
    const dragKey = Number(info.node?.dragOver ? info.dragNode?.key : info.dragNode?.key);
    const dropKey = Number(info.node?.key);
    const dropToGap = info.dropToGap;

    const dragNode = flatList.find((r) => r.id === dragKey);
    const dropNode = flatList.find((r) => r.id === dropKey);
    if (!dragNode || !dropNode) return;

    let newParentId: number | null | undefined = null;
    let newOrderNum = 0;

    if (dropToGap) {
      newParentId = dropNode.parentId ?? null;
      const siblings = flatList
        .filter((r) => (r.parentId ?? null) === (newParentId ?? null))
        .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
      const targetIdx = siblings.findIndex((s) => s.id === dropNode.id);
      const insertIdx = info.dropPosition < info.node.pos.split('-').length - 1 ? targetIdx : targetIdx + 1;
      const swapWith = siblings[Math.min(insertIdx, siblings.length - 1)];
      if (swapWith && swapWith.id !== dragNode.id) {
        try {
          await sortMut.mutateAsync([
            { id: dragNode.id, orderNum: swapWith.orderNum ?? 0 },
            { id: swapWith.id, orderNum: dragNode.orderNum ?? 0 },
          ]);
          message.success('排序已更新');
          actionRef.current?.reload();
          invalidatePerms();
          broadcastMenuRefresh();
        } catch (e: any) {
          message.error(e?.message || '排序失败');
        }
      }
    } else {
      newParentId = dropNode.id;
      const siblings = flatList
        .filter((r) => (r.parentId ?? null) === newParentId)
        .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
      newOrderNum = (siblings[siblings.length - 1]?.orderNum ?? 0) + 1;
      try {
        await updateMut.mutateAsync({
          id: dragNode.id,
          payload: {
            type: dragNode.type as any,
            code: dragNode.code,
            name: dragNode.name,
            path: dragNode.path ?? null,
            component: dragNode.component ?? null,
            icon: dragNode.icon ?? null,
            parentId: newParentId,
            orderNum: newOrderNum,
            meta: (dragNode as any).meta ?? null,
            status: (dragNode.status as any) || 'enabled',
          },
        });
        message.success('层级已调整');
        actionRef.current?.reload();
        invalidatePerms();
        broadcastMenuRefresh();
      } catch (e: any) {
        message.error(e?.message || '调整失败');
      }
    }
  };

  // 获取表单类型值
  const formType = Form.useWatch('type', form);

  return (
    <PageContainer
      header={{
        title: '资源管理',
        subTitle: '管理系统菜单、页面、动作和接口资源',
        style: { padding: '16px 24px' }
      }}
      extra={[
        <Space key="search" size={12}>
          <Input
            placeholder="搜索名称/编码"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 200 }}
            allowClear
          />
        </Space>,
        <Space key="filter" size={8}>
          <Select<ResourceType>
            value={filterType}
            onChange={(v) => setFilterType(v)}
            style={{ width: 120 }}
            allowClear
            placeholder="全部类型"
            options={typeOptions}
          />
        </Space>,
        <Space key="actions" size={8}>
          <Upload accept="application/json" beforeUpload={beforeUpload} showUploadList={false}>
            <Button icon={<UploadOutlined />}>导入</Button>
          </Upload>,
          <Button
            icon={<DownloadOutlined />}
            onClick={async () => {
              try {
                const { data } = await api.get('/admin/resources/v2/export');
                const payload = unwrap<any>(data);
                const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'resources-export.json';
                a.click();
                URL.revokeObjectURL(url);
              } catch (e: any) { message.error(e?.message || '导出失败'); }
            }}
          >
            导出
          </Button>,
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增资源
          </Button>,
          <Button icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()} />,
        </Space>,
      ]}
    >
      <Tabs
        defaultActiveKey="table"
        items={[
          {
            key: 'table',
            label: (
              <span>
                <SearchOutlined />
                资源列表
              </span>
            ),
            children: (
              <Card variant="borderless" style={{ marginTop: 0 }}>
                <ProTable<SysResource>
                  columns={columns}
                  actionRef={actionRef}
                  rowKey="id"
                  dataSource={dataSource}
                  search={false}
                  pagination={false}
                  options={{ density: true, reload: false, setting: { draggable: true } }}
                  expandable={{ defaultExpandAllRows: true }}
                  loading={listQuery.isLoading}
                  scroll={{ x: 1200 }}
                  tableStyle={{ padding: 0 }}
                />
              </Card>
            ),
          },
          {
            key: 'drag',
            label: (
              <span>
                <DragOutlined />
                拖拽调整
              </span>
            ),
            children: (
              <Card
                variant="borderless"
                style={{ marginTop: 0 }}
                title={
                  <Space>
                    <DragOutlined />
                    <span>拖拽调整层级和顺序</span>
                  </Space>
                }
                extra={
                  <Space>
                    <Tag icon={<InfoCircleOutlined />}>提示</Tag>
                    <span style={{ color: '#666' }}>拖动节点到其他节点上可改变层级</span>
                  </Space>
                }
              >
                <div style={{
                  padding: 16,
                  background: '#fafafa',
                  borderRadius: 8,
                  border: '1px dashed #d9d9d9'
                }}>
                  <Tree
                    blockNode
                    draggable
                    defaultExpandAll
                    treeData={treeDraggableData}
                    onDrop={onDropTree}
                    style={{
                      background: '#fff',
                      padding: 16,
                      borderRadius: 4,
                      minHeight: 400
                    }}
                  />
                </div>
              </Card>
            ),
          },
        ]}
      />

      <Modal
        title={
          <Space>
            {editing ? <EditOutlined /> : <PlusOutlined />}
            <span>{editing ? '编辑资源' : '新增资源'}</span>
          </Space>
        }
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleSubmit}
        confirmLoading={createMut.isPending || updateMut.isPending}
        width={720}
        forceRender
        styles={{ body: { paddingTop: 16 } }}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ type: 'MENU', status: 'enabled', orderNum: 0 }}
        >
          {/* 基本信息 */}
          <Descriptions title="基本信息" column={2} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label={<><span style={{ color: 'red' }}>*</span> 资源类型</>}>
              <Form.Item name="type" noStyle>
                <Select options={typeOptions.map(t => ({ label: t.label, value: t.value }))} />
              </Form.Item>
            </Descriptions.Item>
            <Descriptions.Item label={<><span style={{ color: 'red' }}>*</span> 状态</>}>
              <Form.Item name="status" noStyle>
                <Select options={statusOptions} />
              </Form.Item>
            </Descriptions.Item>
          </Descriptions>

          <Form.Item name="code" label="资源编码" rules={[{ required: true, message: '请输入资源编码' }]} tooltip="全局唯一，英文标识">
            <Input placeholder="如: system.users 或 api.users.list" />
          </Form.Item>

          <Form.Item name="name" label="资源名称" rules={[{ required: true, message: '请输入资源名称' }]}>
            <Input placeholder="如: 用户管理" />
          </Form.Item>

          {/* 路由配置 */}
          <Descriptions title="路由配置" column={2} size="small" style={{ marginBottom: 16, marginTop: 8 }}>
            <Descriptions.Item label={<><InfoCircleOutlined style={{ marginRight: 4 }} />路由路径</>}>
              <Form.Item name="path" noStyle>
                <Input placeholder="/system/users 或 /api/users" />
              </Form.Item>
            </Descriptions.Item>
            <Descriptions.Item label={<><InfoCircleOutlined style={{ marginRight: 4 }} />前端组件</>}>
              <Form.Item name="component" noStyle>
                <Input placeholder="如: system/Users" />
              </Form.Item>
            </Descriptions.Item>
          </Descriptions>

          <Space size={16} style={{ width: '100%' }}>
            <Form.Item name="icon" label="图标" style={{ flex: 1 }}>
              <Input placeholder="如: UserOutlined" />
            </Form.Item>
            <Form.Item name="parentId" label="父级资源" style={{ flex: 1 }}>
              <TreeSelect
                allowClear
                showSearch
                treeData={parentTree}
                placeholder="选择父级资源（可空）"
                filterTreeNode={(input, node) =>
                  (node?.label as string)?.toLowerCase().includes(input.toLowerCase())
                }
              />
            </Form.Item>
            <Form.Item name="orderNum" label="排序号" style={{ width: 120 }}>
              <InputNumber min={0} placeholder="数字越大越靠后" />
            </Form.Item>
          </Space>

          {/* 菜单属性 - 仅 MENU/VIEW 类型显示 */}
          {(formType === 'MENU' || formType === 'VIEW') && (
            <>
              <Descriptions title="菜单属性" column={2} size="small" style={{ marginBottom: 16, marginTop: 8 }}>
                <Descriptions.Item label="页面缓存">
                  <Form.Item name="keepAlive" noStyle>
                    <Select
                      allowClear
                      placeholder="是否缓存"
                      options={[{ label: '启用缓存', value: true }, { label: '不缓存', value: false }]}
                    />
                  </Form.Item>
                </Descriptions.Item>
                <Descriptions.Item label="固定标签">
                  <Form.Item name="affix" noStyle>
                    <Select
                      allowClear
                      placeholder="是否固定"
                      options={[{ label: '固定标签', value: true }, { label: '不固定', value: false }]}
                    />
                  </Form.Item>
                </Descriptions.Item>
              </Descriptions>

              <Form.Item name="hidden" label="菜单可见性" tooltip="隐藏后菜单中不显示，但权限仍然有效">
                <Select
                  allowClear
                  placeholder="请选择"
                  options={[
                    { label: '显示菜单', value: false },
                    { label: '隐藏菜单', value: true },
                  ]}
                />
              </Form.Item>

            </>
          )}

          {/* API 方法 - 仅 API 类型显示 */}
          {formType === 'API' && (
            <Form.Item name="method" label="HTTP 方法" tooltip="API接口的HTTP请求方法">
              <Select
                allowClear
                placeholder="选择HTTP方法"
                options={[
                  { label: 'GET', value: 'GET' },
                  { label: 'POST', value: 'POST' },
                  { label: 'PUT', value: 'PUT' },
                  { label: 'DELETE', value: 'DELETE' },
                  { label: 'PATCH', value: 'PATCH' },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default ResourceManager;
