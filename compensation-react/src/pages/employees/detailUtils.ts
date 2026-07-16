import type { Employee, EmployeeFormData } from '@services/queries/employee';
import dayjs from 'dayjs';

export type SettlementAccountType = NonNullable<EmployeeFormData['settlementAccountType']>;
export type StatusColor = 'success' | 'default' | 'warning' | 'error';

const MASKED_VALUE_PATTERN = /\*{2,}/;

export function normalizeTextValue(value?: string | null) {
  if (value == null) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export function isMaskedDisplayValue(value?: string | null) {
  return Boolean(value && MASKED_VALUE_PATTERN.test(value));
}

export function normalizeSettlementAccountType(
  value?: string | null,
): SettlementAccountType | undefined {
  const normalized = normalizeTextValue(value)?.toLowerCase();
  if (
    normalized === 'bank_card' ||
    normalized === 'alipay' ||
    normalized === 'wechat' ||
    normalized === 'other'
  ) {
    return normalized;
  }
  return undefined;
}

export function isBankCardType(type?: string | null) {
  return normalizeSettlementAccountType(type) === 'bank_card';
}

export function getSettlementAccountLabel(type?: string | null) {
  switch (normalizeSettlementAccountType(type)) {
    case 'alipay':
      return '支付宝账号';
    case 'wechat':
      return '微信账号';
    default:
      return '收款账户';
  }
}

export function getSettlementAccountPlaceholder(type?: string | null) {
  switch (normalizeSettlementAccountType(type)) {
    case 'alipay':
      return '请输入支付宝账号（手机号/邮箱）';
    case 'wechat':
      return '请输入微信账号';
    default:
      return '请输入收款账户';
  }
}

export function getSettlementAccountTypeName(type?: string | null) {
  if (!type) {
    return '-';
  }
  const typeMap: Record<string, string> = {
    bank_card: '银行卡',
    alipay: '支付宝',
    wechat: '微信',
    other: '其他',
  };
  return typeMap[type] || type;
}

export function getStatusInfo(status?: Employee['status'] | null) {
  switch (status) {
    case 'active':
      return { text: '在职', color: 'success' as StatusColor };
    case 'inactive':
      return { text: '离职', color: 'default' as StatusColor };
    case 'suspended':
      return { text: '暂停', color: 'warning' as StatusColor };
    default:
      return { text: '未知', color: 'error' as StatusColor };
  }
}

export function getPlatformName(platformType?: string | null) {
  const platformMap: Record<string, string> = {
    wechat: '企业微信',
    dingtalk: '钉钉',
    feishu: '飞书',
  };
  return platformType ? platformMap[platformType] || platformType : '未绑定';
}

export function getPlatformColor(platformType?: string | null) {
  switch (platformType) {
    case 'wechat':
      return 'green';
    case 'dingtalk':
      return 'blue';
    case 'feishu':
      return 'orange';
    default:
      return 'default';
  }
}

export function getApprovalStatusColor(status?: string | null) {
  switch ((status || '').toLowerCase()) {
    case 'approved':
      return 'success';
    case 'rejected':
    case 'cancelled':
      return 'error';
    default:
      return 'processing';
  }
}

export function getPayslipStatusColor(status?: string | null) {
  switch ((status || '').toLowerCase()) {
    case 'paid':
    case 'success':
      return 'success';
    case 'failed':
    case 'error':
      return 'error';
    case 'processing':
      return 'processing';
    default:
      return 'default';
  }
}

export function getPaymentStatusColor(status?: string | null) {
  switch ((status || '').toLowerCase()) {
    case 'success':
    case 'paid':
      return 'success';
    case 'failed':
    case 'error':
      return 'error';
    case 'processing':
    case 'pending':
      return 'processing';
    default:
      return 'default';
  }
}

export function getConfirmationStatusColor(status?: string | null) {
  switch ((status || '').toLowerCase()) {
    case 'confirmed':
    case 'objected_approved':
      return 'success';
    case 'objected':
      return 'processing';
    case 'objected_rejected':
      return 'warning';
    default:
      return 'default';
  }
}

export function getConfirmationStatusText(status?: string | null) {
  const mapping: Record<string, string> = {
    pending: '待确认',
    confirmed: '已确认',
    objected: '异议处理中',
    objected_approved: '异议通过',
    objected_rejected: '异议驳回',
  };
  const normalized = (status || '').toLowerCase();
  return mapping[normalized] || status || '-';
}

export function formatDateTime(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

export function formatDate(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-';
}

export function formatAmount(value?: number | string | null) {
  return value == null || value === '' ? '-' : Number(value).toFixed(2);
}

export function getEmployeeInitials(name?: string | null) {
  const normalized = normalizeTextValue(name);
  return normalized ? normalized.slice(0, 2) : '员工';
}
