import { describe, expect, it } from 'vitest';
import {
  getBatchRevisionText,
  getCalculationEvidenceMeta,
  getCalculationEvidenceStatus,
  getCalculationStatusMeta,
  getDistributionStatusMeta,
  getPayrollBlockers,
  getPayrollFlowCurrentStep,
  getPayrollNextAction,
  getSettlementRouteFailureMeta,
  getSnapshotHashPreview,
  isSettlementRouteBlocked,
  isPayrollBatchComputable,
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
    expect(getPayrollBlockers(record, false)).toContain(
      '当前还没有任何导入项，无法进入后续核算与审批。',
    );
  });

  it('marks failed calculation as a blocking state', () => {
    const record = {
      status: 'LOCKED',
      calculationStatus: 'FAILED',
      confirmationRequired: true,
    };

    expect(getPayrollNextAction(record, true, true)).toContain('核算失败');
    expect(getPayrollBlockers(record, true)).toContain(
      '当前版本核算失败，需要先修复数据或规则错误。',
    );
  });

  it('matches backend computable payroll batch statuses', () => {
    expect(isPayrollBatchComputable('locked')).toBe(true);
    expect(isPayrollBatchComputable('confirming')).toBe(true);
    expect(isPayrollBatchComputable('dispute_processing')).toBe(true);
    expect(isPayrollBatchComputable('rejected')).toBe(true);
    expect(isPayrollBatchComputable('confirmed')).toBe(false);
    expect(isPayrollBatchComputable('approved')).toBe(false);
    expect(isPayrollBatchComputable('pay_failed')).toBe(false);
    expect(isPayrollBatchComputable('pay_processing')).toBe(false);
  });

  it('exposes calculation evidence completeness without exposing snapshot JSON', () => {
    const complete = {
      inputSnapshotHash: 'a'.repeat(64),
      ruleSnapshotHash: 'b'.repeat(64),
      calculationEngineVersion: 'basic-v1',
    };

    expect(getCalculationEvidenceStatus(complete)).toBe('complete');
    expect(getCalculationEvidenceMeta(complete)).toMatchObject({ text: '证据完整' });
    expect(getCalculationEvidenceStatus({ inputSnapshotHash: complete.inputSnapshotHash })).toBe(
      'partial',
    );
    expect(getCalculationEvidenceStatus({})).toBe('missing');
    expect(getSnapshotHashPreview(complete.inputSnapshotHash)).toBe('aaaaaaaa…aaaaaaaa');
  });

  it('classifies distribution items blocked before payment routing', () => {
    expect(
      isSettlementRouteBlocked({
        itemStatus: 'failed',
        paymentMethod: 'UNKNOWN',
        failureReason: '缺少收款账号',
      }),
    ).toBe(true);
    expect(
      isSettlementRouteBlocked({
        itemStatus: 'failed',
        paymentRecordId: 1001,
        paymentMethod: 'UNKNOWN',
      }),
    ).toBe(false);
    expect(getSettlementRouteFailureMeta('ACCOUNT_DECRYPT_FAILED')).toMatchObject({
      text: '收款账号解密失败',
      color: 'error',
    });
  });
});
