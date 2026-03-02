# 云账户沙箱快速接入（接口增删改查 + 示例）

## 1. 平台类型

- `platformType`: `yunzhanghu`

## 2. CRUD 接口

- 列表查询：`GET /api/admin/integration-configs`
- 详情查询：`GET /api/admin/integration-configs/yunzhanghu`
- 新增/修改：`PUT /api/admin/integration-configs/yunzhanghu`
- 禁用（逻辑删除）：`DELETE /api/admin/integration-configs/yunzhanghu`
- 连通性测试：`POST /api/admin/integration-configs/yunzhanghu/test-connection`

## 3. 沙箱配置请求体（可直接调用）

```json
{
  "enabled": true,
  "yunzhanghu": {
    "dealerId": "25xxxx15",
    "brokerId": "275xxxx44",
    "appKey": "Isg2Wxxxxzx6iP",
    "des3Key": "0gyU3Fxxxxk516E",
    "rsaPrivateKey": "-----BEGIN PRIVATE KEY-----...-----END PRIVATE KEY-----",
    "rsaPublicKey": "-----BEGIN PUBLIC KEY-----...-----END PUBLIC KEY-----",
    "signType": "rsa",
    "url": "https://api-service.yunzhanghu.com/sandbox",
    "notifyUrl": "https://your-domain/api/v1/settlement/callback/yunzhanghu",
    "projectId": "payroll",
    "dealerPlatformName": "薪酬助手",
    "checkName": "Check",
    "isDebug": true
  }
}
```

## 4. cURL 示例

```bash
curl -X PUT "http://localhost:8080/api/admin/integration-configs/yunzhanghu" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "enabled": true,
    "yunzhanghu": {
      "dealerId": "25xxxx15",
      "brokerId": "275xxxx44",
      "appKey": "Isg2Wxxxxzx6iP",
      "des3Key": "0gyU3Fxxxxk516E",
      "rsaPrivateKey": "-----BEGIN PRIVATE KEY-----...-----END PRIVATE KEY-----",
      "rsaPublicKey": "-----BEGIN PUBLIC KEY-----...-----END PUBLIC KEY-----",
      "signType": "rsa",
      "url": "https://api-service.yunzhanghu.com/sandbox",
      "notifyUrl": "https://your-domain/api/v1/settlement/callback/yunzhanghu",
      "projectId": "payroll",
      "dealerPlatformName": "薪酬助手",
      "checkName": "Check",
      "isDebug": true
    }
  }'
```

```bash
curl -X POST "http://localhost:8080/api/admin/integration-configs/yunzhanghu/test-connection" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

## 5. 必填字段

- `dealerId`
- `brokerId`
- `appKey`
- `des3Key`
- `rsaPrivateKey`
- `rsaPublicKey`
- `signType`
- `url`

## 6. 说明

- 本系统当前接入的是云账户 SDK：`com.yunzhanghu.openapi:sdk:1.4.38-RELEASE`。
- 配置统一存储在 `integration_config` 表中（加密落库），不依赖 yml。
- 完整回归步骤见：`docs/yunzhanghu-crud-regression.md`。
- 可直接执行自动化脚本：`scripts/test_yunzhanghu_config_crud.sh`。
