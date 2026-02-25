# 授权中心 - 新架构实现

## 📋 概述

这是授权中心的重新设计实现，基于以下原则：

1. **解耦复杂页面**：将原来的一体化授权中心拆分为多个独立、简洁的页面
2. **遵循 Ant Design 设计规范**：使用标准的 Ant Design 组件和布局模式
3. **降低信息密度**：每个页面专注一个核心任务，提升用户体验
4. **清晰的导航流程**：页面间通过明确的导航关系连接

## 🏗️ 架构设计

### 页面结构

```
授权中心
│
├─ 用户授权模块 (4个页面)
│  ├─ UserList.tsx              # 用户列表页（简洁表格）
│  ├─ UserRoleAssign.tsx        # 用户角色分配页（Transfer穿梭框）
│  ├─ UserPermissionConfig.tsx  # 用户权限配置页（Tree树形选择）
│  └─ UserPermissionView.tsx    # 用户权限查看页（只读展示）[待实现]
│
├─ 角色管理模块 (4个页面)
│  ├─ RoleList.tsx              # 角色列表页（卡片式布局）
│  ├─ RoleEdit.tsx              # 角色编辑页（简单表单）[待实现]
│  ├─ RolePermissionConfig.tsx  # 角色权限配置页（Tree + Collapse）
│  └─ RoleMembers.tsx           # 角色成员管理页（表格展示）[待实现]
│
└─ 资源管理模块 (2个页面)
   ├─ ResourceList.tsx          # 资源树列表页 [待实现]
   └─ ResourceEdit.tsx          # 资源编辑页 [待实现]
```

### 目录结构

```
src/pages/admin/auth-center/
├── index.ts                    # 导出所有组件
├── users/
│   ├── UserList.tsx
│   ├── UserRoleAssign.tsx
│   └── UserPermissionConfig.tsx
├── roles/
│   ├── RoleList.tsx
│   └── RolePermissionConfig.tsx
└── resources/
    └── (待实现)
```

## 🚀 快速开始

### 1. 集成路由

在 `src/routes/index.tsx` 中添加新的授权中心路由：

```typescript
import { authCenterRoutes } from './authCenterRoutes';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      // ... 现有路由

      // 授权中心路由（新架构）
      ...authCenterRoutes.map(route => ({
        ...route,
        element: withGuard(route.element, route.meta.roles)
      })),

      // 保留旧的授权中心路由用于兼容
      {
        path: 'admin/auth-center',
        element: withGuard(<Suspense fallback={<Loading />}><AuthCenterPage /></Suspense>, ['ADMIN']),
      },
    ],
  },
]);
```

### 2. 更新导航菜单

在菜单配置中添加新的授权中心菜单项：

```typescript
{
  key: 'auth-center',
  label: '授权中心',
  icon: <SafetyCertificateOutlined />,
  children: [
    {
      key: 'users',
      label: '用户授权',
      path: '/admin/auth-center/users',
    },
    {
      key: 'roles',
      label: '角色管理',
      path: '/admin/auth-center/roles',
    },
    {
      key: 'resources',
      label: '资源管理',
      path: '/admin/auth-center/resources',
    },
  ],
}
```

### 3. 使用路径常量

在代码中使用预定义的路径常量进行导航：

```typescript
import { AUTH_CENTER_PATHS } from '@routes/authCenterRoutes';

// 导航到用户角色分配页
navigate(AUTH_CENTER_PATHS.USER_ROLE_ASSIGN(userId));

// 导航到角色权限配置页
navigate(AUTH_CENTER_PATHS.ROLE_PERMISSION_CONFIG(roleId));
```

## 📦 已实现的页面

### ✅ UserList - 用户列表页

**功能特点：**
- 简洁的表格布局
- 强大的搜索功能（支持用户名、员工姓名、工号等）
- 角色标签展示
- 平台绑定状态显示
- 快速操作菜单（分配角色、配置权限、查看权限）

**使用的 Ant Design 组件：**
- `Table`、`Input.Search`、`Tag`、`Badge`、`Dropdown`

### ✅ UserRoleAssign - 用户角色分配页

**功能特点：**
- 使用 Transfer 穿梭框组件
- 左侧显示"可选角色"，右侧显示"当前角色"
- 支持搜索和筛选
- 底部显示权限预览统计
- 用户基本信息展示

**使用的 Ant Design 组件：**
- `Transfer`、`Descriptions`、`Statistic`、`Alert`

### ✅ UserPermissionConfig - 用户权限配置页

**功能特点：**
- 使用 Tree 组件展示资源树
- 支持勾选资源节点
- Collapse 折叠面板配置操作权限
- 提示非管理员需要审批
- 权限统计展示

**使用的 Ant Design 组件：**
- `Tree`、`Collapse`、`Checkbox.Group`、`Statistic`

### ✅ RoleList - 角色列表页

**功能特点：**
- 卡片式布局（比表格更直观）
- 按角色类型分组（系统/业务/自定义）
- 显示成员数和资源数统计
- 系统角色带锁定标识
- 丰富的操作按钮（编辑/复制/禁用/删除）

**使用的 Ant Design 组件：**
- `Card`、`Row`、`Col`、`Statistic`、`Collapse`、`Tag`

### ✅ RolePermissionConfig - 角色权限配置页

**功能特点：**
- 独立页面（专注权限配置）
- 资源树选择
- Collapse 折叠面板配置操作权限
- 权限统计展示
- 角色基本信息展示

**使用的 Ant Design 组件：**
- `Tree`、`Collapse`、`Checkbox.Group`、`Statistic`、`Descriptions`

## 🎨 设计亮点

### 1. 信息密度降低

- **旧版**：一个页面包含所有功能，信息密度高
- **新版**：每个页面专注一个任务，信息清晰易懂

### 2. 操作流程清晰

- **旧版**：在同一页面内切换模式
- **新版**：通过页面跳转，流程更自然

### 3. 组件使用标准化

- 完全使用 Ant Design 标准组件
- 遵循 Ant Design Pro 的布局规范
- 保持一致的视觉风格

### 4. 响应式友好

- 所有页面支持响应式布局
- 移动端适配（卡片布局、垂直堆叠）

## 🔧 技术栈

- **React 18** + **TypeScript**
- **Ant Design 5.x**
- **Ant Design Pro Components**
- **React Router v6**
- **TanStack Query** (React Query)

## 📝 开发规范

### 1. 组件命名

- 页面组件使用 PascalCase：`UserList.tsx`
- 功能组件使用 PascalCase：`RoleCard.tsx`

### 2. 文件组织

```
ComponentName/
├── index.tsx           # 主组件
├── types.ts            # 类型定义
├── hooks.ts            # 自定义 Hooks
└── components/         # 子组件
    └── SubComponent.tsx
```

### 3. 代码注释

每个文件顶部必须包含：
- 功能描述
- 设计原则
- 使用的核心组件

### 4. 类型安全

- 所有组件都使用 TypeScript
- 定义明确的 Props 接口
- 使用类型推导减少冗余

## 🚧 待实现功能

### 高优先级

- [ ] 用户权限查看页（只读展示）
- [ ] 角色编辑页（基本信息表单）
- [ ] 角色成员管理页

### 中优先级

- [ ] 资源管理模块（ResourceList、ResourceEdit）
- [ ] 批量操作功能（批量分配角色）
- [ ] 导出功能（导出用户权限报表）

### 低优先级

- [ ] 权限变更历史查看
- [ ] 权限申请工作流集成
- [ ] 权限冲突检测

## 📚 相关文档

- [Ant Design 官方文档](https://ant.design/)
- [Ant Design Pro 组件文档](https://procomponents.ant.design/)
- [React Router v6 文档](https://reactrouter.com/)
- [TanStack Query 文档](https://tanstack.com/query/latest)

## 🤝 贡献指南

1. 遵循现有的代码风格和组件结构
2. 确保所有新功能都有 TypeScript 类型定义
3. 使用 Ant Design 标准组件，避免自定义样式
4. 编写清晰的注释和文档

## 📧 联系方式

如有问题或建议，请联系开发团队。

---

**最后更新：** 2026-02-03
**版本：** 1.0.0
**作者：** 芙宁娜 (猫娘工程师) ฅ'ω'ฅ
