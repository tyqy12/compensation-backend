import React, { Suspense, useMemo } from 'react';
import { useLocation, matchPath } from 'react-router-dom';
import { useMeResourcesQuery } from '@services/queries/rbac';
import Loading from '@components/Common/Loading';
import type { SysResource } from '@types/api';

const views = import.meta.glob([
  '/src/pages/**/*.tsx',
  '!/src/pages/**/*.test.tsx',
  '!/src/pages/**/*.test.*.tsx',
  '!/src/pages/**/*.backup.tsx',
  '!/src/pages/demo/**',
]);

function resolveComponentPath(component?: string | null): string | undefined {
  if (!component) return undefined;
  const candidates = [
    `/src/pages/${component}.tsx`,
    `/src/pages/${component}/index.tsx`,
  ];
  return candidates.find((k) => k in views);
}

function isEnabledResource(resource: SysResource): boolean {
  return resource.status == null || resource.status === 'enabled' || resource.status === 1;
}

export const DynamicPageRenderer: React.FC = () => {
  const { pathname } = useLocation();
  const { data } = useMeResourcesQuery();

  const match = useMemo(() => {
    const list = (data?.resources || []).filter(
      (r) => (r.type === 'VIEW' || r.type === 'MENU') && isEnabledResource(r),
    );
    // sort by path length desc for best match
    const sorted = list
      .filter((r) => r.path)
      .sort((a, b) => (b.path!.length - a.path!.length));
    for (const r of sorted) {
      const ok = matchPath({ path: r.path!, end: true }, pathname);
      if (ok) return r;
    }
    return undefined;
  }, [data?.resources, pathname]);

  const modKey = resolveComponentPath(match?.component || undefined);
  if (!match || !modKey) {
    return <div style={{ padding: 24 }}>页面未找到或未配置组件</div>;
  }

  const LazyComp = React.lazy(views[modKey] as any);

  return (
    <Suspense fallback={<Loading />}> 
      <LazyComp />
    </Suspense>
  );
};

export default DynamicPageRenderer;
