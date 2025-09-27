# Approval Module API Guide

Base path: `/api/approval/workflows`

## Endpoints

- POST `/` — Start workflow
  - Body: `{ workflowType: 'BATCH|ADHOC|OFFLINE', businessKey: string, businessType: string, initiatorId: number, workflowData?: object }`
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `approval:start`
  - Returns: workflow id (number)

- POST `/{id}/approve` — Approve workflow
  - Body: `{ approverId: number, comment?: string }`
  - Auth: `ROLE_ADMIN|ROLE_MANAGER|ROLE_APPROVER` or `approval:approve`

- POST `/{id}/reject` — Reject workflow
  - Body: `{ approverId: number, comment?: string }`
  - Auth: `ROLE_ADMIN|ROLE_MANAGER|ROLE_APPROVER` or `approval:reject`

- POST `/{id}/cancel` — Cancel workflow (initiator only)
  - Body: `{ operatorId: number, reason?: string }`
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `approval:cancel`

- GET `/pending?approverId=...` — My pending workflows
  - Auth: `ROLE_ADMIN|ROLE_MANAGER|ROLE_APPROVER` or `approval:read`
  - Returns: `ApprovalWorkflowVO[]`

- GET `/my?initiatorId=...` — Workflows I started
  - Auth: authenticated
  - Returns: `ApprovalWorkflowVO[]`

- GET `/{id}` — Workflow detail + data
  - Auth: `ROLE_ADMIN|ROLE_MANAGER|ROLE_APPROVER` or `approval:read`
  - Returns: `{ workflow: ApprovalWorkflowVO, data: object }`

- GET `/{id}/steps` — Steps
  - Auth: `ROLE_ADMIN|ROLE_MANAGER|ROLE_APPROVER` or `approval:read`
  - Returns: `ApprovalStepVO[]`

## VO Shapes

- ApprovalWorkflowVO
```
{ id, workflowName, workflowType, workflowTypeName, businessKey, businessType,
  currentStep, totalSteps, status, statusName, initiatorId, currentApproverId,
  submitTime, completeTime }
```

- ApprovalStepVO
```
{ id, stepNo, stepName, approverId, approverName, status, statusName,
  approveComment, rejectReason, timeoutHours, approveTime }
```

## Notes
- Security enforced via `@PreAuthorize`; supply roles or `authorities` in JWT claims.
- Status enums: workflow/step use `ApprovalStatus` codes: `pending|approved|rejected|cancelled|skipped`.
- Types: `WorkflowType` codes: `BATCH|ADHOC|OFFLINE`.

