# Ant Design Icons 导出错误排查指南

## 问题描述

```
Unexpected Application Error!
The requested module '/node_modules/.vite/deps/@ant-design_icons.js?v=fa6433fc' 
does not provide an export named 'ServerOutlined'
```

## 根本原因

**@ant-design/icons v5 → v6 的重大变化：**

在 v6.x 版本中，部分图标被**重命名**。具体来说：

| v5.x 名称 | v6.x 名称 |
|-----------|-----------|
| `ServerOutlined` | `CloudServerOutlined` |

当前项目使用 `@ant-design/icons: ^6.1.0`，因此 `ServerOutlined` 不再可用。

## 解决方案

### 步骤 1：将 `ServerOutlined` 替换为 `CloudServerOutlined`

```typescript
// 旧代码 (v5.x)
import { ServerOutlined } from '@ant-design/icons';
<ServerOutlined />

// 新代码 (v6.x)
import { CloudServerOutlined } from '@ant-design/icons';
<CloudServerOutlined />
```

> ✅ 已修复：`compensation-react/src/pages/admin/Monitor.tsx` 中的 `ServerOutlined` 已替换为 `CloudServerOutlined`

### 步骤 2：清理 Vite 缓存

```bash
cd compensation-react
rm -rf node_modules/.vite
npm run dev
```

### 步骤 3：如仍有缓存问题，完全重新安装

```bash
rm -rf node_modules package-lock.json
npm install
```

## 验证步骤

1. 启动开发服务器：
   ```bash
   npm run dev
   ```

2. 访问 `/admin/monitor` 页面

3. 检查控制台是否有导出错误

4. 确认服务器图标正常显示

## 常见问题

### Q: 如何查找可用的图标名称？

```bash
# 查找服务器相关图标
ls node_modules/@ant-design/icons/es/icons/ | grep -i server
# 输出：CloudServerOutlined.js (v6.x)
```

### Q: 如何快速查找所有图标？

```bash
ls node_modules/@ant-design/icons/es/icons/ | wc -l  # 查看图标总数
ls node_modules/@ant-design/icons/es/icons/          # 列出所有图标
```

### Q: 清除缓存后问题仍然存在？

A: 尝试完全删除 `node_modules` 后重新安装：

```bash
rm -rf node_modules package-lock.json
npm install
```

### Q: 如何确认图标是否存在？

```typescript
import * as Icons from '@ant-design/icons';
console.log(Icons.CloudServerOutlined); // v6.x
```

### Q: 是否需要配置别名？

一般不需要，但如果遇到其他解析问题：

```typescript
// vite.config.ts
resolve: {
  alias: {
    '@ant-design/icons': '@ant-design/icons/esm',
  }
}
```

## v5 → v6 迁移检查清单

- [x] 将 `ServerOutlined` 替换为 `CloudServerOutlined`
- [ ] 运行 `npm run dev` 检查其他图标错误
- [ ] 清理 Vite 缓存：`rm -rf node_modules/.vite`
- [ ] 测试 `/admin/monitor` 页面

## 相关资源

- [Ant Design Icons GitHub](https://github.com/ant-design/ant-design-icons)
- [Ant Design Icons v6.0.0 发布说明](https://github.com/ant-design/ant-design-icons/releases/tag/v6.0.0)
- [Vite 依赖预构建文档](https://vitejs.dev/guide/dep-pre-bundling.html)