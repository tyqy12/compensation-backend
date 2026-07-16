import { describe, expect, it } from 'vitest';
import type {
  PayrollBatchSummaryDto,
  PayrollDistributionDto,
  PayrollLedgerDto,
  PayrollReconciliationTaskDto,
} from '@types/openapi';
import {
  getPayrollOperationsQueue,
  getPayrollOperationsStages,
  type PayrollOperationsSnapshot,
} from './Operations';

const baseBatch: PayrollBatchSummaryDto = {
  batchId: 1001,
  periodLabel: '2026-06',
  payrollType: 'full_time',
  status: 'paid',
  calculationStatus: 'calculated',
  paymentStatus: 'success',
  batchRevision: 2,
  inputSnapshotHash: 'input-hash',
  ruleSnapshotHash: 'rule-hash',
  calculationEngineVersion: 'engine-2',
  confirmationRequired: true,
};

const createSnapshot = (
  overrides: Partial<PayrollOperationsSnapshot> = {},
): PayrollOperationsSnapshot => ({
  batch: { ...baseBatch },
  importItemCount: 3,
  ledger: {
    ...baseBatch,
    linesWithBlockingIssues: 0,
    blockingIssueCount: 0,
  } as PayrollLedgerDto,
  confirmation: { batchId: 1001, pendingCount: 0, totalLines: 3 },
  distributions: [
    {
      id: 2001,
      batchId: 1001,
      batchRevision: 2,
      distributionStatus: 'success',
    } as PayrollDistributionDto,
  ],
  reconciliations: [
    {
      id: 3001,
      batchId: 1001,
      batchRevision: 2,
      taskStatus: 'completed',
      result: 'matched',
    } as PayrollReconciliationTaskDto,
  ],
  routeBlockedCount: 0,
  staleDistributionCount: 0,
  ...overrides,
});

describe('Payroll operations workbench model', () => {
  it('recognizes a completed batch without creating false blockers', () => {
    const snapshot = createSnapshot();

    expect(getPayrollOperationsQueue(snapshot)).toEqual([]);
    expect(getPayrollOperationsStages(snapshot).map((stage) => stage.state)).toEqual([
      'done',
      'done',
      'done',
      'done',
      'done',
      'done',
    ]);
  });

  it('turns input, confirmation, route and reconciliation issues into actionable queue items', () => {
    const snapshot = createSnapshot({
      batch: {
        ...baseBatch,
        status: 'confirming',
        paymentStatus: 'processing',
      },
      importItemCount: 0,
      confirmation: { batchId: 1001, pendingCount: 4, totalLines: 4 },
      routeBlockedCount: 2,
      staleDistributionCount: 1,
      reconciliations: [
        {
          id: 3001,
          batchId: 1001,
          batchRevision: 2,
          taskStatus: 'completed',
          result: 'mismatch',
        },
      ],
    });

    expect(getPayrollOperationsQueue(snapshot).map((item) => item.key)).toEqual([
      'confirmation-pending',
      'route-blocked',
      'stale-distribution',
      'reconciliation-mismatch',
    ]);
    expect(getPayrollOperationsStages(snapshot).map((stage) => stage.state)).toEqual([
      'done',
      'done',
      'active',
      'pending',
      'blocked',
      'blocked',
    ]);
  });

  it('flags missing draft input and blocking calculation issues before downstream stages', () => {
    const snapshot = createSnapshot({
      batch: {
        ...baseBatch,
        status: 'draft',
        calculationStatus: 'failed',
        inputSnapshotHash: undefined,
        ruleSnapshotHash: undefined,
        calculationEngineVersion: undefined,
        paymentStatus: undefined,
      },
      importItemCount: 0,
      ledger: {
        ...baseBatch,
        hasBlockingIssues: true,
        linesWithBlockingIssues: 3,
        blockingIssueCount: 3,
      } as PayrollLedgerDto,
      distributions: [],
      reconciliations: [],
    });

    expect(getPayrollOperationsQueue(snapshot).map((item) => item.key)).toEqual([
      'input-missing',
      'calculation-blocked',
    ]);
    expect(getPayrollOperationsStages(snapshot).map((stage) => stage.state)).toEqual([
      'active',
      'blocked',
      'pending',
      'pending',
      'pending',
      'pending',
    ]);
  });
});
