import { describe, expect, it } from 'vitest';
import {
  getBatchRevisionText,
  getCalculationStatusMeta,
  getDistributionStatusMeta,
  getPayrollBlockers,
  getPayrollFlowCurrentStep,
  getPayrollNextAction,
} from './payrollFlow';

describe('payrollFlow helpers', () => {
  it('normalizes calculation and distribution statuses from legacy aliases', () => {
    expect(getCalculationStatusMeta('completed')).toMatchObject({ text: '计算完成' });
    expect(getDistributionStatusMeta('paid')).toMatchObject({ text: '全部成功' });
  });

  it('builds revision label and current step for V2.1 batch states', () => {
    expect(getBatchRevisionText(3)).toBe('R3');
    expect(
      getPayrollFlowCurrentStep({
        status: 'APPROVED',
        calculationStatus: 'CALCULATED',
        paymentStatus: 'PROCESSING',
        batchRevision: 3,
      }),
    ).toBe(4);
  });

  it('returns next action and blockers for draft batch without import data', () => {
    const record = {
      status: 'DRAFT',
      calculationStatus: 'DRAFT',
      confirmationRequired: true,
    };

    expect(getPayrollNextAction(record, false, true)).toContain('先录入数据');
    expect(getPayrollBlockers(record, false)).toContain('当前还没有任何导入项，无法进入后续核算与审批。');
  });

  it('marks failed calculation as a blocking state', () => {
    const record = {
      status: 'LOCKED',
      calculationStatus: 'FAILED',
      confirmationRequired: true,
    };

    expect(getPayrollNextAction(record, true, true)).toContain('核算失败');
    expect(getPayrollBlockers(record, true)).toContain('当前版本核算失败，需要先修复数据或规则错误。');
  });
});
