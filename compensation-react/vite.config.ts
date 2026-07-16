import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// https://vitejs.dev/config/
const r = (p: string) => path.resolve(fileURLToPath(new URL('.', import.meta.url)), p);

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080';
  return {
    plugins: [
      react({
        babel: {
          parserOpts: {
            plugins: ['decoratorAutoAccessors'],
          },
        },
      }),
    ],
    build: {
      rollupOptions: {
        output: {
          manualChunks: (id) => {
            if (id.includes('node_modules/@ant-design/pro-components')) return 'pro-components';
            if (id.includes('node_modules/antd')) return 'antd';
          },
        },
      },
    },
    resolve: {
      alias: {
        '@app': r('src/app'),
        '@routes': r('src/routes'),
        '@components': r('src/components'),
        '@pages': r('src/pages'),
        '@services': r('src/services'),
        '@hooks': r('src/hooks'),
        '@utils': r('src/utils'),
        '@types': r('src/types'),
        '@styles': r('src/styles'),
        '@config': r('src/config'),
        /**
         * 兼容 antd@6 (ESM) 与 pro-components 对 antd/lib 子路径的引用。
         *
         * pro-components 的 ESM 包内使用 `import ... from 'antd/lib/table/hooks/xxx'`，
         * 该子路径在 antd@6 下是 CJS（且通过 exports.default 导出），在 Vite dev 的 ESM 语义下
         * default import 会拿到模块对象而不是函数，从而触发：
         *   (0, import_useLazyKVMap.default) is not a function
         *
         * 直接别名到 antd/es 对应 ESM 实现，避免 CJS/ESM default interop 坑。
         */
        'antd/lib/table/hooks/useLazyKVMap': 'antd/es/table/hooks/useLazyKVMap',
        'antd/lib/table/hooks/usePagination': 'antd/es/table/hooks/usePagination',
        'antd/lib/table/hooks/useSelection': 'antd/es/table/hooks/useSelection',
        'antd/lib/grid/hooks/useBreakpoint': 'antd/es/grid/hooks/useBreakpoint',
        // Vitest resolves package main before the ESM entry and loads pro-components/lib (CJS)
        // inside the ESM test runner. Pin the package to its published ESM entry.
        '@ant-design/pro-components': r('node_modules/@ant-design/pro-components/es/index.js'),
      },
    },
    server: {
      host: '0.0.0.0', // 绑定到所有网络接口
      port: 5173,
      // 允许通过自定义域名访问本地开发服务（适配反向代理/内网穿透等场景）
      allowedHosts: ['comp.yiykj.com'],
      proxy: {
        // 将 /api 代理到后端，避免开发环境 CORS
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          // 保持 /api 路径不变，因为后端期望 /api/* 路径
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: 'vitest.setup.ts',
      globals: true,
      css: true,
      testTimeout: 15000,
      hookTimeout: 15000,
      server: {
        deps: {
          inline: ['@ant-design/pro-components'],
        },
      },
    },
    // 优化依赖预构建配置，解决 @ant-design/icons 导出问题
    optimizeDeps: {
      include: ['@ant-design/icons', '@ant-design/icons/esm', '@ant-design/icons/lib'],
      esbuildOptions: {
        // 解决 ESM/CJS 互操作问题
        mainFields: ['module', 'main'],
      },
    },
  };
});
