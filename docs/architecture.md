# Architecture & Modularization Guide

This service adopts a feature-first, high-cohesion package structure. Business code is grouped by module under `modules/`, with cross‑cutting utilities in `common/` and external exposure in `interfaces/`.

## Directory Responsibilities
- common/
  - config/: Spring config (security, MyBatis‑Plus, Redis, WebClient, Alipay)
  - exception/: BusinessException and GlobalExceptionHandler
  - response/: ApiResponse<T>
  - utils/: Reusable helpers (e.g., SensitiveDataValidator)
- interfaces/
  - controller/: HTTP controllers (organized by module, e.g., employee/, payment/)
  - dto/, vo/: Request/response contracts for controllers
- modules/
  - <feature>/service + impl/: Business services per module (e.g., employee, payment, approval, user)
  - audit/: audit service is here to keep write concerns together
- entity/, mapper/
  - Persisted domain objects (PO) and MyBatis‑Plus mappers (temporary location; see Migration)
- adapter/
  - External platform adapters (e.g., WeChat), can be considered an “interfaces/adapter” slice

## Layering Model
- Interfaces (input/output): Web controllers, DTO/VO, and external callbacks
- Application (optional): Use cases that orchestrate multiple services; currently merged into module services
- Domain/Modules: Core business services, policies, aggregates; placed under `modules/<feature>`
- Infrastructure: DB mappers, clients, integration details; currently `mapper/`, future `infrastructure/dao` and `infrastructure/client`
- Common/Cross‑cutting: configuration, exception handling, response, utils

## Module Anatomy (example: employee)
- interfaces/controller/employee/EmployeeController.java
- interfaces/dto/employee/* (requests)
- interfaces/vo/employee/* (responses, masked/transformed)
- modules/employee/service/EmployeeService.java (interface)
- modules/employee/service/impl/EmployeeServiceImpl.java (business logic)

Guidelines
- Controller returns VO only (never entities). Mask or translate sensitive fields.
- Services expose intent‑oriented methods (create, bindPlatformUser, batchImport, …) and enforce invariants.
- Reuse common exceptions; let GlobalExceptionHandler shape API errors.

## Migration Plan (incremental)
1) Move mappers to infrastructure/dao
   - Package: `com.yiyundao.compensation.infrastructure.dao` (per module subpackages allowed)
   - Update `@MapperScan` in common/config/MyBatisPlusConfig
2) Move persisted entities to module PO or `domain` (choose per team preference)
   - If placed under `modules/<feature>/entity`, set `mybatis-plus.type-aliases-package`
3) Adapters
   - Consider relocating adapter/* to interfaces/adapter/*; keep external client code under infrastructure/client
4) Configuration remains in common/config; shared helpers in common/utils

## Naming & Conventions
- Packages: lower case. Classes: UpperCamelCase. Methods/fields: lowerCamelCase.
- Suffix: *Controller, *Service, *ServiceImpl, *Mapper, *VO, *Request
- Security: prefer method security (`@PreAuthorize`) for sensitive endpoints (e.g., decrypt APIs)
- Sorting/filters: whitelist fields in services; avoid passing raw column names from clients

## Add a New Feature Module
1) Create `modules/<feature>/service` (+impl) with interfaces; write business logic there
2) Add controller under `interfaces/controller/<feature>` and DTO/VO under `interfaces/dto|vo/<feature>`
3) If persistence needed, add Mapper (temporary in mapper/) and Entity (temporary in entity/); plan migration to infrastructure/dao and module PO
4) Add tests and update README’s “常用端点”
