import React, { Suspense, useMemo } from 'react';
import { useLocation, matchPath } from 'react-router-dom';
import { useMeResourcesQuery } from '@services/queries/rbac';
import Loading from '@components/Common/Loading';
import type { SysResource } from '@types/api';

const NotFoundPage = React.lazy(() => import('@pages/misc/NotFound'));

const views = import.meta.glob([
  '/src/pages/**/*.tsx',
  '!/src/pages/**/*.test.tsx',
  '!/src/pages/**/*.test.*.tsx',
  '!/src/pages/**/*.backup.tsx',
  '!/src/pages/demo/**',
]);

export function resolveComponentPath(component?: string | null): string | undefined {
  if (!component?.trim()) return undefined;
  const normalized = component
    .trim()
    .replace(/\\/g, '/')
    .replace(/^\/?(?:src\/)?pages\//, '')
    .replace(/^@pages\//, '')
    .replace(/\.tsx$/, '')
    .replace(/\/index$/, '')
    .replace(/^\/+|\/+$/g, '');
  if (!normalized) return undefined;
  const candidates = [
    `/src/pages/${normalized}.tsx`,
    `/src/pages/${normalized}/index.tsx`,
  ];
  return candidates.find((key) => key in views);
}

function normalizeRoutePath(path?: string | null): string | undefined {
  if (!path?.trim()) return undefined;
  const normalized = `/${path.trim().replace(/^\/+|\/+$/g, '')}`;
  return normalized === '/' ? '/' : normalized.replace(/\/+$/g, '');
}

export function findMatchingResource(resources: SysResource[], pathname: string): SysResource | undefined {
  const currentPath = normalizeRoutePath(pathname) || '/';
  const list = resources
    .filter((resource) =>
      (resource.type === 'VIEW' || resource.type === 'MENU') && isEnabledResource(resource) && resource.path,
    )
    .sort((left, right) => {
      const lengthOrder = (normalizeRoutePath(right.path)?.length || 0) - (normalizeRoutePath(left.path)?.length || 0);
      if (lengthOrder !== 0) return lengthOrder;
      // If stale duplicate resources exist, prefer the one whose component is actually in the bundle.
      const componentOrder = Number(Boolean(resolveComponentPath(right.component))) -
        Number(Boolean(resolveComponentPath(left.component)));
      if (componentOrder !== 0) return componentOrder;
      return Number(left.type === 'VIEW') - Number(right.type === 'VIEW');
    });

  return list.find((resource) => {
    const pattern = normalizeRoutePath(resource.path);
    return pattern ? Boolean(matchPath({ path: pattern, end: true }, currentPath)) : false;
  });
}

type LazyView = React.LazyExoticComponent<React.ComponentType<any>>;
const lazyViews = new Map<string, LazyView>();

function getLazyView(moduleKey: string): LazyView {
  const cached = lazyViews.get(moduleKey);
  if (cached) return cached;
  const lazyView = React.lazy(views[moduleKey] as () => Promise<{ default: React.ComponentType<any> }>);
  lazyViews.set(moduleKey, lazyView);
  return lazyView;
}

function isEnabledResource(resource: SysResource): boolean {
  return resource.status == null || resource.status === 'enabled' || resource.status === 1;
}

export const DynamicPageRenderer: React.FC = () => {
  const { pathname } = useLocation();
  const { data } = useMeResourcesQuery();

  const match = useMemo(() => findMatchingResource(data?.resources || [], pathname), [data?.resources, pathname]);

  const modKey = resolveComponentPath(match?.component || undefined);
  if (!match) {
    return (
      <Suspense fallback={<Loading />}>
        <NotFoundPage />
      </Suspense>
    );
  }
  if (!modKey) {
    return (
      <div style={{ padding: 24 }} data-testid="dynamic-route-config-error">
        页面资源已配置，但组件不存在：{match.name}（{match.code}）
      </div>
    );
  }

  const LazyComp = getLazyView(modKey);

  return (
    <Suspense fallback={<Loading />}> 
      <LazyComp />
    </Suspense>
  );
};

export default DynamicPageRenderer;
