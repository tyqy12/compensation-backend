import type { SysResource } from '@types/api';

export type SimpleMenuItem = {
  key: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  children?: SimpleMenuItem[];
  // carry-through raw icon key from backend for mapping in UI layer
  iconKey?: string | null;
};

function parseMeta(meta?: unknown): Record<string, any> {
  if (!meta) return {};
  if (typeof meta === 'string') {
    try {
      return JSON.parse(meta);
    } catch {
      return {};
    }
  }
  return (meta as any) ?? {};
}

/**
 * 菜单树节点类型（支持 _children 字段）
 */
interface MenuNode extends SysResource {
  _children?: number[];
  children?: MenuNode[];
}

function isEnabledResource(resource: SysResource): boolean {
  return resource.status == null || resource.status === 'enabled' || resource.status === 1;
}

// 标准侧边菜单构建规则：资源可见性完全来自后端返回的资源状态和元数据。
export function buildMenuFromResources(
  resources: SysResource[],
) {
  // 检查是否已包含实际的嵌套节点；_children 仅是旧版ID元数据。
  const hasNestedStructure = (resources as MenuNode[]).some((resource) => Array.isArray(resource.children));

  const menus = (resources || [])
    .filter((r) => r.type === 'MENU')
    .filter(isEnabledResource)
    .filter((r) => {
      const meta = parseMeta((r as any).meta ?? (r as any).propsJson);
      return !meta?.hidden;
    });

  if (hasNestedStructure) {
    // 已包含嵌套结构，递归过滤禁用/隐藏子节点，避免旧子菜单从父节点重新出现。
    const filterTree = (nodes: MenuNode[]): MenuNode[] => nodes
      .filter((node) => isEnabledResource(node))
      .filter((node) => {
        const meta = parseMeta((node as any).meta ?? (node as any).propsJson);
        return !meta.hidden;
      })
      .map((node) => ({
        ...node,
        children: node.children ? filterTree(node.children) : undefined,
      }))
      .filter((node) => Boolean(node.path) || Boolean(node.children?.length));

    const filteredMenus = filterTree(menus as MenuNode[]);
    const sortTree = (nodes: MenuNode[]) => {
      nodes.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
      nodes.forEach((n) => {
        if (n.children && n.children.length) sortTree(n.children);
      });
    };
    sortTree(filteredMenus);

    const toItems = (nodes: MenuNode[]): SimpleMenuItem[] =>
      nodes.map((n) => ({
        key: n.path || n.code || String(n.id),
        label: n.name,
        iconKey: (n as any).icon ?? null,
        children: n.children && n.children.length ? toItems(n.children) : undefined,
      }));

    return toItems(filteredMenus);
  }

  // 构建树结构
  const byId = new Map<number, MenuNode>();
  menus.forEach((m) => byId.set(m.id, { ...m, children: [] }));
  const roots: MenuNode[] = [];
  menus.forEach((m) => {
    const pid = m.parentId ?? null;
    if (pid && byId.has(pid)) byId.get(pid)!.children!.push(byId.get(m.id)!);
    else roots.push(byId.get(m.id)!);
  });
  const sortTree = (nodes: MenuNode[]) => {
    nodes.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
    nodes.forEach((n) => n.children && sortTree(n.children));
  };
  sortTree(roots);

  // 生成 antd Menu items 结构
  const toItems = (nodes: MenuNode[]): SimpleMenuItem[] =>
    nodes.map((n) => ({
      key: n.path || n.code || String(n.id),
      // label 由调用方生成（需要 React 环境），这里只占位
      label: n.name,
      iconKey: (n as any).icon ?? null,
      children: n.children && n.children.length ? toItems(n.children) : undefined,
    }));

  return toItems(roots);
}
