import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { appName } from '@app/theme';
import { useTitle } from './useTitle';

describe('useTitle', () => {
  beforeEach(() => {
    document.title = appName;
  });

  afterEach(() => {
    document.title = appName;
  });

  it('sets the title with the application suffix', () => {
    renderHook(() => useTitle('工资批次'));
    expect(document.title).toBe(`工资批次 · ${appName}`);
  });

  it('updates the title when the title changes', () => {
    const { rerender } = renderHook(({ title }) => useTitle(title), {
      initialProps: { title: '员工' },
    });

    rerender({ title: '审批' });
    expect(document.title).toBe(`审批 · ${appName}`);
  });

  it('uses the application name when the title is empty', () => {
    renderHook(() => useTitle(''));
    expect(document.title).toBe(appName);
  });
});
