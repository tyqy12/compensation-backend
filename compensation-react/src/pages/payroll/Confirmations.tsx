import React, { useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useSearchParams } from 'react-router-dom';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import {
  useAssignPayrollConfirmationMutation,
  useBatchConfirmPayrollMutation,
  useConfirmPayrollPayslipMutation,
  useObjectPayrollPayslipMutation,
  usePayrollConfirmationSummaryQuery,
  usePayrollPendingConfirmationsQuery,
  type PayrollPendingConfirmationDto,
} from '@services/queries/payroll';

const { Text } = Typography;

const confirmationStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待确认', color: 'default' },
  confirmed: { text: '已确认', color: 'success' },
  objected: { text: '异议处理中', color: 'processing' },
  objected_approved: { text: '异议通过', color: 'success' },
  objected_rejected: { text: '异议驳回待重提', color: 'warning' },
};

const statusTag = (status?: string) => {
  const key = (status || '').toLowerCase();
  const meta = confirmationStatusMeta[key];
  if (!meta) {
    return <Tag>{status || '-'}</Tag>;
  }
  return <Tag color={meta.color}>{meta.text}</Tag>;
};

const PayrollConfirmations: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialBatchId = Number(searchParams.get('batchId') || '') || undefined;

  const [filters, setFilters] = useState<{ current: number; pageSize: number; batchId?: number }>({
    current: 1,
    pageSize: 10,
    batchId: initialBatchId,
  });
  const [batchIdInput, setBatchIdInput] = useState<number | undefined>(initialBatchId);
  const [selectedLineIds, setSelectedLineIds] = useState<number[]>([]);

  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [objectModalOpen, setObjectModalOpen] = useState(false);
  const [batchConfirmModalOpen, setBatchConfirmModalOpen] = useState(false);
  const [activeLine, setActiveLine] = useState<PayrollPendingConfirmationDto | null>(null);

  const [confirmForm] = Form.useForm<{ signature: string; comment?: string }>();
  const [objectForm] = Form.useForm<{ reason: string; comment?: string }>();
  const [batchConfirmForm] = Form.useForm<{ signature: string; comment?: string }>();
  const [assignForm] = Form.useForm<{ assigneeEmployeeId: number; scope: 'selected' | 'all' }>();

  const pendingQuery = usePayrollPendingConfirmationsQuery(filters);
  const summaryQuery = usePayrollConfirmationSummaryQuery(filters.batchId ?? 0, {
    enabled: Boolean(filters.batchId),
  });

  const confirmMutation = useConfirmPayrollPayslipMutation();
  const objectMutation = useObjectPayrollPayslipMutation();
  const batchConfirmMutation = useBatchConfirmPayrollMutation();
  const assignMutation = useAssignPayrollConfirmationMutation();

  const pendingRecords = useMemo(() => pendingQuery.data?.records ?? [], [pendingQuery.data?.records]);

  const refreshAll = async () => {
    await pendingQuery.refetch();
    if (filters.batchId) {
      await summaryQuery.refetch();
    }
  };

  const openConfirmModal = (record: PayrollPendingConfirmationDto) => {
    setActiveLine(record);
    confirmForm.resetFields();
    setConfirmModalOpen(true);
  };

  const openObjectModal = (record: PayrollPendingConfirmationDto) => {
    setActiveLine(record);
    objectForm.resetFields();
    setObjectModalOpen(true);
  };

  const submitConfirm = async () => {
    if (!activeLine?.lineId) {
      return;
    }
    try {
      const values = await confirmForm.validateFields();
      await confirmMutation.mutateAsync({
        lineId: activeLine.lineId,
        payload: values,
      });
      message.success('签字确认成功');
      setConfirmModalOpen(false);
      setActiveLine(null);
      await refreshAll();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '签字确认失败');
    }
  };

  const submitObject = async () => {
    if (!activeLine?.lineId) {
      return;
    }
    try {
      const values = await objectForm.validateFields();
      const result = await objectMutation.mutateAsync({
        lineId: activeLine.lineId,
        payload: values,
      });
      message.success(`异议已提交，流程号：${result.workflowId || '-'}`);
      setObjectModalOpen(false);
      setActiveLine(null);
      await refreshAll();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '发起异议失败');
    }
  };

  const openBatchConfirmModal = () => {
    if (!filters.batchId) {
      message.warning('请先按批次筛选后再执行批量确认');
      return;
    }
    if (selectedLineIds.length === 0) {
      message.warning('请至少选择一条待确认工资行');
      return;
    }
    batchConfirmForm.resetFields();
    setBatchConfirmModalOpen(true);
  };

  const submitBatchConfirm = async () => {
    if (!filters.batchId) {
      return;
    }
    try {
      const values = await batchConfirmForm.validateFields();
      const result = await batchConfirmMutation.mutateAsync({
        batchId: filters.batchId,
        payload: {
          lineIds: selectedLineIds,
          signature: values.signature,
          comment: values.comment,
        },
      });
      message.success(`批量确认完成，成功 ${result.affected} 条`);
      setBatchConfirmModalOpen(false);
      setSelectedLineIds([]);
      await refreshAll();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '批量确认失败');
    }
  };

  const submitAssign = async () => {
    if (!filters.batchId) {
      message.warning('请先按批次筛选后再分配确认负责人');
      return;
    }
    try {
      const values = await assignForm.validateFields();
      if (values.scope === 'selected' && selectedLineIds.length === 0) {
        message.warning('请选择待确认工资行，或改为“全部待确认行”');
        return;
      }

      const payload =
        values.scope === 'all'
          ? {
              assigneeEmployeeId: values.assigneeEmployeeId,
              applyAll: true,
            }
          : {
              assigneeEmployeeId: values.assigneeEmployeeId,
              lineIds: selectedLineIds,
            };

      const result = await assignMutation.mutateAsync({
        batchId: filters.batchId,
        payload,
      });
      message.success(`分配完成，更新 ${result.affected} 条`);
      await refreshAll();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '分配负责人失败');
    }
  };

  const columns: ColumnsType<PayrollPendingConfirmationDto> = [
    {
      title: '工资行ID',
      dataIndex: 'lineId',
      width: 100,
      fixed: 'left',
      render: (value: number) => <Text code>{value}</Text>,
    },
    {
      title: '批次ID',
      dataIndex: 'batchId',
      width: 100,
      render: (value: number) => value || '-',
    },
    {
      title: '期间',
      dataIndex: 'periodLabel',
      width: 120,
      render: (value: string) => value || '-',
    },
    {
      title: '员工工号',
      dataIndex: 'employeeNo',
      width: 130,
      render: (value: string) => value || '-',
    },
    {
      title: '姓名',
      dataIndex: 'employeeName',
      width: 120,
      render: (value: string) => value || '-',
    },
    {
      title: '部门',
      dataIndex: 'department',
      width: 160,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '实发',
      dataIndex: 'netAmount',
      width: 120,
      align: 'right',
      render: (value?: number, record) =>
        value != null ? `${value.toFixed(2)} ${record.currency || ''}`.trim() : '-',
    },
    {
      title: '确认状态',
      dataIndex: 'confirmationStatus',
      width: 140,
      render: (value: string) => statusTag(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            icon={<CheckCircleOutlined />}
            onClick={() => openConfirmModal(record)}
          >
            签字确认
          </Button>
          <Button
            size="small"
            type="link"
            danger
            icon={<ExclamationCircleOutlined />}
            onClick={() => openObjectModal(record)}
          >
            发起异议
          </Button>
        </Space>
      ),
    },
  ];

  const summary = summaryQuery.data;

  return (
    <PageContainer
      header={{
        title: '薪酬确认工作台',
        subTitle: '员工签字确认、异议提交流转、负责人批量确认',
        extra: [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={refreshAll}
            loading={pendingQuery.isFetching || summaryQuery.isFetching}
          >
            刷新
          </Button>,
        ],
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Space wrap>
            <InputNumber
              style={{ width: 180 }}
              min={1}
              placeholder="按批次ID筛选"
              value={batchIdInput}
              onChange={(value) => setBatchIdInput(value ?? undefined)}
            />
            <Button
              type="primary"
              onClick={() => {
                setSelectedLineIds([]);
                const next = new URLSearchParams(searchParams);
                if (batchIdInput) {
                  next.set('batchId', String(batchIdInput));
                } else {
                  next.delete('batchId');
                }
                setSearchParams(next);
                setFilters((prev) => ({
                  ...prev,
                  current: 1,
                  batchId: batchIdInput,
                }));
              }}
            >
              查询
            </Button>
            <Button
              onClick={() => {
                setBatchIdInput(undefined);
                setSelectedLineIds([]);
                const next = new URLSearchParams(searchParams);
                next.delete('batchId');
                setSearchParams(next);
                setFilters((prev) => ({
                  ...prev,
                  current: 1,
                  batchId: undefined,
                }));
              }}
            >
              重置
            </Button>
            <Button type="default" onClick={openBatchConfirmModal}>
              批量确认选中
            </Button>
          </Space>
          {!filters.batchId && (
            <Alert
              style={{ marginTop: 12 }}
              type="info"
              showIcon
              message="当前为“我的待确认”视图"
              description="普通员工仅能看到自己负责确认的工资条；财务/管理员可看到更多待确认记录。"
            />
          )}
        </Card>

        {filters.batchId && summary && (
          <Card title={`批次 ${filters.batchId} 确认汇总`}>
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto' }}>
              <Card size="small" style={{ minWidth: 140 }}>
                <Statistic title="批次状态" value={summary.batchStatus || '-'} />
              </Card>
              <Card size="small" style={{ minWidth: 140 }}>
                <Statistic title="确认模式" value={summary.confirmationMode || '-'} />
              </Card>
              <Card size="small" style={{ minWidth: 120 }}>
                <Statistic title="总行数" value={summary.totalLines ?? 0} />
              </Card>
              <Card size="small" style={{ minWidth: 120 }}>
                <Statistic title="待确认" value={summary.pendingCount ?? 0} />
              </Card>
              <Card size="small" style={{ minWidth: 120 }}>
                <Statistic title="已确认" value={summary.confirmedCount ?? 0} />
              </Card>
              <Card size="small" style={{ minWidth: 120 }}>
                <Statistic title="异议中" value={summary.objectedCount ?? 0} />
              </Card>
              <Card size="small" style={{ minWidth: 140 }}>
                <Statistic title="异议通过" value={summary.objectedApprovedCount ?? 0} />
              </Card>
              <Card size="small" style={{ minWidth: 140 }}>
                <Statistic title="异议驳回" value={summary.objectedRejectedCount ?? 0} />
              </Card>
            </div>
          </Card>
        )}

        {filters.batchId && (
          <Card title="负责人分配">
            <Form
              form={assignForm}
              layout="inline"
              initialValues={{ scope: 'selected' }}
              onFinish={submitAssign}
            >
              <Form.Item
                name="assigneeEmployeeId"
                label="负责人员工ID"
                rules={[{ required: true, message: '请输入负责人员工ID' }]}
              >
                <InputNumber min={1} style={{ width: 180 }} placeholder="例如 10086" />
              </Form.Item>
              <Form.Item name="scope" label="分配范围">
                <Select
                  style={{ width: 180 }}
                  options={[
                    { label: '选中工资行', value: 'selected' },
                    { label: '全部待确认行', value: 'all' },
                  ]}
                />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  icon={<TeamOutlined />}
                  htmlType="submit"
                  loading={assignMutation.isPending}
                >
                  分配负责人
                </Button>
              </Form.Item>
            </Form>
          </Card>
        )}

        <Card title="待确认工资行">
          <Table<PayrollPendingConfirmationDto>
            rowKey={(record) => record.lineId}
            columns={columns}
            dataSource={pendingRecords}
            loading={pendingQuery.isLoading || pendingQuery.isFetching}
            scroll={{ x: 1300 }}
            rowSelection={{
              selectedRowKeys: selectedLineIds,
              onChange: (keys) => setSelectedLineIds(keys as number[]),
            }}
            pagination={{
              current: filters.current,
              pageSize: filters.pageSize,
              total: pendingQuery.data?.total ?? 0,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (current, pageSize) => {
                setFilters((prev) => ({
                  ...prev,
                  current,
                  pageSize: pageSize || prev.pageSize,
                }));
              },
            }}
          />
        </Card>
      </Space>

      <Modal
        title={`工资行 ${activeLine?.lineId || '-'} 签字确认`}
        open={confirmModalOpen}
        onCancel={() => {
          setConfirmModalOpen(false);
          setActiveLine(null);
        }}
        onOk={submitConfirm}
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
        onCancel={() => {
          setObjectModalOpen(false);
          setActiveLine(null);
        }}
        onOk={submitObject}
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
        onCancel={() => setBatchConfirmModalOpen(false)}
        onOk={submitBatchConfirm}
        confirmLoading={batchConfirmMutation.isPending}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={`将对 ${selectedLineIds.length} 条工资行执行批量签字确认`}
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
    </PageContainer>
  );
};

export default PayrollConfirmations;
