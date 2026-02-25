/**
 * 开发环境警告抑制配置
 * 用于抑制第三方库产生的非关键性警告
 */

export const suppressDevelopmentWarnings = () => {
  if (process.env.NODE_ENV !== 'production') {
    const originalError = console.error;
    const originalWarn = console.warn;

    // 保存原始的 error 方法用于调试
    const originalErrorFn = console.error;
    // 拦截所有 console.error 调用（包括 React 的警告）
    console.error = (...args: any[]) => {
      const message = args
        .map((arg) => (typeof arg === 'string' ? arg : String(arg)))
        .join(' ');
      // 检查是否为 findDOMNode 警告（来自 @ant-design/pro-components 的 ResizeObserver）
      if (
        message.includes('findDOMNode is deprecated') ||
        message.includes('DomWrapper4') ||
        message.includes('ResizeObserver') ||
        // React Router v7 transition warning
        message.includes('React Router Future Flag Warning') ||
        message.includes('v7_startTransition') ||
        // 其他常见 React 警告
        message.includes('ReactDOM.render is no longer supported') ||
        message.includes('Warning: React.jsx: type is invalid') ||
        // StrictMode 警告
        message.includes('StrictMode')
      ) {
        // 调试时可以取消注释来查看被抑制的警告
        // originalErrorFn('[SUPPRESSED]', message);
        return;
      }
      originalErrorFn(...args);
    };

    console.warn = (...args: any[]) => {
      const message = args[0];
      if (
        typeof message === 'string' &&
        // Ant Design 相关警告
        (message.includes('[antd:') ||
          // React Router 警告
          message.includes('React Router Future Flag Warning') ||
          message.includes('v7_startTransition') ||
          // findDOMNode 警告
          message.includes('findDOMNode is deprecated') ||
          // 其他第三方库警告
          message.includes('Warning: React.createElement:'))
      ) {
        // 抑制这些警告
        return;
      }
      originalWarn(...args);
    };
  }
};
