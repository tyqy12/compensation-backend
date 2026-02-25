import React, { useEffect } from 'react';
import { Modal, Form, Input, Select, InputNumber, message } from 'antd';
import type { RoleInfo, RoleType } from '@types/api';
import { useCreateRoleMutation, useUpdateRoleMutation } from '@services/queries/roles';

interface RoleFormModalProps {
  visible: boolean;
  role: RoleInfo | null;
  onCancel: () => void;
  onSuccess: () => void;
}

const { TextArea } = Input;

const RoleFormModal: React.FC<RoleFormModalProps> = ({
  visible,
  role,
  onCancel,
  onSuccess,
}) => {
  const [form] = Form.useForm();
  const isEdit = !!role;

  const createMutation = useCreateRoleMutation();
  const updateMutation = useUpdateRoleMutation();

  // 重置表单数据
  useEffect(() => {
    if (visible) {
      if (role) {
        form.setFieldsValue({
          code: role.code,
          name: role.name,
          description: role.description,
          roleType: role.roleType,
          sortOrder: role.sortOrder,
          icon: role.icon,
          remarks: role.remarks,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          roleType: 'BUSINESS',
          sortOrder: 0,
        });
      }
    }
  }, [visible, role, form]);

  // 处理提交
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (isEdit) {
        await updateMutation.mutateAsync({
          id: role!.id,
          request: {
            name: values.name,
            description: values.description,
            roleType: values.roleType,
            sortOrder: values.sortOrder,
            icon: values.icon,
            remarks: values.remarks,
          },
        });
        message.success('更新成功');
      } else {
        await createMutation.mutateAsync({
          code: values.code,
          name: values.name,
          description: values.description,
          roleType: values.roleType,
          sortOrder: values.sortOrder,
          icon: values.icon,
          remarks: values.remarks,
        });
        message.success('创建成功');
      }
      onSuccess();
    } catch (e: any) {
      if (e.errorFields) {
        // 表单验证错误
        return;
      }
      message.error(e?.message || (isEdit ? '更新失败' : '创建失败'));
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑角色' : '新建角色'}
      open={visible}
      onCancel={onCancel}
      onOk={handleSubmit}
      confirmLoading={createMutation.isPending || updateMutation.isPending}
      width={560}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          roleType: 'BUSINESS',
          sortOrder: 0,
        }}
      >
        {!isEdit && (
          <Form.Item
            name="code"
            label="角色编码"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '角色编码必须以字母开头，只能包含字母、数字和下划线' },
              { min: 2, max: 50, message: '角色编码长度必须在2-50之间' },
            ]}
          >
            <Input placeholder="请输入角色编码，如 admin、manager" disabled={isEdit} />
          </Form.Item>
        )}

        <Form.Item
          name="name"
          label="角色名称"
          rules={[
            { required: true, message: '请输入角色名称' },
            { min: 2, max: 100, message: '角色名称长度必须在2-100之间' },
          ]}
        >
          <Input placeholder="请输入角色名称" />
        </Form.Item>

        <Form.Item
          name="description"
          label="角色描述"
          rules={[{ max: 500, message: '角色描述长度不能超过500' }]}
        >
          <TextArea rows={3} placeholder="请输入角色描述" />
        </Form.Item>

        <Form.Item
          name="roleType"
          label="角色类型"
          rules={[{ required: true, message: '请选择角色类型' }]}
        >
          <Select placeholder="请选择角色类型">
            <Select.Option value="SYSTEM">系统角色</Select.Option>
            <Select.Option value="BUSINESS">业务角色</Select.Option>
            <Select.Option value="CUSTOM">自定义角色</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="sortOrder"
          label="排序号"
          rules={[{ required: true, message: '请输入排序号' }]}
        >
          <InputNumber min={0} max={9999} style={{ width: '100%' }} placeholder="请输入排序号，数字越小越靠前" />
        </Form.Item>

        <Form.Item
          name="icon"
          label="角色图标"
          rules={[{ max: 100, message: '图标标识长度不能超过100' }]}
        >
          <Input placeholder="请输入图标标识，如 UserOutlined" />
        </Form.Item>

        <Form.Item
          name="remarks"
          label="备注"
          rules={[{ max: 500, message: '备注长度不能超过500' }]}
        >
          <TextArea rows={2} placeholder="请输入备注信息" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default RoleFormModal;
