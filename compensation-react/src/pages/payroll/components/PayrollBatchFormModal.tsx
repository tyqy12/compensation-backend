import React from 'react';
import { Alert, Form, Input, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';

export type PayrollBatchFormValues = {
  payCycleId?: number;
  periodLabel: string;
  type: string;
  currency?: string;
  remark?: string;
};

type MutationState = {
  isPending?: boolean;
};

type PayrollBatchFormModalProps = {
  mode: 'create' | 'edit';
  open: boolean;
  form: FormInstance<PayrollBatchFormValues>;
  mutation: MutationState;
  payrollTypeOptions: Array<{ label: string; value: string }>;
  payCycleOptions: Array<{
    label: string;
    value: number;
    type?: string;
    periodLabel?: string;
    status?: string;
  }>;
  onSubmit: () => void;
  onCancel: () => void;
};

const PayrollBatchFormModal: React.FC<PayrollBatchFormModalProps> = ({
  mode,
  open,
  form,
  mutation,
  payrollTypeOptions,
  payCycleOptions,
  onSubmit,
  onCancel,
}) => {
  const isCreate = mode === 'create';
  const selectedType = Form.useWatch('type', form);
  const selectedPayCycleId = Form.useWatch('payCycleId', form);
  const compatiblePayCycles = payCycleOptions.filter(
    (cycle) =>
      (!selectedType || !cycle.type || cycle.type === selectedType) &&
      (cycle.status === 'open' || (!isCreate && cycle.value === selectedPayCycleId)),
  );

  return (
    <Modal
      title={isCreate ? '新建薪酬批次' : '编辑薪酬批次'}
      open={open}
      onOk={onSubmit}
      onCancel={onCancel}
      confirmLoading={mutation.isPending}
      width={480}
      destroyOnHidden
    >
      <Alert
        type={isCreate ? 'info' : 'warning'}
        showIcon
        className="payroll-modal-alert"
        title={isCreate ? '创建后将进入批次工作台' : '仅草稿状态的批次可以编辑'}
        description={
          isCreate ? '继续上传 CSV 或直接手动录入薪资项。' : '修改后请回到批次工作台核对录入数据。'
        }
      />
      <Form form={form} layout="vertical">
        <Form.Item
          name="payCycleId"
          label="发薪日历"
          rules={[{ required: true, message: '请选择开放的发薪日历' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder={compatiblePayCycles.length ? '请选择发薪日历' : '暂无可用的发薪日历'}
            options={compatiblePayCycles}
            disabled={!compatiblePayCycles.length}
            onChange={(value) => {
              const cycle = payCycleOptions.find((item) => item.value === value);
              if (cycle?.periodLabel) {
                form.setFieldValue('periodLabel', cycle.periodLabel);
              }
              if (cycle?.type && cycle.type !== form.getFieldValue('type')) {
                form.setFieldValue('type', cycle.type);
              }
            }}
          />
        </Form.Item>
        <Form.Item
          name="periodLabel"
          label="批次期间"
          rules={[
            { required: true, message: '请先选择发薪日历' },
          ]}
        >
          <Input disabled placeholder="选择发薪日历后自动带入" />
        </Form.Item>
        <Form.Item
          name="type"
          label="用工类型"
          rules={[{ required: true, message: '请选择用工类型' }]}
        >
          <Select placeholder="请选择用工类型" options={payrollTypeOptions} />
        </Form.Item>
        <Form.Item name="currency" label="币种">
          <Select
            options={[
              { label: '人民币 (CNY)', value: 'CNY' },
              { label: '美元 (USD)', value: 'USD' },
            ]}
          />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} placeholder="可选，添加批次备注信息" />
        </Form.Item>
      </Form>
      {selectedPayCycleId && compatiblePayCycles.length === 0 && (
        <Alert
          type="warning"
          showIcon
          title="当前用工类型没有可用日历"
          description="请返回发薪日历先创建并开放对应类型的运行计划。"
          style={{ marginTop: 12 }}
        />
      )}
      {!compatiblePayCycles.length && (
        <Alert
          type="warning"
          showIcon
          title="暂无开放的发薪日历"
          description="批次必须关联开放日历，请先到发薪日历配置期间、截数日和发薪日。"
          style={{ marginTop: 12 }}
        />
      )}
    </Modal>
  );
};

export default PayrollBatchFormModal;
