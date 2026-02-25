import dayjs from 'dayjs';

export function pickDirtyValues<T extends Record<string, any>>(
  original: T,
  current: T,
): Partial<T> {
  const result: Partial<T> = {};
  Object.keys(current).forEach((k) => {
    const key = k as keyof T;
    if (current[key] !== original[key]) {
      result[key] = current[key];
    }
  });
  return result;
}

export function formatDateTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}
