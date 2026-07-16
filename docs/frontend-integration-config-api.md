# 集成配置管理 API 文档

## 概述

本文档描述了管理员在后台前端页面进行第三方服务集成配置的 API 接口。

## 基础信息

- **Base URL**: `/admin/integration-configs`
- **认证**: 需要 `ADMIN` 角色
- **响应格式**: JSON

## CRUD 对照

- `CREATE/UPDATE`: `PUT /admin/integration-configs/{platformType}`
- `READ(LIST)`: `GET /admin/integration-configs`
- `READ(DETAIL)`: `GET /admin/integration-configs/{platformType}`
- `DELETE(逻辑禁用)`: `DELETE /admin/integration-configs/{platformType}`
- `连接检测`: `POST /admin/integration-configs/{platformType}/test-connection`

## 1. 获取所有配置列表

获取所有支持的集成平台配置概览。

**请求**
```
GET /admin/integration-configs
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "platformType": "wechat",
      "platformName": "企业微信",
      "enabled": true,
      "configured": true,
      "connectionStatus": "connected",
      "lastModified": "2024-01-15T10:30:00"
    },
    {
      "platformType": "dingtalk",
      "platformName": "钉钉",
      "enabled": false,
      "configured": false,
      "connectionStatus": "disconnected",
      "lastModified": null
    },
    {
      "platformType": "feishu",
      "platformName": "飞书",
      "enabled": false,
      "configured": false,
      "connectionStatus": "disconnected",
      "lastModified": null
    },
    {
      "platformType": "alipay",
      "platformName": "支付宝",
      "enabled": true,
      "configured": true,
      "connectionStatus": "connected",
      "lastModified": "2024-01-14T15:20:00"
    },
    {
      "platformType": "yunzhanghu",
      "platformName": "云账户",
      "enabled": true,
      "configured": true,
      "connectionStatus": "connected",
      "lastModified": "2024-01-16T12:00:00"
    },
    {
      "platformType": "sms",
      "platformName": "短信服务",
      "enabled": true,
      "configured": true,
      "connectionStatus": "connected",
      "lastModified": "2024-01-13T09:15:00"
    },
    {
      "platformType": "email",
      "platformName": "邮件服务",
      "enabled": false,
      "configured": false,
      "connectionStatus": "disconnected",
      "lastModified": null
    },
    {
      "platformType": "encryption",
      "platformName": "加密配置",
      "enabled": true,
      "configured": true,
      "connectionStatus": "connected",
      "lastModified": "2024-01-12T14:00:00"
    }
  ]
}
```

**字段说明**
- `platformType`: 平台类型标识
- `platformName`: 平台显示名称
- `enabled`: 是否启用
- `configured`: 是否已配置
- `connectionStatus`: 连接状态 (`connected`/`disconnected`/`unknown`)
- `lastModified`: 最后修改时间

## 2. 获取单个配置详情

获取特定平台的配置详情（敏感信息已脱敏）。

**请求**
```
GET /admin/integration-configs/{platformType}
```

**路径参数**
- `platformType`: 平台类型 (`wechat`/`dingtalk`/`feishu`/`alipay`/`yunzhanghu`/`sms`/`email`/`encryption`)

**响应示例（企业微信）**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "platformType": "wechat",
    "platformName": "企业微信",
    "enabled": true,
    "config": {
      "corpId": "***wx123456789",
      "corpSecret": "******",
      "agentId": "1000002"
    },
    "connectionStatus": "connected",
    "lastModified": "2024-01-15T10:30:00"
  }
}
```

## 3. 保存配置

创建或更新平台配置。

**请求**
```
PUT /admin/integration-configs/{platformType}
Content-Type: application/json
```

**请求体示例（企业微信）**
```json
{
  "enabled": true,
  "wechat": {
    "corpId": "ww123456789abcdef",
    "corpSecret": "your-corp-secret-here",
    "agentId": "1000002"
  }
}
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": "配置保存成功"
}
```

## 4. 禁用配置

禁用指定平台的配置。

**请求**
```
DELETE /admin/integration-configs/{platformType}
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": "配置已禁用"
}
```

## 5. 测试连接

测试指定平台的连接状态。

**请求**
```
POST /admin/integration-configs/{platformType}/test-connection
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

## 平台配置格式

### 企业微信 (`wechat`)
```json
{
  "enabled": true,
  "wechat": {
    "corpId": "企业ID",
    "corpSecret": "应用密钥",
    "agentId": "应用ID"
  }
}
```

**必填字段**: `corpId`, `corpSecret`

### 钉钉 (`dingtalk`)
```json
{
  "enabled": true,
  "dingtalk": {
    "appKey": "应用AppKey",
    "appSecret": "应用AppSecret"
  }
}
```

**必填字段**: `appKey`, `appSecret`

### 飞书 (`feishu`)
```json
{
  "enabled": true,
  "feishu": {
    "appId": "应用ID",
    "appSecret": "应用密钥"
  }
}
```

**必填字段**: `appId`, `appSecret`

### 支付宝 (`alipay`)
```json
{
  "enabled": true,
  "alipay": {
    "appId": "应用ID",
    "privateKey": "应用私钥",
    "publicKey": "支付宝平台公钥",
    "serverUrl": "https://openapi.alipay.com/gateway.do",
    "charset": "UTF-8",
    "signType": "RSA2",
    "format": "json",
    "notifyUrl": "回调地址",
    "returnUrl": "返回地址",
    "certMode": "publicKey",
    "encryptKey": "AES加密密钥(32位)",
    "encryptType": "AES",
    "appCertPath": "/path/to/appCert.crt",
    "alipayCertPath": "/path/to/alipayCert.crt",
    "alipayRootCertPath": "/path/to/alipayRootCert.crt",
    "singleLimit": 10000,
    "dailyLimit": 100000,
    "realNameVerify": false
  }
}
```

**必填字段**: `appId`, `privateKey`

**字段说明**:
- `certMode`: 密钥模式，`publicKey`(公钥模式) 或 `cert`(证书模式)
- `encryptKey`: 接口内容加密密钥（32位），从支付宝开放平台获取
- `encryptType`: 加密类型，固定值 `AES`
- `appCertPath`: 应用公钥证书路径（证书模式必填）
- `alipayCertPath`: 支付宝公钥证书路径（证书模式必填）
- `alipayRootCertPath`: 支付宝根证书路径（证书模式必填）
- `singleLimit`: 单笔转账限额（元）
- `dailyLimit`: 日累计转账限额（元）
- `realNameVerify`: 是否校验收款人姓名

**接口内容加密说明**:
当配置了 `encryptKey` 时，系统会自动启用支付宝接口内容AES加密。这需要先在支付宝开放平台的应用详情页开启"接口内容加密方式"。加密后的请求会自动处理，无需前端额外操作。

### 云账户 (`yunzhanghu`)
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

**必填字段**: `dealerId`, `brokerId`, `appKey`, `des3Key`, `rsaPrivateKey`, `rsaPublicKey`, `signType`, `url`, `dealerPlatformName`

**沙箱地址**: `https://api-service.yunzhanghu.com/sandbox`

### 短信服务 (`sms`)
```json
{
  "enabled": true,
  "sms": {
    "provider": "aliyun",
    "accessKeyId": "阿里云AccessKeyId",
    "accessKeySecret": "阿里云AccessKeySecret",
    "signName": "短信签名",
    "templateCode": "默认模板代码",
    "dailyLimit": 10000,
    "rateLimitPerMinute": 60
  }
}
```

**支持的服务商**: `aliyun`, `tencent`, `huawei`, `mock`
**必填字段**: `provider`

### 邮件服务 (`email`)
```json
{
  "enabled": true,
  "email": {
    "host": "smtp.example.com",
    "port": 587,
    "username": "用户名",
    "password": "密码",
    "fromAddress": "发件人邮箱",
    "fromName": "发件人名称",
    "ssl": false,
    "tls": true,
    "encoding": "UTF-8"
  }
}
```

**必填字段**: `host`, `username`

### 加密配置 (`encryption`)
```json
{
  "enabled": true,
  "encryption": {
    "aesKey": "AES加密密钥(至少16字符)",
    "sm4Key": "SM4加密密钥(至少16字符)",
    "algorithm": "SM4+AES",
    "keyDerivation": "SHA-256",
    "keyRotationDays": 90
  }
}
```

**必填字段**: 至少设置 `aesKey` 或 `sm4Key` 其中之一

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 权限不足（需要ADMIN角色） |
| 404 | 配置不存在 |
| 500 | 服务器内部错误 |

## 前端集成建议

### 1. 配置列表页面
- 显示所有平台的配置状态
- 提供启用/禁用开关
- 显示连接状态指示器
- 提供配置和测试按钮

### 2. 配置表单页面
- 根据平台类型动态显示表单字段
- 对敏感字段使用密码输入框
- 提供字段说明和验证提示
- 支持测试连接功能

### 3. 状态指示
- `connected`: 绿色，表示连接正常
- `disconnected`: 红色，表示连接失败或未配置
- `unknown`: 灰色，表示状态未知

### 4. 操作流程
1. 查看配置列表
2. 选择需要配置的平台
3. 填写配置表单
4. 测试连接
5. 保存配置
6. 启用平台

## 安全注意事项

1. **敏感信息脱敏**: API返回的配置信息中，密钥等敏感字段已脱敏处理
2. **权限控制**: 所有接口都需要ADMIN角色权限
3. **审计日志**: 所有配置操作都会记录审计日志
4. **加密存储**: 所有配置信息在数据库中加密存储
5. **HTTPS**: 生产环境必须使用HTTPS传输
