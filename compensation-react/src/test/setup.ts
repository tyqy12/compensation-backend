import '@testing-library/jest-dom';
import { vi, beforeAll, afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Global test setup for the compensation-react project

// Cleanup after each test
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// Mock global objects
beforeAll(() => {
  // Mock window.matchMedia
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(), // deprecated
      removeListener: vi.fn(), // deprecated
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

  // Mock IntersectionObserver
  global.IntersectionObserver = vi.fn().mockImplementation(() => ({
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  }));

  // Mock window.getComputedStyle
  Object.defineProperty(window, 'getComputedStyle', {
    value: () => ({
      getPropertyValue: () => '',
      display: 'none',
      visibility: '',
      opacity: '',
      zIndex: '',
      height: '',
      width: '',
      position: '',
      top: '',
      left: '',
      transform: '',
    }),
  });

  // Mock scrollTo
  Object.defineProperty(window, 'scrollTo', {
    value: vi.fn(),
    writable: true,
  });

  // Mock localStorage
  const localStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  };
  Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
  });

  // Mock sessionStorage
  const sessionStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  };
  Object.defineProperty(window, 'sessionStorage', {
    value: sessionStorageMock,
  });

  // Suppress common warnings in tests
  const originalError = console.error;
  const originalWarn = console.warn;

  console.error = (...args: any[]) => {
    const message = args[0];
    if (
      typeof message === 'string' &&
      (message.includes('Warning:') ||
       message.includes('ReactDOM.render is no longer supported') ||
       message.includes('findDOMNode is deprecated') ||
       message.includes('React Router Future Flag Warning') ||
       message.includes('v7_startTransition'))
    ) {
      return;
    }
    originalError(...args);
  };

  console.warn = (...args: any[]) => {
    const message = args[0];
    if (
      typeof message === 'string' &&
      (message.includes('[antd:') ||
       message.includes('Warning:') ||
       message.includes('React Router Future Flag Warning') ||
       message.includes('findDOMNode is deprecated') ||
       message.includes('v7_startTransition'))
    ) {
      return;
    }
    originalWarn(...args);
  };
});

// Export test utilities
export const createMockFunction = <T extends (...args: any[]) => any>(
  implementation?: T
): ReturnType<typeof vi.fn> => {
  return vi.fn(implementation);
};

export const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0));
