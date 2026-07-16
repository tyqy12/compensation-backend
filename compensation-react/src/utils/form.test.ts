import { describe, expect, it } from 'vitest';
import { formatDateTime, pickDirtyValues } from './form';

describe('form utilities', () => {
  it('returns only changed fields', () => {
    expect(pickDirtyValues(
      { name: '张三', age: 20 },
      { name: '李四', age: 20 },
    )).toEqual({ name: '李四' });
  });

  it('keeps explicit undefined changes', () => {
    expect(pickDirtyValues({ name: '张三' }, { name: undefined })).toEqual({ name: undefined });
  });

  it('formats a date for the application list views', () => {
    expect(formatDateTime('2026-07-12T08:30:00Z')).toMatch(/^2026-07-12/);
    expect(formatDateTime()).toBe('—');
  });
});
