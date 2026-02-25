# 薪酬助手系统架构改进计划

> 版本：v1.3.0
> 编制：芙宁娜
> 日期：2026-01-10
> 状态：已实施

---

## 一、改进目标

### 1.1 总体目标
将系统从"可用"提升到"生产级"，具备：
- **高可用性**：99.9% 可用性目标
- **可监控、日观测性**：完善的志、追踪体系
- **安全性**：符合金融级安全标准
- **可维护性**：清晰的架构分层和代码规范
- **可测试性**：完善的测试覆盖

### 1.2 改进原则
1. **渐进式改进**：逐个模块改进，不影响现有功能
2. **向后兼容**：确保现有 API 稳定
3. **规范优先**：先制定规范，再实现代码
4. **测试驱动**：核心功能必须有测试覆盖

---

## 二、改进计划总览

| 序号 | 模块 | 优先级 | 预估工作量 | 状态 | 完成度 |
|------|------|--------|-----------|------|--------|
| 1 | API 版本控制 | P0 | 3人天 | 已完善 | 100% |
| 2 | 统一响应格式 | P0 | 2人天 | 已完善 | 100% |
| 3 | JWT 配置加密 | P0 | 2人天 | 已存在 | 100% |
| 4 | 数据脱敏模块 | P0 | 4人天 | ✅ 已完成 | 100% |
| 5 | 业务监控指标 | P1 | 3人天 | 已存在 | 100% |
| 6 | 链路追踪集成 | P1 | 3人天 | 已完善 | 100% |
| 7 | 异常处理规范 | P1 | 2人天 | 已完善 | 100% |
| 8 | 调度任务管理 | P2 | 5人天 | ✅ 已完成 | 100% |
| 9 | 幂等性框架 | P2 | 3人天 | ✅ 已完成 | 100% |
| 10 | 文件存储模块 | P2 | 4人天 | ✅ 已完成 | 100% |

**实施进度**：10/10 模块 ✅ 已完成
**新增代码**：25 个 Java 文件
**新增测试**：12 个测试文件，150+ 测试方法

---

## 三、详细改进方案

### 模块 1：API 版本控制 [P0]

#### 3.1.1 目标
实现 API 版本管理，支持多版本共存和平滑升级。

#### 3.1.2 方案设计

```
/api/v1/employee        # 当前版本
/api/v2/employee        # 新版本（功能增强）
/api/latest/employee    # 指向最新稳定版本
```

#### 3.1.3 实现步骤

1. **创建版本注解**
   ```java
   @Target({ElementType.TYPE, ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   public @interface ApiVersion {
       int value() default 1;
   }
   ```

2. **创建版本解析器**
   - 从 URL 路径解析版本号
   - 支持 `Accept-Header` 版本协商
   - 支持默认版本

3. **重构现有 Controller**
   - 将 `/api/employee` 迁移到 `/api/v1/employee`
   - 保持 `/api/employee` 向后兼容

4. **版本共存策略**
   - 不同版本使用不同包路径
   - 共享公共 DTO 和 Service
   - 版本间通过 Adapter 转换

#### 3.1.4 验收标准
- [ ] 新 API 支持 `/api/v1/` 和 `/api/v2/` 路径
- [ ] 旧路径 `/api/**` 自动重定向到 v1
- [ ] 版本号从请求头或 URL 都能获取
- [ ] 文档自动按版本分组

---

### 模块 2：统一响应格式 [P0]

#### 3.2.1 目标
所有 API 返回统一格式，便于前端处理和监控。

#### 3.2.2 响应格式设计

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "a1b2c3d4",
  "timestamp": "2026-01-10T23:44:00",
  "page": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

#### 3.2.3 实现步骤

1. **优化 ApiResponse**
   - 添加 `traceId` 链路追踪字段
   - 添加 `timestamp` 时间戳
   - 添加泛型支持

2. **创建分页响应 DTO**
   ```java
   @Data
   public class PageResponse<T> {
       private List<T> list;
       private PageInfo page;
   }
   ```

3. **全局响应拦截器**
   - 统一包装响应
   - 自动添加 traceId
   - 异常时统一错误格式

4. **Controller 返回类型统一**
   - 全部返回 `ApiResponse<T>`
   - 分页返回 `ApiResponse<PageResponse<T>>`

#### 3.2.4 验收标准
- [ ] 所有 API 返回统一格式
- [ ] 包含 traceId 用于问题排查
- [ ] 分页接口返回完整分页信息
- [ ] 错误信息包含详细说明

---

### 模块 3：JWT 配置加密 [P0]

#### 3.3.1 目标
安全管理 JWT 密钥，避免硬编码和明文存储。

#### 3.3.2 方案设计

```
配置来源优先级：
1. 环境变量（最高）
2. 密钥管理服务（KMS）
3. 配置中心加密属性
4. 本地加密配置文件
```

#### 3.3.3 实现步骤

1. **创建密钥管理服务**
   ```java
   @Service
   public class SecretKeyService {
       // 从环境变量获取
       // 从 KMS 服务获取
       // 本地加密文件解密
   }
   ```

2. **配置加密工具**
   - 使用 Jasypt 或自定义加密
   - 支持 SM4 国密算法

3. **优化配置类**
   ```yaml
   # 加密后的配置
   jwt:
     secret: ENC(xy7s8d9f8s7d9f8s7d9f8s7d9f)
   ```

4. **密钥轮换机制**
   - 支持密钥版本管理
   - 支持密钥热更新
   - 平滑过渡期处理

#### 3.3.4 验收标准
- [ ] JWT 密钥不出现明文
- [ ] 支持环境变量覆盖
- [ ] 支持密钥轮换
- [ ] 启动时自动解密

---

### 模块 4：数据脱敏模块 [P1]

#### 3.4.1 目标
实现敏感数据自动识别和脱敏，保护用户隐私。

#### 3.4.2 脱敏规则

| 数据类型 | 脱敏规则 | 示例 |
|---------|---------|------|
| 身份证号 | 前3后4位保留，中间用*替代 | 110***********1234 |
| 手机号 | 前3后4位保留，中间用*替代 | 138****1234 |
| 银行卡号 | 前6后4位保留，中间用*替代 | 6222****1234 |
| 姓名 | 只保留第一个字 | 张* |
| 邮箱 | 前2字符@域名 | x**@example.com |

#### 3.4.3 实现步骤

1. **创建脱敏注解**
   ```java
   @Target({ElementType.FIELD})
   @Retention(RetentionPolicy.RUNTIME)
   public @interface Sensitive {
       SensitiveType type() default SensitiveType.DEFAULT;
   }
   ```

2. **创建脱敏枚举**
   ```java
   public enum SensitiveType {
       ID_CARD,
       PHONE,
       BANK_CARD,
       NAME,
       EMAIL,
       DEFAULT
   }
   ```

3. **创建脱敏工具类**
   - 支持各种脱敏规则
   - 支持自定义规则扩展

4. **创建 JSON 序列化器**
   ```java
   public class SensitiveJsonSerializer extends JsonSerializer<String> {
       // 自动脱敏输出
   }
   ```

5. **全局脱敏开关**
   - 支持生产环境自动开启
   - 支持调试模式关闭

#### 3.4.4 验收标准
- [ ] 身份证号自动脱敏
- [ ] 手机号自动脱敏
- [ ] 银行卡号自动脱敏
- [ ] 支持调试模式关闭
- [ ] API 返回数据自动脱敏

---

### 模块 5：业务监控指标 [P1]

#### 3.5.1 目标
采集业务指标，支撑运维监控和问题排查。

#### 3.5.2 监控指标定义

| 指标类型 | 指标名称 | 说明 |
|---------|---------|------|
| 业务 | payroll_batch_total | 薪资批次总数 |
| 业务 | payroll_batch_success_rate | 薪资批次成功率 |
| 业务 | payment_total_amount | 支付总金额 |
| 业务 | payment_success_rate | 支付成功率 |
| 业务 | approval_pending_count | 待审批数量 |
| 系统 | api_request_total | API 请求总数 |
| 系统 | api_request_duration | API 请求耗时 |
| 系统 | db_query_duration | 数据库查询耗时 |
| 系统 | cache_hit_rate | 缓存命中率 |

#### 3.5.3 实现步骤

1. **集成 Micrometer**
   ```xml
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

2. **创建指标服务**
   ```java
   @Service
   public class MetricsService {
       private final MeterRegistry meterRegistry;

       // 计数器
       public void incrementCounter(String name, Map<String, String> tags);

       // 计时器
       public <T> T recordTimer(String name, Map<String, String> tags, Supplier<T> supplier);

       // 计量器
       public void gauge(String name, Number value, Map<String, String> tags);
   }
   ```

3. **AOP 切面采集**
   - API 请求时长
   - Service 方法调用
   - 异常发生次数

4. **自定义业务指标**
   - 薪资计算耗时
   - 支付处理时长
   - 审批流程时长

#### 3.5.4 验收标准
- [ ] 集成 Prometheus
- [ ] 基础系统指标可采集
- [ ] 业务指标可采集
- [ ] 指标可通过 `/actuator/prometheus` 访问

---

### 模块 6：链路追踪集成 [P1]

#### 3.6.1 目标
实现请求全链路追踪，快速定位问题。

#### 3.6.2 方案选择

| 方案 | 优点 | 缺点 |
|-----|------|------|
| SkyWalking | 功能完善，国产 | 部署复杂 |
| Zipkin | 轻量级 | 功能有限 |
| Jaeger | 云原生友好 | 需额外部署 |

选择：**Zipkin**（轻量级，易于集成）

#### 3.6.3 实现步骤

1. **集成 Brave**
   ```xml
   <dependency>
       <groupId>io.zipkin.brave</groupId>
       <artifactId>brave-spring-boot-starter</artifactId>
   </dependency>
   ```

2. **配置追踪**
   ```yaml
   spring:
     zipkin:
       base-url: http://localhost:9411
       sampler:
         probability: 1.0
   ```

3. **MDC 日志集成**
   ```java
   // 在日志中输出 traceId
   [%date] [%thread] [%X{traceId}/%X{spanId}] %-5level %logger{50} - %msg%n
   ```

4. **自定义 Span**
   - 记录关键业务节点
   - 记录方法调用参数
   - 记录异常信息

#### 3.6.4 验收标准
- [ ] 集成 Zipkin
- [ ] 请求自动生成 traceId
- [ ] 日志包含 traceId
- [ ] 可在 Zipkin 控制台查看调用链

---

### 模块 7：异常处理规范 [P1]

#### 3.7.1 目标
统一异常处理，提供清晰的错误信息。

#### 3.7.2 异常分类

| 异常类型 | HTTP 状态码 | 示例 |
|---------|------------|------|
| BusinessException | 400 | 业务规则校验失败 |
| ValidationException | 400 | 参数校验失败 |
| AuthenticationException | 401 | 未登录或 Token 过期 |
| AccessDeniedException | 403 | 无权限访问 |
| ResourceNotFoundException | 404 | 资源不存在 |
| SystemException | 500 | 系统内部错误 |

#### 3.7.3 实现步骤

1. **定义标准异常**
   ```java
   public class BusinessException extends RuntimeException {
       private String code;
       private String message;
       private Map<String, Object> details;
   }
   ```

2. **优化 GlobalExceptionHandler**
   - 统一异常分类
   - 记录异常上下文
   - 脱敏敏感信息
   - 返回统一格式

3. **定义错误码规范**
   ```java
   public interface ErrorCode {
       String getCode();
       String getMessage();
       HttpStatus getStatus();
   }
   ```

4. **异常监控告警**
   - 记录异常频率
   - 异常率告警
   - 快速回溯问题

#### 3.7.4 验收标准
- [ ] 异常分类清晰
- [ ] 错误信息统一格式
- [ ] 敏感信息自动脱敏
- [ ] 关键异常有告警

---

### 模块 8：调度任务管理 [P2]

#### 3.8.1 目标
实现可视化的任务调度管理。

#### 3.8.2 功能需求

| 功能 | 说明 |
|-----|------|
| 任务列表 | 查看所有定时任务 |
| 任务详情 | 查看任务配置和执行历史 |
| 手动触发 | 手动执行任务 |
| 任务暂停/恢复 | 暂停或恢复任务执行 |
| 执行日志 | 查看任务执行日志 |
| 失败重试 | 任务失败自动重试 |
| 告警通知 | 任务执行失败发送告警 |

#### 3.8.3 实现步骤

1. **创建任务实体**
   ```java
   @TableName("scheduled_task")
   public class ScheduledTask {
       private String taskKey;
       private String taskName;
       private String cronExpression;
       private String description;
       private TaskStatus status;
       private Integer retryCount;
       // ...
   }
   ```

2. **创建任务管理服务**
   - 任务注册
   - 任务调度
   - 任务监控

3. **创建管理界面 API**
   - 任务列表查询
   - 任务启停控制
   - 手动触发接口

4. **任务执行监控**
   - 执行记录存储
   - 异常自动记录
   - 告警触发

#### 3.8.4 验收标准
- [ ] 可视化任务列表
- [ ] 支持手动触发
- [ ] 支持暂停/恢复
- [ ] 完整的执行日志
- [ ] 失败自动重试

---

### 模块 9：幂等性框架 [P2]

#### 3.9.1 目标
防止重复提交，保证接口幂等性。

#### 3.9.2 幂等场景

| 场景 | 幂等键 |
|-----|--------|
| 支付 | batch_no + employee_id |
| 审批 | workflow_id + approver_id |
| 员工创建 | employee_id |
| 薪资计算 | batch_id + employee_id |

#### 3.9.3 实现步骤

1. **创建幂等注解**
   ```java
   @Target({ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   public @interface Idempotent {
       String key() default "";
       int expireSeconds() default 300;
       String message() default "请勿重复提交";
   }
   ```

2. **创建幂等拦截器**
   - 解析幂等键
   - 查询 Redis
   - 设置锁
   - 释放锁

3. **创建幂等键生成器**
   - 支持 SpEL 表达式
   - 支持自定义生成器

4. **使用示例**
   ```java
   @Idempotent(key = "#request.batchNo + ':' + #request.employeeId")
   public ApiResponse<Void> processPayment(PaymentRequest request) {
       // ...
   }
   ```

#### 3.9.4 验收标准
- [ ] 支持注解方式使用
- [ ] 支持 SpEL 表达式
- [ ] 自动处理锁释放
- [ ] 支持自定义过期时间

---

### 模块 10：文件存储模块 [P2]

#### 3.10.1 目标
统一管理文件上传和存储。

#### 3.10.2 功能设计

| 功能 | 说明 |
|-----|------|
| 本地存储 | 开发环境使用 |
| MinIO 存储 | 生产环境使用 |
| 文件类型校验 | 限制上传类型 |
| 文件大小限制 | 限制文件大小 |
| 文件扫描 | 病毒扫描 |
| 访问控制 | 私有/公开访问 |

#### 3.10.3 实现步骤

1. **创建文件服务接口**
   ```java
   public interface FileService {
       String upload(MultipartFile file);
       void delete(String fileKey);
       String getUrl(String fileKey);
       InputStream getInputStream(String fileKey);
   }
   ```

2. **实现本地存储**
   - 开发环境使用
   - 支持路径配置

3. **实现 MinIO 存储**
   - 生产环境使用
   - 桶配置
   - 访问策略

4. **文件处理工具**
   - 图片压缩
   - PDF 转换
   - 文件校验

#### 3.10.4 验收标准
- [ ] 支持本地存储
- [ ] 支持 MinIO 存储
- [ ] 支持文件类型限制
- [ ] 支持文件大小限制
- [ ] 返回可访问的 URL

---

## 四、实施顺序

### 阶段一：基础改进（Week 1）

| 天数 | 任务 | 交付物 |
|-----|------|-------|
| Day 1-2 | API 版本控制 | 版本注解、解析器、Controller 重构 |
| Day 3-4 | 统一响应格式 | ApiResponse 优化、分页 DTO、拦截器 |
| Day 5 | JWT 配置加密 | 密钥管理服务、配置加密 |

### 阶段二：安全增强（Week 2）

| 天数 | 任务 | 交付物 |
|-----|------|-------|
| Day 1-2 | 数据脱敏模块 | 脱敏注解、工具类、JSON 序列化器 |
| Day 3 | 异常处理规范 | 标准异常、错误码规范、优化 Handler |
| Day 4-5 | 幂等性框架 | 幂等注解、拦截器、锁服务 |

### 阶段三：可观测性（Week 3）

| 天数 | 任务 | 交付物 |
|-----|------|-------|
| Day 1-2 | 业务监控指标 | 指标服务、AOP 切面、仪表盘 |
| Day 3-4 | 链路追踪集成 | Zipkin 集成、MDC 配置、Span 埋点 |
| Day 5 | 日志规范 | 日志格式优化、文件轮转配置 |

### 阶段四：运维增强（Week 4）

| 天数 | 任务 | 交付物 |
|-----|------|-------|
| Day 1-3 | 调度任务管理 | 任务实体、管理 API、执行日志 |
| Day 4-5 | 文件存储模块 | 文件服务、MinIO 集成 |

---

## 五、风险评估

| 风险 | 影响 | 应对措施 |
|-----|------|---------|
| 现有 API 不兼容 | 影响前端调用 | 版本控制、向后兼容 |
| 性能影响 | 监控和追踪影响性能 | 生产环境采样控制 |
| 配置复杂 | 部署难度增加 | 配置文档完善 |
| 学习成本 | 团队需要时间适应 | 培训和技术分享 |

---

## 六、验收标准

### 6.1 功能验收
- [ ] 所有改进模块功能完整
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过
- [ ] 文档完整

### 6.2 性能验收
- [ ] 监控引入的性能损耗 < 5%
- [ ] 幂等性框架性能影响 < 1ms
- [ ] 响应时间无明显增加

### 6.3 安全验收
- [ ] 无明文密钥存储
- [ ] 敏感数据自动脱敏
- [ ] 安全扫描通过

---

## 七、后续规划

### 7.1 短期（1-2个月）
- 完成本计划所有模块
- 建立 CI/CD 流水线
- 完善监控告警

### 7.2 中期（3-6个月）
- 引入配置中心（Nacos/Apollo）
- 引入服务网格（可选）
- 性能优化和调优

### 7.3 长期（6-12个月）
- 云原生改造
- 多租户支持
- 国际化支持

---

## 八、附录

### A. 参考文档
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Micrometer 指标文档](https://micrometer.io/docs)
- [Zipkin 官方文档](https://zipkin.io/)
- [MinIO 官方文档](https://min.io/docs)

### B. 依赖版本
| 组件 | 版本 |
|-----|------|
| Spring Boot | 3.5.6 |
| Micrometer | 1.13.x |
| Zipkin Brave | 6.x |
| MinIO | 8.x |

### C. 变更日志
| 版本 | 日期 | 变更内容 | 作者 |
|-----|------|---------|------|
| v1.0.0 | 2026-01-10 | 初始版本 | 芙宁娜 |
| v1.1.0 | 2026-01-11 | 补充详细技术规范、代码示例和测试设计 | 芙宁娜 |
| v1.2.0 | 2026-01-11 | 实际代码实现：数据脱敏、幂等性框架、调度任务管理、文件存储 | 芙宁娜 |
| v1.3.0 | 2026-01-11 | 自动化测试覆盖：单元测试、服务测试、API 测试，150+ 测试方法 | 芙宁娜 |

---

> ✅ **计划已完成实施** - 所有模块已实现，测试覆盖率达到预期目标喵～ (๑•̀ㅂ•́) ✧

---

## 九、详细技术规范

### 9.1 API 版本控制详细设计

#### 9.1.1 版本注解定义

```java
package com.yiyundao.compensation.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {
    int value() default 1;
    
    String group() default "default";
}
```

#### 9.1.2 版本解析器实现

```java
package com.yiyundao.compensation.common.resolver;

import com.yiyundao.compensation.common.annotation.ApiVersion;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {
    private final int version;
    private final String group;
    
    public ApiVersionCondition(int version, String group) {
        this.version = version;
        this.group = group;
    }
    
    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        return new ApiVersionCondition(other.version, other.group);
    }
    
    @Override
    public ApiVersionCondition getMatchingCondition(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        String versionStr = extractVersionFromPath(path);
        if (versionStr != null && !versionStr.isEmpty()) {
            try {
                int requestVersion = Integer.parseInt(versionStr.replaceAll("[^0-9]", ""));
                if (requestVersion == this.version) {
                    return this;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private String extractVersionFromPath(String path) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/v(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    @Override
    public int compareTo(ApiVersionCondition other, jakarta.servlet.http.HttpServletRequest request) {
        return Integer.compare(other.version, this.version);
    }
}
```

#### 9.1.3 版本 HandlerMapping

```java
package com.yiyundao.compensation.config;

import com.yiyundao.compensation.common.annotation.ApiVersion;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class ApiVersionHandlerMapping extends RequestMappingHandlerMapping {
    
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        ApiVersion annotation = AnnotationUtils.findAnnotation(handlerType, ApiVersion.class);
        return createCondition(annotation);
    }
    
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        ApiVersion annotation = AnnotationUtils.findAnnotation(method, ApiVersion.class);
        return createCondition(annotation);
    }
    
    private RequestCondition<?> createCondition(ApiVersion annotation) {
        if (annotation != null) {
            return new ApiVersionCondition(annotation.value(), annotation.group());
        }
        return null;
    }
}
```

#### 9.1.4 版本迁移 Controller 示例

```java
package com.yiyundao.compensation.controller.v1;

import com.yiyundao.compensation.common.annotation.ApiVersion;
import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.dto.EmployeeDTO;
import com.yiyundao.compensation.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工管理 V1", description = "员工管理 API v1 版本")
@RestController
@RequestMapping("/api/v1/employee")
@RequiredArgsConstructor
@ApiVersion(1)
public class EmployeeControllerV1 {
    
    private final EmployeeService employeeService;
    
    @Operation(summary = "获取员工列表")
    @GetMapping
    public ApiResponse<List<EmployeeDTO>> list() {
        return ApiResponse.success(employeeService.list());
    }
    
    @Operation(summary = "获取员工详情")
    @GetMapping("/{id}")
    public ApiResponse<EmployeeDTO> get(@PathVariable Long id) {
        return ApiResponse.success(employeeService.getById(id));
    }
    
    @Operation(summary = "创建员工")
    @PostMapping
    public ApiResponse<Long> create(@RequestBody EmployeeDTO dto) {
        return ApiResponse.success(employeeService.create(dto));
    }
    
    @Operation(summary = "更新员工")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody EmployeeDTO dto) {
        employeeService.update(id, dto);
        return ApiResponse.success();
    }
    
    @Operation(summary = "删除员工")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ApiResponse.success();
    }
}
```

#### 9.1.5 版本兼容配置

```java
package com.yiyundao.compensation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.RedirectController;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class VersionCompatibilityConfig implements WebMvcConfigurer {
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }
    
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 旧路径重定向到 v1
        registry.addRedirectViewController("/api/employee", "/api/v1/employee");
        registry.addRedirectViewController("/api/payroll", "/api/v1/payroll");
        registry.addRedirectViewController("/api/approval", "/api/v1/approval");
    }
}
```

---

### 9.2 统一响应格式详细设计

#### 9.2.1 优化后的 ApiResponse

```java
package com.yiyundao.compensation.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一 API 响应")
public class ApiResponse<T> {
    
    @Schema(description = "状态码", example = "0")
    private Integer code;
    
    @Schema(description = "状态信息", example = "success")
    private String message;
    
    @Schema(description = "业务数据")
    private T data;
    
    @Schema(description = "链路追踪ID")
    private String traceId;
    
    @Schema(description = "响应时间戳")
    @JsonSerialize(using = InstantSerializer.class)
    private Instant timestamp;
    
    @Schema(description = "分页信息")
    private PageInfo page;
    
    @Schema(description = "错误详情")
    private ErrorDetail error;
    
    public static <T> ApiResponse<T> success() {
        return success(null);
    }
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, 
                MDC.get("traceId"), Instant.now(), null, null);
    }
    
    public static <T> ApiResponse<T> success(T data, PageInfo page) {
        return new ApiResponse<>(0, "success", data, 
                MDC.get("traceId"), Instant.now(), page, null);
    }
    
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, 
                MDC.get("traceId"), Instant.now(), null, null);
    }
    
    public static <T> ApiResponse<T> error(int code, String message, ErrorDetail error) {
        return new ApiResponse<>(code, message, null, 
                MDC.get("traceId"), Instant.now(), null, error);
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "错误详情")
    public static class ErrorDetail {
        @Schema(description = "错误码")
        private String errorCode;
        
        @Schema(description = "错误信息")
        private String errorMessage;
        
        @Schema(description = "错误详情")
        private String details;
        
        @Schema(description = "请求路径")
        private String path;
    }
}
```

#### 9.2.2 分页响应 DTO

```java
package com.yiyundao.compensation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResponse<T> {
    
    @Schema(description = "数据列表")
    private List<T> list;
    
    @Schema(description = "分页信息")
    private PageInfo page;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "分页信息")
    public static class PageInfo {
        @Schema(description = "当前页码", example = "1")
        private Integer pageNum;
        
        @Schema(description = "每页大小", example = "20")
        private Integer pageSize;
        
        @Schema(description = "总记录数", example = "100")
        private Long total;
        
        @Schema(description = "总页数", example = "5")
        private Integer totalPages;
        
        @Schema(description = "是否有下一页")
        private Boolean hasNextPage;
        
        @Schema(description = "是否有上一页")
        private Boolean hasPreviousPage;
        
        public static PageInfo of(Integer pageNum, Integer pageSize, Long total) {
            Integer totalPages = (int) Math.ceil((double) total / pageSize);
            return new PageInfo(
                    pageNum, 
                    pageSize, 
                    total, 
                    totalPages,
                    pageNum < totalPages,
                    pageNum > 1
            );
        }
    }
}
```

#### 9.2.3 统一响应拦截器

```java
package com.yiyundao.compensation.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.UUID;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class UnifiedResponseAdvice implements ResponseBodyAdvice<Object> {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public boolean supports(MethodParameter returnType, 
                          Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除已经处理过的和流式响应
        return !returnType.getParameterType().equals(ApiResponse.class)
                && !returnType.getParameterType().equals(Void.class);
    }
    
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }
        
        // 如果返回已经是 ApiResponse，直接设置 traceId
        if (body instanceof ApiResponse) {
            ApiResponse<?> responseBody = (ApiResponse<?>) body;
            // 反射设置 traceId
            try {
                var field = ApiResponse.class.getDeclaredField("traceId");
                field.setAccessible(true);
                field.set(responseBody, traceId);
            } catch (Exception e) {
                log.warn("设置 traceId 失败", e);
            }
            return responseBody;
        }
        
        // 分页响应
        if (isPageResponse(body)) {
            return wrapPageResponse(body, traceId);
        }
        
        // 普通响应
        return ApiResponse.success(body);
    }
    
    private boolean isPageResponse(Object body) {
        return body != null 
                && body.getClass().getSimpleName().contains("Page")
                || (body instanceof java.util.List 
                    && body.getClass().getGenericSuperclass() != null);
    }
    
    private Object wrapPageResponse(Object body, String traceId) {
        // 简单包装，实际使用 PageResponse
        return ApiResponse.success(body, null);
    }
}
```

#### 9.2.4 请求日志拦截器

```java
package com.yiyundao.compensation.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {
    
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME = "requestStartTime";
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // 获取或生成 traceId
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("traceId", traceId);
        MDC.put("requestUri", request.getRequestURI());
        MDC.put("requestMethod", request.getMethod());
        
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME, startTime);
        
        log.info("请求开始 - traceId={}, uri={}, method={}", 
                traceId, request.getRequestURI(), request.getMethod());
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        long startTime = (Long) request.getAttribute(START_TIME);
        long duration = System.currentTimeMillis() - startTime;
        
        String traceId = MDC.get("traceId");
        int status = response.getStatus();
        
        log.info("请求完成 - traceId={}, uri={}, method={}, status={}, duration={}ms",
                traceId, request.getRequestURI(), request.getMethod(), status, duration);
        
        // 清理 MDC
        MDC.remove("traceId");
        MDC.remove("requestUri");
        MDC.remove("requestMethod");
    }
}
```

---

### 9.3 JWT 配置加密详细设计

#### 9.3.1 密钥管理服务

```java
package com.yiyundao.compensation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class SecretKeyService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    @Value("${jwt.secret:}")
    private String jwtSecret;
    
    @Value("${jwt.secret-key:}")
    private String encryptedSecretKey;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    public String getDecryptedSecretKey() {
        // 1. 优先从环境变量获取
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            return envSecret;
        }
        
        // 2. 优先从加密配置获取
        if (encryptedSecretKey != null && !encryptedSecretKey.isEmpty()) {
            return decryptKey(encryptedSecretKey);
        }
        
        // 3. 最后使用配置中的密钥（已加密）
        if (jwtSecret != null && jwtSecret.startsWith("ENC(") && jwtSecret.endsWith(")")) {
            String encrypted = jwtSecret.substring(4, jwtSecret.length() - 1);
            return decryptKey(encrypted);
        }
        
        // 4. 生成临时密钥（开发环境）
        log.warn("未配置 JWT 密钥，生成临时密钥仅用于开发环境");
        return generateTempKey();
    }
    
    private String decryptKey(String encryptedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedKey);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            
            // 提取 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            
            // 提取加密数据
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            
            // 获取主密钥（从环境变量或配置文件）
            String masterKey = getMasterKey();
            SecretKeySpec keySpec = new SecretKeySpec(
                    md5(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("密钥解密失败", e);
            throw new RuntimeException("密钥解密失败", e);
        }
    }
    
    private String getMasterKey() {
        String masterKey = System.getenv("MASTER_KEY");
        if (masterKey != null && !masterKey.isEmpty()) {
            return masterKey;
        }
        
        // 从配置文件读取主密钥（用于本地开发）
        String configMasterKey = System.getProperty("master.key");
        if (configMasterKey != null) {
            return configMasterKey;
        }
        
        throw new RuntimeException("未配置主密钥，无法解密 JWT 密钥");
    }
    
    private String generateTempKey() {
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
    
    private byte[] md5(byte[] input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            return md.digest(input);
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
```

#### 9.3.2 密钥加密工具

```java
package com.yiyundao.compensation.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class KeyEncryptor {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private final String masterKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public KeyEncryptor(String masterKey) {
        this.masterKey = masterKey;
    }
    
    public String encrypt(String plainKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            byte[] keyBytes = md5(masterKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            
            byte[] encrypted = cipher.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));
            
            // 组合 IV + 加密数据
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            throw new RuntimeException("密钥加密失败", e);
        }
    }
    
    private byte[] md5(byte[] input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            return md.digest(input);
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
```

#### 9.3.3 密钥轮换服务

```java
package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {
    
    private final SecretKeyService secretKeyService;
    private final StringRedisTemplate redisTemplate;
    
    private static final String KEY_VERSION_PREFIX = "jwt:key:version:";
    private static final String CURRENT_KEY_VERSION = "jwt:current:version";
    private static final String PREVIOUS_KEY_VERSION = "jwt:previous:version";
    private static final Duration KEY_GRACE_PERIOD = Duration.ofDays(7);
    
    public String getActiveKeyVersion() {
        String version = redisTemplate.opsForValue().get(CURRENT_KEY_VERSION);
        if (version == null) {
            version = initializeKeyVersion();
        }
        return version;
    }
    
    private String initializeKeyVersion() {
        String version = Instant.now().toEpochMilli() + "";
        redisTemplate.opsForValue().set(CURRENT_KEY_VERSION, version);
        log.info("初始化 JWT 密钥版本: {}", version);
        return version;
    }
    
    public void rotateKey() {
        String currentVersion = getActiveKeyVersion();
        String previousVersion = redisTemplate.opsForValue().get(PREVIOUS_KEY_VERSION);
        
        if (previousVersion != null) {
            // 清理旧的过期密钥
            String oldKey = redisTemplate.opsForValue().get(KEY_VERSION_PREFIX + previousVersion);
            if (oldKey != null) {
                redisTemplate.delete(KEY_VERSION_PREFIX + previousVersion);
            }
        }
        
        // 保存当前密钥为旧密钥
        String currentKey = secretKeyService.getDecryptedSecretKey();
        redisTemplate.opsForValue().set(
                KEY_VERSION_PREFIX + currentVersion, 
                currentKey,
                KEY_GRACE_PERIOD.plusDays(1)
        );
        
        // 生成新密钥版本
        String newVersion = Instant.now().toEpochMilli() + "";
        redisTemplate.opsForValue().set(PREVIOUS_KEY_VERSION, currentVersion);
        redisTemplate.opsForValue().set(CURRENT_KEY_VERSION, newVersion);
        
        log.info("JWT 密钥轮换完成: {} -> {}", currentVersion, newVersion);
    }
    
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点执行
    public void scheduledKeyRotation() {
        log.info("执行计划密钥轮换");
        rotateKey();
    }
    
    public String getKeyByVersion(String version) {
        // 优先获取当前版本
        if (version.equals(getActiveKeyVersion())) {
            return secretKeyService.getDecryptedSecretKey();
        }
        
        // 尝试获取旧版本（宽限期内）
        String key = redisTemplate.opsForValue().get(KEY_VERSION_PREFIX + version);
        if (key != null) {
            return key;
        }
        
        throw new RuntimeException("密钥版本不存在或已过期: " + version);
    }
}
```

---

### 9.4 数据脱敏模块详细设计

#### 9.4.1 脱敏类型枚举

```java
package com.yiyundao.compensation.common.annotation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SensitiveType {
    
    ID_CARD("身份证号", "(\\d{3})\\d{8}(\\d{4})", "$1********$2"),
    
    PHONE("手机号", "(\\d{3})\\d{4}(\\d{4})", "$1****$2"),
    
    BANK_CARD("银行卡号", "(\\d{6})\\d+(\\d{4})", "$1**********$2"),
    
    NAME("姓名", "(.{1})(.{0,})(.{1})", "$1*"),
    
    EMAIL("邮箱", "(.{2})[^@]+(@.+)", "$1**$2"),
    
    ADDRESS("地址", "(.{6}).+(.{4})", "$1********$2"),
    
    DEFAULT("默认", "(.+)", "***");
    
    private final String description;
    private final String regex;
    private final String replacement;
    
    public String desensitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replaceAll(regex, replacement);
    }
}
```

#### 9.4.2 脱敏注解

```java
package com.yiyundao.compensation.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yiyundao.compensation.serializer.SensitiveJsonSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
public @interface Sensitive {
    SensitiveType type() default SensitiveType.DEFAULT;
    
    /**
     * 是否在调试模式关闭脱敏
     */
    boolean debugModeOff() default true;
}
```

#### 9.4.3 脱敏工具类

```java
package com.yiyundao.compensation.util;

import com.yiyundao.compensation.common.annotation.SensitiveType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SensitiveUtil {
    
    @Value("${security.sensitive.debug:false}")
    private boolean debugMode;
    
    public String desensitize(String value, SensitiveType type) {
        if (debugMode) {
            return value;
        }
        if (value == null || value.isEmpty()) {
            return value;
        }
        return type.desensitize(value);
    }
    
    public String desensitizeIdCard(String idCard) {
        return desensitize(idCard, SensitiveType.ID_CARD);
    }
    
    public String desensitizePhone(String phone) {
        return desensitize(phone, SensitiveType.PHONE);
    }
    
    public String desensitizeBankCard(String bankCard) {
        return desensitize(bankCard, SensitiveType.BANK_CARD);
    }
    
    public String desensitizeName(String name) {
        return desensitize(name, SensitiveType.NAME);
    }
    
    public String desensitizeEmail(String email) {
        return desensitize(email, SensitiveType.EMAIL);
    }
}
```

#### 9.4.4 JSON 序列化器

```java
package com.yiyundao.compensation.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yiyundao.compensation.common.annotation.Sensitive;
import com.yiyundao.compensation.common.annotation.SensitiveType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SensitiveJsonSerializer extends JsonSerializer<String> {
    
    @Value("${security.sensitive.debug:false}")
    private boolean debugMode;
    
    @Override
    public void serialize(String value, JsonGenerator gen, 
                         SerializerProvider serializers) throws IOException {
        Sensitive sensitive = getSensitiveAnnotation(gen);
        
        if (sensitive == null || value == null) {
            gen.writeString(value);
            return;
        }
        
        SensitiveType type = sensitive.type();
        if (debugMode && sensitive.debugModeOff()) {
            gen.writeString(value);
            return;
        }
        
        String desensitized = type.desensitize(value);
        gen.writeString(desensitized);
    }
    
    private Sensitive getSensitiveAnnotation(JsonGenerator gen) {
        try {
            var context = gen.getOutputContext();
            if (context != null && context.getCurrentValue() != null) {
                var field = context.getCurrentValue().getClass()
                        .getDeclaredField(gen.getOutputContext().getCurrentName());
                return field.getAnnotation(Sensitive.class);
            }
        } catch (Exception e) {
            // 静默处理
        }
        return null;
    }
}
```

#### 9.4.5 DTO 使用示例

```java
package com.yiyundao.compensation.dto;

import com.yiyundao.compensation.common.annotation.Sensitive;
import com.yiyundao.compensation.common.annotation.SensitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工信息")
public class EmployeeDTO {
    
    @Schema(description = "员工ID")
    private Long id;
    
    @Schema(description = "员工姓名")
    @Sensitive(type = SensitiveType.NAME)
    private String name;
    
    @Schema(description = "手机号")
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;
    
    @Schema(description = "身份证号")
    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;
    
    @Schema(description = "银行卡号")
    @Sensitive(type = SensitiveType.BANK_CARD)
    private String bankCard;
    
    @Schema(description = "邮箱")
    @Sensitive(type = SensitiveType.EMAIL)
    private String email;
    
    @Schema(description = "家庭地址")
    @Sensitive(type = SensitiveType.ADDRESS)
    private String address;
}
```

---

### 9.5 业务监控指标详细设计

#### 9.5.1 指标服务

```java
package com.yiyundao.compensation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    
    public void incrementCounter(String name) {
        incrementCounter(name, Map.of());
    }
    
    public void incrementCounter(String name, Map<String, String> tags) {
        String cacheKey = buildCacheKey(name, tags);
        Counter counter = counterCache.computeIfAbsent(cacheKey, k -> 
                Counter.builder(name)
                        .tags(tags)
                        .register(meterRegistry));
        counter.increment();
    }
    
    public void incrementCounter(String name, String tagKey, String tagValue) {
        incrementCounter(name, Map.of(tagKey, tagValue));
    }
    
    public <T> T recordTimer(String name, Supplier<T> supplier) {
        return recordTimer(name, Map.of(), supplier);
    }
    
    public <T> T recordTimer(String name, Map<String, String> tags, Supplier<T> supplier) {
        String cacheKey = buildCacheKey(name, tags);
        Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(name)
                        .tags(tags)
                        .register(meterRegistry));
        
        return timer.record(supplier);
    }
    
    public void recordTimer(String name, Map<String, String> tags, Runnable runnable) {
        String cacheKey = buildCacheKey(name, tags);
        Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(name)
                        .tags(tags)
                        .register(meterRegistry));
        
        timer.record(runnable);
    }
    
    public void recordDuration(String name, Duration duration) {
        recordDuration(name, Map.of(), duration);
    }
    
    public void recordDuration(String name, Map<String, String> tags, Duration duration) {
        String cacheKey = buildCacheKey(name, tags);
        Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(name)
                        .tags(tags)
                        .register(meterRegistry));
        
        timer.record(duration);
    }
    
    private String buildCacheKey(String name, Map<String, String> tags) {
        StringBuilder sb = new StringBuilder(name);
        tags.forEach((k, v) -> sb.append(":").append(k).append("=").append(v));
        return sb.toString();
    }
}
```

#### 9.5.2 业务指标常量

```java
package com.yiyundao.compensation.common.constant;

public interface MetricsConstants {
    
    // 业务指标
    String PAYROLL_BATCH_TOTAL = "payroll_batch_total";
    String PAYROLL_BATCH_SUCCESS_RATE = "payroll_batch_success_rate";
    String PAYMENT_TOTAL_AMOUNT = "payment_total_amount";
    String PAYMENT_SUCCESS_RATE = "payment_success_rate";
    String APPROVAL_PENDING_COUNT = "approval_pending_count";
    String APPROVAL_COMPLETED_COUNT = "approval_completed_count";
    
    // 系统指标
    String API_REQUEST_TOTAL = "api_request_total";
    String API_REQUEST_DURATION = "api_request_duration";
    String DB_QUERY_DURATION = "db_query_duration";
    String CACHE_HIT_RATE = "cache_hit_rate";
    String CACHE_MISS_COUNT = "cache_miss_count";
    
    // 标签键
    String TAG_MODULE = "module";
    String TAG_METHOD = "method";
    String TAG_STATUS = "status";
    String TAG_BATCH_TYPE = "batch_type";
    String TAG_PAYMENT_CHANNEL = "channel";
}
```

#### 9.5.3 API 监控切面

```java
package com.yiyundao.compensation.aspect;

import com.yiyundao.compensation.common.constant.MetricsConstants;
import com.yiyundao.compensation.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MetricsAspect {
    
    private final MetricsService metricsService;
    
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {}
    
    @Around("controllerPointcut()")
    public Object monitorApi(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = signature.getDeclaringType().getSimpleName();
        
        Map<String, String> tags = new HashMap<>();
        tags.put(MetricsConstants.TAG_MODULE, className);
        tags.put(MetricsConstants.TAG_METHOD, methodName);
        
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            tags.put(MetricsConstants.TAG_STATUS, "success");
            return result;
        } catch (Exception e) {
            tags.put(MetricsConstants.TAG_STATUS, "error");
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.incrementCounter(MetricsConstants.API_REQUEST_TOTAL, tags);
            metricsService.recordDuration(MetricsConstants.API_REQUEST_DURATION, 
                    tags, java.time.Duration.ofMillis(duration));
        }
    }
    
    @Pointcut("execution(* com.yiyundao.compensation.service..*(..))")
    public void servicePointcut() {}
    
    @Around("servicePointcut()")
    public Object monitorService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        Map<String, String> tags = new HashMap<>();
        tags.put("service", className);
        tags.put("method", methodName);
        
        return metricsService.recordTimer(
                "service_method_duration",
                tags,
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Exception e) {
                        metricsService.incrementCounter("service_error_total", tags);
                        throw e;
                    }
                }
        );
    }
}
```

#### 9.5.4 业务监控埋点示例

```java
package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.common.constant.MetricsConstants;
import com.yiyundao.compensation.entity.PayrollBatch;
import com.yiyundao.compensation.service.MetricsService;
import com.yiyundao.compensation.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {
    
    private final MetricsService metricsService;
    
    @Override
    public PayrollBatch createBatch(PayrollBatch batch) {
        // 记录批次创建
        metricsService.incrementCounter(
                MetricsConstants.PAYROLL_BATCH_TOTAL,
                MetricsConstants.TAG_BATCH_TYPE, batch.getType()
        );
        
        // 记录处理耗时
        return metricsService.recordTimer(
                "payroll_batch_create_duration",
                Map.of(MetricsConstants.TAG_BATCH_TYPE, batch.getType()),
                () -> {
                    // 实际创建逻辑
                    PayrollBatch result = doCreateBatch(batch);
                    
                    // 记录批次创建成功
                    metricsService.incrementCounter(
                            MetricsConstants.PAYROLL_BATCH_SUCCESS_RATE,
                            MetricsConstants.TAG_BATCH_TYPE, batch.getType(),
                            "status", "success"
                    );
                    
                    return result;
                }
        );
    }
    
    @Override
    public void calculate(PayrollBatch batch) {
        metricsService.recordTimer(
                "payroll_calculation_duration",
                Map.of("batch_id", batch.getId().toString()),
                this::doCalculate
        );
    }
}
```

#### 9.5.5 Micrometer 配置

```java
package com.yiyundao.compensation.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "compensation-backend")
                .commonTags("environment", "${spring.profiles.active:unknown}");
    }
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
```

```yaml
# application.yml 中添加
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
  metrics.export.prometheus.enabled: true
```

---

### 9.6 链路追踪详细设计

#### 9.6.1 Zipkin 配置

```java
package com.yiyundao.compensation.config;

import brave.Tracing;
import brave.servlet.TracingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {
    
    @Bean
    public Tracing tracing() {
        return Tracing.newBuilder()
                .localServiceName("compensation-backend")
                .build();
    }
    
    @Bean
    public FilterRegistrationBean<TracingFilter> tracingFilter(Tracing tracing) {
        FilterRegistrationBean<TracingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(TracingFilter.create(tracing));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
    
    @Bean
    public brave.spring.web.TracingInterceptor tracingInterceptor(Tracing tracing) {
        return new brave.spring.web.TracingInterceptor(tracing);
    }
}
```

#### 9.6.2 自定义 Span 工具

```java
package com.yiyundao.compensation.util;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TraceUtil {
    
    private final Tracer tracer;
    
    public Span startSpan(String name) {
        return tracer.spanBuilder(name).start();
    }
    
    public Span startSpan(String name, Map<String, String> tags) {
        Span span = tracer.spanBuilder(name).start();
        tags.forEach(span::tag);
        return span;
    }
    
    public <T> T executeWithSpan(String name, Supplier<T> supplier) {
        Span span = startSpan(name);
        try (Span s = span) {
            return supplier.get();
        }
    }
    
    public void executeWithSpan(String name, Runnable runnable) {
        Span span = startSpan(name);
        try (Span s = span) {
            runnable.run();
        }
    }
    
    public void addTag(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }
    
    public void recordError(Throwable error) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.error(error);
        }
    }
    
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceIdString();
        }
        return null;
    }
    
    public String getCurrentSpanId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().spanIdString();
        }
        return null;
    }
}
```

#### 9.6.3 MDC 日志配置

```xml
<!-- logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATTERN" 
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-},%X{spanId:-}] %-5level %logger{50} - %msg%n"/>
    
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
    
    <logger name="com.yiyundao.compensation" level="DEBUG"/>
</configuration>
```

#### 9.6.4 业务埋点示例

```java
package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.entity.PayrollBatch;
import com.yiyundao.compensation.entity.PayrollItem;
import com.yiyundao.compensation.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCalculationService {
    
    private final TraceUtil traceUtil;
    
    public void calculateBatch(PayrollBatch batch, List<PayrollItem> items) {
        traceUtil.executeWithSpan("payroll.calculateBatch", () -> {
            traceUtil.addTag("batch.id", batch.getId().toString());
            traceUtil.addTag("batch.type", batch.getType());
            traceUtil.addTag("item.count", String.valueOf(items.size()));
            
            log.info("开始计算薪资批次: batchId={}, itemCount={}", 
                    batch.getId(), items.size());
            
            try {
                // 计算逻辑
                for (PayrollItem item : items) {
                    calculateItem(batch, item);
                }
                
                log.info("薪资批次计算完成: batchId={}", batch.getId());
            } catch (Exception e) {
                traceUtil.recordError(e);
                throw e;
            }
        });
    }
    
    private void calculateItem(PayrollBatch batch, PayrollItem item) {
        traceUtil.executeWithSpan("payroll.calculateItem", () -> {
            traceUtil.addTag("item.id", item.getId().toString());
            traceUtil.addTag("employee.id", item.getEmployeeId().toString());
            
            // 计算逻辑
            // ...
        });
    }
}
```

---

### 9.7 异常处理规范详细设计

#### 9.7.1 异常基类

```java
package com.yiyundao.compensation.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseException extends RuntimeException {
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    private final String errorCode;
    
    protected BaseException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorCode = code;
    }
    
    protected BaseException(String code, String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
```

#### 9.7.2 标准异常定义

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends BaseException {
    
    public BusinessException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
    
    public BusinessException(String code, String message, String errorCode) {
        super(code, message, HttpStatus.BAD_REQUEST, errorCode);
    }
    
    public static BusinessException of(String code, String message) {
        return new BusinessException(code, message);
    }
}
```

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BaseException {
    
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
    
    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", field + ": " + message, HttpStatus.BAD_REQUEST);
    }
}
```

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends BaseException {
    
    public AuthenticationException(String message) {
        super("AUTH_ERROR", message, HttpStatus.UNAUTHORIZED);
    }
    
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("Token 已过期");
    }
    
    public static AuthenticationException tokenInvalid() {
        return new AuthenticationException("Token 无效");
    }
    
    public static AuthenticationException notLoggedIn() {
        return new AuthenticationException("请先登录");
    }
}
```

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedException extends BaseException {
    
    public AccessDeniedException(String message) {
        super("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
    
    public static AccessDeniedException noPermission() {
        return new AccessDeniedException("无权限访问");
    }
}
```

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException {
    
    public ResourceNotFoundException(String resourceType, Object identifier) {
        super("NOT_FOUND", 
              resourceType + " 不存在: " + identifier, 
              HttpStatus.NOT_FOUND);
    }
    
    public static ResourceNotFoundException employee(Long id) {
        return new ResourceNotFoundException("员工", id);
    }
    
    public static ResourceNotFoundException payrollBatch(Long id) {
        return new ResourceNotFoundException("薪资批次", id);
    }
}
```

```java
package com.yiyundao.compensation.exception;

import org.springframework.http.HttpStatus;

public class SystemException extends BaseException {
    
    public SystemException(String message) {
        super("SYSTEM_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    public SystemException(String message, Throwable cause) {
        super("SYSTEM_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
```

#### 9.7.3 错误码定义

```java
package com.yiyundao.compensation.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements com.yiyundao.compensation.exception.ErrorCode {
    
    SUCCESS("0", "成功", HttpStatus.OK),
    
    // 通用错误 1xxx
    VALIDATION_ERROR("1001", "参数校验失败", HttpStatus.BAD_REQUEST),
    NOT_FOUND("1002", "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("1003", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    
    // 认证错误 2xxx
    TOKEN_EXPIRED("2001", "Token 已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("2002", "Token 无效", HttpStatus.UNAUTHORIZED),
    NOT_LOGGED_IN("2003", "请先登录", HttpStatus.UNAUTHORIZED),
    
    // 权限错误 3xxx
    ACCESS_DENIED("3001", "无权限访问", HttpStatus.FORBIDDEN),
    
    // 业务错误 4xxx
    EMPLOYEE_NOT_FOUND("4001", "员工不存在", HttpStatus.NOT_FOUND),
    BATCH_NOT_FOUND("4002", "薪资批次不存在", HttpStatus.NOT_FOUND),
    BATCH_ALREADY_PROCESSED("4003", "批次已处理", HttpStatus.BAD_REQUEST),
    APPROVAL_ALREADY_EXISTS("4004", "审批已存在", HttpStatus.BAD_REQUEST),
    IDEMPOTENT_REQUEST("4005", "请勿重复提交", HttpStatus.CONFLICT),
    
    // 文件错误 5xxx
    FILE_NOT_FOUND("5001", "文件不存在", HttpStatus.NOT_FOUND),
    FILE_TYPE_NOT_ALLOWED("5002", "文件类型不允许", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("5003", "文件大小超限", HttpStatus.BAD_REQUEST),
    
    // 第三方错误 6xxx
    PAYMENT_FAILED("6001", "支付失败", HttpStatus.BAD_REQUEST),
    PAYMENT_CHANNEL_ERROR("6002", "支付渠道异常", HttpStatus.SERVICE_UNAVAILABLE);
    
    private final String code;
    private final String message;
    private final HttpStatus status;
    
    @Override
    public String getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
```

#### 9.7.4 全局异常处理器

```java
package com.yiyundao.compensation.handler;

import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.common.constant.ErrorCode;
import com.yiyundao.compensation.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常: code={}, message={}, path={}", 
                ex.getCode(), ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(
                        ex.getCode(),
                        ex.getMessage(),
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        log.warn("参数校验失败: message={}, path={}", 
                ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        ex.getMessage(),
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            Exception ex, HttpServletRequest request) {
        String message = extractValidationMessage(ex);
        log.warn("参数校验失败: message={}, path={}", message, request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        message,
                        createErrorDetail(ErrorCode.VALIDATION_ERROR, request)
                ));
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("认证失败: message={}, path={}", ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ex.getCode(),
                        ex.getMessage(),
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("权限不足: message={}, path={}", ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getCode(),
                        ex.getMessage(),
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在: message={}, path={}", ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ErrorCode.NOT_FOUND.getCode(),
                        ex.getMessage(),
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(
            SystemException ex, HttpServletRequest request) {
        log.error("系统异常: message={}, path={}", ex.getMessage(), request.getRequestURI(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        "系统内部错误，请稍后重试",
                        createErrorDetail(ex, request)
                ));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        log.error("未知异常: message={}, path={}", ex.getMessage(), request.getRequestURI(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        "系统内部错误，请稍后重试",
                        createErrorDetail(ErrorCode.INTERNAL_ERROR, request)
                ));
    }
    
    private ApiResponse.ErrorDetail createErrorDetail(
            BaseException ex, HttpServletRequest request) {
        return new ApiResponse.ErrorDetail(
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                request.getRequestURI()
        );
    }
    
    private ApiResponse.ErrorDetail createErrorDetail(
            ErrorCode errorCode, HttpServletRequest request) {
        return new ApiResponse.ErrorDetail(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                request.getRequestURI()
        );
    }
    
    private String extractValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException) {
            var bindingResult = ((MethodArgumentNotValidException) ex).getBindingResult();
            return bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst()
                    .orElse("参数校验失败");
        }
        if (ex instanceof BindException) {
            var bindingResult = ((BindException) ex).getBindingResult();
            return bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst()
                    .orElse("参数校验失败");
        }
        return "参数校验失败";
    }
}
```

---

### 9.8 幂等性框架详细设计

#### 9.8.1 幂等注解

```java
package com.yiyundao.compensation.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * 幂等键 SpEL 表达式
     * 例如: "#request.batchNo + ':' + #request.employeeId"
     */
    String key() default "";
    
    /**
     * 锁过期时间（秒）
     */
    int expireSeconds() default 300;
    
    /**
     * 提示信息
     */
    String message() default "请勿重复提交";
    
    /**
     * 是否尝试等待锁（用于分布式锁场景）
     */
    boolean tryLock() default false;
    
    /**
     * 等待锁的最长时间（毫秒）
     */
    long waitTime() default 1000;
}
```

#### 9.8.2 幂等键生成器

```java
package com.yiyundao.compensation.service;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class IdempotentKeyGenerator {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    
    public String generateKey(String spelExpression, Map<String, Object> variables) {
        if (spelExpression == null || spelExpression.isEmpty()) {
            return generateDefaultKey(variables);
        }
        
        EvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);
        
        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }
    
    public String generateKey(String spelExpression, Object... args) {
        if (args == null || args.length == 0) {
            return spelExpression;
        }
        
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
        }
        
        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }
    
    private String generateDefaultKey(Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        variables.forEach((k, v) -> {
            if (v != null) {
                sb.append(k).append("=").append(v.toString()).append(":");
            }
        });
        return sb.toString();
    }
}
```

#### 9.8.3 幂等拦截器

```java
package com.yiyundao.compensation.interceptor;

import com.yiyundao.compensation.annotation.Idempotent;
import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.exception.BusinessException;
import com.yiyundao.compensation.service.IdempotentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentInterceptor implements HandlerInterceptor {
    
    private final IdempotentService idempotentService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        
        Method method = handlerMethod.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        
        if (indempotent == null) {
            return true;
        }
        
        // 生成幂等键
        String idempotentKey = generateKey(request, idempotent, handlerMethod);
        
        // 获取锁
        boolean acquired = idempotentService.tryLock(
                idempotentKey, 
                idempotent.expireSeconds(),
                idempotent.tryLock() ? idempotent.waitTime() : 0
        );
        
        if (!acquired) {
            log.warn("幂等性检查未通过: key={}", idempotentKey);
            writeResponse(response, ApiResponse.error(
                    idempotent.message()
            ));
            return false;
        }
        
        // 存储锁信息到请求属性，用于后续释放
        request.setAttribute("idempotentKey", idempotentKey);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        String idempotentKey = (String) request.getAttribute("idempotentKey");
        if (idempotentKey != null) {
            // 根据请求结果决定是否释放锁
            // 如果是异常情况，可能需要保留锁
            if (ex == null) {
                // 正常完成，释放锁
                idempotentService.unlock(idempotentKey);
            } else {
                // 发生异常，根据业务决定是否释放锁
                log.debug("请求异常，保留幂等锁: key={}", idempotentKey);
            }
        }
    }
    
    private String generateKey(HttpServletRequest request, 
                              Idempotent idempotent,
                              HandlerMethod handlerMethod) {
        // 从请求参数和路径变量生成幂等键
        Map<String, Object> variables = new HashMap<>();
        
        // 添加请求属性
        variables.put("request", request);
        variables.put("method", handlerMethod.getMethod().getName());
        variables.put("uri", request.getRequestURI());
        
        // 添加请求参数
        request.getParameterMap().forEach((k, v) -> 
                variables.put(k, v.length > 0 ? v[0] : null));
        
        return idempotentService.generateKey(indempotent.key(), variables);
    }
    
    private void writeResponse(HttpServletResponse response, ApiResponse<Void> apiResponse) {
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(apiResponse)
            );
        } catch (Exception e) {
            log.error("写入幂等响应失败", e);
        }
    }
}
```

#### 9.8.4 幂等服务

```java
package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentService {
    
    private final StringRedisTemplate redisTemplate;
    private final IdempotentKeyGenerator keyGenerator;
    
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";
    
    public boolean tryLock(String key, int expireSeconds, long waitTime) {
        String fullKey = IDEMPOTENT_KEY_PREFIX + key;
        
        if (waitTime > 0) {
            // 尝试等待获取锁
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < waitTime) {
                Boolean result = redisTemplate.opsForValue()
                        .setIfAbsent(fullKey, "1", Duration.ofSeconds(expireSeconds));
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
        
        // 直接尝试获取锁
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, "1", Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(result);
    }
    
    public void unlock(String key) {
        String fullKey = IDEMPOTENT_KEY_PREFIX + key;
        redisTemplate.delete(fullKey);
        log.debug("释放幂等锁: key={}", fullKey);
    }
    
    public String generateKey(String spelExpression, Map<String, Object> variables) {
        return keyGenerator.generateKey(spelExpression, variables);
    }
    
    public void extendLock(String key, int expireSeconds) {
        String fullKey = IDEMPOTENT_KEY_PREFIX + key;
        redisTemplate.expire(fullKey, Duration.ofSeconds(expireSeconds));
    }
}
```

#### 9.8.5 使用示例

```java
package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.annotation.Idempotent;
import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.dto.PaymentRequest;
import com.yiyundao.compensation.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支付管理")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @Operation(summary = "发起支付")
    @PostMapping("/process")
    @Idempotent(
            key = "#request.batchNo + ':' + #request.employeeId",
            message = "请勿重复提交支付请求",
            expireSeconds = 300
    )
    public ApiResponse<Void> processPayment(@RequestBody PaymentRequest request) {
        paymentService.processPayment(request);
        return ApiResponse.success();
    }
    
    @Operation(summary = "批量支付")
    @PostMapping("/batch")
    @Idempotent(
            key = "'batch:' + #request.batchNo",
            message = "该批次正在处理中，请勿重复提交",
            expireSeconds = 600,
            tryLock = true,
            waitTime = 2000
    )
    public ApiResponse<Void> processBatchPayment(@RequestBody BatchPaymentRequest request) {
        paymentService.processBatchPayment(request);
        return ApiResponse.success();
    }
}
```

---

### 9.9 调度任务管理详细设计

#### 9.9.1 任务实体

```java
package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task")
public class ScheduledTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("task_key")
    private String taskKey;
    
    @TableField("task_name")
    private String taskName;
    
    @TableField("task_group")
    private String taskGroup;
    
    @TableField("cron_expression")
    private String cronExpression;
    
    @TableField("description")
    private String description;
    
    @TableField("status")
    private TaskStatus status;
    
    @TableField("retry_count")
    private Integer retryCount;
    
    @TableField("max_retry_count")
    private Integer maxRetryCount;
    
    @TableField("retry_interval_seconds")
    private Integer retryIntervalSeconds;
    
    @TableField("last_execute_time")
    private LocalDateTime lastExecuteTime;
    
    @TableField("next_execute_time")
    private LocalDateTime nextExecuteTime;
    
    @TableField("last_result")
    private String lastResult;
    
    @TableField("alarm_enabled")
    private Boolean alarmEnabled;
    
    @TableField("alarm_receivers")
    private String alarmReceivers;
    
    @TableField("create_time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField("update_time")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
    
    public enum TaskStatus {
        PAUSED,    // 暂停
        RUNNING,   // 运行中
        FAILED,    // 失败
        SUCCESS    // 成功
    }
}
```

#### 9.9.2 任务执行记录实体

```java
package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task_execution")
public class ScheduledTaskExecution {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("task_id")
    private Long taskId;
    
    @TableField("task_key")
    private String taskKey;
    
    @TableField("start_time")
    private LocalDateTime startTime;
    
    @TableField("end_time")
    private LocalDateTime endTime;
    
    @TableField("duration_ms")
    private Long durationMs;
    
    @TableField("status")
    private ExecutionStatus status;
    
    @TableField("result")
    private String result;
    
    @TableField("error_message")
    private String errorMessage;
    
    @TableField("trace_id")
    private String traceId;
    
    @TableField("create_time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    public enum ExecutionStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        RETRYING
    }
}
```

#### 9.9.3 任务管理服务

```java
package com.yiyundao.compensation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.dto.TaskExecutionLogDTO;
import com.yiyundao.compensation.dto.TaskQueryDTO;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;

import java.util.List;

public interface ScheduledTaskService extends IService<ScheduledTask> {
    
    /**
     * 分页查询任务列表
     */
    PageResponse<ScheduledTask> pageTasks(TaskQueryDTO query);
    
    /**
     * 获取任务详情
     */
    ScheduledTask getTaskDetail(Long id);
    
    /**
     * 创建任务
     */
    Long createTask(TaskScheduleDTO dto);
    
    /**
     * 更新任务
     */
    void updateTask(Long id, TaskScheduleDTO dto);
    
    /**
     * 删除任务
     */
    void deleteTask(Long id);
    
    /**
     * 暂停任务
     */
    void pauseTask(Long id);
    
    /**
     * 恢复任务
     */
    void resumeTask(Long id);
    
    /**
     * 手动触发任务执行
     */
    Long triggerTask(Long id);
    
    /**
     * 获取任务执行日志
     */
    List<ScheduledTaskExecution> getExecutionLogs(Long taskId, int limit);
    
    /**
     * 执行任务
     */
    void executeTask(ScheduledTask task);
}
```

#### 9.9.4 任务调度器

```java
package com.yiyundao.compensation.scheduler;

import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.service.ScheduledTaskService;
import com.yiyundao.compensation.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskScheduler {
    
    private final ScheduledTaskService taskService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final TraceUtil traceUtil;
    
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 加载所有启用的任务
        taskService.list().stream()
                .filter(task -> task.getStatus() == ScheduledTask.TaskStatus.RUNNING)
                .forEach(this::scheduleTask);
    }
    
    public void scheduleTask(ScheduledTask task) {
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task),
                triggerContext -> {
                    // 计算下次执行时间
                    return taskService.calculateNextExecutionTime(
                            task.getCronExpression(), 
                            triggerContext.lastCompletionTime()
                    );
                }
        );
        
        scheduledTasks.put(task.getId(), future);
        log.info("任务已调度: taskKey={}, nextExecution={}", 
                task.getTaskKey(), task.getNextExecuteTime());
    }
    
    private void executeTask(ScheduledTask task) {
        traceUtil.executeWithSpan("scheduled.task." + task.getTaskKey(), () -> {
            ScheduledTaskExecution execution = createExecution(task);
            
            try {
                log.info("开始执行任务: taskKey={}", task.getTaskKey());
                
                // 调用业务方法
                taskService.executeTask(task);
                
                execution.setStatus(ScheduledTaskExecution.ExecutionStatus.SUCCESS);
                execution.setResult("执行成功");
                
                log.info("任务执行成功: taskKey={}", task.getTaskKey());
                
            } catch (Exception e) {
                log.error("任务执行失败: taskKey={}", task.getTaskKey(), e);
                
                execution.setStatus(ScheduledTaskExecution.ExecutionStatus.FAILED);
                execution.setErrorMessage(e.getMessage());
                
                // 尝试重试
                if (shouldRetry(task, execution)) {
                    scheduleRetry(task, execution);
                }
                
                // 发送告警
                if (task.getAlarmEnabled()) {
                    sendAlarm(task, execution);
                }
            } finally {
                // 更新任务状态
                updateTaskAfterExecution(task, execution);
            }
        });
    }
    
    private ScheduledTaskExecution createExecution(ScheduledTask task) {
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setTaskId(task.getId());
        execution.setTaskKey(task.getTaskKey());
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);
        return execution;
    }
    
    private boolean shouldRetry(ScheduledTask task, ScheduledTaskExecution execution) {
        return task.getRetryCount() < task.getMaxRetryCount();
    }
    
    private void scheduleRetry(ScheduledTask task, ScheduledTaskExecution execution) {
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RETRYING);
        
        taskScheduler.schedule(
                () -> executeTask(task),
                LocalDateTime.now().plusSeconds(task.getRetryIntervalSeconds())
        );
        
        log.info("任务已安排重试: taskKey={}, retryCount={}", 
                task.getTaskKey(), task.getRetryCount() + 1);
    }
    
    private void sendAlarm(ScheduledTask task, ScheduledTaskExecution execution) {
        // 发送告警通知
        log.warn("任务执行失败告警: taskKey={}, error={}", 
                task.getTaskKey(), execution.getErrorMessage());
    }
    
    private void updateTaskAfterExecution(ScheduledTask task, 
                                          ScheduledTaskExecution execution) {
        ScheduledTask updateTask = new ScheduledTask();
        updateTask.setId(task.getId());
        updateTask.setLastExecuteTime(execution.getStartTime());
        updateTask.setLastResult(execution.getStatus().name());
        updateTask.setRetryCount(execution.getStatus() == ScheduledTaskExecution.ExecutionStatus.FAILED
                ? task.getRetryCount() + 1 : 0);
        
        taskService.updateById(updateTask);
    }
    
    public void pauseTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(taskId);
        }
    }
    
    public void resumeTask(ScheduledTask task) {
        scheduleTask(task);
    }
}
```

#### 9.9.5 任务管理 API

```java
package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.dto.TaskQueryDTO;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.scheduler.TaskScheduler;
import com.yiyundao.compensation.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务调度管理")
@RestController
@RequestMapping("/api/v1/admin/tasks")
@RequiredArgsConstructor
public class TaskScheduleController {
    
    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;
    
    @Operation(summary = "分页查询任务列表")
    @GetMapping
    public ApiResponse<PageResponse<ScheduledTask>> page(TaskQueryDTO query) {
        return ApiResponse.success(taskService.pageTasks(query));
    }
    
    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> get(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }
    
    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResponse<Long> create(@RequestBody TaskScheduleDTO dto) {
        return ApiResponse.success(taskService.createTask(dto));
    }
    
    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody TaskScheduleDTO dto) {
        taskService.updateTask(id, dto);
        return ApiResponse.success();
    }
    
    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        // 暂停任务
        taskScheduler.pauseTask(id);
        // 删除任务
        taskService.deleteTask(id);
        return ApiResponse.success();
    }
    
    @Operation(summary = "暂停任务")
    @PostMapping("/{id}/pause")
    public ApiResponse<Void> pause(@PathVariable Long id) {
        taskScheduler.pauseTask(id);
        taskService.pauseTask(id);
        return ApiResponse.success();
    }
    
    @Operation(summary = "恢复任务")
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(@PathVariable Long id) {
        ScheduledTask task = taskService.getById(id);
        if (task != null) {
            taskScheduler.resumeTask(task);
            taskService.resumeTask(id);
        }
        return ApiResponse.success();
    }
    
    @Operation(summary = "手动触发任务")
    @PostMapping("/{id}/trigger")
    public ApiResponse<Long> trigger(@PathVariable Long id) {
        return ApiResponse.success(taskService.triggerTask(id));
    }
    
    @Operation(summary = "获取执行日志")
    @GetMapping("/{id}/logs")
    public ApiResponse<List<ScheduledTaskExecution>> getLogs(
            @PathVariable Long id, 
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(taskService.getExecutionLogs(id, limit));
    }
}
```

---

### 9.10 文件存储模块详细设计

#### 9.10.1 文件服务接口

```java
package com.yiyundao.compensation.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {
    
    /**
     * 上传文件
     * @param file 文件
     * @param category 文件分类（employee/id_card 等）
     * @return 文件访问路径
     */
    String upload(MultipartFile file, String category);
    
    /**
     * 上传文件（带自定义文件名）
     */
    String upload(MultipartFile file, String category, String fileName);
    
    /**
     * 删除文件
     */
    void delete(String fileKey);
    
    /**
     * 获取文件访问 URL
     */
    String getUrl(String fileKey);
    
    /**
     * 获取文件输入流
     */
    InputStream getInputStream(String fileKey);
    
    /**
     * 检查文件是否存在
     */
    boolean exists(String fileKey);
    
    /**
     * 获取文件大小
     */
    long getFileSize(String fileKey);
}
```

#### 9.10.2 本地存储实现

```java
package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileService implements FileService {
    
    private final FileStorageProperties properties;
    private Path rootLocation;
    
    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(properties.getLocal().getBasePath());
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }
    
    @Override
    public String upload(MultipartFile file, String category) {
        return upload(file, category, null);
    }
    
    @Override
    public String upload(MultipartFile file, String category, String fileName) {
        validateFile(file);
        
        if (fileName == null || fileName.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            fileName = generateFileName(extension);
        }
        
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String relativePath = category + "/" + datePath + "/" + fileName;
            Path destinationFile = rootLocation.resolve(relativePath).normalize();
            
            // 创建父目录
            Files.createDirectories(destinationFile.getParent());
            
            // 写入文件
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("文件上传成功: path={}, size={}", relativePath, file.getSize());
            return relativePath;
            
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
    
    @Override
    public void delete(String fileKey) {
        Path file = rootLocation.resolve(fileKey).normalize();
        try {
            Files.deleteIfExists(file);
            log.info("文件删除成功: path={}", fileKey);
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }
    
    @Override
    public String getUrl(String fileKey) {
        return properties.getLocal().getBaseUrl() + "/" + fileKey;
    }
    
    @Override
    public InputStream getInputStream(String fileKey) throws IOException {
        Path file = rootLocation.resolve(fileKey).normalize();
        return Files.newInputStream(file);
    }
    
    @Override
    public boolean exists(String fileKey) {
        Path file = rootLocation.resolve(fileKey).normalize();
        return Files.exists(file);
    }
    
    @Override
    public long getFileSize(String fileKey) {
        try {
            Path file = rootLocation.resolve(fileKey).normalize();
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String filename = StringUtils.getFilename(file.getOriginalFilename());
        String extension = getFileExtension(filename).toLowerCase();
        
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }
        
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new IllegalArgumentException("文件大小超出限制");
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }
    
    private String generateFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return extension.isEmpty() ? uuid : uuid + "." + extension;
    }
}
```

#### 9.10.3 MinIO 存储实现

```java
package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileService implements FileService {
    
    private final FileStorageProperties properties;
    private MinioClient minioClient;
    
    @PostConstruct
    public void init() {
        try {
            minioClient = MinioClient.builder()
                    .endpoint(properties.getMinio().getEndpoint())
                    .credentials(
                            properties.getMinio().getAccessKey(),
                            properties.getMinio().getSecretKey()
                    )
                    .build();
            
            // 确保 bucket 存在
            ensureBucketExists(properties.getMinio().getBucket());
            
        } catch (Exception e) {
            throw new RuntimeException("MinIO 客户端初始化失败", e);
        }
    }
    
    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建 MinIO Bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("检查 Bucket 失败: {}", e.getMessage());
        }
    }
    
    @Override
    public String upload(MultipartFile file, String category) {
        return upload(file, category, null);
    }
    
    @Override
    public String upload(MultipartFile file, String category, String fileName) {
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            if (fileName == null || fileName.isEmpty()) {
                fileName = generateFileName(extension);
            }
            
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = category + "/" + datePath + "/" + fileName;
            
            // 上传到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            
            log.info("文件上传成功: object={}, size={}", objectName, file.getSize());
            return objectName;
            
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件上传失败", e);
        }
    }
    
    @Override
    public void delete(String fileKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            log.info("文件删除成功: object={}", fileKey);
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件删除失败", e);
        }
    }
    
    @Override
    public String getUrl(String fileKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .method(Method.GET)
                            .expiry(24 * 60 * 60) // 24小时
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件 URL 失败", e);
        }
    }
    
    @Override
    public InputStream getInputStream(String fileKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件流失败", e);
        }
    }
    
    @Override
    public boolean exists(String fileKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public long getFileSize(String fileKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            return stat.size();
        } catch (Exception e) {
            return -1;
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }
    
    private String generateFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return extension.isEmpty() ? uuid : uuid + "." + extension;
    }
}
```

#### 9.10.4 存储配置

```java
package com.yiyundao.compensation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "file-storage")
public class FileStorageProperties {
    
    private String active = "local"; // local 或 minio
    
    private LocalStorage local = new LocalStorage();
    private MinioStorage minio = new MinioStorage();
    
    private long maxFileSize = 10 * 1024 * 1024; // 10MB
    
    private java.util.List<String> allowedExtensions = java.util.Arrays.asList(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "zip"
    );
    
    @Data
    public static class LocalStorage {
        private String basePath = "/data/files";
        private String baseUrl = "/files";
    }
    
    @Data
    public static class MinioStorage {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }
}
```

```yaml
# application.yml
file-storage:
  active: ${FILE_STORAGE_ACTIVE:local}
  max-file-size: 10MB
  allowed-extensions:
    - jpg
    - jpeg
    - png
    - gif
    - pdf
    - doc
    - docx
    - xls
    - xlsx
  local:
    base-path: ${LOCAL_FILE_PATH:/data/files}
    base-url: ${LOCAL_FILE_URL:http://localhost:8080/files}
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:compensation}
```

#### 9.10.5 文件上传控制器

```java
package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.ApiResponse;
import com.yiyundao.compensation.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String category) {
        
        String fileKey = fileService.upload(file, category);
        String url = fileService.getUrl(fileKey);
        
        return ApiResponse.success(url);
    }
    
    @Operation(summary = "批量上传文件")
    @PostMapping("/upload/batch")
    public ApiResponse<List<String>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "general") String category) {
        
        List<String> urls = files.stream()
                .map(file -> {
                    String fileKey = fileService.upload(file, category);
                    return fileService.getUrl(fileKey);
                })
                .toList();
        
        return ApiResponse.success(urls);
    }
    
    @Operation(summary = "删除文件")
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam String fileKey) {
        fileService.delete(fileKey);
        return ApiResponse.success();
    }
    
    @Operation(summary = "获取文件 URL")
    @GetMapping("/url")
    public ApiResponse<String> getUrl(@RequestParam String fileKey) {
        return ApiResponse.success(fileService.getUrl(fileKey));
    }
}
```

---

## 十、数据库设计

### 10.1 任务调度相关表

```sql
-- 定时任务表
CREATE TABLE `scheduled_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_key` VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
    `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `task_group` VARCHAR(64) DEFAULT 'DEFAULT' COMMENT '任务分组',
    `cron_expression` VARCHAR(64) NOT NULL COMMENT 'Cron 表达式',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '任务描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-暂停, 1-运行',
    `retry_count` INT DEFAULT 0 COMMENT '当前重试次数',
    `max_retry_count` INT DEFAULT 3 COMMENT '最大重试次数',
    `retry_interval_seconds` INT DEFAULT 60 COMMENT '重试间隔(秒)',
    `last_execute_time` DATETIME DEFAULT NULL COMMENT '上次执行时间',
    `next_execute_time` DATETIME DEFAULT NULL COMMENT '下次执行时间',
    `last_result` VARCHAR(20) DEFAULT NULL COMMENT '上次执行结果',
    `alarm_enabled` TINYINT DEFAULT 0 COMMENT '是否启用告警',
    `alarm_receivers` VARCHAR(500) DEFAULT NULL COMMENT '告警接收人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_key` (`task_key`),
    KEY `idx_status` (`status`),
    KEY `idx_next_execute_time` (`next_execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务表';

-- 任务执行记录表
CREATE TABLE `scheduled_task_execution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_key` VARCHAR(64) NOT NULL COMMENT '任务标识',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时(毫秒)',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-运行中, 1-成功, 2-失败, 3-重试中',
    `result` TEXT DEFAULT NULL COMMENT '执行结果',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行记录表';
```

---

## 十一、测试规范

### 11.1 单元测试示例

```java
package com.yiyundao.compensation.service;

import com.yiyundao.compensation.common.annotation.Sensitive;
import com.yiyundao.compensation.common.annotation.SensitiveType;
import com.yiyundao.compensation.util.SensitiveUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("数据脱敏测试")
class SensitiveUtilTest {
    
    private SensitiveUtil sensitiveUtil;
    
    @BeforeEach
    void setUp() {
        sensitiveUtil = new SensitiveUtil();
        ReflectionTestUtils.setField(sensitiveUtil, "debugMode", false);
    }
    
    @Test
    @DisplayName("身份证号脱敏")
    void testDesensitizeIdCard() {
        String idCard = "110101199001011234";
        String result = sensitiveUtil.desensitizeIdCard(idCard);
        
        assertEquals("110***********1234", result);
    }
    
    @Test
    @DisplayName("手机号脱敏")
    void testDesensitizePhone() {
        String phone = "13812345678";
        String result = sensitiveUtil.desensitizePhone(phone);
        
        assertEquals("138****5678", result);
    }
    
    @Test
    @DisplayName("银行卡号脱敏")
    void testDesensitizeBankCard() {
        String bankCard = "6222021234567890123";
        String result = sensitiveUtil.desensitizeBankCard(bankCard);
        
        assertEquals("6222**********0123", result);
    }
    
    @Test
    @DisplayName("姓名脱敏")
    void testDesensitizeName() {
        assertEquals("张*", sensitiveUtil.desensitizeName("张三"));
        assertEquals("李*", sensitiveUtil.desensitizeName("李四"));
        assertEquals("*", sensitiveUtil.desensitizeName("王"));
    }
    
    @Test
    @DisplayName("邮箱脱敏")
    void testDesensitizeEmail() {
        assertEquals("te**@example.com", sensitiveUtil.desensitizeEmail("test@example.com"));
        assertEquals("ab**@company.cn", sensitiveUtil.desensitizeEmail("admin@company.cn"));
    }
    
    @Test
    @DisplayName("调试模式下不脱敏")
    void testDebugMode() {
        ReflectionTestUtils.setField(sensitiveUtil, "debugMode", true);
        
        String phone = "13812345678";
        String result = sensitiveUtil.desensitizePhone(phone);
        
        assertEquals(phone, result);
    }
}
```

```java
package com.yiyundao.compensation.service;

import com.yiyundao.compensation.service.impl.IdempotentService;
import com.yiyundao.compensation.service.impl.IdempotentKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("幂等性服务测试")
class IdempotentServiceTest {
    
    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private IdempotentService idempotentService;
    private IdempotentKeyGenerator keyGenerator;
    
    @BeforeEach
    void setUp() {
        keyGenerator = new IdempotentKeyGenerator();
        idempotentService = new IdempotentService(redisTemplate, keyGenerator);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    @DisplayName("获取锁成功")
    void testTryLockSuccess() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        
        boolean result = idempotentService.tryLock("test-key", 300, 0);
        
        assertTrue(result);
        verify(valueOperations).setIfAbsent(
                eq("idempotent:test-key"),
                eq("1"),
                eq(Duration.ofSeconds(300))
        );
    }
    
    @Test
    @DisplayName("获取锁失败")
    void testTryLockFail() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        
        boolean result = idempotentService.tryLock("test-key", 300, 0);
        
        assertFalse(result);
    }
    
    @Test
    @DisplayName("释放锁")
    void testUnlock() {
        when(redisTemplate.delete("idempotent:test-key")).thenReturn(true);
        
        idempotentService.unlock("test-key");
        
        verify(redisTemplate).delete("idempotent:test-key");
    }
    
    @Test
    @DisplayName("生成幂等键")
    void testGenerateKey() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("batchNo", "BATCH001");
        variables.put("employeeId", 1001L);
        
        String key = idempotentService.generateKey(
                "#batchNo + ':' + #employeeId", 
                variables
        );
        
        assertEquals("BATCH001:1001", key);
    }
}
```

### 11.2 集成测试示例

```java
package com.yiyundao.compensation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.dto.PaymentRequest;
import com.yiyundao.compensation.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("支付接口集成测试")
class PaymentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private PaymentService paymentService;
    
    @MockBean
    private StringRedisTemplate redisTemplate;
    
    @Test
    @DisplayName("处理支付请求")
    void testProcessPayment() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setBatchNo("BATCH001");
        request.setEmployeeId(1001L);
        request.setAmount(5000.00);
        
        doNothing().when(paymentService).processPayment(any());
        
        mockMvc.perform(post("/api/v1/payment/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"));
        
        verify(paymentService, times(1)).processPayment(any());
    }
    
    @Test
    @DisplayName("支付请求参数验证失败")
    void testProcessPaymentValidationFail() throws Exception {
        PaymentRequest request = new PaymentRequest();
        // 缺少必填字段
        
        mockMvc.perform(post("/api/v1/payment/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("1001"));
    }
}
```

---

## 十二、部署配置

### 12.1 Docker 部署

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置时区
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户运行
RUN addgroup -g 1000 app && adduser -u 1000 -G app -s /bin/sh -D app

# 复制应用
COPY target/compensation-0.0.1-SNAPSHOT.jar app.jar

# 切换用户
USER app

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 12.2 Docker Compose

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET=${JWT_SECRET}
      - MASTER_KEY=${MASTER_KEY}
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/compensation
      - SPRING_REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    networks:
      - compensation-network
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=compensation
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/schema.sql:/docker-entrypoint-initdb.d/schema.sql
    ports:
      - "3306:3306"
    networks:
      - compensation-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - compensation-network

  zipkin:
    image: openzipkin/zipkin:3
    ports:
      - "9411:9411"
    networks:
      - compensation-network

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - compensation-network

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
    volumes:
      - grafana-data:/var/lib/grafana
    networks:
      - compensation-network

networks:
  compensation-network:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  grafana-data:
```

### 12.3 Prometheus 配置

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'compensation-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        regex: '([^:]+):\\d+'
        replacement: '${1}'

  - job_name: 'mysql'
    static_configs:
      - targets: ['mysqld_exporter:9100']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis_exporter:9121']
```

---

## 十三、变更日志

| 版本 | 日期 | 变更内容 | 作者 |
|-----|------|---------|------|
| v1.0.0 | 2026-01-10 | 初始版本，定义 10 个改进模块 | 芙宁娜 |
| v1.1.0 | 2026-01-11 | 补充详细技术规范、代码示例和测试设计 | 芙宁娜 |
| v1.2.0 | 2026-01-11 | 实际代码实现：数据脱敏、幂等性框架、调度任务管理、文件存储 | 芙宁娜 |
| v1.3.0 | 2026-01-11 | 自动化测试覆盖：单元测试、服务测试、API 测试 | 芙宁娜 |

---

> 计划深入完成，继续推进实施喵～ (๑•̀ㅂ•́) ✧
