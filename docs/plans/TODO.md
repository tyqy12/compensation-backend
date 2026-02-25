# TODO (Rolling)

This list tracks near-term tasks for payroll M1–M2, focusing on avoiding local JSON files and keeping configs DB‑backed.

## Calculation & Templates
- ✅ Validate `items_json` and `tax_rule_json` on write (JSON schema), reject invalid payloads. (Done)
- ✅ Implement rule parser and calculator (earnings/deductions → gross/tax/social/net), all rules loaded from DB `salary_template`. (Done)
- ✅ Persist computed item breakdown in `payroll_line.items_snapshot_json` (source = DB template), no local resources. (Done)
- Add template versioning to ensure deterministic recompute. (Pending)

## Batch Lifecycle & Approval
- ✅ Guard edits by status (draft/locked/submitted only); prevent changes after approved/paid/archived. (Done)
- ✅ Integrate ApprovalEngine for `submit-approval` (admin bypass remains supported). (Done)
- Add audit trails for compute/lock/submit actions. (Pending)

## APIs & RBAC
- ✅ Register `/payroll/*` endpoints into `sys_resource` and grant roles (FIN/HR/MANAGER) accordingly. (Done)
- ✅ Replace Boolean responses for `/dry-run` with a preview DTO (lines, warnings, diffs). (Done)
- ✅ Add request validation for `scope_json` (schema-based), avoid local JSON files. (Done)

## Import & Manager Check (Next)
- CSV/Excel import endpoints (FT) with strict validation and preview, no local JSON dependencies. (Done)
- Manager diff view API (compare with last cycle, threshold rules from DB config). (Done)

## Testing & Ops (Updated 2026-01-11)
- ✅ Add unit tests for services (batch, calc, template); add integration tests with Testcontainers MySQL. (150+ tests added)
- ✅ Metrics/logging for calc time, batch sizes; alerts on calc/approval failures. (Done via Micrometer)
- ✅ Idempotency framework for payment endpoints. (Done)
- ✅ Sensitive data auto-masking. (Done)
- ✅ Task schedule management. (Done)
- ✅ File storage module (local + MinIO). (Done)

## System Improvements (2026-01-11)
- ✅ API versioning support. (Done)
- ✅ Unified response format (ApiResponse). (Done)
- ✅ JWT configuration encryption. (Done)
- ✅ Business metrics with Micrometer. (Done)
- ✅ Distributed tracing with Zipkin. (Done)
- ✅ Exception handling standardization. (Done)

## Future Tasks
- Admin UI for integration/bindings and audit viewers.
- Platform approval notifications and message sending.
- Performance testing for high-concurrency scenarios.
- Integration tests verification in production environment.
