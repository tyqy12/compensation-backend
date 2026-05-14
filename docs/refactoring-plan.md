# 薪酬助手系统设计缺陷修复计划

## 执行摘要

本仓库存在严重的架构设计缺陷，影响系统的可维护性、扩展性和数据一致性。本文档提供分阶段的修复方案。

---

## 一、核心问题清单

### P0 级别 - 数据一致性与并发安全

#### 1.1 事务管理混乱
**位置**: `modules/payroll/service/PayrollProcessManager.java`

**问题**:
```java
@Transactional  // 外层事务
public boolean computeAndInitialize(Long batchId) {
    boolean computed = payrollCalculationService.computeAndSave(batchId);  // 内部可能有自己的事务
    PayrollConfirmation confirmation = confirmationAggregateService.createOrRefreshForBatch(batch);
    onConfirmationCompleted(batch.getId(), batch.getBatchRevision());  // 又调用其他带事务的方法
}
```

**风险**:
- 嵌套事务导致部分提交/回滚不一致
- 长事务占用数据库连接，并发性能差
- 分布式环境下无法保证 ACID

**修复方案**:
1. 使用事件驱动架构替代同步调用
2. 引入 Saga 模式管理分布式事务
3. 明确事务边界，每个事务只做一件事

#### 1.2 并发安全隐患
**位置**: `modules/payment/service/impl/SettlementServiceImpl.java`

**问题**:
- 批量支付无幂等性控制
- 重试机制可能导致重复转账
- 缺少分布式锁保护临界区

**修复方案**:
1. 为每笔支付生成唯一业务流水号
2. 数据库层面添加唯一索引约束
3. 使用 Redis 分布式锁 + 数据库乐观锁双重保障

---

### P1 级别 - 业务逻辑与可扩展性

#### 2.1 领域模型贫血
**问题表现**:
- Entity 类只有 getter/setter
- 所有业务逻辑在 Service 层
- `PayrollCalculationServiceImpl.java` 1169 行，违反单一职责

**修复方案 - 引入 DDD**:
```java
// 重构前
public class PayrollBatch {
    private Long id;
    private String status;
    // 只有字段，没有行为
}

// 重构后
public class PayrollBatch {
    private BatchStatus status;
    
    public void submitForApproval() {
        if (this.status != BatchStatus.DRAFT) {
            throw new BusinessException("只有草稿状态可提交审批");
        }
        this.status = BatchStatus.PENDING_APPROVAL;
        DomainEventPublisher.publish(new BatchSubmittedEvent(this.id));
    }
    
    public Money calculateTotalAmount(List<PayrollLine> lines) {
        return lines.stream()
            .map(PayrollLine::getNetAmount)
            .reduce(Money.ZERO, Money::add);
    }
}
```

#### 2.2 模块耦合严重
**问题**:
```java
// PayrollBatchController 依赖过多
@RestController
public class PayrollBatchController {
    private final PayrollBatchService payrollBatchService;
    private final PayrollCalculationService calculationService;
    private final PayrollPaymentService payrollPaymentService;
    private final PayrollProcessManager payrollProcessManager;
    private final PayrollBatchMapper payrollBatchMapper;  // Mapper 不应该在 Controller 出现
}
```

**修复方案**:
1. 引入 Facade 层聚合服务调用
2. Controller 只依赖一个 Facade
3. 使用 CQRS 分离读写操作

---

### P2 级别 - 配置管理与安全性

#### 3.1 敏感信息硬编码
**位置**: `application-dev.yml`

```yaml
spring:
  datasource:
    password: 123456  # 明文密码
jwt:
  secret: compensation-assistant-dev-secret-key-2024  # 硬编码密钥
encryption:
  sm4:
    key: your_sm4_key_32_chars_long_here  # 加密密钥暴露
```

**修复方案**:
1. 使用环境变量或配置中心
2. 生产环境密钥必须加密存储
3. 接入 Vault 或 KMS 管理密钥

#### 3.2 魔法数字散落
```java
// PayrollProcessManager.java
private int safeRetryLimit(PayrollDistribution distribution) {
    return distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1 ? 3 : distribution.getRetryLimit();
}

// PayrollCalculationServiceImpl.java
if (existingLines.size() > 1000) {  // 1000 是什么？
    // ...
}
```

**修复方案**:
```java
@ConfigurationProperties(prefix = "payroll")
public class PayrollProperties {
    private int maxRetryCount = 3;
    private int maxBatchSize = 1000;
    private Duration calculationTimeout = Duration.ofMinutes(5);
}
```

---

### P3 级别 - 测试与质量保障

#### 4.1 测试覆盖率不足
**现状**: 504 个 Java 文件，仅 53 个测试文件（~10%）

**缺失的测试**:
- [ ] 薪资计算核心算法单元测试
- [ ] 支付流程集成测试（Mock 支付宝）
- [ ] 审批状态机测试
- [ ] 并发场景压力测试

**修复方案**:
1. 为核心领域服务补充单元测试（目标 80%+）
2. 使用 Testcontainers 进行集成测试
3. 添加契约测试保证 API 稳定性

---

## 二、分阶段实施计划

### 第一阶段：紧急修复（1-2 周）

#### 任务 1.1: 修复事务问题
- [ ] 识别所有 `@Transactional` 嵌套场景
- [ ] 将长事务拆分为短事务 + 事件
- [ ] 为关键操作添加补偿机制

**示例代码**:
```java
// 重构前
@Transactional
public void processPayroll(Long batchId) {
    calculate(batchId);      // 耗时 30s
    confirm(batchId);        // 耗时 10s
    submitPayment(batchId);  // 耗时 60s
}

// 重构后
public void processPayroll(Long batchId) {
    calculateAndPublishEvent(batchId);
}

@Transactional
public void calculateAndPublishEvent(Long batchId) {
    calculate(batchId);
    eventPublisher.publish(new PayrollCalculatedEvent(batchId));
}

@TransactionalEventListener
public void onPayrollCalculated(PayrollCalculatedEvent event) {
    confirm(event.getBatchId());
    eventPublisher.publish(new PayrollConfirmedEvent(event.getBatchId()));
}

@TransactionalEventListener
public void onPayrollConfirmed(PayrollConfirmedEvent event) {
    submitPayment(event.getBatchId());
}
```

#### 任务 1.2: 添加幂等性控制
- [ ] 为支付接口实现幂等性 Token
- [ ] 数据库添加唯一索引防止重复
- [ ] 实现分布式锁

```java
@Service
public class IdempotentPaymentService {
    
    @Idempotent(keyPrefix = "payment", expireSeconds = 3600)
    @Transactional
    public PaymentResult transfer(PaymentRequest request) {
        String bizNo = generateBizNo(request);
        
        // 数据库唯一索引保障最终一致性
        PaymentRecord record = new PaymentRecord();
        record.setBizNo(bizNo);  // UNIQUE INDEX(biz_no)
        // ...
        paymentRecordMapper.insert(record);
        
        return alipayService.transfer(request);
    }
}
```

#### 任务 1.3: 移除 Controller 中的 Mapper 依赖
- [ ] 将所有 `XxxMapper` 从 Controller 移到 Service
- [ ] Controller 只调用 Service 接口

---

### 第二阶段：架构重构（3-4 周）

#### 任务 2.1: 引入 DDD 领域模型
- [ ] 识别聚合根（PayrollBatch, PaymentBatch）
- [ ] 将业务逻辑移入 Entity
- [ ] 引入领域事件

**目录结构调整**:
```
src/main/java/com/yiyundao/compensation/modules/payroll/
├── domain/                    # 领域层
│   ├── model/                # 聚合根、实体、值对象
│   │   ├── PayrollBatch.java
│   │   ├── PayrollLine.java
│   │   └── Money.java
│   ├── repository/           # 仓储接口
│   │   └── PayrollBatchRepository.java
│   └── event/               # 领域事件
│       ├── PayrollCalculatedEvent.java
│       └── PayrollApprovedEvent.java
├── application/             # 应用层
│   ├── service/            # 应用服务（编排）
│   └── dto/               # DTO
├── infrastructure/         # 基础设施层
│   ├── persistence/       # 仓储实现
│   └── external/          # 外部服务适配
└── interfaces/            # 接口层
    ├── controller/
    └── facade/
```

#### 任务 2.2: 引入 CQRS
- [ ] 分离命令（写）和查询（读）
- [ ] 为复杂查询构建专用读模型
- [ ] 使用 Event Sourcing 记录状态变更

```java
// 命令端
@Service
public class PayrollCommandService {
    public void submitApproval(SubmitApprovalCommand cmd) {
        PayrollBatch batch = repository.findById(cmd.getBatchId());
        batch.submitForApproval();
        repository.save(batch);
    }
}

// 查询端
@Service
public class PayrollQueryService {
    public PayrollBatchDetailVO getDetail(Long batchId) {
        // 直接从读库或缓存获取，不经过领域模型
        return readMapper.selectDetailById(batchId);
    }
}
```

#### 任务 2.3: 重构服务依赖
- [ ] 引入 Facade 层
- [ ] 使用 DI 容器管理依赖
- [ ] 消除循环依赖

---

### 第三阶段：配置与安全（1-2 周）

#### 任务 3.1: 配置中心迁移
- [ ] 接入 Nacos/Apollo 配置中心
- [ ] 敏感配置加密存储
- [ ] 实现配置动态刷新

```yaml
# bootstrap.yml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NAMESPACE}
        encryption:
          enabled: true
          password: ${CONFIG_DECRYPT_KEY}
```

#### 任务 3.2: 统一配置管理
- [ ] 提取所有魔法数字到配置类
- [ ] 添加配置校验
- [ ] 实现配置变更审计

```java
@Configuration
@ConfigurationProperties(prefix = "payroll")
@Validated
public class PayrollProperties {
    
    @Min(1)
    @Max(10)
    private int maxRetryCount = 3;
    
    @NotNull
    private Duration calculationTimeout = Duration.ofMinutes(5);
    
    @Min(100)
    @Max(10000)
    private int maxBatchSize = 1000;
}
```

---

### 第四阶段：测试体系建设（持续）

#### 任务 4.1: 补充单元测试
```java
@ExtendWith(MockitoExtension.class)
class PayrollBatchTest {
    
    @Test
    void should_submit_for_approval_when_status_is_draft() {
        // Given
        PayrollBatch batch = new PayrollBatch(BatchStatus.DRAFT);
        
        // When
        batch.submitForApproval();
        
        // Then
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_APPROVAL);
    }
    
    @Test
    void should_throw_exception_when_submit_non_draft_batch() {
        // Given
        PayrollBatch batch = new PayrollBatch(BatchStatus.APPROVED);
        
        // When & Then
        assertThatThrownBy(() -> batch.submitForApproval())
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只有草稿状态可提交审批");
    }
}
```

#### 任务 4.2: 集成测试框架
```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PayrollIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test_payroll");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
    }
    
    @Test
    void should_complete_payroll_flow() {
        // 完整测试创建->计算->审批->支付流程
    }
}
```

#### 任务 4.3: 契约测试
```java
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureRestDocs
class PayrollApiContractTest {
    
    @Test
    void create_batch_api_contract() throws Exception {
        mockMvc.perform(post("/payroll/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "periodLabel": "2024-05",
                        "payCycleId": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andDo(document("create-batch"));
    }
}
```

---

## 三、验收标准

### 代码质量指标
| 指标 | 当前值 | 目标值 |
|------|--------|--------|
| 单元测试覆盖率 | ~10% | ≥80% |
| 圈复杂度 | >20 | ≤10 |
| 单文件行数 | >1000 | ≤500 |
| Controller 依赖数 | 5+ | ≤2 |

### 性能指标
| 场景 | 当前 TPS | 目标 TPS |
|------|----------|----------|
| 薪资计算（100 人） | ~10 | ≥50 |
| 批量支付（500 笔） | ~20 | ≥100 |
| 审批流程 | ~30 | ≥100 |

### 可靠性指标
- [ ] 支付零重复
- [ ] 数据零丢失
- [ ] 故障自动恢复时间 < 5 分钟
- [ ] 支持水平扩展

---

## 四、风险控制

### 回滚策略
1. 所有重构保留旧代码分支
2. 使用 Feature Flag 控制新逻辑开关
3. 灰度发布，先小流量验证

### 监控告警
```java
@Component
public class PayrollMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordCalculationTime(Duration duration) {
        meterRegistry.timer("payroll.calculation.time")
            .record(duration);
    }
    
    public void recordPaymentResult(boolean success) {
        meterRegistry.counter("payroll.payment.result", 
            "success", String.valueOf(success))
            .increment();
    }
}
```

---

## 五、立即行动项

### 今天就可以做的（低风险高收益）

1. **移除 Controller 中的 Mapper**
   ```bash
   # 查找所有 Controller 中直接注入 Mapper 的情况
   grep -r "Mapper" src/main/java/*/controller/ --include="*.java"
   ```

2. **提取配置常量**
   ```java
   // 新建 PayrollConstants.java
   public class PayrollConstants {
       public static final int DEFAULT_RETRY_COUNT = 3;
       public static final int MAX_BATCH_SIZE = 1000;
   }
   ```

3. **为关键方法添加日志**
   ```java
   log.info("开始薪资计算：batchId={}, employeeCount={}", batchId, employeeCount);
   try {
       // 业务逻辑
   } catch (Exception e) {
       log.error("薪资计算失败：batchId={}", batchId, e);
       throw;
   }
   ```

4. **添加基础单元测试**
   ```bash
   # 为核心 Service 添加最基础的测试
   mkdir -p src/test/java/com/yiyundao/compensation/modules/payroll/service
   ```

---

## 六、技术债务追踪

建议使用 Issue 追踪系统管理技术债务：

```markdown
## [Tech Debt] 事务管理优化
- 优先级：P0
- 预估工作量：3 天
- 影响范围：PayrollProcessManager, PaymentService
- 验收标准：无嵌套事务，所有长事务拆分

## [Tech Debt] 领域模型重构
- 优先级：P1
- 预估工作量：5 天
- 影响范围：所有 Entity 类
- 验收标准：Entity 包含业务逻辑，Service 只负责编排
```

---

## 附录 A：推荐工具

| 用途 | 工具 |
|------|------|
| 代码质量 | SonarQube |
| 架构守护 | ArchUnit |
| 测试覆盖 | JaCoCo |
| 性能测试 | JMeter/Gatling |
| 配置中心 | Nacos/Apollo |
| 链路追踪 | SkyWalking/Zipkin |
| 密钥管理 | HashiCorp Vault |

## 附录 B：参考资源

- [DDD 实战指南](https://domainlanguage.com/ddd/)
- [Spring 最佳实践](https://spring.io/guides)
- [微服务事务模式](https://microservices.io/patterns/data/transaction-saga.html)
- [阿里巴巴 Java 开发手册](https://alibaba.github.io/Alibaba-Java-Development-Guide/)

---

**文档版本**: v1.0  
**更新日期**: 2024  
**维护者**: 架构组
