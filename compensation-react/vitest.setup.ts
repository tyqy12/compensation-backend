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

// Mock getComputedStyle to avoid jsdom "Not implemented" errors when pseudoElt is passed.
Object.defineProperty(window, 'getComputedStyle', {
  writable: true,
  value: vi.fn().mockImplementation(() => ({
    getPropertyValue: vi.fn().mockReturnValue(''),
    display: 'block',
    visibility: 'visible',
    opacity: '1',
    zIndex: '1',
    height: '0px',
    width: '0px',
    position: 'static',
    top: '0px',
    left: '0px',
    transform: 'none',
  })),
});

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
