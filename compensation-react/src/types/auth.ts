/**
 * 权限中心类型定义
 * 基于统一授权中心架构设计
 */

// ==================== 资源类型定义 ====================

/**
 * 资源类型枚举
 */
export type ResourceType =
  | 'MENU'   // 菜单资源（显示在导航栏）
  | 'VIEW'   // 页面资源（路由访问）
  | 'ACTION' // 操作资源（按钮级别权限）
  | 'API';   // API资源（接口访问权限）

/**
 * 资源状态
 */
export type ResourceStatus = 'enabled' | 'disabled';

/**
 * 资源定义
 */
export interface Resource {
  id: number;
  type: ResourceType;
  code: string;           // 全局唯一标识，如 'employee:list', 'payment:batch.start'
  name: string;           // 显示名称
  path?: string | null;   // 路由路径或API路径
  component?: string | null; // 前端组件路径
  icon?: string | null;   // 图标
  parentId?: number | null; // 父资源ID
  parentCode?: string | null; // 父资源代码（便于导入）
  orderNum?: number | null; // 排序号
  meta?: ResourceMeta | null; // 扩展属性
  status: ResourceStatus;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * 资源扩展属性
 */
export interface ResourceMeta {
  // 菜单属性
  keepAlive?: boolean;        // 是否缓存页面
  hidden?: boolean;           // 是否隐藏（不在菜单显示）
  affix?: boolean;            // 是否固定在标签栏
  icon?: string;              // 图标覆盖
  // API属性
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'; // HTTP方法
}

// ==================== 角色资源关联 ====================

/**
 * 角色资源关联
 */
export interface RoleResource {
  id: number;
  roleId: number;
  resourceId: number;
  actions: string[];      // 授权的操作，如 ['read', 'write', 'delete']
  conditions?: string | null; // 条件表达式（数据权限用）
  createdAt: string;
  updatedAt: string;
}

/**
 * 用户资源关联（直接授权）
 */
export interface UserResource {
  id: number;
  userId: number;
  resourceId: number;
  actions: string[];
  conditions?: string | null;
  inheritFromRole: boolean; // 是否继承自角色（用于审计）
  createdAt: string;
  updatedAt: string;
}

// ==================== 权限配置结构 ====================

/**
 * 权限配置结构
 */
export interface PermissionConfig {
  version: number;              // 权限版本号
  lastUpdate: string;           // 最后更新时间
  roles: RolePermission[];      // 角色权限列表
  userPermissions: UserPermission[]; // 用户直接权限
}

/**
 * 角色权限配置
 */
export interface RolePermission {
  roleId: number;
  roleCode: string;
  resources: ResourcePermission[];
  inheritedFrom?: number | null; // 继承自哪个角色
}

/**
 * 用户权限配置
 */
export interface UserPermission {
  userId: number;
  username: string;
  resources: ResourcePermission[];
}

/**
 * 资源权限配置
 */
export interface ResourcePermission {
  resourceId: number;
  resourceCode: string;
  resourceType: ResourceType;
  actions: ActionPermission[];
  conditions?: string | null;
}

/**
 * 操作权限配置
 */
export interface ActionPermission {
  code: string;         // 操作码，如 'read', 'write', 'delete', 'export'
  name: string;         // 显示名称
  description?: string | null;
  dependencies?: string[]; // 依赖的其他权限
}

/**
 * 有效权限（合并后的权限）
 */
export interface EffectivePermission {
  resource: Resource;
  actions: string[];
  source: string; // 来源: 'role:{roleCode}' | 'user:direct'
}

// ==================== API 请求/响应类型 ====================

/**
 * 创建角色请求
 */
export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string;
  roleType?: 'SYSTEM' | 'BUSINESS' | 'CUSTOM';
  sortOrder?: number;
  icon?: string;
  remarks?: string;
  initialPermissions?: {
    resourceIds: number[];
    actions: Record<string, string[]>;
  };
}

/**
 * 更新角色请求
 */
export interface UpdateRoleRequest {
  name?: string;
  description?: string;
  roleType?: 'SYSTEM' | 'BUSINESS' | 'CUSTOM';
  sortOrder?: number;
  icon?: string;
  status?: 'enabled' | 'disabled';
  remarks?: string;
}

/**
 * 更新角色权限请求
 */
export interface UpdateRolePermissionsRequest {
  resourceIds: number[];
  actions: Record<string, string[]>; // resourceId -> actions[]
  conditions?: Record<string, string>; // resourceId -> condition
  replaceExisting?: boolean; // 是否替换现有权限
}

/**
 * 更新用户权限请求
 */
export interface UpdateUserPermissionsRequest {
  resourceIds: number[];
  actions: Record<string, string[]>;
  conditions?: Record<string, string>;
  replaceExisting?: boolean;
}

/**
 * 检查权限请求
 */
export interface CheckPermissionRequest {
  userId?: number;
  permissions: Array<{
    resourceCode: string;
    action: string;
    resourceId?: number;
  }>;
}

/**
 * 检查权限响应
 */
export interface CheckPermissionResult {
  results: Array<{
    resourceCode: string;
    action: string;
    allowed: boolean;
    reason?: string;
  }>;
  checkedAt: string;
}

/**
 * 权限变更事件
 */
export interface PermissionChangeEvent {
  eventType: 'GRANT' | 'REVOKE' | 'MODIFY' | 'CLEAR';
  targetType: 'USER' | 'ROLE';
  targetId: number;
  changes: Array<{
    resourceId: number;
    resourceCode: string;
    oldActions: string[];
    newActions: string[];
  }>;
  triggeredBy: number; // 操作人ID
  triggeredAt: string;
  workflowId?: string; // 审批流程ID
}

// ==================== 角色类型扩展 ====================

/**
 * 角色类型
 */
export type RoleType = 'SYSTEM' | 'BUSINESS' | 'CUSTOM';

/**
 * 角色状态
 */
export type RoleStatus = 'enabled' | 'disabled';

/**
 * 角色基础信息
 */
export interface RoleInfo {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  roleType: RoleType;
  roleTypeDisplayName: string;
  sortOrder: number;
  isEditable: boolean;
  isProtected: boolean;
  icon?: string | null;
  status: RoleStatus;
  statusDisplayName: string;
  remarks?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 角色详情
 */
export interface RoleDetail extends RoleInfo {
  userCount: number;
  resourceCount: number;
  resources?: RoleResourceBrief[];
}

/**
 * 角色关联的资源简要信息
 */
export interface RoleResourceBrief {
  id: number;
  code: string;
  name: string;
  type: ResourceType;
  actions: string[];
}

// ==================== 资源树类型 ====================

/**
 * 资源树节点
 */
export interface ResourceTreeNode {
  id: number;
  key: number;
  title: React.ReactNode;
  children?: ResourceTreeNode[];
  resource: Resource;
}

// ==================== 批量操作类型 ====================

/**
 * 批量排序请求
 */
export interface BatchSortRequest {
  id: number;
  orderNum: number;
}

/**
 * 导入结果
 */
export interface ImportResult {
  success: number;
  failed: number;
  errors: string[];
}

/**
 * 资源导入请求项
 */
export interface ResourceImportRequest {
  code: string;
  name: string;
  type: ResourceType;
  path?: string;
  parentCode?: string;
  orderNum?: number;
  meta?: Record<string, unknown>;
}

// ==================== 查询参数类型 ====================

/**
 * 角色列表查询参数
 */
export interface RoleListParams {
  keyword?: string;
  roleType?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}

/**
 * 资源列表查询参数
 */
export interface ResourceListParams {
  type?: ResourceType;
  status?: ResourceStatus;
  keyword?: string;
  page?: number;
  pageSize?: number;
}

/**
 * 权限检查参数
 */
export interface PermissionCheckParams {
  resourceCode: string;
  action: string;
}
