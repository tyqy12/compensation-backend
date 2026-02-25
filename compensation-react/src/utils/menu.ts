import type { SysResource } from '@types/api';
import { normalizeRoles } from '@utils/rbac';

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

// 标准侧边菜单构建规则：
// - 仅 MENU 类型参与菜单
// - 按 parentId 组装层级、orderNum 升序
// - 支持 meta.hidden、meta.roles 过滤
// - 支持后端返回的 _children 嵌套结构
export function buildMenuFromResources(
  resources: SysResource[],
  opts?: { userRoles?: string[]; respectRoles?: boolean },
) {
  const userRoles = normalizeRoles(opts?.userRoles || []);

  // 检查是否已包含嵌套结构
  const hasNestedStructure = (resources as MenuNode[]).some(r => r._children && r._children.length > 0);

  const menus = (resources || [])
    .filter((r) => r.type === 'MENU')
    .filter((r) => {
      const meta = parseMeta((r as any).meta ?? (r as any).propsJson);
      if (meta?.hidden) return false;
      if (opts?.respectRoles && Array.isArray(meta?.roles) && meta.roles.length > 0) {
        const rr = normalizeRoles(meta.roles);
        return rr.some((x) => userRoles.includes(x));
      }
      return true;
    });

  if (hasNestedStructure) {
    // 已包含嵌套结构，直接过滤后返回
    const filteredMenus = menus as MenuNode[];
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
