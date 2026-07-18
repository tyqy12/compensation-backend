import type { SysResource } from '@types/api';
import { normalizeRoles } from '@utils/rbac';

export interface MenuNode extends SysResource {
  children?: MenuNode[];
}

function parseProps(json: any): any {
  if (!json) return {};
  if (typeof json === 'string') {
    try { return JSON.parse(json); } catch { return {}; }
  }
  return json;
}

type BuildOpts = { userRoles?: string[]; respectRoles?: boolean };

/**
 * 检查路径是否包含路由参数（如 :id, :batchNo 等）
 * 这些路径不应该出现在导航菜单中
 */
function hasRouteParams(path?: string | null): boolean {
  if (!path) return false;
  return /:[a-zA-Z_][a-zA-Z0-9_]*/.test(path);
}

function isEnabledResource(resource: SysResource): boolean {
  return resource.status == null || resource.status === 'enabled' || resource.status === 1;
}

/**
 * 构建资源树（支持后端返回的嵌套结构）
 * @param resources 扁平资源列表或已嵌套的资源列表
 * @param opts 构建选项
 * @returns 树形结构的菜单节点
 */
export function buildResourceTree(resources: SysResource[], opts?: BuildOpts): MenuNode[] {
  // 如果资源已经包含 _children 字段（后端返回的嵌套结构），直接返回
  const hasNestedStructure = resources.some(r => r._children && r._children.length > 0);

  if (hasNestedStructure) {
    return (resources as MenuNode[]).filter((r) => {
      if (!isEnabledResource(r)) return false;
      const meta = r.meta ?? {};
      if (meta?.hidden) return false;
      // 过滤掉带有路由参数的路径（如 /employees/:id），这些是详情页，不应出现在菜单中
      if (hasRouteParams(r.path)) return false;
      if (opts?.respectRoles && meta?.roles && Array.isArray(meta.roles)) {
        const rr = normalizeRoles(meta.roles as string[]);
        const ur = normalizeRoles(opts?.userRoles || []);
        return rr.length === 0 || rr.some((x) => ur.includes(x));
      }
      return true;
    });
  }

  // 否则，构建树结构
  const byId = new Map<number, MenuNode>();
  const roots: MenuNode[] = [];
  const list = (resources || [])
    .filter((r) => r.type === 'MENU' || r.type === 'VIEW')
    .filter((r) => {
      if (!isEnabledResource(r)) return false;
      const meta = r.meta ?? {};
      if (meta?.hidden) return false;
      // 过滤掉带有路由参数的路径（如 /employees/:id），这些是详情页，不应出现在菜单中
      if (hasRouteParams(r.path)) return false;
      if (opts?.respectRoles && meta?.roles && Array.isArray(meta.roles)) {
        const rr = normalizeRoles(meta.roles as string[]);
        const ur = normalizeRoles(opts?.userRoles || []);
        return rr.length === 0 || rr.some((x) => ur.includes(x));
      }
      return true;
    });
  list.forEach((r) => byId.set(r.id, { ...r, children: [] }));
  list.forEach((r) => {
    const node = byId.get(r.id)!;
    const pid = r.parentId ?? null;
    if (pid && byId.has(pid)) {
      byId.get(pid)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortChildren = (nodes: MenuNode[]) => {
    nodes.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
    nodes.forEach((n) => n.children && sortChildren(n.children));
  };
  sortChildren(roots);
  return roots;
}

/**
 * 将树形结构展平为路径列表
 */
export function flattenAllowedPaths(resources: SysResource[]): string[] {
  return (resources || [])
    .filter((r) => (r.type === 'MENU' || r.type === 'VIEW') && r.path)
    .map((r) => r.path!)
    .filter(Boolean);
}

/**
 * 根据 ID 列表从树中查找节点
 */
export function findNodesByIds(tree: MenuNode[], ids: number[]): MenuNode[] {
  const idSet = new Set(ids);
  const result: MenuNode[] = [];

  const traverse = (nodes: MenuNode[]) => {
    for (const node of nodes) {
      if (idSet.has(node.id)) {
        result.push(node);
      }
      if (node.children && node.children.length > 0) {
        traverse(node.children);
      }
    }
  };

  traverse(tree);
  return result;
}
