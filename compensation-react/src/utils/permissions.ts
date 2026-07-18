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

export type BuildOpts = {
  userRoles?: string[];
  respectRoles?: boolean;
  /** Navigation callers hide disabled resources by default. */
  includeDisabled?: boolean;
  /** Navigation callers hide metadata-hidden resources by default. */
  includeHidden?: boolean;
  /** Navigation callers hide parameterized detail routes by default. */
  includeRouteParams?: boolean;
};

export function getResourceMeta(resource: SysResource): Record<string, any> {
  const raw = resource.meta ?? (resource as SysResource & { propsJson?: unknown }).propsJson;
  return parseProps(raw);
}

export function isEnabledResource(resource: SysResource): boolean {
  return resource.status == null || resource.status === 'enabled' || resource.status === 1;
}

function isAllowedByRoles(resource: SysResource, opts?: BuildOpts): boolean {
  if (!opts?.respectRoles) return true;
  const roles = getResourceMeta(resource).roles;
  if (!Array.isArray(roles) || roles.length === 0) return true;
  const requiredRoles = normalizeRoles(roles.filter((role): role is string => typeof role === 'string'));
  const userRoles = normalizeRoles(opts.userRoles || []);
  return requiredRoles.length === 0 || requiredRoles.some((role) => userRoles.includes(role));
}

function isVisibleInTree(resource: SysResource, opts?: BuildOpts): boolean {
  if (!opts?.includeDisabled && !isEnabledResource(resource)) return false;
  const meta = getResourceMeta(resource);
  if (!opts?.includeHidden && meta.hidden) return false;
  if (!opts?.includeRouteParams && hasRouteParams(resource.path)) return false;
  return isAllowedByRoles(resource, opts);
}

/**
 * 检查路径是否包含路由参数（如 :id, :batchNo 等）
 * 这些路径不应该出现在导航菜单中
 */
function hasRouteParams(path?: string | null): boolean {
  if (!path) return false;
  return /:[a-zA-Z_][a-zA-Z0-9_]*/.test(path);
}

/**
 * 构建资源树（支持后端返回的嵌套结构）
 * @param resources 扁平资源列表或已嵌套的资源列表
 * @param opts 构建选项
 * @returns 树形结构的菜单节点
 */
export function buildResourceTree(resources: SysResource[], opts?: BuildOpts): MenuNode[] {
  // 支持资源管理接口返回的实际 children 结构；_children 只是旧版的ID元数据，不能当成树节点。
  const hasNestedStructure = resources.some((r) => Array.isArray((r as MenuNode).children));

  if (hasNestedStructure) {
    const filterNested = (nodes: MenuNode[]): MenuNode[] => nodes
      .filter((node) => isVisibleInTree(node, opts))
      .map((node) => ({
        ...node,
        children: node.children ? filterNested(node.children) : undefined,
      }))
      .filter((node) => Boolean(node.path) || Boolean(node.children?.length));

    const roots = filterNested(resources as MenuNode[]);
    const sortChildren = (nodes: MenuNode[]) => {
      nodes.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
      nodes.forEach((node) => node.children && sortChildren(node.children));
    };
    sortChildren(roots);
    return roots;
  }

  // 否则，构建树结构
  const byId = new Map<number, MenuNode>();
  const roots: MenuNode[] = [];
  const list = (resources || [])
    .filter((r) => r.type === 'MENU' || r.type === 'VIEW')
    .filter((r) => isVisibleInTree(r, opts));
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
  const removeEmptyGroups = (nodes: MenuNode[]): MenuNode[] => nodes
    .map((node) => ({
      ...node,
      children: node.children ? removeEmptyGroups(node.children) : undefined,
    }))
    .filter((node) => Boolean(node.path) || Boolean(node.children?.length));
  return removeEmptyGroups(roots);
}

/**
 * 将树形结构展平为路径列表
 */
export function flattenAllowedPaths(resources: SysResource[]): string[] {
  return (resources || [])
    .filter((r) => (r.type === 'MENU' || r.type === 'VIEW') && r.path)
    .filter(isEnabledResource)
    .map((r) => r.path!)
    .filter(Boolean)
    .filter((path, index, paths) => paths.indexOf(path) === index);
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
