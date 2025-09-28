# API Client & Contracts

## ApiResponse Shape
Backend returns:
```
interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}
```
Treat `code===200` as success.

## axios instance (services/api.ts)
- Exports `api` configured with baseURL and interceptors (see auth.md)
- Helper `unwrap<T>()` to transform ApiResponse<T> → T, throwing on non‑200

## Query Keys (TanStack Query)
```
export const qk = {
  integration: (platform: string) => ['integration', platform] as const,
  platforms: ['org', 'platforms'] as const,
  orgCheck: (platform: string) => ['org', 'check', platform] as const,
  employees: (params) => ['employees', params] as const,
  paymentBatch: (batchNo) => ['paymentBatch', batchNo] as const,
};
```

## Endpoints Mapping
- Auth
  - POST `/api/auth/login`, GET `/api/auth/oauth/authorize`, GET `/api/auth/oauth/callback/{platform}`
  - POST `/api/auth/refresh`, POST `/api/auth/logout`
- Integration (Admin)
  - GET `/api/system/integration/{platform}`
  - PUT `/api/system/integration/{platform}`
  - POST `/api/system/integration/{platform}/test-connection`
- Org Sync
  - POST `/api/system/org/sync?platform=...|all`
  - GET `/api/system/org/platforms`
  - GET `/api/system/org/check?platform=...`
- Admin Binding (Admin)
  - GET/PUT/DELETE `/api/admin/users/{id}/platform-binding`
- Employees
  - GET `/api/employee` (+ filters)
  - POST `/api/employee`, PUT `/api/employee/{id}`
- Payments
  - GET `/api/payment/batch/{batchNo}`
  - GET `/api/payment/batch/{batchNo}/records`
  - POST `/api/payment/batch/{batchNo}/start`

## Types (types/api.ts)
Define DTOs mirrored to backend minimal fields used by UI.

