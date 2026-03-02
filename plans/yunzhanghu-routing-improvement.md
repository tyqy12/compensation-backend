# 云账户集成改进计划 - 灵活结算渠道路由

## 1. 改进目标

支持三种渠道路由方式（优先级从高到低）：
1. **员工配置**：在员工档案中指定结算渠道
2. **薪酬批次配置**：在薪酬批次中指定结算渠道
3. **员工类型自动匹配**：按员工类型自动选择渠道（灵活用工→云账户，全职→支付宝）

## 2. 数据库设计

### 2.1 settlement_provider_config 表（渠道配置）

```sql
CREATE TABLE settlement_provider_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(32) NOT NULL UNIQUE COMMENT '渠道编码：alipay/yunzhanghu/wechat',
    provider_name VARCHAR(64) NOT NULL COMMENT '渠道名称',
    config_json TEXT COMMENT '渠道配置JSON（密钥等敏感信息加密存储）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认渠道',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='结算渠道配置表';

-- 初始化数据
INSERT INTO settlement_provider_config (provider_code, provider_name, enabled, is_default) VALUES
('alipay', '支付宝', 1, 1),
('yunzhanghu', '云账户', 1, 0);
```

### 2.2 employee 表扩展

```sql
ALTER TABLE employee 
ADD COLUMN settlement_provider_code VARCHAR(32) COMMENT '结算渠道编码（优先级最高）';
```

### 2.3 payroll_batch 表扩展

```sql
ALTER TABLE payroll_batch 
ADD COLUMN settlement_provider_code VARCHAR(32) COMMENT '结算渠道编码';
```

### 2.4 employee_type_provider_mapping 表（员工类型→渠道映射）

```sql
CREATE TABLE employee_type_provider_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employment_type VARCHAR(32) NOT NULL COMMENT '员工类型：full_time/part_time/intern/contract',
    provider_code VARCHAR(32) NOT NULL COMMENT '结算渠道编码',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级（数字越大越高）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_employment_provider (employment_type, provider_code)
) COMMENT='员工类型渠道映射表（支持一对多）';

-- 初始化数据（所有类型默认支持支付宝）
INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
('full_time', 'alipay', 10, 1),
('part_time', 'alipay', 10, 1),
('intern', 'alipay', 10, 1),
('contract', 'alipay', 10, 1);

-- 管理员可通过界面添加云账户等其他渠道
-- INSERT INTO employee_type_provider_mapping (employment_type, provider_code, priority, enabled) VALUES
-- ('part_time', 'yunzhanghu', 20, 1),
-- ('intern', 'yunzhanghu', 20, 1);
```

## 3. 实体类变更

### 3.1 Employee 实体

```java
@TableField("settlement_provider_code")
private String settlementProviderCode;
```

### 3.2 PayrollBatch 实体

```java
@TableField("settlement_provider_code")
private String settlementProviderCode;
```

## 4. 路由逻辑改进

### 4.1 SettlementServiceImpl.resolveProviderCode() 改进

```java
private String resolveProviderCode(PaymentRecord record) {
    // 优先级1：PaymentRecord.providerCode（渠道路由配置）
    if (StringUtils.hasText(record.getProviderCode())) {
        return normalizeProviderCode(record.getProviderCode());
    }
    
    // 优先级2：员工档案配置
    if (record.getEmployeeId() != null) {
        String employeeProvider = getEmployeeProvider(record.getEmployeeId());
        if (StringUtils.hasText(employeeProvider)) {
            return normalizeProviderCode(employeeProvider);
        }
    }
    
    // 优先级3：薪酬批次配置
    if (StringUtils.hasText(record.getBatchNo())) {
        String batchProvider = getBatchProvider(record.getBatchNo());
        if (StringUtils.hasText(batchProvider)) {
            return normalizeProviderCode(batchProvider);
        }
    }
    
    // 优先级4：按员工类型自动匹配
    String typeBasedProvider = getProviderByEmployeeType(record.getEmployeeId());
    if (StringUtils.hasText(typeBasedProvider)) {
        return normalizeProviderCode(typeBasedProvider);
    }
    
    // 优先级5：默认渠道
    return getDefaultProvider();
}
```

## 5. 接口设计

### 5.1 渠道配置管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/settlement/configs` | GET | 查询渠道配置列表 |
| `/api/v1/settlement/configs/{code}` | GET | 查询单个渠道配置 |
| `/api/v1/settlement/configs` | POST | 创建渠道配置 |
| `/api/v1/settlement/configs/{code}` | PUT | 更新渠道配置 |
| `/api/v1/settlement/configs/{code}` | DELETE | 删除渠道配置 |
| `/api/v1/settlement/configs/{code}/enable` | POST | 启用渠道 |
| `/api/v1/settlement/configs/{code}/disable` | POST | 禁用渠道 |
| `/api/v1/settlement/configs/default/{code}` | POST | 设置默认渠道 |

### 5.2 员工类型映射接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/settlement/mappings` | GET | 查询映射列表（按员工类型分组） |
| `/api/v1/settlement/mappings` | POST | 添加映射关系 |
| `/api/v1/settlement/mappings/{id}` | PUT | 更新映射关系 |
| `/api/v1/settlement/mappings/{id}` | DELETE | 删除映射关系 |
| `/api/v1/settlement/mappings/{id}/enable` | POST | 启用映射 |
| `/api/v1/settlement/mappings/{id}/disable` | POST | 禁用映射 |

## 6. 实现步骤

### 步骤1：数据库迁移
- 创建 settlement_provider_config 表
- 创建 employee_type_provider_mapping 表
- employee 表添加 settlement_provider_code 字段
- payroll_batch 表添加 settlement_provider_code 字段

### 步骤2：实体类变更
- Employee 实体添加 settlementProviderCode 字段
- PayrollBatch 实体添加 settlementProviderCode 字段
- 新增 SettlementProviderConfig 实体
- 新增 EmployeeTypeProviderMapping 实体

### 步骤3：Service 层实现
- SettlementProviderConfigService
- EmployeeTypeProviderMappingService
- 改进 SettlementService.resolveProviderCode() 逻辑

### 步骤4：Controller 层实现
- SettlementConfigController（渠道配置管理）
- SettlementMappingController（映射管理）

### 步骤5：初始化数据
- 初始化渠道配置数据
- 初始化员工类型映射数据

## 7. 员工类型与渠道映射说明

### 7.1 设计原则

- **灵活配置**：所有员工类型都支持多个渠道，由管理员根据实际业务需求配置
- **优先级机制**：同一员工类型可配置多个渠道，通过优先级决定默认选择
- **动态调整**：支持运行时启用/禁用某个映射关系

### 7.2 配置示例

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

### 7.3 常见配置建议

| 员工类型 | 可选渠道 | 说明 |
|---------|---------|------|
| 全职 (full_time) | 支付宝、云账户、银行 | 根据企业需求配置 |
| 兼职 (part_time) | 云账户、支付宝 | 灵活用工建议云账户 |
| 实习 (intern) | 云账户、支付宝 | 灵活用工建议云账户 |
| 合同工 (contract) | 支付宝、云账户、银行 | 视合同约定 |

> 注：具体映射由管理员在系统中配置，支持随时调整
