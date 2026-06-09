import { describe, expect, it } from 'vitest';
import type { ApprovalWorkflow } from '@services/queries/approval';
import {
  buildApprovalSearchParams,
  getApprovalTableRecords,
} from './workflowPageUtils';

describe('workflowPageUtils', () => {
  it('builds URL params from the next query state and tab', () => {
    const params = buildApprovalSearchParams({
      current: 2,
      pageSize: 20,
      status: 'pending',
      workflowType: 'PAYROLL_DISTRIBUTION',
      keyword: '9001',
      startDate: '2026-06-01',
      endDate: '2026-06-05',
      sortBy: 'submitTime',
      order: 'desc',
    }, 'pending');

    expect(Object.fromEntries(params)).toEqual({
      page: '2',
      size: '20',
      status: 'pending',
      workflowType: 'PAYROLL_DISTRIBUTION',
      keyword: '9001',
      startDate: '2026-06-01',
      endDate: '2026-06-05',
      sortBy: 'submitTime',
      order: 'desc',
      tab: 'pending',
    });
  });

  it('omits cleared filters from URL params', () => {
    const params = buildApprovalSearchParams({
      current: 1,
      pageSize: 10,
      status: undefined,
      workflowType: undefined,
      keyword: undefined,
      sortBy: 'submitTime',
      order: 'desc',
    }, 'list');

    expect(params.get('status')).toBeNull();
    expect(params.get('workflowType')).toBeNull();
    expect(params.get('keyword')).toBeNull();
    expect(params.get('tab')).toBe('list');
  });

  it('uses pending approval arrays directly', () => {
    const records = [{ id: 1 } as ApprovalWorkflow];

    expect(getApprovalTableRecords(records)).toBe(records);
  });

  it('supports paged list and records responses', () => {
    const listRecords = [{ id: 1 } as ApprovalWorkflow];
    const pageRecords = [{ id: 2 } as ApprovalWorkflow];

    expect(getApprovalTableRecords({ list: listRecords })).toBe(listRecords);
    expect(getApprovalTableRecords({ records: pageRecords })).toBe(pageRecords);
  });
});
