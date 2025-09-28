# Employee Management API Documentation

## Overview
The Employee Management module provides comprehensive APIs for managing employee data in the Compensation Assistant System, including CRUD operations, platform integration, and secure access to sensitive information.

**Base Path**: `/api/employee`

**Authentication**: Bearer JWT Token required for all endpoints

---

## Table of Contents
1. [Core CRUD Operations](#core-crud-operations)
2. [Query & Search](#query--search)
3. [Status Management](#status-management)
4. [Platform Integration](#platform-integration)
5. [Sensitive Data Access](#sensitive-data-access)
6. [Batch Operations](#batch-operations)
7. [Data Models](#data-models)
8. [Security & Permissions](#security--permissions)

---

## Core CRUD Operations

### Create Employee
Creates a new employee record in the system.

**Endpoint**: `POST /api/employee`

**Request Body**:
```json
{
  "employeeId": "EMP001",           // Required: Unique employee ID
  "name": "张三",                   // Required: Employee name
  "phone": "13812345678",          // Optional: Phone number
  "email": "zhangsan@company.com", // Optional: Email address
  "idCard": "110101199001011234",  // Optional: ID card (will be encrypted)
  "department": "技术部",           // Optional: Department
  "position": "高级工程师",         // Optional: Position
  "platformUserId": "wx123456",    // Optional: Platform user ID
  "platformType": "wechat",        // Optional: Platform type (wechat/dingtalk/feishu)
  "managerId": 100,                // Optional: Manager's employee ID
  "hireDate": "2024-01-15",        // Optional: Hire date (YYYY-MM-DD)
  "status": "active",              // Optional: Employee status (default: active)
  "bankAccount": "6222021234567890", // Optional: Bank account (will be encrypted)
  "bankName": "中国银行",           // Optional: Bank name
  "offline": false                 // Optional: Offline employee flag (default: false)
}
```

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "employeeId": "EMP001",
    "name": "张三",
    "phoneMasked": "138****5678",
    "email": "zhangsan@company.com",
    "department": "技术部",
    "position": "高级工程师",
    "platformUserId": "wx123456",
    "platformType": "wechat",
    "managerId": 100,
    "hireDate": "2024-01-15",
    "status": "active",
    "bankAccountMasked": null,
    "bankName": "中国银行",
    "offline": false,
    "createTime": "2024-01-15T10:30:00",
    "updateTime": "2024-01-15T10:30:00"
  }
}
```

### Update Employee
Updates an existing employee's information.

**Endpoint**: `PUT /api/employee/{id}`

**Path Parameters**:
- `id` (Long): Employee ID

**Request Body**:
```json
{
  "name": "张三",
  "phone": "13812345678",
  "email": "zhangsan@company.com",
  "idCard": "110101199001011234",
  "department": "技术部",
  "position": "高级工程师",
  "hireDate": "2024-01-15",
  "bankAccount": "6222021234567890",
  "bankName": "中国银行"
}
```

**Response**: Same as Create Employee response

### Get Employee Details
Retrieves detailed information for a specific employee.

**Endpoint**: `GET /api/employee/{id}`

**Path Parameters**:
- `id` (Long): Employee ID

**Response**: Same as Create Employee response

---

## Query & Search

### Employee List (Paginated)
Retrieves a paginated list of employees with optional filtering and sorting.

**Endpoint**: `GET /api/employee`

**Query Parameters**:
- `page` (int): Page number (default: 1)
- `size` (int): Page size (default: 10)
- `keyword` (string): Search keyword (matches name or employee ID)
- `department` (string): Filter by department
- `status` (string): Filter by status (active/inactive/suspended)
- `isOffline` (boolean): Filter offline employees
- `platformType` (string): Filter by platform type (wechat/dingtalk/feishu)
- `managerId` (Long): Filter by manager ID
- `sortBy` (string): Sort field (default: createTime)
- `order` (string): Sort order (asc/desc, default: desc)

**Request Example**:
```
GET /api/employee?page=1&size=20&keyword=张&department=技术部&status=active&sortBy=name&order=asc
```

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "records": [
      {
        "id": 1,
        "employeeId": "EMP001",
        "name": "张三",
        "phoneMasked": "138****5678",
        "department": "技术部",
        "status": "active",
        "offline": false
      }
    ],
    "total": 100,
    "current": 1,
    "size": 20
  }
}
```

### Offline Employees List
Retrieves a list of offline employees (not in organization structure).

**Endpoint**: `GET /api/employee/offline`

**Query Parameters**:
- `managerId` (Long, optional): Filter by specific manager

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "employeeId": "OFF001",
      "name": "李四",
      "department": "外部顾问",
      "offline": true,
      "managerId": 100
    }
  ]
}
```

---

## Status Management

### Update Employee Status
Updates the status of an employee.

**Endpoint**: `PATCH /api/employee/{id}/status`

**Path Parameters**:
- `id` (Long): Employee ID

**Request Body**:
```json
{
  "status": "inactive"  // active/inactive/suspended
}
```

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": null
}
```

---

## Platform Integration

### Bind Platform User
Binds an employee to a platform user account (WeChat/DingTalk/Feishu).

**Endpoint**: `POST /api/employee/{id}/bind-platform`

**Path Parameters**:
- `id` (Long): Employee ID

**Request Body**:
```json
{
  "platformUserId": "wx123456",  // Required: Platform user ID
  "platformType": "wechat"       // Required: wechat/dingtalk/feishu
}
```

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": null
}
```

---

## Sensitive Data Access

⚠️ **ADMIN Only**: These endpoints require ADMIN role and are logged for audit purposes.

### Decrypt ID Card
Retrieves the decrypted ID card number for an employee.

**Endpoint**: `GET /api/employee/{id}/id-card`

**Authorization**: `ROLE_ADMIN` required

**Path Parameters**:
- `id` (Long): Employee ID

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": "110101199001011234"
}
```

**Audit Log**: All access to this endpoint is logged with user, timestamp, and result.

### Decrypt Bank Account
Retrieves the decrypted bank account number for an employee.

**Endpoint**: `GET /api/employee/{id}/bank-account`

**Authorization**: `ROLE_ADMIN` required

**Path Parameters**:
- `id` (Long): Employee ID

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": "6222021234567890"
}
```

**Audit Log**: All access to this endpoint is logged with user, timestamp, and result.

---

## Batch Operations

### Batch Import Employees
Imports multiple employees in a single operation.

**Endpoint**: `POST /api/employee/batch-import`

**Request Body**:
```json
{
  "employees": [
    {
      "employeeId": "EMP001",
      "name": "张三",
      "phone": "13812345678",
      "department": "技术部",
      "position": "工程师"
    },
    {
      "employeeId": "EMP002",
      "name": "李四",
      "phone": "13987654321",
      "department": "产品部",
      "position": "产品经理"
    }
  ]
}
```

**Response**:
```json
{
  "code": 200,
  "message": "Success",
  "data": null
}
```

---

## Data Models

### Employee Entity
```typescript
interface Employee {
  id: number;                    // Primary key
  employeeId: string;            // Unique employee identifier
  name: string;                  // Employee name
  phone: string;                 // Phone number (encrypted in storage)
  email: string;                 // Email address
  encryptedIdCard: string;       // ID card (SM4 + AES encrypted)
  department: string;            // Department name
  position: string;              // Job position
  platformUserId: string;        // Platform user ID
  platformType: string;          // Platform type (wechat/dingtalk/feishu)
  offline: boolean;              // Offline employee flag
  managerId: number;             // Manager's employee ID
  hireDate: string;              // Hire date (YYYY-MM-DD)
  status: string;                // Employee status
  bankAccount: string;           // Bank account (encrypted in storage)
  bankName: string;              // Bank name
  createTime: string;            // Creation timestamp
  updateTime: string;            // Last update timestamp
}
```

### Employee Status Enum
- `active`: Active employee
- `inactive`: Inactive employee
- `suspended`: Suspended employee

### Platform Types
- `wechat`: Enterprise WeChat (企业微信)
- `dingtalk`: DingTalk (钉钉)
- `feishu`: Feishu (飞书)

---

## Security & Permissions

### Authentication
- All endpoints require a valid JWT Bearer token
- Token must be included in the Authorization header: `Authorization: Bearer <token>`

### Authorization Levels
1. **Authenticated User**: Basic employee operations (read, create, update)
2. **ADMIN Role**: Access to sensitive data decryption endpoints

### Data Security
- **Encryption**: ID cards and bank accounts are encrypted using SM4 + AES-256
- **Masking**: Phone numbers are automatically masked in API responses
- **Audit Logging**: All sensitive data access is logged for compliance

### Rate Limiting
- Standard rate limiting applies to all endpoints
- Sensitive data endpoints may have additional restrictions

---

## Error Handling

### Common Error Responses

**400 Bad Request**:
```json
{
  "code": 400,
  "message": "Validation failed",
  "data": null
}
```

**401 Unauthorized**:
```json
{
  "code": 401,
  "message": "Authentication required",
  "data": null
}
```

**403 Forbidden**:
```json
{
  "code": 403,
  "message": "Insufficient permissions",
  "data": null
}
```

**404 Not Found**:
```json
{
  "code": 404,
  "message": "Employee not found",
  "data": null
}
```

**500 Internal Server Error**:
```json
{
  "code": 500,
  "message": "Internal server error",
  "data": null
}
```

---

## Integration Notes

### Platform Synchronization
- Employee data can be synchronized from WeChat/DingTalk/Feishu platforms
- Use the Organization Sync APIs (see `docs/integration.md`) for bulk synchronization
- Platform binding enables approval workflow integration

### Approval Workflows
- Employees with platform bindings can participate in approval workflows
- Offline employees require manager assignment for approval routing

### Payment Processing
- Employee bank account information is required for salary payments
- Encrypted storage ensures payment data security
- See `docs/payment-api.md` for payment-related operations

---

*Last Updated: 2024-01-15*
*API Version: v1.0*