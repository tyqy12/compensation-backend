# 前端授权中心架构优化方案

## 一、现状分析与问题诊断

### 1.1 当前架构概述

当前系统采用基于 RBAC 的权限模型，包含以下核心组件：

- **后端 RBAC 模块**：`src/main/java/com/yiyundao/compensation/modules/rbac/`
  - 实体层：`SysResource`、`SysRole`、`SysRoleResource`、`SysUserRole`、`SysUserResource`
  - 服务层：`ResourceService`、`RoleService`、`UserResourceService`、`ResourceCacheService`
  - 支持资源类型：`MENU`、`VIEW`、`ACTION`、`API`

- **前端权限模块**：
  - 页面：`AuthCenter.tsx`、`RoleManagement.tsx`、`RoleAuthorization.tsx`、`UserAuthorization.tsx`
  - 服务：`adminAuth.ts`、`resources.ts`
  - 工具：`permissions.ts`、`rbac.ts`
  - 类型定义：`api.ts` 中的 `SysResource`、`RoleInfo` 等

### 1.2 功能重叠分析

经过代码审查，发现以下功能重叠问题：

| 功能模块 | 涉及文件 | 问题描述 |
|---------|---------|---------|
| 角色管理 | `AuthCenter.tsx:442-463` 与 `RoleManagement.tsx:30-428` | 两个页面都包含角色 CRUD 操作 |
| 角色授权 | `AuthCenter.tsx:244-314` 与 `RoleAuthorization.tsx:42-168` | 授权逻辑重复实现 |
| 用户授权 | `AuthCenter.tsx:343-438` 与 `UserAuthorization.tsx` | 用户授权入口分散 |
| 资源管理 | `ResourceManager.tsx` 与 `ResourcesV2.tsx` | 资源管理入口不统一 |

### 1.3 核心问题总结

1. **职责边界不清**：角色管理页面与授权中心页面功能边界模糊
2. **入口分散**：用户需要在多个页面间切换完成权限配置
3. **角色管理冗余**：`RoleManagement` 与 `AuthCenter` 中的角色管理功能重复
4. **缓存机制不完善**：权限变更后缺乏实时推送能力
5. **异常处理不统一**：403/401 等错误处理分散在各模块

---

## 二、优化目标与原则

### 2.1 优化目标

1. **统一入口**：所有权限相关操作集中在授权中心完成
2. **职责分离**：明确区分角色管理和授权管理的边界
3. **平滑下线**：`RoleManagement` 页面功能迁移至授权中心
4. **增强能力**：支持多层次权限控制、实时推送、审计日志
5. **提升体验**：优化权限变更的响应速度和异常提示

### 2.2 设计原则

- **单一职责**：每个模块只负责一类职责
- **高内聚低耦合**：模块间通过清晰接口通信
- **可扩展性**：支持后续功能迭代（如数据权限、条件权限）
- **安全性**：敏感操作可审计，权限变更可追溯

---

## 三、统一授权中心架构设计

### 3.1 设计理念

**核心原则**：角色管理不再是独立功能，而是授权中心的核心组成部分。所有权限相关操作在授权中心一站式完成，消除分散的入口。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      AuthCenter（授权中心 - 唯一入口）                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        角色管理区                                 │    │
│  │  ┌───────────────────────────────────────────────────────────┐  │    │
│  │  │                    角色卡片列表                             │  │    │
│  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │  │    │
│  │  │  │ 管理员   │ │ 人事专员 │ │ 财务专员 │ │ 部门主管 │     │  │    │
│  │  │  │ (admin)  │ │          │ │          │ │          │     │  │    │
│  │  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │  │    │
│  │  │                              [ + 新建角色 ]               │  │    │
│  │  └───────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        权限分配区                                 │    │
│  │  ┌───────────────────────────┐ ┌─────────────────────────────┐  │    │
│  │  │      选择角色/用户          │ │        资源权限树           │  │    │
│  │  │                           │ │                             │  │    │
│  │  │  [下拉框]                  │ │  ☑ 菜单树                  │  │    │
│  │  │  - 角色: 管理员            │ │    ☑ Dashboard [read]      │  │    │
│  │  │  - 用户: 张三              │ │    ☑ 员工管理 [read/write] │  │    │
│  │  │                           │ │    ├─ 员工列表             │  │    │
│  │  │  [查看关联用户]            │ │    └─ 员工详情             │  │    │
│  │  └───────────────────────────┘ └─────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        权限预览区                                 │    │
│  │  ┌───────────────────────────────────────────────────────────┐  │    │
│  │  │  当前选中对象权限摘要                                      │  │    │
│  │  │  ┌──────────────────────────────────────────────────────┐ │  │    │
│  │  │  │ 菜单权限: 5 项  │ API权限: 12 项  │ 按钮权限: 8 项   │ │  │    │
│  │  │  └──────────────────────────────────────────────────────┘ │  │    │
│  │  └───────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心功能模块
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                      PermissionContext（状态管理）                 │    │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────┐  │    │
│  │  │ usePermission │ │ useRole       │ │ useResource           │  │    │
│  │  │               │ │               │ │                       │  │    │
│  │  │ • 权限检查    │ │ • 角色列表    │ │ • 资源树获取          │  │    │
│  │  │ • 动作验证    │ │ • 角色详情    │ │ • 资源CRUD            │  │    │
│  │  │ • 权限版本    │ │ • 角色资源    │ │ • 资源分配            │  │    │
│  │  └───────────────┘ └───────────────┘ └───────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    PermissionGuard（权限控制）                     │    │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────┐  │    │
│  │  │ PageGuard     │ │ ComponentGuard│ │ ButtonGuard           │  │    │
│  │  │               │ │               │ │                       │  │    │
│  │  │ • 路由守卫    │ │ • 组件显示    │ │ • 按钮权限            │  │    │
│  │  │ • 页面级控制  │ │ • 区域控制    │ │ • 操作权限            │  │    │
│  │  └───────────────┘ └───────────────┘ └───────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    Cache & Push（缓存与推送）                      │    │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────┐  │    │
│  │  │ Permission    │ │ Permission    │ │ AuditLogger           │  │    │
│  │  │ Cache         │ │ Push          │ │                       │  │    │
│  │  │               │ │               │ │                       │  │    │
│  │  │ • 版本控制    │ │ • WebSocket   │ │ • 操作记录            │  │    │
│  │  │ • 内存/Storage│ │ • 实时推送    │ │ • 变更追踪            │  │    │
│  │  └───────────────┘ └───────────────┘ └───────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 模块职责划分（授权中心内部分工）

授权中心不再是三个并列的Tab，而是**一体化工作台**，所有操作在同一个页面完成。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      AuthCenter 一体化工作台                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        角色管理子模块                             │    │
│  │  ─────────────────────────────────────────────────────────────  │    │
│  │  • 角色列表（卡片式展示）                                         │    │
│  │  • 角色CRUD（弹窗表单）                                          │    │
│  │  • 角色状态（启用/禁用）                                          │    │
│  │  • 角色复制（快捷操作）                                           │    │
│  │  • 关联用户统计                                                  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                   ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        权限分配子模块                             │    │
│  │  ─────────────────────────────────────────────────────────────  │    │
│  │  • 选择角色/用户                                                  │    │
│  │  • 资源树勾选（菜单/API/按钮）                                    │    │
│  │  • 操作权限配置（read/write/delete/export）                      │    │
│  │  • 批量授权/回收                                                  │    │
│  │  • 权限预览（实时显示）                                           │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                   ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                        资源管理子模块                             │    │
│  │  ─────────────────────────────────────────────────────────────  │    │
│  │  • 资源树展示                                                    │    │
│  │  • 资源元数据配置                                                │    │
│  │  • 资源导入导出                                                  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 3.2.1 角色管理子模块（内置于授权中心）

**职责范围**（授权中心内部子模块）：
- 角色的增删改查（CRUD）
- 角色基本信息管理（编码、名称、描述、图标）
- 角色状态管理（启用/禁用）
- 角色复制功能
- 角色类型标识（SYSTEM/BUSINESS/CUSTOM）
- **与权限分配联动**：点击角色直接进入权限配置

**与旧 RoleManagement 区别**：
- ❌ 不再是独立页面，而是授权中心的组成部分
- ✅ 点击角色卡片立即显示权限配置区
- ✅ 角色创建后可直接配置权限，无需跳转

#### 3.2.2 权限分配子模块（内置于授权中心）

**职责范围**（授权中心内部子模块）：
- 选中角色/用户后的权限配置
- 角色与资源的关联配置
- 用户与资源的直接授权（用户个性授权）
- 权限的批量分配与回收
- 权限变更的审批流程
- 权限继承与叠加规则
- **实时预览**：权限变更即时反映在预览区

#### 3.2.3 资源管理子模块（内置于授权中心）

**职责范围**（授权中心内部子模块）：
- 资源树的展示与选择
- 资源的导入导出（批量操作）
- 资源元数据配置

**设计变更**：
- ❌ 不再需要独立的 ResourceManager 页面
- ✅ 授权中心内嵌资源树，支持勾选式权限配置

---

## 四、权限数据结构设计

### 4.1 核心实体关系

```
┌─────────────┐       ┌─────────────────┐       ┌─────────────┐
│   SysRole   │───────│ SysRoleResource │───────│ SysResource │
│   (角色)    │       │  (角色资源关联)  │       │   (资源)    │
└─────────────┘       └─────────────────┘       └─────────────┘
      │                                               │
      │                                               │
      ▼                                               ▼
┌─────────────┐                               ┌─────────────┐
│ SysUserRole │                               │ SysUser     │
│ (用户角色关联)│                               │   (用户)    │
└─────────────┘                               └─────────────┘
      │
      │ (用户个性授权)
      ▼
┌─────────────────┐
│ SysUserResource │
│  (用户资源关联)  │
└─────────────────┘
```

### 4.2 资源类型定义

```typescript
// compensation-react/src/types/auth.ts

/**
 * 资源类型枚举
 */
export type ResourceType = 
  | 'MENU'      // 菜单资源（显示在导航栏）
  | 'VIEW'      // 页面资源（路由访问）
  | 'ACTION'    // 操作资源（按钮级别权限）
  | 'API';      // API资源（接口访问权限）

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
  // 权限属性
  roles?: string[];           // 角色限制
  permissionCode?: string;    // 所需权限码
  // API属性
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'; // HTTP方法
  requireAllPermissions?: boolean; // 是否需要全部权限
}

/**
 * 资源状态
 */
export type ResourceStatus = 'enabled' | 'disabled';

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
```

### 4.3 权限元数据结构

```typescript
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
```

### 4.4 权限继承规则

```
授权优先级（从高到低）：
1. 用户直接授权（最高优先级，覆盖其他设置）
2. 用户角色授权（按角色优先级叠加）
3. 角色默认权限
4. 系统默认权限（最低优先级）

权限叠加规则：
- 动作权限：取并集（用户拥有的所有权限）
- 数据权限：取最严格规则（按条件优先级）
- 菜单权限：取并集（可见菜单叠加）
```

---

## 五、API 接口设计

### 5.1 授权中心 API 分组

#### 5.1.1 角色管理 API

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| GET | `/api/auth-center/roles` | 获取角色列表 | Query: `keyword, roleType, status, page, size` | `Page<RoleInfo>` |
| GET | `/api/auth-center/roles/{id}` | 获取角色详情 | - | `RoleDetail` |
| POST | `/api/auth-center/roles` | 创建角色 | `CreateRoleRequest` | `RoleInfo` |
| PUT | `/api/auth-center/roles/{id}` | 更新角色 | `UpdateRoleRequest` | `RoleInfo` |
| DELETE | `/api/auth-center/roles/{id}` | 删除角色 | - | `void` |
| POST | `/api/auth-center/roles/{id}/copy` | 复制角色 | `CopyRoleRequest` | `RoleInfo` |
| PUT | `/api/auth-center/roles/{id}/status` | 更新角色状态 | `{ status: 'enabled' | 'disabled' }` | `RoleInfo` |

#### 5.1.2 授权管理 API

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| GET | `/api/auth-center/roles/{roleId}/permissions` | 获取角色权限 | - | `RolePermissionDetail` |
| PUT | `/api/auth-center/roles/{roleId}/permissions` | 更新角色权限 | `UpdateRolePermissionsRequest` | `{ workflowId: string }` |
| GET | `/api/auth-center/users/{userId}/permissions` | 获取用户权限 | - | `UserPermissionDetail` |
| PUT | `/api/auth-center/users/{userId}/permissions` | 更新用户权限 | `UpdateUserPermissionsRequest` | `{ workflowId: string }` |
| GET | `/api/auth-center/users/{userId}/effective-permissions` | 获取用户有效权限 | Query: `resourceType` | `EffectivePermission[]` |
| POST | `/api/auth-center/permissions/check` | 批量检查权限 | `CheckPermissionRequest` | `CheckPermissionResult` |

#### 5.1.3 资源管理 API

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| GET | `/api/auth-center/resources/tree` | 获取资源树 | Query: `type, status` | `ResourceTreeNode[]` |
| GET | `/api/auth-center/resources` | 获取资源列表 | Query: `type, status, keyword, page, size` | `Page<Resource>` |
| POST | `/api/auth-center/resources` | 创建资源 | `CreateResourceRequest` | `Resource` |
| PUT | `/api/auth-center/resources/{id}` | 更新资源 | `UpdateResourceRequest` | `Resource` |
| DELETE | `/api/auth-center/resources/{id}` | 删除资源 | - | `void` |
| POST | `/api/auth-center/resources/sort` | 批量排序 | `Array<{ id: number; orderNum: number }>` | `void` |
| POST | `/api/auth-center/resources/import` | 批量导入 | `ResourceImportRequest[]` | `ImportResult` |
| GET | `/api/auth-center/resources/export` | 导出资源 | Query: `type` | `Blob` |

#### 5.1.4 实时推送 API

| 方法 | 路径 | 说明 |
|------|------|------|
| WebSocket | `/ws/permission-push` | 权限变更实时推送 |
| GET | `/api/auth-center/permissions/version` | 获取当前权限版本 |
| GET | `/api/auth-center/permissions/changes` | 获取变更历史（自某版本） |

### 5.2 请求/响应 DTO 定义

```typescript
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
 * 更新角色权限请求
 */
export interface UpdateRolePermissionsRequest {
  resourceIds: number[];
  actions: Record<string, string[]>; // resourceId -> actions[]
  conditions?: Record<string, string>; // resourceId -> condition
  replaceExisting?: boolean; // 是否替换现有权限
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
```

---

## 六、前端权限控制组件设计

### 6.1 权限 Hooks 设计

```typescript
// compensation-react/src/hooks/usePermission.ts

import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  PermissionConfig,
  Resource,
  RolePermission,
  UserPermission,
  EffectivePermission,
  CheckPermissionRequest,
  CheckPermissionResult,
} from '@types/auth';
import { api } from '@services/api';

/**
 * 权限上下文
 */
export interface PermissionContextValue {
  // 权限状态
  config: PermissionConfig | null;
  version: number | null;
  isLoading: boolean;
  error: Error | null;
  
  // 刷新方法
  refresh: () => Promise<void>;
  checkPermission: (resourceCode: string, action: string) => boolean;
  hasAnyPermission: (permissions: Array<{ resourceCode: string; action: string }>) => boolean;
  hasAllPermissions: (permissions: Array<{ resourceCode: string; action: string }>) => boolean;
  
  // 资源方法
  getResources: (type?: ResourceType) => Resource[];
  getResourceByCode: (code: string) => Resource | undefined;
  getResourceByPath: (path: string) => Resource | undefined;
}

/**
 * 权限 Hook
 */
export function usePermission(): PermissionContextValue {
  const queryClient = useQueryClient();
  
  // 获取权限配置
  const { data: config, isLoading, error } = useQuery({
    queryKey: ['permission', 'config'],
    queryFn: () => api.get<PermissionConfig>('/auth-center/permissions/config'),
    staleTime: 5 * 60 * 1000, // 5分钟内不重新请求
  });

  // 权限版本
  const version = config.value?.version ?? null;

  // 权限检查
  const checkPermission = useCallback(
    (resourceCode: string, action: string): boolean => {
      if (!config.value) return false;
      
      const effectivePermissions = getEffectivePermissions(config.value);
      
      const resourcePermission = effectivePermissions.find(
        (p) => p.resource.code === resourceCode || p.resource.id === Number(resourceCode)
      );
      
      if (!resourcePermission) return false;
      
      // 特殊动作：'*' 表示所有权限
      if (resourcePermission.actions.includes('*')) return true;
      
      return resourcePermission.actions.includes(action);
    },
    [config]
  );

  // 检查任意权限
  const hasAnyPermission = useCallback(
    (permissions: Array<{ resourceCode: string; action: string }>): boolean => {
      return permissions.some((p) => checkPermission(p.resourceCode, p.action));
    },
    [checkPermission]
  );

  // 检查全部权限
  const hasAllPermissions = useCallback(
    (permissions: Array<{ resourceCode: string; action: string }>): boolean => {
      return permissions.every((p) => checkPermission(p.resourceCode, p.action));
    },
    [checkPermission]
  );

  // 刷新权限
  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['permission', 'config'] });
  }, [queryClient]);

  // 获取资源
  const getResources = useCallback(
    (type?: ResourceType): Resource[] => {
      if (!config.value) return [];
      
      const allResources = [
        ...config.value.roles.flatMap((r) => r.resources.map((rp) => rp.resource)),
        ...config.value.userPermissions.flatMap((up) => up.resources.map((rp) => rp.resource)),
      ];
      
      if (type) {
        return allResources.filter((r) => r.type === type);
      }
      
      return allResources;
    },
    [config]
  );

  // 根据 Code 获取资源
  const getResourceByCode = useCallback(
    (code: string): Resource | undefined => {
      return getResources().find((r) => r.code === code);
    },
    [getResources]
  );

  // 根据 Path 获取资源
  const getResourceByPath = useCallback(
    (path: string): Resource | undefined => {
      return getResources().find((r) => r.path === path);
    },
    [getResources]
  );

  return {
    config: config.value ?? null,
    version,
    isLoading,
    error: error ?? null,
    refresh,
    checkPermission,
    hasAnyPermission,
    hasAllPermissions,
    getResources,
    getResourceByCode,
    getResourceByPath,
  };
}

/**
 * 获取有效权限（合并角色和用户权限）
 */
function getEffectivePermissions(config: PermissionConfig): EffectivePermission[] {
  const permissionMap = new Map<string, EffectivePermission>();

  // 处理角色权限
  for (const role of config.roles) {
    for (const resource of role.resources) {
      const key = resource.resource.code;
      const existing = permissionMap.get(key);
      
      if (existing) {
        // 合并权限（取并集）
        const actionsSet = new Set([...existing.actions, ...resource.actions]);
        existing.actions = Array.from(actionsSet);
      } else {
        permissionMap.set(key, {
          resource: resource.resource,
          actions: [...resource.actions],
          source: `role:${role.roleCode}`,
        });
      }
    }
  }

  // 处理用户直接权限（覆盖角色权限）
  for (const userPerm of config.userPermissions) {
    for (const resource of userPerm.resources) {
      const key = resource.resource.code;
      const existing = permissionMap.get(key);
      
      if (existing) {
        // 用户直接权限优先级更高
        existing.actions = [...resource.actions];
        existing.source = 'user:direct';
      } else {
        permissionMap.set(key, {
          resource: resource.resource,
          actions: [...resource.actions],
          source: 'user:direct',
        });
      }
    }
  }

  return Array.from(permissionMap.values());
}
```

### 6.2 权限守卫组件

```typescript
// compensation-react/src/components/PermissionGuard/index.tsx

import React, { useMemo } from 'react';
import { usePermission } from '@hooks/usePermission';
import { Forbidden } from '@pages/misc/Forbidden';

/**
 * 权限守卫组件属性
 */
export interface PermissionGuardProps {
  /** 要检查的权限列表（AND 关系） */
  permissions: Array<{
    resourceCode: string;
    action: string;
  }>;
  /** 权限检查模式 */
  mode?: 'all' | 'any';
  /** 无权限时显示的内容 */
  fallback?: React.ReactNode;
  /** 是否在无权限时显示 403 页面 */
  showForbidden?: boolean;
  /** 子组件 */
  children: React.ReactNode;
}

/**
 * 权限守卫组件
 * 
 * @example
 * ```tsx
 * <PermissionGuard
 *   permissions={[
 *     { resourceCode: 'employee', action: 'read' },
 *     { resourceCode: 'employee', action: 'write' },
 *   ]}
 *   mode="all"
 * >
 *   <EmployeeForm />
 * </PermissionGuard>
 * ```
 */
export function PermissionGuard({
  permissions,
  mode = 'all',
  fallback = null,
  showForbidden = false,
  children,
}: PermissionGuardProps) {
  const { hasAnyPermission, hasAllPermissions, isLoading } = usePermission();

  const hasPermission = useMemo(() => {
    if (isLoading || permissions.length === 0) return true;

    if (mode === 'any') {
      return hasAnyPermission(permissions);
    }
    return hasAllPermissions(permissions);
  }, [isLoading, permissions, mode, hasAnyPermission, hasAllPermissions]);

  if (isLoading) {
    return <>{fallback}</>;
  }

  if (!hasPermission) {
    if (showForbidden) {
      return <Forbidden />;
    }
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

/**
 * 按钮权限组件
 */
interface ButtonGuardProps extends Omit<PermissionGuardProps, 'children'> {
  /** 按钮属性 */
  buttonProps?: React.ButtonHTMLAttributes<HTMLButtonElement>;
  /** 有权限时显示的按钮 */
  children: React.ReactElement;
  /** 无权限时隐藏还是禁用 */
  disabled?: boolean;
}

/**
 * 按钮权限控制组件
 * 
 * @example
 * ```tsx
 * <ButtonGuard
 *   permissions={[{ resourceCode: 'employee', action: 'create' }]}
 *   disabled={false}
 * >
 *   <Button type="primary">新建员工</Button>
 * </ButtonGuard>
 * ```
 */
export function ButtonGuard({
  permissions,
  buttonProps,
  children,
  disabled = true,
}: ButtonGuardProps) {
  const { isLoading, hasAnyPermission } = usePermission();

  const hasPermission = useMemo(() => {
    if (permissions.length === 0) return true;
    return hasAnyPermission(permissions);
  }, [permissions, hasAnyPermission]);

  if (isLoading) {
    return null;
  }

  if (!hasPermission) {
    if (disabled) {
      return React.cloneElement(children, {
        ...buttonProps,
        disabled: true,
        style: { ...children.props.style, ...buttonProps?.style, opacity: 0.5 },
      });
    }
    return null;
  }

  return React.cloneElement(children, buttonProps);
}

/**
 * 页面级权限守卫
 */
interface PageGuardProps {
  /** 页面所需权限 */
  permissions: Array<{
    resourceCode: string;
    action: string;
  }>;
  /** 重定向路径（默认跳转到 403） */
  redirectTo?: string;
}

/**
 * 页面级权限守卫（用于路由）
 * 
 * @example
 * ```tsx
 * <Route
 *   path="/admin/employees"
 *   element={
 *     <PageGuard
 *       permissions={[{ resourceCode: 'employee', action: 'read' }]}
 *       redirectTo="/403"
 *     >
 *       <EmployeeList />
 *     </PageGuard>
 *   }
 * />
 * ```
 */
export function PageGuard({
  permissions,
  redirectTo,
  children,
}: PageGuardProps & { children: React.ReactNode }) {
  const { hasAllPermissions, isLoading } = usePermission();

  const hasPermission = useMemo(() => {
    if (permissions.length === 0) return true;
    return hasAllPermissions(permissions);
  }, [permissions, hasAllPermissions]);

  if (isLoading) {
    return null;
  }

  if (!hasPermission) {
    if (redirectTo) {
      window.location.href = redirectTo;
      return null;
    }
    return <Forbidden />;
  }

  return <>{children}</>;
}
```

### 6.3 路由守卫增强

```typescript
// compensation-react/src/routes/ProtectedRoute.tsx（增强版）

import React, { useMemo } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@services/stores/authSlice';
import { usePermission } from '@hooks/usePermission';
import { Loading } from '@components/Common/Loading';
import { PermissionAlert } from '@components/PermissionGuard/PermissionAlert';

interface ProtectedRouteProps {
  /** 路由路径 */
  path: string;
  /** 所需角色（可选） */
  roles?: string[];
  /** 所需权限（可选） */
  permissions?: Array<{ resourceCode: string; action: string }>;
  /** 是否需要认证 */
  requiresAuth?: boolean;
  /** 重定向到登录页 */
  loginPath?: string;
  /** 403 页面路径 */
  forbiddenPath?: string;
  /** 组件 */
  element: React.ReactElement;
}

export function ProtectedRoute({
  path,
  roles,
  permissions,
  requiresAuth = true,
  loginPath = '/login',
  forbiddenPath = '/403',
  element,
}: ProtectedRouteProps) {
  const location = useLocation();
  const queryClient = useQueryClient();
  const { isAuthenticated, user } = useAuth();
  const { hasAllPermissions, isLoading: permLoading, version: permVersion } = usePermission();

  // 检查认证状态
  const isAuth = requiresAuth ? isAuthenticated : true;

  // 检查角色
  const hasRole = useMemo(() => {
    if (!roles || roles.length === 0) return true;
    if (!user?.roles) return false;
    return roles.some((role) => user.roles.includes(role));
  }, [roles, user]);

  // 检查权限
  const hasPermission = useMemo(() => {
    if (!permissions || permissions.length === 0) return true;
    return hasAllPermissions(permissions);
  }, [permissions, hasAllPermissions]);

  // 权限版本（用于检测变更）
  const permissionKey = `${permVersion ?? 'unknown'}`;

  // 加载状态
  if (permLoading) {
    return <Loading tip="正在检查权限..." />;
  }

  // 未认证，跳转登录
  if (!isAuth) {
    return (
      <Navigate
        to={loginPath}
        state={{ from: location.pathname }}
        replace
      />
    );
  }

  // 角色检查失败
  if (!hasRole) {
    return <Navigate to={forbiddenPath} replace />;
  }

  // 权限检查失败
  if (!hasPermission) {
    return <Navigate to={forbiddenPath} replace />;
  }

  // 权限检查通过，渲染目标组件
  return element;
}
```

---

## 七、动态权限加载与缓存策略

### 7.1 权限缓存层级

```
┌─────────────────────────────────────────────────────────────────┐
│                    权限缓存架构                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Memory Cache (内存)                      │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 权限配置    │ │ 权限版本    │ │ 权限检查结果缓存    │  │  │
│  │  │ (Config)    │ │ (Version)   │ │ (Check Results)     │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ 生命周期：  │ │ 生命周期：  │ │ 生命周期：          │  │  │
│  │  │ Session    │ │ 内存驻留    │ │ 5分钟 TTL           │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   SessionStorage                            │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 权限配置    │ │ 权限版本    │ │ 资源树数据          │  │  │
│  │  │ (Config)    │ │ (Version)   │ │ (Resource Tree)     │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ 生命周期：  │ │ 生命周期：  │ │ 生命周期：          │  │  │
│  │  │ 浏览器会话  │ │ 浏览器会话  │ │ 浏览器会话          │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                      Server Cache (Redis)                  │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 用户权限    │ │ 权限版本    │ │ 变更事件队列        │  │  │
│  │  │ (User Perm) │ │ (Version)   │ │ (Change Events)     │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ TTL: 30min  │ │ TTL: 24h    │ │ TTL: 7天           │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 权限加载策略

```typescript
// compensation-react/src/services/permissionCache.ts

import { api } from '@services/api';
import type { PermissionConfig, PermissionChangeEvent } from '@types/auth';

const CACHE_KEYS = {
  CONFIG: 'permission_config',
  VERSION: 'permission_version',
  RESOURCES: 'permission_resources',
  CHANGE_EVENTS: 'permission_change_events',
};

const CACHE_TTL = {
  MEMORY: 5 * 60 * 1000, // 5分钟内存缓存
  SESSION: 30 * 60 * 1000, // 30分钟会话缓存
};

/**
 * 权限缓存服务
 */
export class PermissionCacheService {
  private static instance: PermissionCacheService;
  private memoryCache: Map<string, { value: unknown; timestamp: number }> = new Map();
  private ws: WebSocket | null = null;
  private listeners: Set<(event: PermissionChangeEvent) => void> = new Set();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  private constructor() {}

  static getInstance(): PermissionCacheService {
    if (!PermissionCacheService.instance) {
      PermissionCacheService.instance = new PermissionCacheService();
    }
    return PermissionCacheService.instance;
  }

  /**
   * 获取权限配置（带缓存）
   */
  async getConfig(forceRefresh = false): Promise<PermissionConfig | null> {
    // 1. 检查内存缓存
    const memoryKey = `${CACHE_KEYS.CONFIG}`;
    const memoryCached = this.memoryCache.get(memoryKey);
    if (!forceRefresh && memoryCached && Date.now() - memoryCached.timestamp < CACHE_TTL.MEMORY) {
      return memoryCached.value as PermissionConfig;
    }

    // 2. 检查会话缓存
    const sessionCached = sessionStorage.getItem(CACHE_KEYS.CONFIG);
    if (!forceRefresh && sessionCached) {
      try {
        const parsed = JSON.parse(sessionCached);
        if (Date.now() - parsed.timestamp < CACHE_TTL.SESSION) {
          // 更新内存缓存
          this.memoryCache.set(memoryKey, { value: parsed.data, timestamp: parsed.timestamp });
          return parsed.data;
        }
      } catch {
        sessionStorage.removeItem(CACHE_KEYS.CONFIG);
      }
    }

    // 3. 从服务器获取
    try {
      const config = await api.get<PermissionConfig>('/auth-center/permissions/config');
      
      // 写入缓存
      this.setConfig(config);
      
      return config;
    } catch (error) {
      console.error('Failed to fetch permission config:', error);
      
      // 降级：使用会话缓存（即使过期）
      if (sessionCached) {
        try {
          return JSON.parse(sessionCached).data;
        } catch {
          return null;
        }
      }
      return null;
    }
  }

  /**
   * 设置权限配置
   */
  setConfig(config: PermissionConfig): void {
    const timestamp = Date.now();
    
    // 内存缓存
    this.memoryCache.set(CACHE_KEYS.CONFIG, { value: config, timestamp });
    
    // 会话缓存
    sessionStorage.setItem(CACHE_KEYS.CONFIG, JSON.stringify({
      data: config,
      version: config.version,
      timestamp,
    }));
    
    // 更新版本号
    sessionStorage.setItem(CACHE_KEYS.VERSION, String(config.version));
  }

  /**
   * 获取权限版本
   */
  async getVersion(): Promise<number> {
    // 先检查缓存
    const cached = sessionStorage.getItem(CACHE_KEYS.VERSION);
    if (cached) {
      return parseInt(cached, 10);
    }

    // 从服务器获取
    try {
      const { version } = await api.get<{ version: number }>('/auth-center/permissions/version');
      sessionStorage.setItem(CACHE_KEYS.VERSION, String(version));
      return version;
    } catch {
      return 0;
    }
  }

  /**
   * 检测版本变化
   */
  async detectVersionChange(): Promise<boolean> {
    const currentVersion = await this.getVersion();
    const config = await this.getConfig(true); // 强制刷新
    return config?.version !== currentVersion;
  }

  /**
   * 订阅权限变更
   */
  subscribe(listener: (event: PermissionChangeEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * 监听权限变更（WebSocket）
   */
  startListening(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/permission-push`;
    
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      console.log('Permission WebSocket connected');
      this.reconnectAttempts = 0;
      
      // 发送心跳
      this.ws?.send(JSON.stringify({ type: 'ping' }));
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'permission_change') {
          const changeEvent = data.payload as PermissionChangeEvent;
          
          // 通知所有监听器
          this.listeners.forEach((listener) => listener(changeEvent));
          
          // 清除缓存，触发刷新
          this.invalidateCache();
        }
      } catch (error) {
        console.error('Failed to parse permission change event:', error);
      }
    };

    this.ws.onclose = () => {
      console.log('Permission WebSocket disconnected');
      
      // 重连逻辑
      if (this.reconnectAttempts < this.maxReconnectAttempts) {
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
        setTimeout(() => this.startListening(), delay);
      }
    };

    this.ws.onerror = (error) => {
      console.error('Permission WebSocket error:', error);
    };
  }

  /**
   * 停止监听
   */
  stopListening(): void {
    this.ws?.close();
    this.ws = null;
  }

  /**
   * 使缓存失效
   */
  invalidateCache(): void {
    this.memoryCache.clear();
    sessionStorage.removeItem(CACHE_KEYS.CONFIG);
    sessionStorage.removeItem(CACHE_KEYS.VERSION);
    sessionStorage.removeItem(CACHE_KEYS.RESOURCES);
  }

  /**
   * 清除所有缓存
   */
  clearAll(): void {
    this.invalidateCache();
    this.stopListening();
    this.listeners.clear();
  }
}

// 导出单例
export const permissionCache = PermissionCacheService.getInstance();
```

### 7.3 权限变更响应

```typescript
// compensation-react/src/hooks/usePermissionChange.ts

import { useEffect, useCallback } from 'react';
import { message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { permissionCache } from '@services/permissionCache';
import type { PermissionChangeEvent } from '@types/auth';

/**
 * 权限变更监听 Hook
 */
export function usePermissionChange() {
  const queryClient = useQueryClient();

  const handlePermissionChange = useCallback((event: PermissionChangeEvent) => {
    console.log('Permission changed:', event);

    // 根据变更类型显示提示
    switch (event.eventType) {
      case 'GRANT':
        message.success('权限已授予');
        break;
      case 'REVOKE':
        message.warning('部分权限已被回收');
        break;
      case 'MODIFY':
        message.info('权限已变更');
        break;
      case 'CLEAR':
        message.warning('所有权限已清除');
        break;
    }

    // 清除权限缓存
    permissionCache.invalidateCache();

    // 刷新相关查询
    queryClient.invalidateQueries({ queryKey: ['permission'] });
    queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
    queryClient.invalidateQueries({ queryKey: ['resources'] });

    // 触发全局事件（用于通知其他组件）
    window.dispatchEvent(new CustomEvent('permission:change', { detail: event }));
  }, [queryClient]);

  useEffect(() => {
    // 订阅权限变更
    const unsubscribe = permissionCache.subscribe(handlePermissionChange);

    // 启动 WebSocket 监听
    permissionCache.startListening();

    return () => {
      unsubscribe();
    };
  }, [handlePermissionChange]);

  return {
    refresh: () => permissionCache.invalidateCache(),
    detectChange: () => permissionCache.detectVersionChange(),
  };
}
```

---

## 八、异常处理机制

### 8.1 权限异常分类

```typescript
// compensation-react/src/types/authException.ts

/**
 * 权限异常类型
 */
export type PermissionExceptionType = 
  | 'UNAUTHORIZED'           // 未认证（401）
  | 'FORBIDDEN'              // 无权限访问（403）
  | 'TOKEN_EXPIRED'          // Token 过期
  | 'TOKEN_INVALID'          // Token 无效
  | 'PERMISSION_DENIED'      // 权限不足
  | 'RESOURCE_NOT_FOUND'     // 资源不存在
  | 'RESOURCE_DISABLED'      // 资源已禁用
  | 'VERSION_MISMATCH'       // 权限版本不匹配
  | 'SESSION_TIMEOUT'        // 会话超时
  | 'CONCURRENT_LOGIN'       // 并发登录
  | 'UNKNOWN_ERROR';         // 未知错误

/**
 * 权限异常信息
 */
export interface PermissionException {
  type: PermissionExceptionType;
  message: string;
  code: number;
  resourceCode?: string;
  action?: string;
  requiredPermissions?: Array<{ resourceCode: string; action: string }>;
  retryAfter?: number; // 秒
  timestamp: string;
  requestId?: string;
}

/**
 * 异常处理策略
 */
export interface ExceptionHandlerStrategy {
  // 是否需要登出
  requireLogout: boolean;
  // 是否需要刷新权限
  requireRefresh: boolean;
  // 是否显示错误提示
  showMessage: boolean;
  // 是否跳转到特定页面
  redirectTo?: string;
  // 自定义处理逻辑
  customAction?: () => void;
}

/**
 * 默认异常处理策略映射
 */
export const DEFAULT_EXCEPTION_STRATEGIES: Record<PermissionExceptionType, ExceptionHandlerStrategy> = {
  UNAUTHORIZED: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login',
  },
  FORBIDDEN: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: false,
    redirectTo: '/403',
  },
  TOKEN_EXPIRED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
    customAction: () => {
      // 尝试刷新 Token
      import('@services/auth').then(({ authService }) => {
        authService.refreshToken();
      });
    },
  },
  TOKEN_INVALID: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login',
  },
  PERMISSION_DENIED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  RESOURCE_NOT_FOUND: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/404',
  },
  RESOURCE_DISABLED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  VERSION_MISMATCH: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  SESSION_TIMEOUT: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login?reason=session_timeout',
  },
  CONCURRENT_LOGIN: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login?reason=concurrent_login',
  },
  UNKNOWN_ERROR: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
  },
};
```

### 8.2 权限异常处理器

```typescript
// compensation-react/src/utils/permissionExceptionHandler.ts

import { message, Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@services/stores/authSlice';
import { permissionCache } from '@services/permissionCache';
import type { PermissionException, PermissionExceptionType } from '@types/authException';

let exceptionHandler: ((exception: PermissionException) => void) | null = null;

/**
 * 设置全局异常处理器
 */
export function setExceptionHandler(handler: (exception: PermissionException) => void): void {
  exceptionHandler = handler;
}

/**
 * 默认异常处理逻辑
 */
export function handlePermissionException(exception: PermissionException): void {
  // 如果设置了全局处理器，优先使用
  if (exceptionHandler) {
    exceptionHandler(exception);
    return;
  }

  const strategy = DEFAULT_EXCEPTION_STRATEGIES[exception.type];

  switch (exception.type) {
    case 'UNAUTHORIZED':
    case 'TOKEN_INVALID':
    case 'SESSION_TIMEOUT':
    case 'CONCURRENT_LOGIN':
      // 需要登出的情况
      handleLogoutRequired(exception, strategy);
      break;

    case 'TOKEN_EXPIRED':
      // Token 过期，尝试刷新
      handleTokenExpired(exception, strategy);
      break;

    case 'FORBIDDEN':
      // 无权限访问，跳转 403
      handleForbidden(exception, strategy);
      break;

    case 'PERMISSION_DENIED':
    case 'VERSION_MISMATCH':
    case 'RESOURCE_DISABLED':
      // 权限相关错误，刷新权限
      handlePermissionDenied(exception, strategy);
      break;

    case 'RESOURCE_NOT_FOUND':
      // 资源不存在
      handleNotFound(exception, strategy);
      break;

    default:
      // 未知错误
      handleUnknownError(exception, strategy);
  }
}

/**
 * 处理需要登出的异常
 */
function handleLogoutRequired(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.warning(exception.message || '请重新登录');
  }

  // 清除认证状态
  const authStore = useAuth.getState();
  authStore.logout();

  // 清除权限缓存
  permissionCache.clearAll();

  // 跳转登录页
  if (strategy.redirectTo) {
    const navigate = useNavigate(); // 注意：这里需要在 React 组件中使用
    window.location.href = strategy.redirectTo;
  }
}

/**
 * 处理 Token 过期
 */
function handleTokenExpired(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.info('登录状态已过期，正在刷新...');
  }

  if (strategy.customAction) {
    strategy.customAction();
  } else {
    // 默认刷新 Token
    permissionCache.invalidateCache();
  }
}

/**
 * 处理无权限访问
 */
function handleForbidden(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage && exception.message) {
    message.warning(exception.message);
  }

  if (strategy.redirectTo) {
    // 显示权限不足页面
    showForbiddenPage(exception);
  }
}

/**
 * 处理权限被拒绝
 */
function handlePermissionDenied(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    Modal.warning({
      title: '权限不足',
      content: exception.message || '您没有执行此操作的权限',
      okText: '确定',
    });
  }

  if (strategy.requireRefresh) {
    // 刷新权限配置
    permissionCache.invalidateCache();
  }
}

/**
 * 处理资源不存在
 */
function handleNotFound(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.error(exception.message || '请求的资源不存在');
  }

  if (strategy.redirectTo) {
    window.location.href = strategy.redirectTo;
  }
}

/**
 * 处理未知错误
 */
function handleUnknownError(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.error(exception.message || '发生未知错误，请稍后重试');
  }

  console.error('Permission exception:', exception);
}

/**
 * 显示 403 页面
 */
function showForbiddenPage(exception: PermissionException): void {
  const redirectUrl = encodeURIComponent(window.location.href);
  const forbiddenUrl = `/403?redirect=${redirectUrl}`;
  
  if (exception.requiredPermissions && exception.requiredPermissions.length > 0) {
    const permissionsParam = encodeURIComponent(JSON.stringify(exception.requiredPermissions));
    window.location.href = `${forbiddenUrl}&required=${permissionsParam}`;
  } else {
    window.location.href = forbiddenUrl;
  }
}
```

### 8.3 Axios 拦截器集成

```typescript
// compensation-react/src/services/api.ts（扩展）

import axios, { AxiosError, AxiosResponse } from 'axios';
import { useAuth } from '@services/stores/authSlice';
import { permissionCache } from '@services/permissionCache';
import { handlePermissionException } from '@utils/permissionExceptionHandler';
import type { PermissionException } from '@types/authException';

// 创建 Axios 实例（已有代码）

// 响应拦截器增强
api.interceptors.response.use(
  (response: AxiosResponse) => {
    // 正常响应，直接返回
    return response;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config;

    // 处理 401 未授权
    if (error.response?.status === 401) {
      const exception: PermissionException = {
        type: 'UNAUTHORIZED',
        message: (error.response.data as any)?.message || '未登录或登录已过期',
        code: 401,
        timestamp: new Date().toISOString(),
      };

      handlePermissionException(exception);
      return Promise.reject(error);
    }

    // 处理 403 无权限
    if (error.response?.status === 403) {
      const responseData = error.response.data as any;
      const exception: PermissionException = {
        type: 'FORBIDDEN',
        message: responseData?.message || '您没有权限执行此操作',
        code: 403,
        resourceCode: responseData?.resourceCode,
        action: responseData?.action,
        requiredPermissions: responseData?.requiredPermissions,
        timestamp: new Date().toISOString(),
      };

      handlePermissionException(exception);
      return Promise.reject(error);
    }

    // 处理权限相关错误码
    if (error.response?.status === 409) {
      // 版本冲突
      const exception: PermissionException = {
        type: 'VERSION_MISMATCH',
        message: '权限配置已变更，请刷新后重试',
        code: 409,
        retryAfter: 5,
        timestamp: new Date().toISOString(),
      };

      // 自动刷新权限
      permissionCache.invalidateCache();
      handlePermissionException(exception);
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);
```

---

## 九、权限审计日志

### 9.1 审计日志数据结构

```typescript
// compensation-react/src/types/auditLog.ts

/**
 * 审计日志类型
 */
export type AuditLogType = 
  | 'PERMISSION_GRANT'       // 权限授予
  | 'PERMISSION_REVOKE'      // 权限回收
  | 'PERMISSION_MODIFY'      // 权限变更
  | 'ROLE_CREATE'            // 角色创建
  | 'ROLE_UPDATE'            // 角色更新
  | 'ROLE_DELETE'            // 角色删除
  | 'ROLE_COPY'              // 角色复制
  | 'ROLE_ENABLE'            // 角色启用
  | 'ROLE_DISABLE'           // 角色禁用
  | 'USER_ROLE_ASSIGN'       // 用户角色分配
  | 'USER_ROLE_REMOVE'       // 用户角色移除
  | 'RESOURCE_CREATE'        // 资源创建
  | 'RESOURCE_UPDATE'        // 资源更新
  | 'RESOURCE_DELETE'        // 资源删除
  | 'LOGIN'                  // 登录
  | 'LOGOUT'                 // 登出
  | 'PASSWORD_CHANGE'        // 密码修改
  | 'SETTINGS_CHANGE';       // 设置变更

/**
 * 审计日志级别
 */
export type AuditLogLevel = 'INFO' | 'WARNING' | 'ERROR';

/**
 * 审计日志实体
 */
export interface AuditLog {
  id: number;
  type: AuditLogType;
  level: AuditLogLevel;
  userId: number;
  username: string;
  realName?: string | null;
  action: string;
  resourceType?: string | null;
  resourceId?: string | number | null;
  resourceName?: string | null;
  oldValue?: string | null;      // JSON 字符串
  newValue?: string | null;      // JSON 字符串
  ip?: string | null;
  userAgent?: string | null;
  requestId?: string | null;
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string | null;
  metadata?: Record<string, unknown> | null;
  createdAt: string;
}

/**
 * 审计日志查询参数
 */
export interface AuditLogQueryParams {
  page?: number;
  pageSize?: number;
  type?: AuditLogType;
  level?: AuditLogLevel;
  userId?: number;
  resourceType?: string;
  resourceId?: string | number;
  status?: 'SUCCESS' | 'FAILURE';
  startTime?: string;
  endTime?: string;
  keyword?: string;
}

/**
 * 审计日志统计
 */
export interface AuditLogStatistics {
  totalCount: number;
  successCount: number;
  failureCount: number;
  typeDistribution: Record<AuditLogType, number>;
  dailyTrend: Array<{
    date: string;
    count: number;
  }>;
}
```

### 9.2 审计日志服务

```typescript
// compensation-react/src/services/auditLog.ts

import { api } from '@services/api';
import type { AuditLog, AuditLogQueryParams, AuditLogStatistics } from '@types/auditLog';

const AUDIT_API_BASE = '/api/auth-center/audit-logs';

/**
 * 查询审计日志列表
 */
export async function fetchAuditLogs(
  params: AuditLogQueryParams
): Promise<{ list: AuditLog[]; total: number }> {
  return api.get(AUDIT_API_BASE, { params });
}

/**
 * 获取审计日志详情
 */
export async function fetchAuditLogDetail(id: number): Promise<AuditLog> {
  return api.get(`${AUDIT_API_BASE}/${id}`);
}

/**
 * 获取审计日志统计
 */
export async function fetchAuditStatistics(
  params: Pick<AuditLogQueryParams, 'type' | 'startTime' | 'endTime'>
): Promise<AuditLogStatistics> {
  return api.get(`${AUDIT_API_BASE}/statistics`, { params });
}

/**
 * 导出审计日志
 */
export async function exportAuditLogs(
  params: AuditLogQueryParams
): Promise<Blob> {
  return api.get(`${AUDIT_API_BASE}/export`, {
    params,
    responseType: 'blob',
  });
}

/**
 * 获取审计日志类型列表
 */
export async function fetchAuditLogTypes(): Promise<
  Array<{ type: AuditLogType; name: string; description: string }>
> {
  return api.get(`${AUDIT_API_BASE}/types`);
}
```

### 9.3 审计日志组件

```typescript
// compensation-react/src/pages/admin/AuditLogs.tsx（优化版）

import React, { useState, useCallback } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Card, DatePicker, Select, Tag, Space, Button, Tooltip } from 'antd';
import { DownloadOutlined, EyeOutlined, FilterOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { fetchAuditLogs, fetchAuditLogTypes, exportAuditLogs } from '@services/auditLog';
import { formatDateTime } from '@utils/form';
import type { AuditLog, AuditLogType, AuditLogQueryParams } from '@types/auditLog';
import AuditLogDetailModal from './AuditLogDetailModal';

const { RangePicker } = DatePicker;

const LEVEL_COLORS: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'orange',
  ERROR: 'red',
};

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: 'success',
  FAILURE: 'error',
};

export const AuditLogsPage: React.FC = () => {
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [queryParams, setQueryParams] = useState<AuditLogQueryParams>({
    page: 1,
    pageSize: 20,
  });

  // 查询审计日志类型
  const logTypesQuery = useQuery({
    queryKey: ['auditLogTypes'],
    queryFn: fetchAuditLogTypes,
  });

  // 查询审计日志列表
  const logsQuery = useQuery({
    queryKey: ['auditLogs', queryParams],
    queryFn: () => fetchAuditLogs(queryParams),
  });

  // 打开详情
  const handleViewDetail = useCallback((id: number) => {
    setSelectedLogId(id);
    setDetailModalVisible(true);
  }, []);

  // 导出日志
  const handleExport = useCallback(async () => {
    try {
      const blob = await exportAuditLogs(queryParams);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `audit-logs-${new Date().toISOString().split('T')[0]}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Export failed:', error);
    }
  }, [queryParams]);

  const columns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 140,
      render: (type: AuditLogType) => {
        const typeInfo = logTypesQuery.data?.find((t) => t.type === type);
        return (
          <Tag color={LEVEL_COLORS.INFO}>
            {typeInfo?.name || type}
          </Tag>
        );
      },
    },
    {
      title: '操作人',
      dataIndex: 'realName',
      width: 120,
      render: (_: unknown, record: AuditLog) => (
        <Space direction="vertical" size={0}>
          <span>{record.realName || record.username}</span>
        </Space>
      ),
    },
    {
      title: '操作',
      dataIndex: 'action',
      width: 200,
      ellipsis: true,
    },
    {
      title: '资源',
      dataIndex: 'resourceName',
      width: 150,
      render: (_: unknown, record: AuditLog) => (
        record.resourceName ? (
          <Space>
            <Tag>{record.resourceType}</Tag>
            <span>{record.resourceName}</span>
          </Space>
        ) : '-'
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={STATUS_COLORS[status]}>{status === 'SUCCESS' ? '成功' : '失败'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, record: AuditLog) => (
        <Tooltip title="查看详情">
          <Button
            type="text"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <PageContainer header={{ title: '审计日志' }}>
      <Card>
        <ProTable<AuditLog>
          columns={columns}
          dataSource={logsQuery.data?.list}
          rowKey="id"
          loading={logsQuery.isLoading}
          pagination={{
            current: queryParams.page,
            pageSize: queryParams.pageSize,
            total: logsQuery.data?.total,
            onChange: (page, pageSize) => {
              setQueryParams((prev) => ({ ...prev, page, pageSize }));
            },
          }}
          search={false}
          headerTitle={
            <Space>
              <Select
                placeholder="日志类型"
                allowClear
                style={{ width: 150 }}
                options={logTypesQuery.data?.map((t) => ({
                  label: t.name,
                  value: t.type,
                }))}
                onChange={(value) =>
                  setQueryParams((prev) => ({ ...prev, type: value, page: 1 }))
                }
              />
              <RangePicker
                onChange={(dates, dateStrings) =>
                  setQueryParams((prev) => ({
                    ...prev,
                    startTime: dateStrings[0] || undefined,
                    endTime: dateStrings[1] || undefined,
                    page: 1,
                  }))
                }
              />
            </Space>
          }
          toolBarRender={() => [
            <Button
              key="export"
              icon={<DownloadOutlined />}
              onClick={handleExport}
            >
              导出
            </Button>,
          ]}
        />
      </Card>

      {/* 详情弹窗 */}
      {detailModalVisible && selectedLogId && (
        <AuditLogDetailModal
          logId={selectedLogId}
          visible={detailModalVisible}
          onClose={() => {
            setDetailModalVisible(false);
            setSelectedLogId(null);
          }}
        />
      )}
    </PageContainer>
  );
};
```

---

## 十、角色管理融入授权中心方案

### 10.1 设计原则

**核心目标**：角色管理不再是独立入口，而是授权中心的核心组成部分。管理员在授权中心一个页面完成所有操作。

```
┌───────────────────────────────────────────────────────────────────────┐
│                    AuthCenter（唯一入口）                               │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                      角色管理区（内置）                           │  │
│  │                                                                 │  │
│  │    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │  │
│  │    │ 管理员   │ │ 人事专员 │ │ 财务专员 │ │ 部门主管 │        │  │
│  │    │ (admin)  │ │ hr       │ │ finance  │ │ manager  │        │  │
│  │    └──────────┘ └──────────┘ └──────────┘ └──────────┘        │  │
│  │                              [ + 新建角色 ]                     │  │
│  │                                                                 │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                              │                                        │
│                              ▼ 点击角色                               │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                      权限分配区（联动显示）                       │  │
│  │                                                                 │  │
│  │    [当前角色: 人事专员] [查看关联用户] [复制角色] [编辑]         │  │
│  │                                                                 │  │
│  │    ┌───────────────────────────────┐ ┌────────────────────────┐│  │
│  │    │ 资源树                          │ │ 权限配置              ││  │
│  │    │ ☑ 菜单                         │ │                       ││  │
│  │    │   ☑ Dashboard [read]          │ │ ☑ read  ✓           ││  │
│  │    │   ☑ 员工管理 [read/write]     │ │ ☑ write ✓           ││  │
│  │    │     ├─ 员工列表               │ │ ☑ delete ✗          ││  │
│  │    │     └─ 员工详情               │ │                       ││  │
│  │    │   ☑ 薪资管理 [read]           │ │ [保存并提交审批]       ││  │
│  │    │ ☑ API                         │ │                       ││  │
│  │    │   ☑ /api/employees/**        │ │                       ││  │
│  │    │   ☑ /api/salary/**           │ │                       ││  │
│  │    └───────────────────────────────┘ └────────────────────────┘│  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

### 10.2 功能整合方案

#### 10.2.1 整合内容

| 原功能 | 整合位置 | 整合方式 |
|--------|---------|---------|
| 角色列表 | 授权中心顶部 | 卡片式角色列表，点击选中后显示权限配置 |
| 角色CRUD | 授权中心内 | 弹窗表单，与权限配置联动 |
| 角色授权 | 授权中心右侧 | 内嵌资源树和权限配置区 |
| 用户授权 | 授权中心内 | 复用授权区，切换角色/用户 |
| 资源管理 | 授权中心内 | 内嵌资源树，支持勾选配置 |

#### 10.2.2 整合后路由

```typescript
// compensation-react/src/routes/index.tsx（整合后）

export const routes: RouteConfig[] = [
  // ... 其他路由

  {
    path: '/admin',
    element: <AppLayout />,
    children: [
      // 授权中心（唯一入口，包含角色管理功能）
      {
        path: 'auth-center',
        element: <AuthCenter />,
        meta: {
          title: '权限管理',
          icon: 'safety-certificate',
          permissions: [{ resourceCode: 'auth-center', action: 'read' }],
        },
      },

      // 审计日志（独立页面）
      {
        path: 'audit-logs',
        element: <AuditLogsPage />,
        meta: {
          title: '审计日志',
          icon: 'audit',
          permissions: [{ resourceCode: 'audit-log', action: 'read' }],
        },
      },
    ],
  },

  // ... 其他路由
];
```

### 10.3 迁移步骤

#### 阶段一：功能合并（1-2周）

**任务**：
1. 扩展 `AuthCenter.tsx`，添加角色管理区（顶部卡片列表）
2. 将 `RoleManagement.tsx` 的角色CRUD逻辑移入 `AuthCenter`
3. 将 `RoleAuthorization.tsx` 的权限配置逻辑整合到授权中心右侧面板
4. 统一状态管理：`useRole` + `usePermission` 组合使用

**变更文件**：
```
删除: compensation-react/src/pages/admin/RoleManagement.tsx
删除: compensation-react/src/pages/admin/RoleAuthorization.tsx
修改: compensation-react/src/pages/admin/AuthCenter.tsx (大幅扩展)
新增: compensation-react/src/components/AuthCenter/RoleCard.tsx
新增: compensation-react/src/components/AuthCenter/RoleList.tsx
新增: compensation-react/src/components/AuthCenter/PermissionPanel.tsx
新增: compensation-react/src/hooks/useRole.ts
```

#### 阶段二：入口统一（3-4周）

**任务**：
1. 隐藏旧路由 `/admin/roles`
2. 更新菜单配置，只显示 `/admin/auth-center`
3. 更新面包屑导航
4. 旧页面添加重定向到授权中心

**配置变更**：
```typescript
// menu.config.ts
{
  path: '/admin/auth-center',
  name: '权限管理',
  icon: 'safety-certificate',
  // 移除原来的 /admin/roles
}

// 旧页面添加重定向
if (process.env.NODE_ENV === 'production') {
  // 自动跳转到授权中心
  window.location.href = '/admin/auth-center';
}
```

### 10.4 组件架构

```typescript
// compensation-react/src/pages/admin/AuthCenter.tsx（整合后架构）

import React, { useState } from 'react';
import { PageContainer, Card, Space } from 'antd';
import { RoleList } from './components/RoleList';
import { PermissionPanel } from './components/PermissionPanel';
import { RoleFormModal } from './components/RoleFormModal';
import { useRole } from '@hooks/useRole';
import type { RoleInfo } from '@types/auth';

export const AuthCenter: React.FC = () => {
  const [selectedRole, setSelectedRole] = useState<RoleInfo | null>(null);
  const [formModalVisible, setFormModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleInfo | null>(null);
  
  const { roles, createRole, updateRole, deleteRole, copyRole } = useRole();

  // 选择角色
  const handleSelectRole = (role: RoleInfo) => {
    setSelectedRole(role);
  };

  // 打开创建弹窗
  const handleCreateRole = () => {
    setEditingRole(null);
    setFormModalVisible(true);
  };

  // 打开编辑弹窗
  const handleEditRole = (role: RoleInfo, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingRole(role);
    setFormModalVisible(true);
  };

  // 复制角色
  const handleCopyRole = (role: RoleInfo, e: React.MouseEvent) => {
    e.stopPropagation();
    copyRole(role.id);
  };

  return (
    <PageContainer header={{ title: '权限管理' }}>
      <Space size={16} style={{ width: '100%' }}>
        {/* 左侧：角色列表 */}
        <div style={{ width: 300 }}>
          <RoleList
            roles={roles}
            selectedRole={selectedRole}
            onSelect={handleSelectRole}
            onCreate={handleCreateRole}
            onEdit={handleEditRole}
            onCopy={handleCopyRole}
            onDelete={(role) => deleteRole(role.id)}
          />
        </div>

        {/* 右侧：权限配置面板 */}
        <div style={{ flex: 1 }}>
          {selectedRole ? (
            <PermissionPanel
              role={selectedRole}
              onUpdate={(data) => updateRole(selectedRole.id, data)}
            />
          ) : (
            <Card>
              <div style={{ textAlign: 'center', color: '#999' }}>
                请从左侧选择一个角色进行权限配置
              </div>
            </Card>
          )}
        </div>
      </Space>

      {/* 角色表单弹窗 */}
      <RoleFormModal
        visible={formModalVisible}
        role={editingRole}
        onCancel={() => {
          setFormModalVisible(false);
          setEditingRole(null);
        }}
        onOk={(data) => {
          if (editingRole) {
            updateRole(editingRole.id, data);
          } else {
            createRole(data);
          }
          setFormModalVisible(false);
          setEditingRole(null);
        }}
      />
    </PageContainer>
  );
};
```

### 10.5 旧页面移除检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 路由配置 | ☐ | 移除 `/admin/roles` 路由 |
| 菜单配置 | ☐ | 移除角色管理菜单项 |
| 面包屑 | ☐ | 更新面包屑映射 |
| 权限配置 | ☐ | 移除相关权限码 |
| 导入语句 | ☐ | 清理相关 import |
| 测试用例 | ☐ | 更新/移除相关测试 |
| 类型定义 | ☐ | 保留类型，移除组件导出 |

---

## 十一、可扩展性设计

### 11.1 插件化架构

```typescript
// compensation-react/src/auth/extensions/index.ts

import type { PermissionChecker, ResourceProvider, AuditLogger } from '../core/types';

/**
 * 权限扩展接口
 */
export interface PermissionExtension {
  // 扩展名称
  name: string;
  // 扩展版本
  version: string;
  // 权限检查器
  checker?: PermissionChecker;
  // 资源提供者
  resourceProvider?: ResourceProvider;
  // 审计日志器
  auditLogger?: AuditLogger;
  // 初始化
  init?: (context: ExtensionContext) => void;
  // 销毁
  destroy?: () => void;
}

/**
 * 扩展上下文
 */
export interface ExtensionContext {
  // 注册权限检查器
  registerChecker: (checker: PermissionChecker) => void;
  // 注册资源提供者
  registerResourceProvider: (provider: ResourceProvider) => void;
  // 注册审计日志器
  registerAuditLogger: (logger: AuditLogger) => void;
  // 获取配置
  getConfig: <T>(key: string) => T | undefined;
  // 获取当前用户
  getCurrentUser: () => { id: number; username: string; roles: string[] } | null;
}

/**
 * 扩展注册表
 */
class ExtensionRegistry {
  private extensions: Map<string, PermissionExtension> = new Map();
  private checkers: PermissionChecker[] = [];
  private resourceProviders: ResourceProvider[] = [];
  private auditLoggers: AuditLogger[] = [];

  /**
   * 注册扩展
   */
  register(extension: PermissionExtension): void {
    if (this.extensions.has(extension.name)) {
      console.warn(`Extension ${extension.name} already registered, skipping`);
      return;
    }

    this.extensions.set(extension.name, extension);
    console.log(`Extension ${extension.name} v${extension.version} registered`);

    // 注册组件
    if (extension.checker) {
      this.checkers.push(extension.checker);
    }
    if (extension.resourceProvider) {
      this.resourceProviders.push(extension.resourceProvider);
    }
    if (extension.auditLogger) {
      this.auditLoggers.push(extension.auditLogger);
    }
  }

  /**
   * 获取所有检查器
   */
  getCheckers(): PermissionChecker[] {
    return this.checkers;
  }

  /**
   * 获取所有资源提供者
   */
  getResourceProviders(): ResourceProvider[] {
    return this.resourceProviders;
  }

  /**
   * 获取所有审计日志器
   */
  getAuditLoggers(): AuditLogger[] {
    return this.auditLoggers;
  }

  /**
   * 卸载扩展
   */
  unregister(name: string): void {
    const extension = this.extensions.get(name);
    if (!extension) return;

    // 移除组件
    if (extension.checker) {
      this.checkers = this.checkers.filter((c) => c !== extension.checker);
    }
    if (extension.resourceProvider) {
      this.resourceProviders = this.resourceProviders.filter(
        (p) => p !== extension.resourceProvider
      );
    }
    if (extension.auditLogger) {
      this.auditLoggers = this.auditLoggers.filter((l) => l !== extension.auditLogger);
    }

    // 调用销毁方法
    if (extension.destroy) {
      extension.destroy();
    }

    this.extensions.delete(name);
    console.log(`Extension ${name} unregistered`);
  }
}

export const extensionRegistry = new ExtensionRegistry();
```

### 11.2 条件权限支持

```typescript
// compensation-react/src/auth/extensions/conditions.ts

/**
 * 条件权限表达式
 */
export type ConditionExpression = 
  | { type: 'always' }
  | { type: 'never' }
  | { type: 'equals'; field: string; value: unknown }
  | { type: 'in'; field: string; values: unknown[] }
  | { type: 'gt'; field: string; value: number }
  | { type: 'lt'; field: string; value: number }
  | { type: 'between'; field: string; min: number; max: number }
  | { type: 'contains'; field: string; value: string }
  | { type: 'regex'; field: string; pattern: string }
  | { type: 'and'; conditions: ConditionExpression[] }
  | { type: 'or'; conditions: ConditionExpression[] }
  | { type: 'not'; condition: ConditionExpression };

/**
 * 条件权限配置
 */
export interface ConditionPermission {
  resourceCode: string;
  action: string;
  condition: ConditionExpression;
  description?: string;
  priority: number; // 优先级，数值越大优先级越高
}

/**
 * 条件权限检查器
 */
export class ConditionPermissionChecker {
  /**
   * 检查条件是否满足
   */
  check(
    condition: ConditionExpression,
    context: Record<string, unknown>
  ): boolean {
    switch (condition.type) {
      case 'always':
        return true;
      case 'never':
        return false;
      case 'equals':
        return context[condition.field] === condition.value;
      case 'in':
        return condition.values.includes(context[condition.field]);
      case 'gt':
        return typeof context[condition.field] === 'number' 
          && context[condition.field] > condition.value;
      case 'lt':
        return typeof context[condition.field] === 'number' 
          && context[condition.field] < condition.value;
      case 'between':
        return typeof context[condition.field] === 'number'
          && context[condition.field] >= condition.min
          && context[condition.field] <= condition.max;
      case 'contains':
        return typeof context[condition.field] === 'string'
          && context[condition.field].includes(condition.value);
      case 'regex':
        try {
          const regex = new RegExp(condition.pattern);
          return typeof context[condition.field] === 'string'
            && regex.test(context[condition.field]);
        } catch {
          return false;
        }
      case 'and':
        return condition.conditions.every((c) => this.check(c, context));
      case 'or':
        return condition.conditions.some((c) => this.check(c, context));
      case 'not':
        return !this.check(condition.condition, context);
      default:
        return false;
    }
  }

  /**
   * 批量检查条件权限
   */
  checkBatch(
    permissions: ConditionPermission[],
    context: Record<string, unknown>
  ): Map<string, boolean> {
    const results = new Map<string, boolean>();
    
    // 按优先级排序
    const sorted = [...permissions].sort((a, b) => b.priority - a.priority);
    
    for (const perm of sorted) {
      const key = `${perm.resourceCode}:${perm.action}`;
      results.set(key, this.check(perm.condition, context));
    }
    
    return results;
  }
}
```

### 11.3 未来扩展方向

```
┌─────────────────────────────────────────────────────────────────┐
│                    未来扩展能力规划                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                      数据级权限 (Data Permission)          │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 行级权限    │ │ 列级权限    │ │ 动态数据脱敏        │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ • 部门数据  │ │ • 敏感字段  │ │ • 手机号掩码        │  │  │
│  │  │ • 区域数据  │ │ • 财务字段  │ │ • 身份证掩码        │  │  │
│  │  │ • 时间数据  │ │ • 自定义列  │ │ • 银行卡掩码        │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    租户隔离 (Multi-Tenant)                 │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 数据隔离    │ │ 权限隔离    │ │ 跨租户协作          │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ • 独立Schema│ │ • 租户专属  │ │ • 共享资源池        │  │  │
│  │  │ • 字段隔离  │ │ • 角色继承  │ │ • 权限委托          │  │  │
│  │  │ • 逻辑隔离  │ │ • 资源可见  │ │ • 临时授权          │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    工作流集成 (Workflow)                   │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ 审批流程    │ │ 授权申请    │ │ 权限变更追踪        │  │  │
│  │  │             │ │             │ │                     │  │  │
│  │  │ • 权限变更  │ │ • 申请模板  │ │ • 变更历史          │  │  │
│  │  │ • 角色调整  │ │ • 审批链    │ │ • 版本对比          │  │  │
│  │  │ • 紧急授权  │ │ • 自动派发  │ │ • 影响分析          │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 十二、实施建议

### 12.1 开发优先级

| 优先级 | 功能模块 | 工作量 | 风险 | 说明 |
|--------|---------|--------|------|------|
| P0 | 统一授权中心页面整合 | 中 | 低 | 整合现有页面，消除重复 |
| P0 | 权限缓存优化 | 低 | 低 | 提升加载性能 |
| P1 | 权限守卫组件 | 中 | 中 | 页面/组件/按钮级控制 |
| P1 | 异常处理机制 | 低 | 低 | 统一错误处理 |
| P2 | 实时权限推送 | 中 | 高 | WebSocket 集成 |
| P2 | 审计日志 | 中 | 低 | 操作记录 |
| P3 | 条件权限 | 高 | 高 | 数据级权限（预留） |
| P3 | 插件化扩展 | 高 | 中 | 预留扩展能力 |

### 12.2 测试计划

```typescript
// compensation-react/src/tests/auth-center.spec.ts

/**
 * 授权中心测试用例
 */
describe('AuthCenter', () => {
  describe('权限加载', () => {
    it('should load permissions on login', async () => {
      // 登录后应自动加载权限配置
    });

    it('should use cached permissions within TTL', async () => {
      // 缓存有效期内应使用缓存
    });

    it('should refresh permissions on version change', async () => {
      // 版本号变化时应重新加载
    });
  });

  describe('权限检查', () => {
    it('should return true when user has permission', async () => {
      // 有权限时应返回 true
    });

    it('should return false when user lacks permission', async () => {
      // 无权限时应返回 false
    });

    it('should check multiple permissions correctly', async () => {
      // AND/OR 模式应正确工作
    });
  });

  describe('权限守卫', () => {
    it('should render children when authorized', async () => {
      // 有权限时应渲染子组件
    });

    it('should show fallback when unauthorized', async () => {
      // 无权限时应显示 fallback
    });

    it('should redirect to 403 page when forbidden', async () => {
      // 禁止访问时应跳转 403
    });
  });

  describe('实时推送', () => {
    it('should receive permission change events', async () => {
      // 应能接收权限变更事件
    });

    it('should refresh permissions on change', async () => {
      // 收到变更事件后应刷新权限
    });

    it('should handle WebSocket reconnection', async () => {
      // WebSocket 断连后应重连
    });
  });

  describe('异常处理', () => {
    it('should handle 401 correctly', async () => {
      // 401 应跳转登录
    });

    it('should handle 403 correctly', async () => {
      // 403 应跳转 403 页面
    });

    it('should show friendly error message', async () => {
      // 应显示友好的错误提示
    });
  });
});
```

### 12.3 性能基准

```
┌─────────────────────────────────────────────────────────────────┐
│                    性能基准指标                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  指标                        │ 目标值          │ 告警阈值        │
│  ───────────────────────────┼────────────────┼────────────────│
│  权限配置首次加载时间        │ < 500ms        │ > 1s          │
│  权限检查响应时间            │ < 10ms         │ > 50ms        │
│  权限版本检测时间            │ < 50ms         │ > 200ms       │
│  权限缓存命中率              │ > 95%          │ < 80%         │
│  WebSocket 连接建立时间      │ < 200ms        │ > 500ms       │
│  页面首次渲染时间 (FMP)      │ < 1s           │ > 2s          │
│  交互响应时间 (TTI)          │ < 100ms        │ > 300ms       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 十三、总结

### 13.1 核心价值

1. **统一管理**：消除功能重叠，所有权限操作在一个入口完成
2. **粒度控制**：支持页面级、组件级、按钮级多层次权限控制
3. **实时响应**：权限变更即时生效，无需用户重新登录
4. **安全可控**：完整的审计日志，所有操作可追溯
5. **可扩展性**：预留多种扩展能力，支持未来业务发展

### 13.2 迁移风险与应对

| 风险 | 可能性 | 影响 | 应对措施 |
|------|--------|------|---------|
| 旧页面功能缺失 | 低 | 高 | 迁移前完整对比测试 |
| 权限缓存失效 | 中 | 中 | 多级缓存，降级策略 |
| WebSocket 连接不稳定 | 中 | 低 | 自动重连，心跳检测 |
| 权限检查逻辑变更 | 低 | 高 | 保持兼容，提供迁移脚本 |

### 13.3 下一步行动

1. **立即执行**：完成授权中心页面整合，消除重复代码
2. **短期目标**：实现权限守卫组件和异常处理机制
3. **中期目标**：集成实时推送和审计日志功能
4. **长期规划**：实现条件权限和租户隔离能力

---

> 文档版本：1.0
> 最后更新：2025-02-01
> 维护团队：前端架构组
