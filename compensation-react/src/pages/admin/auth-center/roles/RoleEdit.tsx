import React, { useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Form, Input, Radio, Select, Spin, Typography, message } from 'antd';
import { LockOutlined, SaveOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  useCreateRoleMutation,
  useRolesQuery,
  useUpdateRoleMutation,
} from '@services/queries/roles';
import type { CreateRoleRequest, RoleInfo, UpdateRoleRequest } from '@types/api';
import './RolePages.less';

const { TextArea } = Input;
const { Text } = Typography;

type RoleFormData = {
  code: string;
  name: string;
  roleType: 'SYSTEM' | 'BUSINESS' | 'CUSTOM';
  status: 'enabled' | 'disabled';
  description?: string;
};

const ROLE_TYPE_OPTIONS = [
  { value: 'BUSINESS', label: '业务角色' },
  { value: 'CUSTOM', label: '自定义角色' },
];

const RoleEdit: React.FC = () => {
  const navigate = useNavigate();
  const { roleId } = useParams<{ roleId: string }>();
  const [form] = Form.useForm<RoleFormData>();
  const isEditMode = Boolean(roleId);
  const roleIdNum = roleId ? Number(roleId) : 0;

  const rolesQuery = useRolesQuery({});
  const createRoleMutation = useCreateRoleMutation();
  const updateRoleMutation = useUpdateRoleMutation();

  const currentRole: RoleInfo | undefined = useMemo(
    () => rolesQuery.data?.find((role) => role.id === roleIdNum),
    [roleIdNum, rolesQuery.data],
  );
  const isLocked = Boolean(currentRole?.isProtected || currentRole?.isEditable === false);
  const pageTitle = isEditMode ? '编辑角色' : '新建角色';

  useEffect(() => {
    if (currentRole) {
      form.setFieldsValue({
        code: currentRole.code,
        name: currentRole.name,
        roleType: currentRole.roleType,
        status: currentRole.status,
        description: currentRole.description ?? undefined,
      });
    }
  }, [currentRole, form]);

  const pageHeader = {
    title: pageTitle,
    breadcrumb: {
      items: [
        { path: '/', title: '首页' },
        { path: '/admin', title: '管理端' },
        { path: '/admin/auth-center', title: '授权中心' },
        { path: '/admin/auth-center/roles', title: '角色管理' },
        { title: pageTitle },
      ],
    },
    onBack: () => navigate('/admin/auth-center/roles'),
  };

  const handleSubmit = async (values: RoleFormData) => {
    try {
      if (isEditMode) {
        const request: UpdateRoleRequest = {
          name: values.name,
          roleType: values.roleType,
          status: values.status,
          description: values.description,
        };
        await updateRoleMutation.mutateAsync({ id: roleIdNum, request });
        message.success('角色更新成功');
      } else {
        const request: CreateRoleRequest = {
          code: values.code,
          name: values.name,
          roleType: values.roleType,
          description: values.description,
        };
        await createRoleMutation.mutateAsync(request);
        message.success('角色创建成功');
      }
      navigate('/admin/auth-center/roles');
    } catch (error: any) {
      message.error(error?.message || '保存失败');
    }
  };

  if (isEditMode && rolesQuery.isLoading) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Spin size="large" description="加载角色信息..." />
        </main>
      </PageContainer>
    );
  }

  if (rolesQuery.isError) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert
            title="角色信息加载失败"
            description={
              rolesQuery.error instanceof Error ? rolesQuery.error.message : '请稍后重试'
            }
            type="error"
            showIcon
          />
        </main>
      </PageContainer>
    );
  }

  if (isEditMode && !currentRole) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert title="角色不存在" description="未找到指定的角色信息" type="error" showIcon />
        </main>
      </PageContainer>
    );
  }

  if (isLocked) {
    return (
      <PageContainer header={pageHeader}>
        <main className="role-page-state">
          <Alert
            title="系统保护角色"
            description="该角色是系统内置角色，受保护无法编辑。如需修改，请联系系统管理员。"
            type="warning"
            showIcon
            icon={<LockOutlined />}
          />
        </main>
      </PageContainer>
    );
  }

  return (
    <PageContainer header={pageHeader}>
      <main className="role-edit-page">
        <section className="role-page-intro">
          <div className="role-page-intro-icon">
            <SafetyCertificateOutlined />
          </div>
          <div>
            <Text className="role-page-eyebrow">ROLE CONFIGURATION</Text>
            <Typography.Title level={3}>{pageTitle}</Typography.Title>
            <Text type="secondary">
              {isEditMode
                ? '维护角色的基本信息，已分配的权限不会因本次编辑被清除。'
                : '创建一个清晰、可追踪的授权角色。'}
            </Text>
          </div>
        </section>

        <div className="role-edit-layout">
          <section className="role-form-surface">
            <div className="role-section-heading">
              <div>
                <Text className="role-section-kicker">IDENTITY</Text>
                <Typography.Title level={4}>角色基本信息</Typography.Title>
              </div>
              <Text type="secondary">带 * 的字段为必填项</Text>
            </div>

            <Form
              form={form}
              layout="vertical"
              initialValues={{ roleType: 'CUSTOM', status: 'enabled' }}
              onFinish={handleSubmit}
              autoComplete="off"
              className="role-form"
            >
              <div className="role-form-grid">
                <Form.Item
                  name="code"
                  label="角色编码"
                  rules={[
                    { required: true, message: '请输入角色编码' },
                    {
                      pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
                      message: '编码必须以字母开头，只能包含字母、数字和下划线',
                    },
                    { min: 2, max: 50, message: '角色编码长度必须在 2-50 个字符之间' },
                  ]}
                  extra={isEditMode ? '编码创建后不可修改' : '使用字母、数字和下划线，便于系统识别'}
                >
                  <Input disabled={isEditMode} maxLength={50} placeholder="如：PAYROLL_REVIEWER" />
                </Form.Item>

                <Form.Item
                  name="name"
                  label="角色名称"
                  rules={[
                    { required: true, message: '请输入角色名称' },
                    { min: 2, max: 50, message: '角色名称长度必须在 2-50 个字符之间' },
                  ]}
                >
                  <Input maxLength={50} placeholder="如：薪酬审核员" />
                </Form.Item>
              </div>

              <div className="role-form-grid">
                <Form.Item
                  name="roleType"
                  label="角色类型"
                  rules={[{ required: true, message: '请选择角色类型' }]}
                >
                  <Select options={ROLE_TYPE_OPTIONS} placeholder="请选择角色类型" />
                </Form.Item>

                <Form.Item
                  name="status"
                  label="角色状态"
                  rules={[{ required: true, message: '请选择角色状态' }]}
                >
                  <Radio.Group optionType="button" buttonStyle="solid">
                    <Radio.Button value="enabled">启用</Radio.Button>
                    <Radio.Button value="disabled">禁用</Radio.Button>
                  </Radio.Group>
                </Form.Item>
              </div>

              <Form.Item
                name="description"
                label="角色描述"
                rules={[{ max: 200, message: '角色描述不能超过 200 个字符' }]}
              >
                <TextArea
                  maxLength={200}
                  placeholder="说明该角色的职责、适用人员和权限边界"
                  rows={5}
                  showCount
                />
              </Form.Item>

              <div className="role-form-actions">
                <Button onClick={() => navigate('/admin/auth-center/roles')}>取消</Button>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<SaveOutlined />}
                  loading={createRoleMutation.isPending || updateRoleMutation.isPending}
                >
                  {isEditMode ? '保存修改' : '创建角色'}
                </Button>
              </div>
            </Form>
          </section>

          <aside className="role-edit-aside">
            <div className="role-section-heading is-compact">
              <div>
                <Text className="role-section-kicker">GUIDANCE</Text>
                <Typography.Title level={4}>配置说明</Typography.Title>
              </div>
            </div>
            <div className="role-guidance-list">
              <div className="role-guidance-item">
                <span className="role-guidance-index">01</span>
                <div>
                  <Text strong>先定义身份</Text>
                  <Text type="secondary">角色名称和编码需要能对应实际职责。</Text>
                </div>
              </div>
              <div className="role-guidance-item">
                <span className="role-guidance-index">02</span>
                <div>
                  <Text strong>再分配权限</Text>
                  <Text type="secondary">保存后可进入权限配置页选择资源和操作。</Text>
                </div>
              </div>
              <div className="role-guidance-item">
                <span className="role-guidance-index">03</span>
                <div>
                  <Text strong>最后添加成员</Text>
                  <Text type="secondary">确认职责边界后，再将用户加入角色。</Text>
                </div>
              </div>
            </div>
            <div className="role-edit-aside-note">
              <Text type="secondary">
                {isEditMode
                  ? '当前正在编辑已有角色。权限和成员关系请在对应管理页调整。'
                  : '新建角色默认采用自定义类型并立即启用，可在提交前调整。'}
              </Text>
            </div>
          </aside>
        </div>
      </main>
    </PageContainer>
  );
};

export default RoleEdit;
