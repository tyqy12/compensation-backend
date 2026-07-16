import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import EmptyState from './EmptyState';

describe('EmptyState', () => {
  it('renders the default description', () => {
    render(<EmptyState />);
    expect(screen.getByText('暂无数据')).toBeInTheDocument();
  });

  it('renders a custom description', () => {
    render(<EmptyState description="没有找到员工" />);
    expect(screen.getByText('没有找到员工')).toBeInTheDocument();
  });
});
