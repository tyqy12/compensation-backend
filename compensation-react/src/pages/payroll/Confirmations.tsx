import React, { useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  InputNumber,
  Select,
  Space,
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
  getBatchRevisionText,
  getCalculationStatusMeta,
  getConfirmationModeText,
  getFlowStatusMeta,
} from './components/payrollFlow';
import {
  useAssignPayrollConfirmationMutation,
  useBatchConfirmPayrollMutation,
  useConfirmPayrollPayslipMutation,
  useObjectPayrollPayslipMutation,
  usePayrollBatchDetailQuery,
  usePayrollConfirmationSummaryQuery,
  usePayrollPendingConfirmationsQuery,
  type PayrollPendingConfirmationDto,
} from '@services/queries/payroll';
import ConfirmationActionModals, {
  type ConfirmationFormValues,
  type ObjectFormValues,
} from './components/ConfirmationActionModals';
import { PayrollMetricGrid, PayrollSection } from './components/PayrollPagePrimitives';
import './PayrollPages.less';

const { Text } = Typography;

const confirmationStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待确认', color: 'default' },
  confirmed: { text: '已确认', color: 'success' },
  skipped: { text: '已跳过', color: 'default' },
  timeout: { text: '已超时', color: 'warning' },
  rejected: { text: '已拒绝', color: 'error' },
  superseded: { text: '已作废', color: 'default' },
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

  const [confirmForm] = Form.useForm<ConfirmationFormValues>();
  const [objectForm] = Form.useForm<ObjectFormValues>();
  const [batchConfirmForm] = Form.useForm<ConfirmationFormValues>();
  const [assignForm] = Form.useForm<{ assigneeEmployeeId: number; scope: 'selected' | 'all' }>();

  const pendingQuery = usePayrollPendingConfirmationsQuery(filters);
  const batchDetailQuery = usePayrollBatchDetailQuery(filters.batchId ?? 0, {
    enabled: Boolean(filters.batchId),
  });
  const summaryQuery = usePayrollConfirmationSummaryQuery(filters.batchId ?? 0, {
    enabled: Boolean(filters.batchId),
  });

  const confirmMutation = useConfirmPayrollPayslipMutation();
  const objectMutation = useObjectPayrollPayslipMutation();
  const batchConfirmMutation = useBatchConfirmPayrollMutation();
  const assignMutation = useAssignPayrollConfirmationMutation();

  const pendingRecords = pendingQuery.data?.records ?? [];

  const refreshAll = async () => {
    await pendingQuery.refetch();
    if (filters.batchId) {
      await Promise.all([batchDetailQuery.refetch(), summaryQuery.refetch()]);
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
      title: '员工',
      key: 'employee',
      width: 190,
      fixed: 'left',
      render: (_, record) => (
        <div className="payroll-batch-identity">
          <div>
            <Text strong>{record.employeeName || '-'}</Text>
            <div className="payroll-batch-identity-meta">
              <span>{record.employeeNo || '-'}</span>
              <span>工资行 #{record.lineId}</span>
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '部门',
      dataIndex: 'department',
      width: 150,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '期间',
      dataIndex: 'periodLabel',
      width: 120,
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
      width: 180,
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
  const batchDetail = batchDetailQuery.data;
  const summaryMetrics = summary
    ? [
        {
          key: 'flowStatus',
          title: '流转状态',
          value: getFlowStatusMeta(batchDetail?.status)?.text ?? summary.batchStatus ?? '-',
        },
        {
          key: 'calculationStatus',
          title: '核算状态',
          value:
            getCalculationStatusMeta(batchDetail?.calculationStatus ?? batchDetail?.computeStatus)
              ?.text ?? '-',
        },
        {
          key: 'revision',
          title: '批次版本',
          value: getBatchRevisionText(batchDetail?.batchRevision),
        },
        {
          key: 'confirmationMode',
          title: '确认策略',
          value: getConfirmationModeText(
            batchDetail?.confirmationMode,
            batchDetail?.confirmationRequired,
          ),
        },
        { key: 'total', title: '总行数', value: summary.totalLines ?? 0 },
        { key: 'pending', title: '待确认', value: summary.pendingCount ?? 0 },
        { key: 'confirmed', title: '已确认', value: summary.confirmedCount ?? 0 },
        { key: 'objected', title: '异议中', value: summary.objectedCount ?? 0 },
      ]
    : [];

  return (
    <PageContainer
      className="payroll-page-shell"
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
      <div className="payroll-page-main">
        <PayrollSection
          title="筛选待确认工资行"
          description="按批次查看全部待确认行；不筛选批次时显示当前账号负责的待确认行"
        >
          <div className="payroll-filter-bar">
            <InputNumber
              className="payroll-filter-field"
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
          </div>
          {!filters.batchId && (
            <Alert
              className="payroll-context-alert"
              type="info"
              showIcon
              title="当前为我的待确认视图"
              description="筛选具体批次后可进行批量确认和负责人分配。"
            />
          )}
        </PayrollSection>

        {filters.batchId && summary && (
          <PayrollSection
            title={`批次 ${filters.batchId} 确认汇总`}
            description="确认状态和批次流转状态"
          >
            <PayrollMetricGrid items={summaryMetrics} />
            {batchDetail?.approvalWorkflowId && (
              <Alert
                className="payroll-context-alert"
                type="info"
                showIcon
                title={`当前版本已关联审批流 #${batchDetail.approvalWorkflowId}`}
                description="若确认已完成但尚未提审，请返回批次工作台继续推进审批与发放。"
              />
            )}
          </PayrollSection>
        )}

        {filters.batchId && (
          <PayrollSection
            title="负责人分配"
            description="将选中工资行或全部待确认行分配给指定负责人"
          >
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
          </PayrollSection>
        )}

        <PayrollSection
          title="待确认工资行"
          description={`共 ${pendingQuery.data?.total ?? 0} 条，已选择 ${selectedLineIds.length} 条`}
          className="payroll-table-section"
          extra={
            <Button type="primary" onClick={openBatchConfirmModal}>
              批量确认选中
            </Button>
          }
        >
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
        </PayrollSection>
      </div>

      <ConfirmationActionModals
        activeLine={activeLine}
        selectedCount={selectedLineIds.length}
        confirmModalOpen={confirmModalOpen}
        objectModalOpen={objectModalOpen}
        batchConfirmModalOpen={batchConfirmModalOpen}
        confirmForm={confirmForm}
        objectForm={objectForm}
        batchConfirmForm={batchConfirmForm}
        confirmMutation={confirmMutation}
        objectMutation={objectMutation}
        batchConfirmMutation={batchConfirmMutation}
        onConfirm={submitConfirm}
        onObject={submitObject}
        onBatchConfirm={submitBatchConfirm}
        onCloseConfirm={() => {
          setConfirmModalOpen(false);
          setActiveLine(null);
        }}
        onCloseObject={() => {
          setObjectModalOpen(false);
          setActiveLine(null);
        }}
        onCloseBatchConfirm={() => setBatchConfirmModalOpen(false)}
      />
    </PageContainer>
  );
};

export default PayrollConfirmations;
