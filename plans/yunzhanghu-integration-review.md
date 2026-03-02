# 云账户集成方案评审报告

> **评审日期**：2026-03-01  
> **评审人**：架构师  
> **文档版本**：v2.1（与代码现状对齐）  
> **原方案版本**：v1.0（2026-02-26）

---

## 1. 执行摘要

### 1.1 评审结论

✅ **总体评价：方案设计合理，已有良好的代码实现基础，建议在现有基础上进行优化改进**

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | ⭐⭐⭐⭐⭐ | 渠道抽象层设计优秀，扩展性强 |
| 代码实现 | ⭐⭐⭐⭐ | 核心功能已实现，待收敛实现一致性问题 |
| 数据模型 | ⭐⭐⭐ | 已有配置与映射表，但存在实体-DDL 漂移风险 |
| 业务适配 | ⭐⭐⭐⭐ | 已支持员工/批次/员工类型路由，需补齐测试 |

### 1.2 关键发现

**✅ 已实现的优秀设计**
- [`SettlementProvider`](../src/main/java/com/yiyundao/compensation/modules/payment/provider/SettlementProvider.java) 接口抽象完善
- [`SettlementService`](../src/main/java/com/yiyundao/compensation/modules/payment/service/SettlementService.java) 实现了统一路由和幂等控制
- [`AlipaySettlementProvider`](../src/main/java/com/yiyundao/compensation/modules/payment/provider/impl/AlipaySettlementProvider.java) 和 [`YunzhanghuSettlementProvider`](../src/main/java/com/yiyundao/compensation/modules/payment/provider/impl/YunzhanghuSettlementProvider.java) 已完整实现
- [`PaymentRecord`](../src/main/java/com/yiyundao/compensation/modules/payment/entity/PaymentRecord.java) 实体已扩展 `provider_code`、`provider_order_no`、`provider_trade_no`、`provider_metadata`、`id_card_hash` 字段
- 统一回调入口 [`SettlementCallbackController`](../src/main/java/com/yiyundao/compensation/interfaces/controller/payment/SettlementCallbackController.java) 已实现

**⚠️ 需要改进的方面（2026-03-01 复核）**
- `settlement_provider_config` 存在实体字段与迁移 DDL 不一致风险，需要统一（本次已修复迁移脚本）
- `employee_type_provider_mapping` 优先级注释与排序方向不一致，导致路由结果可能反转（本次已修复为“高优先级优先”）
- 文档 API 路径与实际 Controller 不一致，需要对齐
- 对账能力仍处于表结构预留阶段，缺少业务处理与作业调度实现

---

## 2. 现有实现分析

### 2.1 架构层次

```
┌─────────────────────────────────────────────────────────────────┐
│                        业务应用层                                │
│  PayrollPaymentService → SettlementService                      │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      渠道抽象层（已实现）                         │
│  SettlementProvider 接口                                        │
│  - singleTransfer()                                             │
│  - batchTransfer()                                              │
│  - queryStatus()                                                │
│  - handleCallback()                                             │
│  - verifyCallback()                                             │
└─────────────────────────┬───────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   Alipay        │ │  Yunzhanghu     │ │   Wechat        │
│   Provider      │ │   Provider      │ │   Provider      │
│   ✅ 已实现      │ │   ✅ 已实现      │ │   ⏳ 待实现      │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 2.2 当前路由逻辑

[`SettlementServiceImpl.resolveProviderCode()`](../src/main/java/com/yiyundao/compensation/modules/payment/service/impl/SettlementServiceImpl.java:152) 当前实现（已接入路由服务）：

```java
private String resolveProviderCode(PaymentRecord record) {
    // 1. 优先使用 record.providerCode
    if (StringUtils.hasText(record.getProviderCode())) {
        return normalizeProviderCode(record.getProviderCode());
    }
    // 2. 调用 routingService（员工配置 > 批次配置 > 类型映射）
    String providerCode = routingService.determineProvider(employee, payrollBatch);
    return normalizeProviderCode(providerCode);
    // 3. 异常时兜底 paymentMethod，再兜底 alipay
}
```

**现状说明**：
- ✅ 支持按员工、批次、员工类型路由
- ✅ 支持回退到 `paymentMethod` / `alipay` 的兜底逻辑
- ⚠️ 需关注路由配置数据质量与优先级排序正确性

### 2.3 数据模型现状

#### 已实现的表结构

**payment_record 表**（已扩展）
```sql
-- 通用渠道字段（已实现）
provider_code VARCHAR(32)           -- 渠道编码
provider_order_no VARCHAR(64)       -- 渠道订单号
provider_trade_no VARCHAR(64)       -- 渠道流水号
provider_metadata JSON              -- 渠道元数据
id_card_hash VARCHAR(64)            -- 身份证哈希
```

#### 已落地表结构（复核）

- ✅ `settlement_provider_config` - 渠道配置表
- ✅ `employee_type_provider_mapping` - 员工类型渠道映射表
- ✅ `settlement_reconciliation` - 对账表（结构已落地，业务逻辑待建设）
- ⏳ `feature_flag` - 当前版本未使用

---

## 3. 改进方案

### 3.1 核心改进目标

1. **灵活的渠道路由**：支持三种路由方式（员工配置 > 批次配置 > 员工类型映射）
2. **配置动态化**：将渠道配置从代码迁移到数据库
3. **多渠道支持**：所有员工类型都支持多个渠道，由管理员灵活配置
4. **对账功能**：增加对账表和对账功能（可选，后续实现）

### 3.2 路由优先级设计

当前实现优先级：

1. `PaymentRecord.providerCode`（已设置时直接使用）
2. `Employee.settlementProviderCode`
3. `PayrollBatch.settlementProviderCode`
4. `employee_type_provider_mapping`（按 `priority` 高到低）
5. 若路由服务异常，`SettlementServiceImpl` 兜底：`paymentMethod` → `alipay`

### 3.3 数据库设计

#### 3.3.1 settlement_provider_config 表（渠道配置）

```sql
-- 以 migration 为准，关键字段包括：
provider_code / provider_name / provider_type / enabled / priority
api_endpoint / api_key / api_secret / merchant_id / notify_url / return_url
config_json / supports_batch / supports_query / supports_callback
create_time / update_time / create_by / update_by / deleted / version
```

> 说明：`settlement_provider_config` 结构以  
> [`2026-03-01__settlement_provider_enhancement.sql`](../src/main/resources/sql/migrations/2026-03-01__settlement_provider_enhancement.sql)  
> 和 [`2026-02-26__settlement_channelization.sql`](../src/main/resources/sql/migrations/2026-02-26__settlement_channelization.sql) 的合并结果为准。

#### 3.3.2 employee 表扩展

```sql
ALTER TABLE employee 
ADD COLUMN settlement_provider_code VARCHAR(32) COMMENT '结算渠道编码（优先级最高）';
```

#### 3.3.3 payroll_batch 表扩展

```sql
ALTER TABLE payroll_batch 
ADD COLUMN settlement_provider_code VARCHAR(32) COMMENT '结算渠道编码';
```

#### 3.3.4 employee_type_provider_mapping 表（员工类型→渠道映射）

```sql
CREATE TABLE employee_type_provider_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employment_type VARCHAR(32) NOT NULL COMMENT '员工类型：full_time/part_time/intern/contract',
    provider_code VARCHAR(32) NOT NULL COMMENT '结算渠道编码',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级（数字越大越高）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(50),
    update_by VARCHAR(50),
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_employment_provider (employment_type, provider_code)
) COMMENT='员工类型渠道映射表（支持一对多）';

-- 初始化数据（所有类型默认支持支付宝）
INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
('full_time', 'alipay', 10, 1),
('part_time', 'alipay', 10, 1),
('intern', 'alipay', 10, 1),
('contract', 'alipay', 10, 1);

-- 管理员可通过界面添加云账户等其他渠道
-- 示例：为兼职和实习生添加云账户渠道（优先级更高）
-- INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
-- ('part_time', 'yunzhanghu', 20, 1),
-- ('intern', 'yunzhanghu', 20, 1);
```

#### 3.3.5 settlement_reconciliation 表（对账表，可选）

```sql
CREATE TABLE settlement_reconciliation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recon_date DATE NOT NULL COMMENT '对账日期',
    provider_code VARCHAR(32) NOT NULL COMMENT '渠道编码',
    
    -- 汇总统计
    total_count INT NOT NULL DEFAULT 0 COMMENT '总笔数',
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '总金额',
    match_count INT NOT NULL DEFAULT 0 COMMENT '匹配笔数',
    match_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '匹配金额',
    diff_count INT NOT NULL DEFAULT 0 COMMENT '差异笔数',
    diff_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '差异金额',
    
    -- 差异详情
    unmatched_local JSON COMMENT '我方多账记录ID列表',
    unmatched_provider JSON COMMENT '渠道多账记录列表',
    
    -- 处理状态
    status VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/PROCESSING/COMPLETED',
    processed_by BIGINT COMMENT '处理人ID',
    processed_at TIMESTAMP NULL COMMENT '处理时间',
    remark VARCHAR(512) COMMENT '处理备注',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_recon_date (recon_date, provider_code),
    INDEX idx_status (status)
) COMMENT='渠道对账表';
```

### 3.4 代码改进

#### 3.4.1 实体类变更

**Employee 实体**
```java
@TableField("settlement_provider_code")
private String settlementProviderCode;
```

**PayrollBatch 实体**
```java
@TableField("settlement_provider_code")
private String settlementProviderCode;
```

**新增实体**
- `SettlementProviderConfig`
- `EmployeeTypeProviderMapping`
- `SettlementReconciliation`（可选）

#### 3.4.2 SettlementService 改进

```java
private String resolveProviderCode(PaymentRecord record) {
    if (StringUtils.hasText(record.getProviderCode())) {
        return normalizeProviderCode(record.getProviderCode());
    }
    try {
        Employee employee = employeeService.getById(record.getEmployeeId());
        PayrollBatch batch = findBatchByNo(record.getBatchNo());
        String provider = routingService.determineProvider(employee, batch);
        return normalizeProviderCode(provider);
    } catch (Exception e) {
        if (StringUtils.hasText(record.getPaymentMethod())) {
            return normalizeProviderCode(record.getPaymentMethod());
        }
        return "alipay";
    }
}
```

### 3.5 管理接口设计

#### 3.5.1 渠道配置管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/settlement/provider-config` | GET | 查询渠道配置列表 |
| `/api/settlement/provider-config/{id}` | GET | 查询单个渠道配置 |
| `/api/settlement/provider-config/code/{providerCode}` | GET | 按渠道编码查询 |
| `/api/settlement/provider-config` | POST | 创建渠道配置 |
| `/api/settlement/provider-config/{id}` | PUT | 更新渠道配置 |
| `/api/settlement/provider-config/{id}` | DELETE | 删除渠道配置 |
| `/api/settlement/provider-config/{id}/status?enabled=true/false` | PATCH | 启用/禁用渠道 |

#### 3.5.2 员工类型映射接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/settlement/routing/mapping` | GET | 查询映射列表 |
| `/api/settlement/routing/mapping/type/{employmentType}` | GET | 按员工类型查询映射 |
| `/api/settlement/routing/mapping` | POST | 添加映射关系 |
| `/api/settlement/routing/mapping/{id}` | PUT | 更新映射关系 |
| `/api/settlement/routing/mapping/{id}` | DELETE | 删除映射关系 |
| `/api/settlement/routing/mapping/{id}/status?enabled=true/false` | PATCH | 启用/禁用映射 |

---

## 4. 实施计划

### 4.1 Phase 1：数据库迁移（1天）

- [x] 创建 `settlement_provider_config` 表
- [x] 创建 `employee_type_provider_mapping` 表
- [x] `employee` 表添加 `settlement_provider_code` 字段
- [x] `payroll_batch` 表添加 `settlement_provider_code` 字段
- [x] 初始化配置数据
- [x] 修复 `settlement_provider_config` 迁移脚本与实体字段漂移

### 4.2 Phase 2：实体类和 Mapper（1天）

- [x] 创建 `SettlementProviderConfig` 实体和 Mapper
- [x] 创建 `EmployeeTypeProviderMapping` 实体和 Mapper
- [x] 更新 `Employee` 实体
- [x] 更新 `PayrollBatch` 实体

### 4.3 Phase 3：Service 层实现（2天）

- [x] 实现 `SettlementProviderConfigService`
- [x] 实现 `EmployeeTypeProviderMappingService`
- [x] 改进 `SettlementService.resolveProviderCode()` 逻辑
- [ ] 增加路由优先级和配置 CRUD 的单元测试
- [x] 修复员工类型映射优先级排序方向（高优先级优先）

### 4.4 Phase 4：Controller 层实现（2天）

- [x] 实现 `SettlementProviderConfigController`
- [x] 实现 `SettlementProviderRoutingController`
- [ ] 对齐接口文档与真实路由（本次已更新本评审文档）
- [ ] 增加集成测试

### 4.5 Phase 5：前端界面（可选，3天）

- [ ] 渠道配置管理页面
- [ ] 员工类型映射配置页面
- [ ] 员工档案中添加渠道选择
- [ ] 薪酬批次中添加渠道选择

---

## 5. 员工类型与渠道映射说明

### 5.1 设计原则

- **灵活配置**：所有员工类型都支持多个渠道，由管理员根据实际业务需求配置
- **优先级机制**：同一员工类型可配置多个渠道，通过优先级决定默认选择
- **动态调整**：支持运行时启用/禁用某个映射关系

### 5.2 配置示例

**场景1：全职员工支持支付宝和云账户**
```sql
INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
('full_time', 'alipay', 10, 1),
('full_time', 'yunzhanghu', 5, 1);
```

**场景2：兼职员工优先使用云账户**
```sql
INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
('part_time', 'yunzhanghu', 20, 1),
('part_time', 'alipay', 10, 1);
```

### 5.3 常见配置建议

| 员工类型 | 可选渠道 | 说明 |
|---------|---------|------|
| 全职 (full_time) | 支付宝、云账户、银行 | 根据企业需求配置 |
| 兼职 (part_time) | 云账户、支付宝 | 灵活用工建议云账户 |
| 实习 (intern) | 云账户、支付宝 | 灵活用工建议云账户 |
| 合同工 (contract) | 支付宝、云账户、银行 | 视合同约定 |

> 注：具体映射由管理员在系统中配置，支持随时调整

---

## 6. 风险评估

### 6.1 技术风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 数据库迁移失败 | 🟡 中 | 提供回滚脚本，先在测试环境验证 |
| 路由逻辑复杂度增加 | 🟢 低 | 充分的单元测试，清晰的优先级规则 |
| 性能影响 | 🟢 低 | 增加缓存，优化查询 |

### 6.2 业务风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 配置错误导致支付失败 | 🟡 中 | 提供配置校验，支持配置预览 |
| 渠道切换影响业务 | 🟢 低 | 支持按员工/批次单独配置，逐步切换 |

---

## 7. 与原方案的差异

| 方面 | 原方案 | 改进方案 | 说明 |
|------|--------|---------|------|
| 灰度功能 | ✅ 包含 | ❌ 移除 | 系统未上线，不需要灰度 |
| 员工类型映射 | 固定映射 | 灵活配置 | 支持一对多，管理员可调整 |
| 路由优先级 | 未明确 | 4级路由 + 兜底 | 员工配置 > 批次配置 > 类型映射，异常时回退 paymentMethod/alipay |
| 配置管理 | 代码硬编码 | 数据库配置 | 已有后端管理接口，支持运行时调整 |
| 数据一致性 | - | 持续治理 | 本次补齐迁移脚本与实体字段对齐 |
| 对账功能 | ✅ 包含 | ⏳ 可选 | 表结构已落地，业务能力待实现 |

---

## 8. 总结与建议

### 8.1 核心优势

1. **架构优秀**：现有的 `SettlementProvider` 抽象层设计合理，扩展性强
2. **实现完整**：支付宝和云账户两个核心渠道已实现
3. **数据模型良好**：`PaymentRecord` 扩展字段设计合理

### 8.2 改进重点

1. **一致性治理**：持续保证实体、迁移脚本、初始化脚本一致
2. **路由可靠性**：补全路由优先级与异常兜底的自动化测试
3. **配置可用性**：完善配置管理的校验、审计和可视化

### 8.3 实施建议

- **优先级**：优先完成测试和文档对齐，再推进前端配置页
- **测试策略**：补充 `resolveProviderCode`、`determineProvider`、配置 CRUD 的单元/集成测试
- **上线策略**：先在测试环境执行迁移并回归支付主流程，再上线

---

## 9. 附录

### 9.1 相关文档

- [原集成方案](../docs/yunzhanghu-integration.md)
- [改进计划详细文档](./yunzhanghu-routing-improvement.md)
- [支付批次 API](../docs/payment-batch-api.md)
- [审批流程设计](../docs/approval-api.md)

### 9.2 评审记录

| 日期 | 评审人 | 结论 | 备注 |
|------|--------|------|------|
| 2026-02-26 | - | 初稿 | 原方案待技术评审 |
| 2026-03-01 | 架构师 | 通过（需改进） | 建议增强路由灵活性，移除灰度功能 |
