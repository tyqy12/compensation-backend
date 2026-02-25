/**
 * 资源编辑页
 *
 * 设计原则：
 * - 支持新建和编辑两种模式
 * - 完整的表单字段（名称、编码、类型、父级等）
 * - 表单验证和错误提示
 * - 支持配置操作权限
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import {
  Card,
  Form,
  Input,
  Select,
  Button,
  Space,
  Spin,
  Alert,
  Typography,
  message,
  Radio,
  TreeSelect,
  Divider,
  Tag,
} from 'antd';
import type { FormInstance } from 'antd/es/form';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  FolderOutlined,
  FileOutlined,
  ApiOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { useResourcesQuery, useCreateResourceMutation, useUpdateResourceMutation } from '@services/queries/resources';
import type { SysResource } from '@types/api';

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;
const { TreeNode } = TreeSelect;

// 资源类型选项
const resourceTypeOptions = [
  { value: 'MENU', label: '菜单资源', icon: <FolderOutlined />, color: 'blue', description: '系统导航菜单' },
  { value: 'VIEW', label: '页面资源', icon: <FileOutlined />, color: 'green', description: '页面访问权限' },
  { value: 'ACTION', label: '操作资源', icon: <PlayCircleOutlined />, color: 'orange', description: '按钮级权限' },
  { value: 'API', label: 'API资源', icon: <ApiOutlined />, color: 'purple', description: '接口访问权限' },
];

interface ResourceFormData {
  code: string;
  name: string;
  type: 'MENU' | 'VIEW' | 'ACTION' | 'API';
  parentId?: number | null;
  status: 'enabled' | 'disabled';
  sortOrder?: number;
  path?: string;
  icon?: string;
  description?: string;
}

const ResourceEdit: React.FC = () => {
  const navigate = useNavigate();
  const { resourceId } = useParams<{ resourceId: string }>();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<ResourceFormData>();

  const isEditMode = !!resourceId;
  const resourceIdNum = resourceId ? parseInt(resourceId) : 0;
  const parentIdFromQuery = searchParams.get('parentId');

  // 状态
  const [selectedType, setSelectedType] = useState<string>('MENU');

  // 查询数据
  const resourcesQuery = useResourcesQuery();
  const createResourceMutation = useCreateResourceMutation();
  const updateResourceMutation = useUpdateResourceMutation();

  // 当前资源信息
  const currentResource: SysResource | undefined = useMemo(() => {
    return resourcesQuery.data?.find((r: SysResource) => r.id === resourceIdNum);
  }, [resourcesQuery.data, resourceIdNum]);

  // 如果是编辑模式，加载资源数据到表单
  useEffect(() => {
    if (isEditMode && currentResource) {
      form.setFieldsValue({
        code: currentResource.code,
        name: currentResource.name,
        type: currentResource.type,
        parentId: currentResource.parentId,
        status: currentResource.status,
        sortOrder: currentResource.sortOrder,
        path: currentResource.path,
        icon: currentResource.icon,
        description: currentResource.description,
      });
      setSelectedType(currentResource.type);
    } else if (!isEditMode && parentIdFromQuery) {
      // 新建模式，如果有parentId参数
      form.setFieldsValue({
        parentId: parseInt(parentIdFromQuery),
      });
    }
  }, [isEditMode, currentResource, parentIdFromQuery, form]);

  // 构建父级资源树选项
  const parentTreeOptions = useMemo(() => {
    const list = resourcesQuery.data || [];
    // 过滤掉当前资源及其子资源（避免循环引用）
    const filterIds = new Set<number>();
    if (isEditMode) {
      filterIds.add(resourceIdNum);
      const findChildren = (parentId: number) => {
        list.forEach((r: SysResource) => {
          if (r.parentId === parentId) {
            filterIds.add(r.id);
            findChildren(r.id);
          }
        });
      };
      findChildren(resourceIdNum);
    }

    return list
      .filter((r: SysResource) => !filterIds.has(r.id))
      .map((r: SysResource) => ({
        value: r.id,
        title: `${r.name} (${r.code})`,
        parentId: r.parentId,
      }));
  }, [resourcesQuery.data, isEditMode, resourceIdNum]);

  // 提交表单
  const handleSubmit = async (values: ResourceFormData) => {
    try {
      if (isEditMode) {
        await updateResourceMutation.mutateAsync({
          id: resourceIdNum,
          ...values,
        });
        message.success('资源更新成功');
      } else {
        await createResourceMutation.mutateAsync(values);
        message.success('资源创建成功');
      }
      navigate('/admin/auth-center/resources');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  if (isEditMode && !currentResource && !resourcesQuery.isLoading) {
    return (
      <PageContainer
        header={{
          title: '编辑资源',
          breadcrumb: {
            items: [
              { path: '/', title: '首页' },
              { path: '/admin', title: '管理端' },
              { path: '/admin/auth-center', title: '授权中心' },
              { path: '/admin/auth-center/resources', title: '资源管理' },
              { title: '编辑资源' },
            ],
          },
          onBack: () => navigate('/admin/auth-center/resources'),
        }}
      >
        <Alert
          message="资源不存在"
          description="未找到指定的资源信息"
          type="error"
          showIcon
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      header={{
        title: isEditMode ? '编辑资源' : '新建资源',
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/resources', title: '资源管理' },
            { title: isEditMode ? '编辑资源' : '新建资源' },
          ],
        },
        onBack: () => navigate('/admin/auth-center/resources'),
      }}
    >
      <Card style={{ maxWidth: 800 }}>
        {isEditMode && (
          <Alert
            message="编辑提示"
            description="修改资源信息会影响所有引用该资源的角色和用户权限，请谨慎操作。"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
        )}

        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          autoComplete="off"
        >
          {/* 资源类型选择 */}
          <Form.Item
            name="type"
            label="资源类型"
            rules={[{ required: true, message: '请选择资源类型' }]}
            initialValue="MENU"
          >
            <Radio.Group
              onChange={(e) => setSelectedType(e.target.value)}
              disabled={isEditMode} // 编辑模式下不允许修改类型
            >
              <Space wrap>
                {resourceTypeOptions.map((option) => (
                  <Radio.Button key={option.value} value={option.value}>
                    <Space>
                      {option.icon}
                      <span>{option.label}</span>
                    </Space>
                  </Radio.Button>
                ))}
              </Space>
            </Radio.Group>
          </Form.Item>

          {/* 类型说明 */}
          <Form.Item style={{ marginTop: -12, marginBottom: 24 }}>
            <Text type="secondary">
              {resourceTypeOptions.find((o) => o.value === selectedType)?.description}
            </Text>
          </Form.Item>

          <Divider />

          {/* 资源编码 */}
          <Form.Item
            name="code"
            label="资源编码"
            rules={[
              { required: true, message: '请输入资源编码' },
              { pattern: /^[a-zA-Z][a-zA-Z0-9_:.]*$/, message: '资源编码必须以字母开头，只能包含字母、数字、下划线和冒号' },
              { min: 2, max: 100, message: '资源编码长度必须在2-100个字符之间' },
            ]}
            extra="资源编码用于系统内部识别，创建后不可修改。建议格式：module:feature:action"
          >
            <Input
              placeholder="请输入资源编码，如：system:user:create"
              disabled={isEditMode}
              maxLength={100}
            />
          </Form.Item>

          {/* 资源名称 */}
          <Form.Item
            name="name"
            label="资源名称"
            rules={[
              { required: true, message: '请输入资源名称' },
              { min: 2, max: 50, message: '资源名称长度必须在2-50个字符之间' },
            ]}
          >
            <Input
              placeholder="请输入资源名称，如：创建用户"
              maxLength={50}
            />
          </Form.Item>

          {/* 父级资源 */}
          <Form.Item
            name="parentId"
            label="父级资源"
            extra="选择父级资源以构建资源树结构"
          >
            <Select
              placeholder="请选择父级资源（可选）"
              allowClear
              showSearch
              optionFilterProp="label"
              options={parentTreeOptions.map((opt) => ({
                value: opt.value,
                label: opt.title,
              }))}
            />
          </Form.Item>

          {/* 路由路径（仅菜单和页面） */}
          {(selectedType === 'MENU' || selectedType === 'VIEW') && (
            <Form.Item
              name="path"
              label="路由路径"
              rules={[{ max: 200, message: '路由路径不能超过200个字符' }]}
              extra="前端路由路径，如：/system/users"
            >
              <Input
                placeholder="请输入路由路径"
                maxLength={200}
              />
            </Form.Item>
          )}

          {/* 图标（仅菜单） */}
          {selectedType === 'MENU' && (
            <Form.Item
              name="icon"
              label="图标"
              rules={[{ max: 50, message: '图标名称不能超过50个字符' }]}
              extra="Ant Design 图标名称，如：UserOutlined"
            >
              <Input
                placeholder="请输入图标名称"
                maxLength={50}
              />
            </Form.Item>
          )}

          {/* 排序号 */}
          <Form.Item
            name="sortOrder"
            label="排序号"
            initialValue={0}
            extra="数字越小排序越靠前"
          >
            <Input type="number" placeholder="请输入排序号" />
          </Form.Item>

          {/* 状态 */}
          <Form.Item
            name="status"
            label="状态"
            rules={[{ required: true, message: '请选择状态' }]}
            initialValue="enabled"
          >
            <Radio.Group>
              <Radio.Button value="enabled">启用</Radio.Button>
              <Radio.Button value="disabled">禁用</Radio.Button>
            </Radio.Group>
          </Form.Item>

          {/* 描述 */}
          <Form.Item
            name="description"
            label="描述"
            rules={[{ max: 500, message: '描述不能超过500个字符' }]}
          >
            <TextArea
              placeholder="请输入资源描述"
              rows={4}
              maxLength={500}
              showCount
            />
          </Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button
                onClick={() => navigate('/admin/auth-center/resources')}
              >
                取消
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={createResourceMutation.isPending || updateResourceMutation.isPending}
              >
                {isEditMode ? '保存修改' : '创建资源'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </PageContainer>
  );
};

export default ResourceEdit;
