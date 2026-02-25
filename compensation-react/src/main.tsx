import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppProviders } from './app/AppProviders';
import 'antd/dist/reset.css';
import '@styles/global.less';

// 需要抑制的警告列表
const suppressedWarnings = [
  'findDOMNode is deprecated',
  'DomWrapper4',
  'ResizeObserver',
  'React Router Future Flag',
  'validateDOMNesting',
  // Ant Design 已知问题
  '[antd:',
  'each child in a list should have a unique',
  // Ant Design ProComponents 已知问题（StrictMode 兼容）
  'Each child in a list should have a unique "key" prop',
];

// 抑制 console.warn 警告
const originalWarn = console.warn;
console.warn = (...args: unknown[]) => {
  const message = args.join(' ');
  // 只抑制已知问题的警告，不抑制可能影响功能的错误
  if (!suppressedWarnings.some(warning => message.includes(warning))) {
    originalWarn.apply(console, args);
  }
};

// 抑制 console.error 警告（React 的警告通过 error 输出）
const originalError = console.error;
console.error = (...args: unknown[]) => {
  const message = args.join(' ');
  // 抑制来自第三方库的 React 警告（包括 findDOMNode）
  if (!suppressedWarnings.some(warning => message.includes(warning))) {
    originalError.apply(console, args);
  }
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders />
  </React.StrictMode>,
);
