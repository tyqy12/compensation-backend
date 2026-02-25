# Payroll API v1（只读 PT 外部应用）

说明：本规范定义“兼职（PT）只读”对外 API。外部应用通过注册的 clientId 使用安全凭证访问，仅可查询 PT 数据（批次、工资单、工资条）。全职（FT）数据面向内部系统，不对外暴露。

## 1. 安全与鉴权
- 方案一（推荐）：OAuth2 Client Credentials
  - token 端点：`POST /api/oauth/token`（具体实现沿用现有认证基础）
  - scope：`payroll:read`、`payslip:read`
- 方案二：API Key + HMAC 签名（Header）
  - `X-App-Id: <clientId>`
  - `X-Timestamp: <epoch_ms>`
  - `X-Signature: HMAC_SHA256(baseString, clientSecret)`
  - baseString 约定：`method + "\n" + path + "\n" + query + "\n" + timestamp`
- 限流：按 appId + IP（示例：1000 req/min，可配置）
- 审计：记录 appId、IP、scope、耗时、结果

## 2. 错误码
- 400 参数错误或校验失败（字段缺失、格式错误）
- 401 未授权/凭证无效
- 403 权限不足（无 scope 或越权访问 FT 数据）
- 404 资源不存在
- 429 超出限流
- 5xx 服务器内部错误

`{ code, message, data }` 统一响应包装，成功 `code=200`。

## 3. 资源定义
### payslip（工资条）对象
- id: string（或 long）
- employeeRef: { employeeId | platformType+platformUserId }
- period: YYYY-MM
- employmentType: part_time
- items: [{ code, name, amount, type, taxable, showOnPayslip, order }]
- grossAmount/taxAmount/socialAmount/netAmount
- currency: CNY
- departments: ["主部门","从属部门"]
- generatedAt: ISO datetime

### payroll_batch（发薪批次）对象（只读视图）
- id: long
- periodLabel: YYYY-MM
- type: part_time
- status: approved|paid|archived
- currency: CNY
- lineCount: int
- paidAt: ISO datetime（如已支付）

### payroll_line（工资行）对象（只读视图）
- id: long
- batchId: long
- employeeRef: 同上
- employmentType: part_time
- grossAmount/taxAmount/socialAmount/netAmount
- currency: CNY
- departments: [..]

## 4. 查询接口（只读）

### 4.1 列出 PT 发薪批次
GET `/api/v1/payroll/batches`
Query：
- type=part_time（固定必填）
- period=YYYY-MM（可选）
- status=approved|paid|archived（可选，默认 approved|paid）
- page=1 size=20（默认 20，最大 100）
Response 200：
```
{ "code":200, "message":"OK", "data": { "records":[
  {"id":101,"periodLabel":"2025-09","type":"part_time","status":"paid","currency":"CNY","lineCount":128,"paidAt":"2025-10-01T08:30:00Z"}
], "total":1, "current":1, "size":20 } }
```

### 4.2 批次详情
GET `/api/v1/payroll/batches/{id}`
Response 200：
```
{ "code":200, "message":"OK", "data":
  {"id":101,"periodLabel":"2025-09","type":"part_time","status":"paid","currency":"CNY","lineCount":128,"paidAt":"2025-10-01T08:30:00Z"}
}
```

### 4.3 批次下工资行
GET `/api/v1/payroll/batches/{id}/lines`
Query：
- employeeRef（可选）= employeeId 或 `platformType:platformUserId`
- page=1 size=50
Response 200：
```
{ "code":200, "message":"OK", "data": { "records":[
  {"id":1001,"batchId":101,"employeeRef":"emp:E2001","employmentType":"part_time","grossAmount":3500.00,"taxAmount":120.00,"socialAmount":0.00,"netAmount":3380.00,"currency":"CNY","departments":["外包组"]}
], "total":1, "current":1, "size":50 } }
```

### 4.4 工资条查询（按员工与周期）
GET `/api/v1/payslips`
Query：
- employeeRef（必填）
- period（必填）=YYYY-MM
Response 200：
```
{ "code":200, "message":"OK", "data":[
  {"id":"ps_887766","employeeRef":"emp:E2001","period":"2025-09","employmentType":"part_time",
   "items":[{"code":"hour","name":"计时收入","amount":3600.00,"type":"earning","taxable":true,"showOnPayslip":true,"order":10},
             {"code":"tax","name":"个税","amount":-120.00,"type":"deduction","taxable":false,"showOnPayslip":true,"order":90}],
   "grossAmount":3600.00,"taxAmount":120.00,"socialAmount":0.00,"netAmount":3480.00,
   "currency":"CNY","departments":["外包组"],"generatedAt":"2025-10-01T08:30:00Z"}
]}
```

### 4.5 工资条详情（单条）
GET `/api/v1/payslips/{id}`
Response 200：同 4.4 单条结构

## 5. 过滤与分页约定
- 所有列表接口统一支持 `page`、`size`、`sortBy`、`order`；默认 `sortBy=createTime`、`order=desc`（如未指定）。
- 所有金额字段统一返回 decimal 字符串或数值（建议数值，前端注意精度处理）。

## 6. 脱敏策略
- employeeRef 作为对外员工标识使用；不返回身份证号/银行卡；姓名/手机号可按脱敏规则（如姓名首字、手机号中间四位屏蔽）返回或不返回。
- 部门信息仅作展示用途；如涉及隐私数据，可通过配置关闭。

## 7. OpenAPI 片段（示例）
```
openapi: 3.0.3
info:
  title: Payroll API v1 (PT Read-Only)
  version: 1.0.0
servers:
  - url: https://api.example.com
security:
  - oauth2: [payroll:read, payslip:read]
paths:
  /api/v1/payroll/batches:
    get:
      summary: List PT payroll batches
      parameters:
        - in: query
          name: type
          schema: { type: string, enum: [part_time] }
          required: true
        - in: query
          name: period
          schema: { type: string }
        - in: query
          name: status
          schema: { type: string, enum: [approved, paid, archived] }
        - in: query
          name: page
          schema: { type: integer, default: 1 }
        - in: query
          name: size
          schema: { type: integer, default: 20 }
      responses:
        '200':
          description: OK
  /api/v1/payslips:
    get:
      summary: Query payslips by employeeRef and period
      parameters:
        - in: query
          name: employeeRef
          schema: { type: string }
          required: true
        - in: query
          name: period
          schema: { type: string }
          required: true
      responses:
        '200': { description: OK }
components:
  securitySchemes:
    oauth2:
      type: oauth2
      flows:
        clientCredentials:
          tokenUrl: /api/oauth/token
          scopes:
            payroll:read: Read payroll batches and lines (PT)
            payslip:read: Read payslips (PT)
```

## 8. 版本与兼容
- v1 仅 PT 只读接口；后续 v1.x 可增列与响应字段（向后兼容）。如需变更语义，提升至 v2 并保留 v1。

