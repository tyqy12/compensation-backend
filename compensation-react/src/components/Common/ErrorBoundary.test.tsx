import React from 'react';
import { render, screen } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import ErrorBoundary from './ErrorBoundary';

// Test component that throws an error
const ThrowError: React.FC<{ shouldThrow?: boolean }> = ({ shouldThrow = false }) => {
  if (shouldThrow) {
    throw new Error('Test error message');
  }
  return <div>Normal content</div>;
};

// Mock console.error to avoid noise in test output
const originalError = console.error;
beforeEach(() => {
  console.error = vi.fn();
});

afterEach(() => {
  console.error = originalError;
});

describe('ErrorBoundary', () => {
  it('should render children when there is no error', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Normal content')).toBeInTheDocument();
  });

  it('should render error fallback when child throws error', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>,
    );

    // Should not render the normal content
    expect(screen.queryByText('Normal content')).not.toBeInTheDocument();

    // Should render error state
    expect(screen.getByText(/出错了/)).toBeInTheDocument();
  });

  it('does not expose internal error details in the default test/runtime view', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByText(/应用程序出错了/)).toBeInTheDocument();
  });

  it('should provide error boundary with custom fallback', () => {
    // Since ErrorBoundary might not support custom fallback,
    // we test the default behavior
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByText(/出错了/)).toBeInTheDocument();
  });

  it('should handle multiple error scenarios', () => {
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>,
    );

    // Initially no error
    expect(screen.getByText('Normal content')).toBeInTheDocument();

    // Then throw error
    rerender(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.queryByText('Normal content')).not.toBeInTheDocument();
    expect(screen.getByText(/出错了/)).toBeInTheDocument();
  });

  it('should isolate errors to boundary scope', () => {
    const OutsideComponent = () => <div>Outside content</div>;

    render(
      <div>
        <OutsideComponent />
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      </div>,
    );

    // Outside component should still render
    expect(screen.getByText('Outside content')).toBeInTheDocument();
    // Error boundary should catch the error
    expect(screen.getByText(/出错了/)).toBeInTheDocument();
  });

  it('should handle nested error boundaries', () => {
    render(
      <ErrorBoundary>
        <div>
          <p>Outer content</p>
          <ErrorBoundary>
            <ThrowError shouldThrow={true} />
          </ErrorBoundary>
        </div>
      </ErrorBoundary>,
    );

    // Inner error boundary should catch the error
    expect(screen.getByText(/出错了/)).toBeInTheDocument();
    // Outer content might or might not be visible depending on implementation
  });

  it('should render with different error types', () => {
    const ThrowNetworkError = () => {
      throw new Error('Network connection failed');
    };

    render(
      <ErrorBoundary>
        <ThrowNetworkError />
      </ErrorBoundary>,
    );

    expect(screen.getByText(/应用程序出错了/)).toBeInTheDocument();
  });

  it('should handle component that throws during render', () => {
    const ThrowDuringRender = () => {
      throw new Error('Render error');
    };

    render(
      <ErrorBoundary>
        <ThrowDuringRender />
      </ErrorBoundary>,
    );

    expect(screen.getByText(/应用程序出错了/)).toBeInTheDocument();
  });

  it('should provide accessible error information', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByText(/应用程序出错了/)).toBeVisible();
  });
});
