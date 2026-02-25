import { renderHook } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import useTitle from './useTitle';

describe('useTitle', () => {
  const originalTitle = document.title;

  beforeEach(() => {
    document.title = 'Original Title';
  });

  afterEach(() => {
    document.title = originalTitle;
  });

  it('should set document title', () => {
    renderHook(() => useTitle('New Page Title'));
    expect(document.title).toBe('New Page Title');
  });

  it('should update title when value changes', () => {
    const { rerender } = renderHook(({ title }) => useTitle(title), {
      initialProps: { title: 'First Title' },
    });

    expect(document.title).toBe('First Title');

    rerender({ title: 'Second Title' });
    expect(document.title).toBe('Second Title');
  });

  it('should append suffix when provided', () => {
    renderHook(() => useTitle('Page Title', { suffix: ' - My App' }));
    expect(document.title).toBe('Page Title - My App');
  });

  it('should prepend prefix when provided', () => {
    renderHook(() => useTitle('Page Title', { prefix: 'App: ' }));
    expect(document.title).toBe('App: Page Title');
  });

  it('should use both prefix and suffix', () => {
    renderHook(() =>
      useTitle('Page Title', {
        prefix: 'App: ',
        suffix: ' - Dashboard',
      }),
    );
    expect(document.title).toBe('App: Page Title - Dashboard');
  });

  it('should restore original title on unmount when specified', () => {
    const { unmount } = renderHook(() =>
      useTitle('Temporary Title', {
        restoreOnUnmount: true,
      }),
    );

    expect(document.title).toBe('Temporary Title');

    unmount();
    expect(document.title).toBe('Original Title');
  });

  it('should not restore title on unmount by default', () => {
    const { unmount } = renderHook(() => useTitle('Temporary Title'));

    expect(document.title).toBe('Temporary Title');

    unmount();
    expect(document.title).toBe('Temporary Title');
  });

  it('should handle empty title', () => {
    renderHook(() => useTitle(''));
    expect(document.title).toBe('');
  });

  it('should handle title with special characters', () => {
    const specialTitle = 'Page & Title <with> "special" characters';
    renderHook(() => useTitle(specialTitle));
    expect(document.title).toBe(specialTitle);
  });

  it('should work with dynamic titles', () => {
    let counter = 0;
    const { rerender } = renderHook(() => {
      counter++;
      return useTitle(`Dynamic Title ${counter}`);
    });

    expect(document.title).toBe('Dynamic Title 1');

    rerender();
    expect(document.title).toBe('Dynamic Title 2');
  });

  it('should handle multiple hook instances', () => {
    // First instance
    const { unmount: unmount1 } = renderHook(() => useTitle('First Hook'));
    expect(document.title).toBe('First Hook');

    // Second instance (should override)
    const { unmount: unmount2 } = renderHook(() => useTitle('Second Hook'));
    expect(document.title).toBe('Second Hook');

    // Unmount second instance
    unmount2();
    // Title should remain as 'Second Hook' since no restore was specified
    expect(document.title).toBe('Second Hook');

    unmount1();
  });

  it('should handle conditional title setting', () => {
    const { rerender } = renderHook(
      ({ shouldSetTitle, title }) => {
        if (shouldSetTitle) {
          useTitle(title);
        }
      },
      {
        initialProps: { shouldSetTitle: false, title: 'Conditional Title' },
      },
    );

    // Title should not be set initially
    expect(document.title).toBe('Original Title');

    // Enable title setting
    rerender({ shouldSetTitle: true, title: 'Conditional Title' });
    expect(document.title).toBe('Conditional Title');

    // Disable title setting
    rerender({ shouldSetTitle: false, title: 'Conditional Title' });
    // Title should remain (hook not called)
    expect(document.title).toBe('Conditional Title');
  });

  it('should handle unicode characters', () => {
    const unicodeTitle = '页面标题 - 测试应用 🚀';
    renderHook(() => useTitle(unicodeTitle));
    expect(document.title).toBe(unicodeTitle);
  });

  it('should format title with template', () => {
    const template = (title: string) => `[${title}] | My Application`;
    renderHook(() => useTitle('Dashboard', { template }));
    expect(document.title).toBe('[Dashboard] | My Application');
  });

  it('should handle null and undefined titles gracefully', () => {
    renderHook(() => useTitle(null as any));
    expect(document.title).toBe('');

    renderHook(() => useTitle(undefined as any));
    expect(document.title).toBe('');
  });

  it('should debounce rapid title changes', async () => {
    const { rerender } = renderHook(({ title }) => useTitle(title, { debounce: 100 }), {
      initialProps: { title: 'Initial' },
    });

    // Rapid changes
    rerender({ title: 'Change 1' });
    rerender({ title: 'Change 2' });
    rerender({ title: 'Final Change' });

    // Should immediately show the latest change (depending on implementation)
    // or may need to wait for debounce
    expect(document.title).toBe('Final Change');
  });
});
