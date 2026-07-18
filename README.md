# 薪酬助手系统 - 项目初始化完成

项目已成功初始化，包含以下核心组件：

## 🎯 项目概述
- **项目名称**: Compensation Assistant System (薪酬助手系统)
- **Group ID**: com.yiyundao
- **技术栈**: Spring Boot 3.5.6 + Java 17 + MyBatis-Plus
- **主要功能**: 支付宝批量转账、多平台组织同步、架构外员工管理

## 🏗️ 已完成的初始化工作

### 1. Maven 依赖配置 (/pom.xml)
- ✅ Spring Boot 3.5.6 基础框架
- ✅ Spring Security 6.2 安全框架
- ✅ MyBatis-Plus 3.5.5 数据访问层
- ✅ JWT 认证 (jjwt 0.12.3)
- ✅ 支付宝SDK (4.42.96.ALL)
- ✅ BouncyCastle 加密库 (1.78)
- ✅ Redis、RabbitMQ、Quartz 集成

### 2. 多环境配置文件
- ✅ `application.yml` - 基础配置
- ✅ `application-dev.yml` - 开发环境
- ✅ `application-staging.yml` - 预发布环境
- ✅ `application-prod.yml` - 生产环境

### 3. 核心代码结构（按功能模块高内聚）
```
com.yiyundao.compensation/
├── common/
│   ├── config/ (MyBatisPlusConfig, SecurityConfig, RedisConfig, WebClientConfig)
│   ├── response/ (ApiResponse)
│   ├── exception/ (BusinessException, GlobalExceptionHandler)
│   └── utils/
├── interfaces/
│   ├── controller/ (SystemController、Employee/Payment APIs 等)
│   └── adapter/ (WeChat 等外部平台适配器)
├── modules/
│   ├── employee/ entity/ + service/ + impl/
│   ├── payment/  entity/ + service/ + impl/
│   ├── approval/ entity/ + service/ + impl/
│   ├── audit/    entity/ + service/ + impl/
│   └── user/     entity/ + service/ + impl/
├── infrastructure/
│   └── dao/ (MyBatis‑Plus Mapper 接口)
├── entity/ (BaseEntity 仅保留基类)
└── security/ (JwtTokenProvider, JwtAuthenticationFilter)
```

## 🚀 快速启动

### 环境要求
- Java 17+
- MySQL 8.0+
- Redis 7.2+
- RabbitMQ 3.13+ (可选)

### 启动命令
```bash
# 开发环境启动（必须显式启用，并通过环境变量提供数据库和密钥）
DEV_TOKEN_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 指定环境启动
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 端口被占用时，可通过 SERVER_PORT、spring-boot.run.arguments 指定端口
# 或依赖 dev 环境自动回退到临近可用端口（日志会提示实际端口）
```

### 开发环境快速获取 Token（避免 401/403）
```bash
curl -s -X POST http://localhost:8080/api/auth/dev-token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","roles":["ADMIN"],"authorities":["org:read","org:sync","approval:read"]}' | jq -r .data.token

# 将上面的 token 作为 Authorization: Bearer <token> 调用受保护接口
```

### Docker/WSL 运行（推荐）
```bash
# 生产容器启动（前端 Nginx + 后端 + Redis；MySQL 使用外部实例）
cp deploy/production.env.example .env
# 编辑 .env，替换所有数据库、令牌和加密密钥
docker compose up -d --build

# 查看后端日志
docker compose logs -f app

# 访问前端
http://<server-host>/

# 检查前端到后端的完整链路
curl http://localhost/healthz

# 仅本机调试后端
curl http://127.0.0.1:8080/api/system/health
```
详见：docs/docker-wsl.md

## Git 提交与推送说明

### 提交前检查
```bash
# 查看当前改动，确认只提交本次任务相关文件
git status

# 查看具体差异
git diff

# 后端变更建议至少运行测试
./mvnw -q test
```

不要提交密钥、账号密码、生产配置或本地临时文件；敏感配置请放在环境变量或未跟踪的 `application-local.yml` 中。

### 提交改动
```bash
# 添加指定文件，避免误提交无关改动
git add README.md

# 使用 Conventional Commits 格式
git commit -m "docs: 更新 Git 推送说明"
```

常用提交类型：`feat` 新功能、`fix` 修复、`docs` 文档、`refactor` 重构、`test` 测试、`build` 构建、`chore` 杂项。

### 推送到远端
```bash
# 查看当前分支
git branch --show-current

# 已建立远端跟踪关系时推送
git push

# 新分支首次推送时建立 upstream
git push -u origin <branch-name>
```

如果远端已有更新导致推送失败，先拉取并处理冲突：
```bash
git pull --rebase
git push
```

多人协作时保持提交粒度清晰，PR 中说明变更目的、测试方式、配置或数据库变更，并附上必要的日志或截图。

### 常用端点（更多见 docs/auth-api.md、docs/integration.md、docs/payment-api.md、docs/approval-api.md、docs/dashboard-api.md、docs/admin-api.md、docs/org-structure.md、docs/platform-binding-approval.md、docs/employee-user-linking.md）
- 系统：
  - `GET /api/system/health` 健康检查
  - `GET /api/system/info` 系统信息
  - `POST /api/system/org/sync?platform=wechat|dingtalk|feishu|all` 组织同步（需 ADMIN/MANAGER 或 org:sync）
  - `GET /api/system/org/platforms` 支持平台列表
 - `GET /api/system/org/check?platform=...` 平台连接检查（需 ADMIN/MANAGER 或 org:read）
  - `GET /api/system/integration/{platform}` 读取平台配置（仅 ADMIN，脱敏返回）
  - `PUT /api/system/integration/{platform}` 保存平台配置（仅 ADMIN，加密入库）
  - `POST /api/system/integration/{platform}/test-connection` 连通性测试（仅 ADMIN）
- 员工：
  - `GET /api/employee` 分页（支持 keyword、department、status、platformType、managerId、sortBy、order）
  - `POST /api/employee` 创建；`PUT /api/employee/{id}` 更新
  - `POST /api/employee/{id}/bind-platform` 绑定平台；`PATCH /api/employee/{id}/status` 状态
  - `GET /api/employee/offline` 架构外员工列表
  - `GET /api/employee/resigned` 离职员工列表
  - `GET /api/employee/{id}/id-card`/`bank-account` 解密（ADMIN）
- 支付：
  - `GET /api/payment/batch/{batchNo}` 批次详情
  - `GET /api/payment/batch/{batchNo}/records` 批次记录（status 可选）
  - `POST /api/payment/batch/{batchNo}/start` 启动批量转账（异步）
  - `GET /api/payment/record/{id}` 支付记录详情；`POST /api/payment/record/{id}/retry` 单笔重试
  - `GET /api/payment/transfer-status?outBizNo=...` 查询转账状态
  - `POST /api/alipay/notify` 支付宝异步通知（回调）
- 审批：
   - `POST /api/approval/workflows` 发起审批
   - `POST /api/approval/workflows/{id}/approve` 审批通过
   - `POST /api/approval/workflows/{id}/reject` 审批拒绝
   - `POST /api/approval/workflows/{id}/cancel` 撤销流程
   - `GET /api/approval/workflows/pending?approverId=...` 我的待办
   - `GET /api/approval/workflows/my?initiatorId=...` 我发起的
   - `GET /api/approval/workflows/{id}` 流程详情
   - `GET /api/approval/workflows/{id}/steps` 步骤列表

### Dashboard 工作台
- `GET /api/dashboard/metrics` 指标概览
- `GET /api/dashboard/status` 系统与组件状态
- `GET /api/dashboard/todos` 待办清单
- `GET /api/dashboard/activities` 最近活动

### 管理接口（Admin）
- 架构外员工：`PATCH /api/admin/employees/{id}/offline`、`PUT /api/admin/employees/{id}/manager`
- 批量支付：`POST /api/admin/payment/batch`、`POST /api/admin/payment/batch/{id}/cancel`、`GET /api/admin/payment/batch/stats`
- 审计日志：`GET /api/admin/audit-logs`、`GET /api/admin/audit-logs/{id}`；组织历史：`GET /api/system/org/history`
- 监控：`GET /api/admin/monitor/summary`

### 组织架构与平台绑定审批
- 部门树（分平台）：见 `docs/org-structure.md`，`GET /api/system/org/departments/tree?platform=...`
- 平台账号回写冲突审批（含快照）：见 `docs/platform-binding-approval.md`
- 员工-用户-平台 关联模型与接口：见 `docs/employee-user-linking.md`

## 📋 下一步开发计划

### Phase 1: 数据库设计 (第1-2周) ✅
- [x] 设计员工表结构
- [x] 设计支付记录表
- [x] 设计审批流程表
- [x] 创建数据库迁移脚本

### Phase 2: 核心业务服务 (第3-6周) 进行中
- [x] 业务服务接口与实现重构（Service 接口 + Impl 实现）
- [x] 业务 Controller 编写（员工/支付/审批等）
- [x] 支付宝集成服务 (AlipayService)
- [x] 组织同步适配器 (OrganizationAdapter)
- [x] 审批流程引擎 (ApprovalEngine)

### 补充：持久化层迁移（已完成）
- [x] 实体迁移至 `modules/*/entity`（仅保留 `entity/BaseEntity` 基类）
- [x] Mapper 迁移至 `infrastructure/dao` 并更新 `@MapperScan`
- [x] `mybatis-plus.type-aliases-package` 调整为 `com.yiyundao.compensation.modules.**.entity`

### Phase 3: 平台集成 (第7-9周) ✅
- [x] 企业微信/钉钉/飞书适配器：部门/成员拉取、字段映射与写入
- [x] 访问令牌缓存（Redis，自动续期 TTL 缓冲）
- [x] 组织同步汇总结果与错误记录
- [x] 通知服务完善（路由到各平台、重试、回退策略）

### Phase 4: 管理功能 (第10-11周)
- [x] 平台集成配置管理（DB 加密存储、仅管理员、脱敏返回、连通性测试）
- [x] 聚合登录：账号密码 + 企微/钉钉/飞书扫码（未绑定拒绝登录）
- [x] 刷新令牌与登出（白/黑名单）
- [x] 登录限流/爆破防护（用户名/IP 窗口 + 锁定）
- [x] 架构外员工管理界面（API）
  - `PATCH /api/admin/employees/{id}/offline?value=true|false` 设置架构外标记
  - `PUT /api/admin/employees/{id}/manager?managerId=...` 指定负责人
  - 辅助：`GET /api/employee/offline` 查询架构外员工列表
  - 新增：`GET /api/employee/resigned` 查询离职员工列表
- [x] 批量支付管理（API）
  - `POST /api/admin/payment/batch` 创建批次（草稿）
  - `POST /api/admin/payment/batch/{id}/cancel` 关闭批次（置为 FAILED）
  - `GET /api/admin/payment/batch/stats` 批次与支付统计概览
- [x] 审计日志查看（API）
  - `GET /api/admin/audit-logs` 分页查询（支持 username/operation/businessType/businessKey/时间范围）
  - `GET /api/admin/audit-logs/{id}` 详情
  - 扩展：`GET /api/system/org/history` 组织同步历史（基于审计日志）
- [x] 系统监控面板（API）
  - `GET /api/admin/monitor/summary` 应用概览、JVM 内存/线程、数据库/Redis 连通性

## 🔧 配置说明

### 必需配置项
在启动前，请通过环境变量或未提交的 `application-local.yml` 配置，禁止把凭据写入 Git：

1. **数据库连接**
```yaml
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/compensation_dev'
export SPRING_DATASOURCE_USERNAME='your_username'
export SPRING_DATASOURCE_PASSWORD='your_password'
```

2. **Redis配置**
```yaml
spring.data.redis.host: localhost
spring.data.redis.port: 6379
```

Redis 开启认证时，通过环境变量注入，不要把密码写进配置文件：
```bash
export SPRING_DATA_REDIS_PASSWORD='your-redis-password'
```

3. **JWT密钥**
```bash
export JWT_SECRET='your-random-secret-at-least-32-chars'
```

### 第三方集成配置
以下配置可在开发过程中逐步填写：
- 支付宝沙箱账号信息
- 企业微信/钉钉/飞书应用凭证
- 阿里云SMS配置
- 加密密钥配置

## 🛡️ 安全特性
- ✅ JWT Token 认证
- ✅ 基于角色的访问控制 (RBAC)
- ✅ AES-GCM 版本化密文（兼容历史 SM4/AES-CBC 数据）
- ✅ 安全的密码存储 (BCrypt)
- ✅ CORS 和 CSRF 防护
 - ✅ 集成配置密文落库 + 管理员可见 + 脱敏返回
 - ✅ OAuth state 校验（防重放/CSRF）
 - ✅ 登录限流/爆破防护（用户名/IP）
 - ✅ Token 黑名单与刷新白名单（登出与刷新）

### 审批模块权限建议
- 角色：`ADMIN`、`MANAGER`、`APPROVER`
- 权限串（authorities）：
  - `approval:start` 发起审批
  - `approval:approve` 审批通过
  - `approval:reject` 审批拒绝
  - `approval:cancel` 撤销流程
  - `approval:read` 查看审批与步骤

说明：JWT `authorities` 载荷中可同时包含 `ROLE_xxx` 与自定义权限串，控制器通过 `@PreAuthorize` 校验（`hasRole('...')`/`hasAuthority('...')`）。

更多见：docs/security.md

项目初始化完成！可以开始下一阶段的开发工作。
 - 认证：
   - `POST /api/auth/login` 账号密码登录（含限流/爆破防护）
   - `GET /api/auth/oauth/authorize?platform=wechat|dingtalk|feishu&redirectUri=...` 获取扫码授权地址（含state）
   - `GET /api/auth/oauth/callback/{platform}?code=...&state=...` 回调换登录（未绑定拒绝登录）
   - `POST /api/auth/refresh` 刷新令牌（刷新白名单校验 + 轮换）
  - `POST /api/auth/logout` 登出（黑名单当前access + 失效refresh）
 - 后台绑定：
   - `GET /api/admin/users/{id}/platform-binding` 查询绑定（仅 ADMIN）
   - `PUT /api/admin/users/{id}/platform-binding` 绑定第三方账号（仅 ADMIN）
   - `DELETE /api/admin/users/{id}/platform-binding` 解绑（仅 ADMIN）
