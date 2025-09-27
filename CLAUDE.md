# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Compensation Assistant System** (薪酬助手系统) - a Spring Boot-based backend that handles:
- Multi-platform integration (Enterprise WeChat/DingTalk/Feishu) for organization sync
- Alipay bulk payment processing (1000+ concurrent transfers)
- Offline employee management with mandatory platform binding for managers
- Dynamic approval workflows with dual-track processing
- Compliance with Chinese financial regulations

## Technology Stack

**Backend (Spring Boot 3.5.6)**
- Java 17 with Spring Boot 3.5.6
- Spring Security 6.2 (JWT authentication)
- MyBatis-Plus for database operations (instead of Spring Data JPA)
- RabbitMQ 3.13 (async notifications/payment tasks)
- Redis 7.2 (payment status deduplication/session storage)
- Quartz 3.3 (scheduled organization sync)
- MySQL 8.0 (primary database)
- Bouncy Castle 1.70 (SM4/AES encryption)
- Alipay Open Platform SDK 4.42.96

**Maven Coordinates**
- Group ID: `com.yiyundao`

**Third-party Integrations**
- Alipay for bulk transfers
- Enterprise WeChat/DingTalk/Feishu APIs for organization sync
- Alibaba Cloud SMS for fallback notifications

## Key Architecture Components

### Core Modules
1. **Payment Service** - Handles Alipay bulk transfers with batch processing (1000 transfers/batch)
2. **Organization Sync Service** - Multi-platform employee data synchronization using `OrganizationAdapter` pattern
3. **Approval Engine** - Dynamic approval chains supporting BATCH/ADHOC/OFFLINE modes
4. **Offline Employee Management** - Dedicated workflow for employees not in organization structure
5. **Notification Service** - Multi-level fallback: Platform → SMS → Email

### Database Layer
- Uses **MyBatis-Plus** for enhanced MyBatis functionality
- Automatic CRUD operations with BaseMapper
- Built-in pagination support
- Code generation capabilities
- Lambda-style query wrapper for type-safe queries

### Security & Compliance
- Identity card information encrypted with SM4 (Chinese national standard) + AES-256
- Audit logs retained for ≥5 years (financial compliance requirement)
- Redis-based payment deduplication to prevent duplicate transfers
- Manager role verification for offline employee operations

## Development Commands

Since this is a new project, common Spring Boot commands will be:

```bash
# Build the project
mvn clean compile

# Run tests
mvn test

# Run the application
mvn spring-boot:run

# Package for deployment
mvn clean package

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Generate MyBatis-Plus code (if using code generator)
mvn mybatis-plus:generate
```

## Environment Configuration

The system requires three environments:
- **Development** - Local development with mock integrations
- **Staging** - Pre-production with Alipay sandbox environment
- **Production** - Live environment with real Alipay transfers

Key configuration files:
- `application.yml` - Base configuration including MyBatis-Plus settings
- `application-dev.yml` - Development overrides
- `application-staging.yml` - Staging with Alipay sandbox
- `application-prod.yml` - Production configuration

## MyBatis-Plus Configuration

Expected configuration in `application.yml`:

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.yiyundao.compensation.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

## Critical Configuration Requirements

### Alipay Integration
- Enterprise Alipay account with "Single Transfer to Alipay" permission enabled
- APP_ID and merchant private key configuration
- Async notification URL setup
- Daily transfer limits (default: 10,000 RMB per person)

### Platform Integration Setup
Each supported platform requires:
- **Enterprise WeChat**: CORPID + SECRET with approval permissions
- **DingTalk**: APPKEY + APPSECRET with internal app approval rights
- **Feishu**: APP_ID + APP_SECRET for enterprise self-built apps

### Manager Role Requirements
- All offline employee managers MUST bind to Enterprise WeChat or DingTalk
- SMS fallback configured for critical notifications
- `is_offline_manager` role verification for offline employee operations

## Database Design Highlights

### Key Tables (with MyBatis-Plus entities)
- **Offline Employee Table** - Separate from organization structure
- **Encrypted Identity Storage** - SM4 + AES-256 dual encryption
- **Audit Log Tables** - 5+ year retention for compliance
- **Payment Status Tracking** - Redis-backed deduplication

### Entity Classes Pattern
```java
@TableName("employee")
public class Employee extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("encrypted_id_card")
    private String encryptedIdCard;

    @TableLogic
    private Integer deleted;

    // other fields...
}
```

## Payment Processing Architecture

### Batch Transfer Flow
1. Group transfers into 1000-item batches
2. Redis-based deduplication check
3. Alipay API call with retry logic
4. Async status verification
5. Notification dispatch with fallback chain

### Compliance Features
- Three-factor verification integration
- Payment amount limits per employee
- Audit trail for all financial operations
- Regulatory compliance with Chinese payment laws

## Development Priorities

### Phase 1: Foundation (Weeks 1-3)
- Spring Boot 3.5.6 skeleton setup with Maven (com.yiyundao group)
- MyBatis-Plus configuration and entity generation
- Database modeling and migration scripts
- Alipay sandbox integration POC
- JWT security framework

### Phase 2: Core Features (Weeks 4-8)
- Organization sync adapters
- Dynamic approval engine
- Offline employee management using MyBatis-Plus mappers
- Bulk payment service
- Management UI for offline employees

### Phase 3: Production Readiness (Weeks 9-11)
- Notification service with fallback chain
- Security hardening and encryption
- Monitoring and alerting setup
- Load testing (1000 concurrent payments)

## Testing Requirements

### Critical Test Scenarios
- Alipay sandbox bulk transfer (1000+ items)
- Organization sync failure handling
- Manager platform binding validation
- Payment timeout and retry logic
- Audit log compliance verification
- MyBatis-Plus query performance testing

### Performance Benchmarks
- Payment success rate ≥99.8%
- Batch 1000 transfers completion <5 minutes
- Organization sync latency <1 hour
- 100% manager platform binding rate

## Security Considerations

- Never commit Alipay private keys or API secrets
- Identity card data must use SM4 encryption before database storage
- All financial operations require audit logging
- Manager role verification before offline employee operations
- Redis keys for payment deduplication have appropriate expiration

## Monitoring & Operations

Expected monitoring metrics:
- Payment success rates
- Organization sync status
- Manager notification delivery rates
- System availability (target: 99.9%)
- Approval workflow completion times
- Database query performance (MyBatis-Plus metrics)

## Compliance Notes

This system must comply with:
- "Network Payment Business Management Measures for Non-bank Payment Institutions"
- GB/T 32918-2016 (SM4 national encryption standard)
- Financial Data Security Classification Guidelines
- 5-year audit log retention requirements