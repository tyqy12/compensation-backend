# 云账户（天津）集成方案设计文档

> 状态：待评审
> 版本：v1.0
> 日期：2026-02-26

## 1. 评估结论

### 1.1 业务价值

| 维度 | 评估 | 说明 |
|------|------|------|
| 合规降本 | ⭐⭐⭐⭐⭐ | 灵活用工场景下可优化税务成本，合规发票入账 |
| 结算效率 | ⭐⭐⭐⭐ | 支持批量发放，T+1 到账，与支付宝相当 |
| 适用场景 | ⭐⭐⭐ | 仅适用于灵活用工/外包/兼职，正式员工需走工资薪金 |

### 1.2 技术可行性

**现状匹配度：75%**

- ✅ 现有 `PaymentBatch` + `PaymentRecord` 架构支持多渠道扩展
- ✅ 已有幂等机制（Redis）和状态机（`BatchStatus`）
- ✅ 审批流程已打通
- ⚠️ `PaymentRecord` 存在支付宝硬编码字段（`alipay_order_no` / `alipay_trade_no`）
- ❌ 云账户需要身份证信息，当前 `PaymentRecord` 未存储
- ❌ 缺乏统一渠道抽象层

### 1.3 风险提示

| 风险类型 | 等级 | 说明 |
|----------|------|------|
| 合规风险 | 🔴 高 | 需确认税务口径、地区政策适用性，法务/财务必须参与 |
| 资金风险 | 🔴 高 | 云账户模式是"预充值→发放"，需关注资金沉淀和账户安全 |
| 供应商锁定 | 🟡 中 | 改造不当会导致后续换渠道成本高，必须做抽象层 |
| 身份校验 | 🟡 中 | 云账户强制要求身份证二要素，需确保数据合规采集 |

### 1.4 建议决策

**建议做，但必须"渠道化"改造**

- 不做抽象直接硬编码 → ❌ 不推荐，技术债务严重
- 先抽象再接入云账户 → ✅ 推荐，可复用于后续微信/银行渠道

---

## 2. 架构设计

### 2.1 目标架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        业务应用层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │  薪酬发放    │  │  报销打款    │  │      灵活用工结算        │  │
│  └──────┬──────┘  └──────┬──────┘  └────────────┬────────────┘  │
└─────────┼────────────────┼──────────────────────┼───────────────┘
          │                │                      │
          └────────────────┼──────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      结算服务层                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              SettlementService（统一入口）                │    │
│  │     职责：路由选择、幂等控制、事务管理、异常处理            │    │
│  └─────────────────────────┬───────────────────────────────┘    │
└────────────────────────────┼────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      渠道抽象层                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              SettlementProvider（接口）                   │    │
│  │  - singleTransfer()      单笔打款                        │    │
│  │  - batchTransfer()       批量打款                        │    │
│  │  - queryStatus()         状态查询                        │    │
│  │  - handleCallback()      回调处理                        │    │
│  │  - verifyCallback()      签名验证                        │    │
│  └─────────────────────────────────────────────────────────┘    │
└────────────────────────────┬────────────────────────────────────┘
                             │
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   Yunzhanghu    │ │    Alipay       │ │    Wechat       │
│ SettlementProvider│ │ SettlementProvider│ │ SettlementProvider│
│   （云账户）      │ │   （支付宝）     │ │   （微信支付）   │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 2.2 核心流程

#### 2.2.1 单笔发放流程

```
┌─────────┐     ┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│  业务方  │────▶│ Settlement  │────▶│   路由选择       │────▶│   渠道实现   │
│         │     │  Service    │     │ (配置/灰度)      │     │             │
└─────────┘     └─────────────┘     └─────────────────┘     └──────┬──────┘
                                                                   │
                                                                   ▼
┌─────────┐     ┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│  结果返回│◀────│  状态更新    │◀────│   回调/轮询      │◀────│   云账户     │
│         │     │             │     │                 │     │             │
└─────────┘     └─────────────┘     └─────────────────┘     └─────────────┘
```

#### 2.2.2 云账户特有流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  下单    │───▶│  审核中  │───▶│  签约   │───▶│  扣税   │───▶│  打款   │
│         │    │ (风控)   │    │ (首次)  │    │         │    │         │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
                  │              │              │              │
                  ▼              ▼              ▼              ▼
               [回调]         [回调]         [回调]         [回调]
```

---

## 3. 数据模型变更

### 3.1 PaymentRecord 表改造

#### 3.1.1 新增字段

```sql
-- 通用渠道字段（兼容多渠道路由）
ALTER TABLE payment_record
ADD COLUMN provider_code VARCHAR(32) NOT NULL DEFAULT 'alipay' COMMENT '渠道编码：alipay/yunzhanghu/wechat/bank',
ADD COLUMN provider_order_no VARCHAR(64) COMMENT '渠道侧商户订单号（我方生成）',
ADD COLUMN provider_trade_no VARCHAR(64) COMMENT '渠道侧平台流水号（渠道返回）',
ADD COLUMN provider_metadata JSON COMMENT '渠道返回的扩展信息（如云账户的税务信息）',
ADD COLUMN id_card_hash VARCHAR(64) COMMENT '收款人身份证哈希（云账户等渠道需要）',
ADD INDEX idx_provider_order (provider_code, provider_order_no),
ADD INDEX idx_provider_trade (provider_code, provider_trade_no);
```

#### 3.1.2 存量数据迁移

```sql
-- 迁移存量支付宝数据
UPDATE payment_record
SET provider_code = 'alipay',
    provider_order_no = alipay_order_no,
    provider_trade_no = alipay_trade_no,
    provider_metadata = JSON_OBJECT('legacy', true)
WHERE alipay_order_no IS NOT NULL;

-- 验证迁移结果
SELECT provider_code, COUNT(*) as cnt
FROM payment_record
GROUP BY provider_code;
```

#### 3.1.3 废弃字段（下一版本删除）

```sql
-- 在确认系统稳定运行后，删除支付宝专属字段
-- ALTER TABLE payment_record DROP COLUMN alipay_order_no;
-- ALTER TABLE payment_record DROP COLUMN alipay_trade_no;
```

### 3.2 新增对账表

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

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_recon_date (recon_date, provider_code),
    INDEX idx_status (status)
) COMMENT='渠道对账表';
```

### 3.3 新增渠道配置表

```sql
CREATE TABLE settlement_provider_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(32) NOT NULL UNIQUE COMMENT '渠道编码',
    provider_name VARCHAR(64) NOT NULL COMMENT '渠道名称',

    -- 基础配置（加密存储）
    config_json TEXT COMMENT '渠道配置JSON（包含密钥等敏感信息）',

    -- 功能开关
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用',
    supports_batch TINYINT NOT NULL DEFAULT 1 COMMENT '是否支持批量',
    supports_query TINYINT NOT NULL DEFAULT 1 COMMENT '是否支持主动查询',
    supports_callback TINYINT NOT NULL DEFAULT 1 COMMENT '是否支持回调',

    -- 限额配置
    single_max_amount DECIMAL(15,2) COMMENT '单笔最大金额',
    daily_max_amount DECIMAL(15,2) COMMENT '单日最大金额',

    -- 回调配置
    callback_url VARCHAR(256) COMMENT '回调地址',
    callback_ips VARCHAR(512) COMMENT '回调IP白名单',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='结算渠道配置表';

-- 初始化数据
INSERT INTO settlement_provider_config
(provider_code, provider_name, enabled, supports_batch, supports_query) VALUES
('alipay', '支付宝', 1, 1, 1),
('yunzhanghu', '云账户', 0, 1, 1, 1);
```

### 3.4 新增灰度配置表

```sql
CREATE TABLE feature_flag (
    flag_name VARCHAR(64) PRIMARY KEY,
    flag_value VARCHAR(512) NOT NULL COMMENT 'JSON格式配置',
    description VARCHAR(256),
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='功能开关/灰度配置';

-- 云账户灰度配置
INSERT INTO feature_flag VALUES (
    'yunzhanghu.rollout',
    '{
        "percentage": 0,
        "allowedDepts": [],
        "maxAmount": 50000.00,
        "allowedPayrollTypes": ["flexible_employment"],
        "startTime": null,
        "endTime": null
    }',
    '云账户渠道灰度配置',
    NULL,
    NOW()
);
```

---

## 4. 接口定义

### 4.1 SettlementProvider 接口

```java
package com.yiyundao.compensation.modules.payment.provider;

/**
 * 统一结算渠道接口
 * 所有结算渠道（支付宝、云账户、微信、银行）均需实现此接口
 */
public interface SettlementProvider {

    /**
     * 渠道编码，唯一标识
     * @return 如：alipay, yunzhanghu, wechat
     */
    String getProviderCode();

    /**
     * 渠道名称
     */
    String getProviderName();

    /**
     * 单笔转账
     * @param request 转账请求
     * @return 转账结果
     */
    SettlementResult singleTransfer(SettlementRequest request);

    /**
     * 批量转账
     * @param requests 转账请求列表（建议单次不超过1000条）
     * @return 批量转账结果
     */
    SettlementResult batchTransfer(List<SettlementRequest> requests);

    /**
     * 查询订单状态
     * @param providerOrderNo 渠道订单号（我方生成）
     * @return 当前状态
     */
    SettlementStatus queryStatus(String providerOrderNo);

    /**
     * 处理异步回调
     * @param params 回调参数
     * @return 回调处理结果
     */
    SettlementCallbackResult handleCallback(Map<String, String> params);

    /**
     * 验证回调签名
     * @param params 回调参数
     * @return 是否验证通过
     */
    boolean verifyCallback(Map<String, String> params);

    /**
     * 健康检查
     * @return 渠道是否可用
     */
    boolean healthCheck();

    /**
     * 是否支持该类型的发放
     * @param payrollType 发放类型
     */
    boolean supports(PayrollType payrollType);
}
```

### 4.2 核心 DTO

```java
/**
 * 结算请求
 */
@Data
@Builder
public class SettlementRequest {
    // 内部标识
    private Long paymentRecordId;
    private String bizNo;

    // 金额信息
    private BigDecimal amount;
    private String currency;

    // 收款人信息
    private String recipientName;
    private String recipientAccount;
    private String recipientIdType;  // ID_CARD, PHONE, BANK_CARD
    private String recipientIdNo;    // 身份证号（云账户必需）

    // 业务信息
    private String remark;
    private PayrollType payrollType;

    // 扩展参数（渠道特有）
    private Map<String, Object> extra;
}

/**
 * 结算结果
 */
@Data
@Builder
public class SettlementResult {
    private boolean success;
    private String providerOrderNo;
    private String providerTradeNo;
    private SettlementStatus status;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime responseTime;
    private Map<String, Object> metadata;
}

/**
 * 结算状态枚举
 */
public enum SettlementStatus {
    // 通用状态
    PENDING,        // 待处理
    PROCESSING,     // 处理中
    SUCCESS,        // 成功
    FAILED,         // 失败
    CANCELLED,      // 已取消

    // 云账户特有
    AUDITING,       // 审核中（风控）
    SIGNING,        // 签约中（首次使用）
    TAXING,         // 扣税中
    WITHDRAWING     // 提现中
}

/**
 * 回调处理结果
 */
@Data
@Builder
public class SettlementCallbackResult {
    private boolean success;
    private String bizNo;
    private SettlementStatus status;
    private String errorMsg;
    private Map<String, Object> metadata;
}
```

### 4.3 统一回调接口

```java
/**
 * 统一渠道回调控制器
 */
@RestController
@RequestMapping("/api/v1/settlement/callback")
@RequiredArgsConstructor
@Slf4j
public class SettlementCallbackController {

    private final SettlementService settlementService;

    /**
     * 统一回调入口
     * 路径格式：/api/v1/settlement/callback/{providerCode}
     * 如：/api/v1/settlement/callback/yunzhanghu
     */
    @PostMapping("/{providerCode}")
    public ResponseEntity<String> handleCallback(
            @PathVariable String providerCode,
            @RequestParam Map<String, String> params,
            @RequestBody(required = false) String body) {

        log.info("收到渠道回调: provider={}, params={}", providerCode, params);

        try {
            // 合并 query string 和 body（部分渠道用 form，部分用 json）
            Map<String, String> allParams = new HashMap<>(params);
            if (StringUtils.hasText(body)) {
                allParams.putAll(parseBody(body));
            }

            SettlementCallbackResult result = settlementService.handleCallback(providerCode, allParams);

            if (result.isSuccess()) {
                return ResponseEntity.ok("SUCCESS");
            } else {
                return ResponseEntity.status(500).body("FAIL: " + result.getErrorMsg());
            }

        } catch (Exception e) {
            log.error("回调处理异常: provider={}", providerCode, e);
            return ResponseEntity.status(500).body("ERROR");
        }
    }
}
```

---

## 5. 实施计划

### 5.1 阶段划分

| 阶段 | 周期 | 目标 | 产出物 |
|------|------|------|--------|
| **Phase 1** | 1周 | 基础改造 | 数据库 migration、接口定义、支付宝适配 |
| **Phase 2** | 2周 | 云账户接入 | YunzhanghuProvider、回调处理、联调测试 |
| **Phase 3** | 1周 | 对账补偿 | 对账任务、差异告警、补偿工具 |
| **Phase 4** | 1周 | 灰度上线 | 灰度开关、监控告警、生产验证 |

### 5.2 Phase 1 详细任务

| 任务 | 负责人 | 工时 | 验收标准 |
|------|--------|------|----------|
| 设计评审 | 架构师 | 1d | 方案文档通过评审 |
| DB Migration | 后端 | 1d | SQL 脚本执行无误，回滚脚本就绪 |
| SettlementProvider 接口 | 后端 | 1d | 接口定义完成，评审通过 |
| AlipayProvider 重构 | 后端 | 2d | 原有功能100%兼容，单元测试通过 |
| 路由服务 | 后端 | 1d | 支持按配置路由到不同渠道 |

### 5.3 Phase 2 详细任务

| 任务 | 负责人 | 工时 | 验收标准 |
|------|--------|------|----------|
| 云账户 SDK 调研 | 后端 | 2d | 明确 API 列表、状态码、回调格式 |
| YunzhanghuClient | 后端 | 2d | 封装云账户 HTTP 调用，签名验证 |
| YunzhanghuProvider | 后端 | 3d | 实现 SettlementProvider 全部接口 |
| 回调处理 | 后端 | 1d | 支持云账户状态流转回调 |
| 身份证采集 | 前端 | 2d | 灵活用工场景增加身份证输入 |
| 联调测试 | 全组 | 2d | 沙箱环境全流程跑通 |

### 5.4 灰度策略

```yaml
# 灰度配置示例
yunzhanghu:
  rollout:
    # 百分比灰度（按 userId hash）
    percentage: 10

    # 白名单部门
    allowedDepts: [3, 5, 8]

    # 金额限制（云账户适合小额）
    maxAmount: 50000.00

    # 发放类型限制（仅灵活用工）
    allowedPayrollTypes: ["flexible_employment"]

    # 时间窗口
    startTime: "2026-03-01T00:00:00"
    endTime: "2026-03-31T23:59:59"
```

### 5.5 回滚方案

| 场景 | 回滚操作 | 时效 |
|------|----------|------|
| 数据库变更回滚 | 执行 migration 的 down 脚本 | 5分钟 |
| 功能开关回滚 | 修改 feature_flag 表，percentage 设为 0 | 实时（缓存刷新后） |
| 代码回滚 | 回滚部署包 | 10分钟 |
| 数据修复 | 人工核对后批量修改 provider_code | 按需 |

---

## 6. 关键决策点

### 6.1 Decision 1：身份证存储

**问题**：云账户需要收款人身份证，如何获取和存储？

| 方案 | 优点 | 缺点 | 建议 |
|------|------|------|------|
| A. 实时查询 employee 表 | 不冗余存储 | 耦合强，性能差 | ❌ |
| B. payment_record 新增字段 | 解耦，查询快 | 敏感信息存储 | ✅ 推荐 |
| C. 调用时前端传入 | 灵活 | 安全风险高 | ❌ |

**决策**：采用方案 B，存储身份证哈希（SHA-256），原始信息从 employee 表关联查询。

### 6.2 Decision 2：云账户钱包充值

**问题**：云账户是预充值模式，如何管理资金？

```
方案：财务手动充值 + 余额告警

1. 财务按需向云账户充值
2. 系统每小时查询云账户余额
3. 余额低于阈值时发送告警（钉钉/邮件）
4. 充值后在管理后台更新余额记录
```

### 6.3 Decision 3：失败重试策略

| 错误类型 | 重试策略 | 说明 |
|----------|----------|------|
| 网络超时 | 3次指数退避 | 可能成功，需重试 |
| 余额不足 | 不重试，告警 | 需人工充值 |
| 身份校验失败 | 不重试 | 数据问题，需人工核实 |
| 风控拒绝 | 不重试 | 云账户风控，可换渠道重试 |

---

## 7. 附录

### 7.1 云账户 API 列表（调研中）

| 功能 | API | 状态 |
|------|-----|------|
| 下单 | /api/payment/order | 待确认 |
| 查询 | /api/payment/query | 待确认 |
| 回调 | 配置回调地址 | 待确认 |
| 余额查询 | /api/account/balance | 待确认 |
| 对账单 | /api/reconciliation/download | 待确认 |

### 7.2 状态映射

| 我方状态 | 支付宝状态 | 云账户状态 |
|----------|------------|------------|
| PENDING | - | INIT |
| PROCESSING | DEALING | PROCESSING |
| SUCCESS | SUCCESS | SUCCESS |
| FAILED | FAIL | FAILED/REJECTED |
| - | - | AUDITING |
| - | - | SIGNING |

### 7.3 相关文档

- [支付批次 API](./payment-batch-api.md)
- [支付宝配置 API](./frontend-integration-config-api.md)
- [审批流程设计](./approval-api.md)

---

**评审记录**

| 日期 | 评审人 | 结论 | 备注 |
|------|--------|------|------|
| 2026-02-26 | - | 初稿 | 待技术评审 |
