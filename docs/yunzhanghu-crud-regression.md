# 云账户配置 CRUD 回归清单

> 目的：验证“配置存库 + 禁用后可读 + 再次启用可编辑”闭环行为
> 适用环境：本地开发或测试环境（`/api/admin/integration-configs`）

## 1. 自动化脚本（推荐）

已提供脚本：`scripts/test_yunzhanghu_config_crud.sh`

执行命令：

```bash
BASE_URL="http://localhost:8080/api" USERNAME="admin" ./scripts/test_yunzhanghu_config_crud.sh
```

脚本覆盖的核心场景：

1. 启用并保存 `yunzhanghu` 配置（`PUT`）
2. 读取详情（`GET`）确认 `enabled=true` 且配置脱敏回显
3. 禁用配置（`DELETE`）
4. 再读详情确认 `enabled=false` 且 `config` 仍可读
5. 重新启用并保存，确认“禁用后可继续编辑”
6. 列表校验 `configured=true`

## 2. 前端页面手工回归

页面：`/system/integration`

按下面顺序验证：

1. 打开“云账户”卡片，点击“配置”，填入沙箱参数并保存。
2. 关闭弹窗后重新打开，确认字段已脱敏回显（如 `dealerId` 显示 `***1234`）。
3. 点击“禁用”，确认卡片状态变为“未启用”。
4. 再次点击“配置”，确认配置仍可回显，不是空表单。
5. 修改一个非敏感字段（如 `notifyUrl`）并保存，确认保存成功。
6. 再次点击“测试连接”，确认接口能正常返回（成功/失败均应有明确提示，不报前端异常）。

## 3. 通过标准

满足以下全部条件即判定通过：

1. `DELETE` 后详情接口仍返回 `data.config`（非 `null`）。
2. `DELETE` 后详情接口 `data.enabled=false`。
3. 再次 `PUT enabled=true` 可以成功，且不需要重新完整录入配置。
4. 列表接口 `configured=true` 与详情状态一致。

## 4. 常见失败与定位

1. 禁用后详情变成空配置：
原因通常是读取逻辑错误地按 `enabled=true` 过滤。

2. 禁用后再启用，解密失败：
原因通常是禁用时把已加密密文再次调用加密保存，产生重复加密。

3. 前端弹窗数据丢失：
优先检查 `GET /admin/integration-configs/{platformType}` 的返回是否包含 `config`。

