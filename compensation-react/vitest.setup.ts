import '@testing-library/jest-dom/vitest';
import { beforeEach, vi } from 'vitest';

// Ensure dynamic imports re-evaluate modules between tests
beforeEach(() => {
  vi.resetModules();
});

// Mock matchMedia for Ant Design responsive components
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Prevent unhandled promise rejections from aborting the test run
if (typeof process !== 'undefined' && 'on' in process) {
  // Node side
  (process as any).on('unhandledRejection', () => {});
}

// Browser (jsdom) side
if (typeof window !== 'undefined' && 'addEventListener' in window) {
  window.addEventListener('unhandledrejection', (e) => {
    e.preventDefault();
  });
}
