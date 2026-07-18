import React, { useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  AuditOutlined,
  CalculatorOutlined,
  FileProtectOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import api, { unwrap, type ApiResponse } from '@services/api';
import './PayrollPages.less';

const { Text, Title } = Typography;

type TaxBracket = {
  level: number;
  upperLimit?: number;
  rate: number;
  quickDeduction: number;
};

type DeductionType = { code: string; label: string };

type PolicyPackage = {
  id: number;
  code: string;
  name: string;
  policyType: string;
  regionCode?: string;
  effectiveFrom: string;
  effectiveTo?: string;
  status: string;
  versionNo: number;
  sourceDocument?: string;
  sourceUrl?: string;
};

type TaxResult = {
  cumulativeTaxableIncome: number;
  rate: number;
  quickDeduction: number;
  cumulativeTaxBeforeReduction: number;
  currentWithholdingTax: number;
  bracketLevel: number;
  formula: string;
};

type TaxFormValues = {
  cumulativeIncome: number;
  cumulativeTaxExemptIncome: number;
  cumulativeBasicDeduction: number;
  cumulativeSpecialDeduction: number;
  cumulativeSpecialAdditionalDeduction: number;
  cumulativeOtherDeduction: number;
  cumulativeTaxReduction: number;
  cumulativeWithheldTax: number;
};

type ContributionFormValues = {
  contributionType: string;
  declaredWage: number;
  baseMin?: number;
  baseMax?: number;
  employerRate: number;
  employeeRate: number;
  employerFixedAmount: number;
  employeeFixedAmount: number;
};

type ContributionResult = {
  contributionBase: number;
  employerAmount: number;
  employeeAmount: number;
  formula: string;
};

type AnnualBonusResult = {
  monthlyMatch: number;
  rate: number;
  quickDeduction: number;
  taxAmount: number;
  bracketLevel: number;
};

const money = (value?: number) => (value === undefined ? '-' : `¥${Number(value).toFixed(2)}`);

const CompliancePage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [taxResult, setTaxResult] = useState<TaxResult>();
  const [taxForm] = Form.useForm<TaxFormValues>();
  const [contributionForm] = Form.useForm<ContributionFormValues>();
  const [contributionResult, setContributionResult] = useState<ContributionResult>();
  const [annualBonus, setAnnualBonus] = useState<number>(0);
  const [annualBonusResult, setAnnualBonusResult] = useState<AnnualBonusResult>();
  const [employeeId, setEmployeeId] = useState<number>();
  const [taxYear, setTaxYear] = useState<number>(new Date().getFullYear());

  const bracketsQuery = useQuery({
    queryKey: ['payroll-compliance', 'tax-brackets'],
    queryFn: async () => unwrap((await api.get<ApiResponse<TaxBracket[]>>('/payroll/compliance/tax/brackets')).data),
  });
  const deductionTypesQuery = useQuery({
    queryKey: ['payroll-compliance', 'deduction-types'],
    queryFn: async () => unwrap((await api.get<ApiResponse<DeductionType[]>>('/payroll/compliance/deduction-types')).data),
  });
  const policiesQuery = useQuery({
    queryKey: ['payroll-compliance', 'policies'],
    queryFn: async () => unwrap((await api.get<ApiResponse<PolicyPackage[]>>('/payroll/compliance/policies?status=published')).data),
  });
  const ledgerQuery = useQuery({
    queryKey: ['payroll-compliance', 'ledger', employeeId, taxYear],
    enabled: Boolean(employeeId),
    queryFn: async () => unwrap((await api.get<ApiResponse<Record<string, unknown>[]>>('/payroll/compliance/tax-ledger', {
      params: { employeeId, taxYear },
    })).data),
  });

  const policy = useMemo(
    () => policiesQuery.data?.find((item) => item.code === 'CN.RESIDENT_WAGE_WITHHOLDING'),
    [policiesQuery.data],
  );

  const calculateTax = async (values: TaxFormValues) => {
    try {
      const response = await api.post<ApiResponse<TaxResult>>('/payroll/compliance/tax/preview', {
        ...values,
        scale: 2,
        roundingMode: 'HALF_UP',
      });
      setTaxResult(unwrap(response.data));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '个税试算失败');
    }
  };

  const calculateContribution = async (values: ContributionFormValues) => {
    try {
      const response = await api.post<ApiResponse<ContributionResult>>('/payroll/compliance/contributions/preview', {
        ...values,
        roundingMode: 'HALF_UP',
      });
      setContributionResult(unwrap(response.data));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '五险一金试算失败');
    }
  };

  const calculateAnnualBonus = async () => {
    try {
      const response = await api.post<ApiResponse<AnnualBonusResult>>('/payroll/compliance/tax/annual-bonus-preview', {
        annualBonus,
      });
      setAnnualBonusResult(unwrap(response.data));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '全年一次性奖金试算失败');
    }
  };

  return (
    <PageContainer
      className="payroll-page-shell"
      title="合规薪酬工作台"
      subTitle="政策版本、累计个税、扣除台账与多地参保"
      extra={<Button icon={<ReloadOutlined />} onClick={() => void Promise.all([policiesQuery.refetch(), bracketsQuery.refetch()])}>刷新政策</Button>}
    >
      <Alert
        type="info"
        showIcon
        icon={<SafetyCertificateOutlined />}
        message="当前核算模型已切换为累计预扣"
        description="固定比例个税模板不能发布；政策发布、员工扣除申报和参保关系变更均需留下版本与证据。"
        style={{ marginBottom: 16 }}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
          <Card title={<Space><FileProtectOutlined />当前政策包</Space>} loading={policiesQuery.isLoading}>
            {policy ? (
              <Descriptions column={1} size="small">
                <Descriptions.Item label="版本">{policy.code} · v{policy.versionNo}</Descriptions.Item>
                <Descriptions.Item label="生效">{policy.effectiveFrom}</Descriptions.Item>
                <Descriptions.Item label="状态"><Tag color="green">已发布</Tag></Descriptions.Item>
                <Descriptions.Item label="依据">{policy.sourceDocument || '待补充'}</Descriptions.Item>
              </Descriptions>
            ) : <Text type="secondary">尚未加载已发布政策包，试算使用内置首期政策表。</Text>}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={<Space><TeamOutlined />专项附加扣除</Space>} loading={deductionTypesQuery.isLoading}>
            <Space wrap>
              {(deductionTypesQuery.data || []).map((item) => <Tag key={item.code}>{item.label}</Tag>)}
            </Space>
            <Divider style={{ margin: '16px 0' }} />
            <Text type="secondary">大病医疗按年度汇算场景记录，个人养老金按年度凭证和限额记录。</Text>
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={<Space><AuditOutlined />税率档位</Space>} loading={bracketsQuery.isLoading}>
            <Table<TaxBracket>
              size="small"
              pagination={false}
              rowKey="level"
              dataSource={bracketsQuery.data || []}
              columns={[
                { title: '级数', dataIndex: 'level', width: 52 },
                { title: '税率', dataIndex: 'rate', render: (value: number) => `${(value * 100).toFixed(0)}%` },
                { title: '速算扣除', dataIndex: 'quickDeduction', render: (value: number) => money(value) },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card title={<Space><CalculatorOutlined />累计预扣试算</Space>} style={{ marginTop: 16 }}>
        <Form<TaxFormValues>
          form={taxForm}
          layout="vertical"
          initialValues={{
            cumulativeIncome: 12000,
            cumulativeTaxExemptIncome: 0,
            cumulativeBasicDeduction: 5000,
            cumulativeSpecialDeduction: 0,
            cumulativeSpecialAdditionalDeduction: 0,
            cumulativeOtherDeduction: 0,
            cumulativeTaxReduction: 0,
            cumulativeWithheldTax: 0,
          }}
          onFinish={(values) => void calculateTax(values)}
        >
          <Row gutter={16}>
            {[
              ['cumulativeIncome', '累计收入'],
              ['cumulativeTaxExemptIncome', '累计免税收入'],
              ['cumulativeBasicDeduction', '累计减除费用'],
              ['cumulativeSpecialDeduction', '累计专项扣除'],
              ['cumulativeSpecialAdditionalDeduction', '累计专项附加扣除'],
              ['cumulativeOtherDeduction', '累计其他依法扣除'],
              ['cumulativeTaxReduction', '累计减免税额'],
              ['cumulativeWithheldTax', '累计已预扣税额'],
            ].map(([name, label]) => (
              <Col xs={24} sm={12} lg={6} key={name}>
                <Form.Item name={name} label={label} rules={[{ required: true, message: '请输入金额' }]}>
                  <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            ))}
          </Row>
          <Button type="primary" htmlType="submit" icon={<CalculatorOutlined />}>计算本期应预扣税额</Button>
        </Form>
        {taxResult && (
          <>
            <Divider />
            <Row gutter={[16, 16]}>
              <Col xs={12} md={6}><Statistic title="累计应纳税所得额" value={money(taxResult.cumulativeTaxableIncome)} /></Col>
              <Col xs={12} md={6}><Statistic title="适用税率" value={`${(taxResult.rate * 100).toFixed(0)}%`} /></Col>
              <Col xs={12} md={6}><Statistic title="累计应纳税额" value={money(taxResult.cumulativeTaxBeforeReduction)} /></Col>
              <Col xs={12} md={6}><Statistic title="本期应预扣" value={money(taxResult.currentWithholdingTax)} valueStyle={{ color: '#1677ff' }} /></Col>
            </Row>
            <Descriptions size="small" column={1} style={{ marginTop: 16 }}>
              <Descriptions.Item label="命中档位">第 {taxResult.bracketLevel} 档</Descriptions.Item>
              <Descriptions.Item label="公式">{taxResult.formula}</Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Card>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={16}>
          <Card title={<Space><SafetyCertificateOutlined />五险一金政策试算</Space>}>
            <Form<ContributionFormValues>
              form={contributionForm}
              layout="vertical"
              initialValues={{
                contributionType: 'pension',
                declaredWage: 10000,
                employerRate: 0,
                employeeRate: 0,
                employerFixedAmount: 0,
                employeeFixedAmount: 0,
              }}
              onFinish={(values) => void calculateContribution(values)}
            >
              <Row gutter={16}>
                <Col xs={24} sm={12} lg={6}>
                  <Form.Item name="contributionType" label="险种" rules={[{ required: true }]}>
                    <Select options={[
                      { label: '养老', value: 'pension' },
                      { label: '医疗', value: 'medical' },
                      { label: '失业', value: 'unemployment' },
                      { label: '工伤', value: 'work_injury' },
                      { label: '生育', value: 'maternity' },
                      { label: '住房公积金', value: 'housing_fund' },
                    ]} />
                  </Form.Item>
                </Col>
                {[
                  ['declaredWage', '申报工资'],
                  ['baseMin', '基数下限'],
                  ['baseMax', '基数上限'],
                  ['employerRate', '单位比例'],
                  ['employeeRate', '个人比例'],
                ].map(([name, label]) => (
                  <Col xs={24} sm={12} lg={6} key={name}>
                    <Form.Item name={name} label={label}>
                      <InputNumber min={0} precision={6} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                ))}
              </Row>
              <Button type="primary" htmlType="submit" icon={<CalculatorOutlined />}>计算单位/个人金额</Button>
            </Form>
            {contributionResult && (
              <Descriptions size="small" column={{ xs: 1, sm: 3 }} style={{ marginTop: 16 }}>
                <Descriptions.Item label="缴费基数">{money(contributionResult.contributionBase)}</Descriptions.Item>
                <Descriptions.Item label="单位承担">{money(contributionResult.employerAmount)}</Descriptions.Item>
                <Descriptions.Item label="个人承担">{money(contributionResult.employeeAmount)}</Descriptions.Item>
                <Descriptions.Item label="公式">{contributionResult.formula}</Descriptions.Item>
              </Descriptions>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={<Space><CalculatorOutlined />全年一次性奖金</Space>}>
            <Space.Compact style={{ width: '100%' }}>
              <InputNumber min={0} precision={2} value={annualBonus} onChange={(value) => setAnnualBonus(value || 0)} style={{ width: '100%' }} />
              <Button type="primary" onClick={() => void calculateAnnualBonus()}>试算</Button>
            </Space.Compact>
            {annualBonusResult && (
              <Descriptions size="small" column={1} style={{ marginTop: 16 }}>
                <Descriptions.Item label="月度匹配数">{money(annualBonusResult.monthlyMatch)}</Descriptions.Item>
                <Descriptions.Item label="适用税率">{(annualBonusResult.rate * 100).toFixed(0)}%</Descriptions.Item>
                <Descriptions.Item label="应纳税额">{money(annualBonusResult.taxAmount)}</Descriptions.Item>
              </Descriptions>
            )}
            <Text type="secondary">是否符合全年一次性奖金条件需由业务审核，不能仅凭项目名称判断。</Text>
          </Card>
        </Col>
      </Row>

      <Card title="员工累计台账查询" style={{ marginTop: 16 }}>
        <Space wrap>
          <InputNumber min={1} placeholder="员工ID" value={employeeId} onChange={(value) => setEmployeeId(value || undefined)} />
          <Select value={taxYear} onChange={setTaxYear} options={[taxYear - 1, taxYear, taxYear + 1].map((year) => ({ label: `${year}年度`, value: year }))} />
        </Space>
        {employeeId && (
          <Table
            style={{ marginTop: 16 }}
            size="small"
            loading={ledgerQuery.isLoading}
            rowKey={(row) => String(row.id)}
            dataSource={ledgerQuery.data || []}
            columns={[
              { title: '月份', dataIndex: 'taxMonth' },
              { title: '累计应纳税所得额', dataIndex: 'cumulativeTaxableIncome', render: money },
              { title: '本期预扣', dataIndex: 'currentWithholdingTax', render: money },
              { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'posted' ? 'green' : 'orange'}>{value}</Tag> },
            ]}
          />
        )}
      </Card>
    </PageContainer>
  );
};

export default CompliancePage;
