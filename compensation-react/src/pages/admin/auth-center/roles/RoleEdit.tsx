/**
 * 角色编辑页
 *
 * 设计原则：
 * - 简洁的表单布局
 * - 支持新建和编辑两种模式
 * - 表单验证和错误提示
 * - 系统保护角色不可编辑
 *
 * 遵循 Ant Design 设计规范
 */

import React, { useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
} from 'antd';
import type { FormInstance } from 'antd/es/form';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  LockOutlined,
} from '@ant-design/icons';
import { useRolesQuery, useCreateRoleMutation, useUpdateRoleMutation } from '@services/queries/roles';
import type { RoleInfo } from '@types/api';

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

interface RoleFormData {
  code: string;
  name: string;
  roleType: 'SYSTEM' | 'BUSINESS' | 'CUSTOM';
  status: 'enabled' | 'disabled';
  description?: string;
}

const RoleEdit: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const [form] = Form.useForm<RoleFormData>();

  const isEditMode = !!roleId;
  const roleIdNum = roleId ? parseInt(roleId) : 0;

  // 查询数据
  const rolesQuery = useRolesQuery({});
  const createRoleMutation = useCreateRoleMutation();
  const updateRoleMutation = useUpdateRoleMutation();

  // 当前角色信息
  const currentRole: RoleInfo | undefined = useMemo(() => {
    return rolesQuery.data?.find((r: RoleInfo) => r.id === roleIdNum);
  }, [rolesQuery.data, roleIdNum]);

  // 如果是编辑模式，加载角色数据到表单
  useEffect(() => {
    if (isEditMode && currentRole) {
      form.setFieldsValue({
        code: currentRole.code,
        name: currentRole.name,
        roleType: currentRole.roleType,
        status: currentRole.status,
        description: currentRole.description,
      });
    }
  }, [isEditMode, currentRole, form]);

  // 提交表单
  const handleSubmit = async (values: RoleFormData) => {
    try {
      if (isEditMode) {
        // 编辑模式
        await updateRoleMutation.mutateAsync({
          id: roleIdNum,
          ...values,
        });
        message.success('角色更新成功');
      } else {
        // 新建模式
        await createRoleMutation.mutateAsync(values);
        message.success('角色创建成功');
      }
      navigate('/admin/auth-center/roles');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  // 系统保护角色提示
  if (isEditMode && currentRole?.isProtected) {
    return (
      <PageContainer
        header={{
          title: '编辑角色',
          breadcrumb: {
            items: [
              { path: '/', title: '首页' },
              { path: '/admin', title: '管理端' },
              { path: '/admin/auth-center', title: '授权中心' },
              { path: '/admin/auth-center/roles', title: '角色管理' },
              { title: '编辑角色' },
            ],
          },
          onBack: () => navigate('/admin/auth-center/roles'),
        }}
      >
        <Alert
          message="系统保护角色"
          description="该角色是系统内置角色，受保护无法编辑。如需修改，请联系系统管理员。"
          type="warning"
          showIcon
          icon={<LockOutlined />}
        />
      </PageContainer>
    );
  }

  if (isEditMode && !currentRole && !rolesQuery.isLoading) {
    return (
      <PageContainer
        header={{
          title: '编辑角色',
          breadcrumb: {
            items: [
              { path: '/', title: '首页' },
              { path: '/admin', title: '管理端' },
              { path: '/admin/auth-center', title: '授权中心' },
              { path: '/admin/auth-center/roles', title: '角色管理' },
              { title: '编辑角色' },
            ],
          },
          onBack: () => navigate('/admin/auth-center/roles'),
        }}
      >
        <Alert
          message="角色不存在"
          description="未找到指定的角色信息"
          type="error"
          showIcon
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      header={{
        title: isEditMode ? '编辑角色' : '新建角色',
        breadcrumb: {
          items: [
            { path: '/', title: '首页' },
            { path: '/admin', title: '管理端' },
            { path: '/admin/auth-center', title: '授权中心' },
            { path: '/admin/auth-center/roles', title: '角色管理' },
            { title: isEditMode ? '编辑角色' : '新建角色' },
          ],
        },
        onBack: () => navigate('/admin/auth-center/roles'),
      }}
    >
      <Card style={{ maxWidth: 800 }}>
        {isEditMode && (
          <Alert
            message="编辑提示"
            description="修改角色基本信息不会影响已分配的权限配置。如需调整权限，请前往权限配置页面。"
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
          <Form.Item
            name="code"
            label="角色编码"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '角色编码必须以字母开头，只能包含字母、数字和下划线' },
              { min: 2, max: 50, message: '角色编码长度必须在2-50个字符之间' },
            ]}
            extra="角色编码用于系统内部识别，创建后不可修改"
          >
            <Input
              placeholder="请输入角色编码，如：ADMIN"
              disabled={isEditMode}
              maxLength={50}
            />
          </Form.Item>

          <Form.Item
            name="name"
            label="角色名称"
            rules={[
              { required: true, message: '请输入角色名称' },
              { min: 2, max: 50, message: '角色名称长度必须在2-50个字符之间' },
            ]}
          >
            <Input
              placeholder="请输入角色名称，如：管理员"
              maxLength={50}
            />
          </Form.Item>

          <Form.Item
            name="roleType"
            label="角色类型"
            rules={[{ required: true, message: '请选择角色类型' }]}
            initialValue="CUSTOM"
          >
            <Select placeholder="请选择角色类型">
              <Option value="SYSTEM" disabled>
                系统角色（仅系统内置）
              </Option>
              <Option value="BUSINESS">
                业务角色（预定义业务权限）
              </Option>
              <Option value="CUSTOM">
                自定义角色（完全自定义）
              </Option>
            </Select>
          </Form.Item>

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

          <Form.Item
            name="description"
            label="角色描述"
            rules={[{ max: 200, message: '角色描述不能超过200个字符' }]}
          >
            <TextArea
              placeholder="请输入角色描述，说明该角色的职责和权限范围"
              rows={4}
              maxLength={200}
              showCount
            />
          </Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button
                onClick={() => navigate('/admin/auth-center/roles')}
              >
                取消
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={createRoleMutation.isPending || updateRoleMutation.isPending}
              >
                {isEditMode ? '保存修改' : '创建角色'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </PageContainer>
  );
};

export default RoleEdit;
