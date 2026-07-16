import React from 'react';
import { Alert, Form, Input, Modal } from 'antd';
import type { FormInstance } from 'antd';
import type { PayrollPendingConfirmationDto } from '@services/queries/payroll';

export type ConfirmationFormValues = {
  signature: string;
  comment?: string;
};

export type ObjectFormValues = {
  reason: string;
  comment?: string;
};

export type AssignFormValues = {
  assigneeEmployeeId: number;
  scope: 'selected' | 'all';
};

type MutationState = {
  isPending?: boolean;
};

type ConfirmationActionModalsProps = {
  activeLine: PayrollPendingConfirmationDto | null;
  selectedCount: number;
  confirmModalOpen: boolean;
  objectModalOpen: boolean;
  batchConfirmModalOpen: boolean;
  confirmForm: FormInstance<ConfirmationFormValues>;
  objectForm: FormInstance<ObjectFormValues>;
  batchConfirmForm: FormInstance<ConfirmationFormValues>;
  confirmMutation: MutationState;
  objectMutation: MutationState;
  batchConfirmMutation: MutationState;
  onConfirm: () => void;
  onObject: () => void;
  onBatchConfirm: () => void;
  onCloseConfirm: () => void;
  onCloseObject: () => void;
  onCloseBatchConfirm: () => void;
};

const ConfirmationActionModals: React.FC<ConfirmationActionModalsProps> = ({
  activeLine,
  selectedCount,
  confirmModalOpen,
  objectModalOpen,
  batchConfirmModalOpen,
  confirmForm,
  objectForm,
  batchConfirmForm,
  confirmMutation,
  objectMutation,
  batchConfirmMutation,
  onConfirm,
  onObject,
  onBatchConfirm,
  onCloseConfirm,
  onCloseObject,
  onCloseBatchConfirm,
}) => (
  <>
    <Modal
      title={`工资行 ${activeLine?.lineId || '-'} 签字确认`}
      open={confirmModalOpen}
      destroyOnHidden
      onCancel={onCloseConfirm}
      onOk={onConfirm}
      confirmLoading={confirmMutation.isPending}
    >
      <Form form={confirmForm} layout="vertical">
        <Form.Item
          name="signature"
          label="签字"
          rules={[{ required: true, message: '请输入签字内容' }]}
        >
          <Input placeholder="请输入签字人（姓名/工号）" />
        </Form.Item>
        <Form.Item name="comment" label="备注">
          <Input.TextArea rows={3} placeholder="可选，补充说明" />
        </Form.Item>
      </Form>
    </Modal>

    <Modal
      title={`工资行 ${activeLine?.lineId || '-'} 发起异议`}
      open={objectModalOpen}
      destroyOnHidden
      onCancel={onCloseObject}
      onOk={onObject}
      confirmLoading={objectMutation.isPending}
    >
      <Form form={objectForm} layout="vertical">
        <Form.Item
          name="reason"
          label="异议原因"
          rules={[{ required: true, message: '请输入异议原因' }]}
        >
          <Input.TextArea rows={3} placeholder="例如：绩效系数有误、个税口径不一致等" />
        </Form.Item>
        <Form.Item name="comment" label="备注">
          <Input.TextArea rows={3} placeholder="可选，补充材料或说明" />
        </Form.Item>
      </Form>
    </Modal>

    <Modal
      title="批量签字确认"
      open={batchConfirmModalOpen}
      destroyOnHidden
      onCancel={onCloseBatchConfirm}
      onOk={onBatchConfirm}
      confirmLoading={batchConfirmMutation.isPending}
    >
      <Alert
        type="info"
        showIcon
        className="payroll-modal-alert"
        title={`将对 ${selectedCount} 条工资行执行批量签字确认`}
      />
      <Form form={batchConfirmForm} layout="vertical">
        <Form.Item
          name="signature"
          label="签字"
          rules={[{ required: true, message: '请输入签字内容' }]}
        >
          <Input placeholder="请输入签字人（姓名/工号）" />
        </Form.Item>
        <Form.Item name="comment" label="备注">
          <Input.TextArea rows={3} placeholder="可选，批量确认备注" />
        </Form.Item>
      </Form>
    </Modal>
  </>
);

export default ConfirmationActionModals;
