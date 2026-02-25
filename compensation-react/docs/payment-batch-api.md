# Payment Batch Management API Documentation

## Overview

The Payment Batch Management module provides APIs for managing batch payment operations in the Compensation Assistant System, including batch list, details, and payment record management for Alipay bulk transfers.

**Base Path**: `/api/payment`

**Authentication**: Bearer JWT Token required for all endpoints

---

## Table of Contents

1. [Payment Batch Operations](#payment-batch-operations)
2. [Payment Record Operations](#payment-record-operations)
3. [Transfer Status Query](#transfer-status-query)
4. [Data Models](#data-models)
5. [Enums & Status Codes](#enums--status-codes)
6. [Security & Permissions](#security--permissions)

---

## Payment Batch Operations

### Get Batch List (Paginated)

Retrieves a paginated list of payment batches with optional filtering.

**Endpoint**: `GET /api/payment/batch`

**Query Parameters**:

- `page` (int): Page number (default: 1)
- `size` (int): Page size (default: 10)
- `status` (string): Filter by batch status (draft/submitted/approved/processing/completed/failed)
- `paymentType` (string): Filter by payment type (salary/bonus/reimbursement)
- `startDate` (string): Filter by submit date start (YYYY-MM-DD)
- `endDate` (string): Filter by submit date end (YYYY-MM-DD)
- `keyword` (string): Search keyword (matches batch name or batch number)
- `sortBy` (string): Sort field (default: submitTime)
- `order` (string): Sort order (asc/desc, default: desc)

**Request Example**:

```
GET /api/payment/batch?page=1&size=20&status=processing&paymentType=salary&sortBy=submitTime&order=desc
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
        "batchNo": "BATCH_20240115001",
        "batchName": "2024年1月工资发放",
        "paymentType": "salary",
        "totalAmount": 1500000.0,
        "totalCount": 150,
        "successCount": 148,
        "failedCount": 2,
        "status": "processing",
        "submitTime": "2024-01-15T09:00:00",
        "approveTime": "2024-01-15T10:30:00",
        "processStartTime": "2024-01-15T14:00:00",
        "processEndTime": null,
        "remark": "月度工资批量发放"
      }
    ],
    "total": 50,
    "current": 1,
    "size": 20
  }
}
```

### Get Batch Details

Retrieves detailed information for a specific payment batch.

**Endpoint**: `GET /api/payment/batch/{batchNo}`

**Path Parameters**:

- `batchNo` (string): Batch number

**Request Example**:

```
GET /api/payment/batch/BATCH_20240115001
```

**Response**:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "batchNo": "BATCH_20240115001",
    "batchName": "2024年1月工资发放",
    "paymentType": "salary",
    "totalAmount": 1500000.0,
    "totalCount": 150,
    "successCount": 148,
    "failedCount": 2,
    "status": "processing",
    "submitTime": "2024-01-15T09:00:00",
    "approveTime": "2024-01-15T10:30:00",
    "processStartTime": "2024-01-15T14:00:00",
    "processEndTime": null,
    "remark": "月度工资批量发放"
  }
}
```

### Get Batch Payment Records

Retrieves payment records for a specific batch with optional status filtering.

**Endpoint**: `GET /api/payment/batch/{batchNo}/records`

**Path Parameters**:

- `batchNo` (string): Batch number

**Query Parameters**:

- `status` (string, optional): Filter by payment status (pending/processing/success/failed/cancelled)

**Request Example**:

```
GET /api/payment/batch/BATCH_20240115001/records?status=failed
```

**Response**:

```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1001,
      "batchNo": "BATCH_20240115001",
      "paymentType": "salary",
      "amount": 8500.0,
      "currency": "CNY",
      "status": "failed",
      "alipayOrderNo": "COMP_20240115_1001",
      "alipayTradeNo": null,
      "errorCode": "PAYEE_NOT_EXIST",
      "errorMsg": "收款方账户不存在",
      "paymentTime": null,
      "notificationTime": null
    },
    {
      "id": 1002,
      "batchNo": "BATCH_20240115001",
      "paymentType": "salary",
      "amount": 12000.0,
      "currency": "CNY",
      "status": "failed",
      "alipayOrderNo": "COMP_20240115_1002",
      "alipayTradeNo": null,
      "errorCode": "INSUFFICIENT_BALANCE",
      "errorMsg": "余额不足",
      "paymentTime": null,
      "notificationTime": null
    }
  ]
}
```

### Start Batch Transfer

Initiates asynchronous batch transfer processing for Alipay.

**Endpoint**: `POST /api/payment/batch/{batchNo}/start`

**Path Parameters**:

- `batchNo` (string): Batch number

**Request Example**:

```
POST /api/payment/batch/BATCH_20240115001/start
```

**Response**:

```json
{
  "code": 200,
  "message": "批量转账已启动",
  "data": null
}
```

**Notes**:

- This operation is asynchronous - the response indicates the transfer has been queued
- Use batch details endpoint to monitor progress
- Idempotent operation - multiple calls won't create duplicate transfers

---

## Payment Record Operations

### Get Payment Record Details

Retrieves detailed information for a specific payment record.

**Endpoint**: `GET /api/payment/record/{id}`

**Path Parameters**:

- `id` (Long): Payment record ID

**Request Example**:

```
GET /api/payment/record/1001
```

**Response**:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1001,
    "batchNo": "BATCH_20240115001",
    "paymentType": "salary",
    "amount": 8500.0,
    "currency": "CNY",
    "status": "success",
    "alipayOrderNo": "COMP_20240115_1001",
    "alipayTradeNo": "2024011522000123456789",
    "errorCode": null,
    "errorMsg": null,
    "paymentTime": "2024-01-15T14:05:23",
    "notificationTime": "2024-01-15T14:05:25"
  }
}
```

### Retry Failed Payment

Retries a failed payment record.

**Endpoint**: `POST /api/payment/record/{id}/retry`

**Path Parameters**:

- `id` (Long): Payment record ID

**Request Example**:

```
POST /api/payment/record/1001/retry
```

**Response**:

```json
{
  "code": 200,
  "message": "重试成功: 2024011522000123456790",
  "data": null
}
```

**Error Response**:

```json
{
  "code": 500,
  "message": "重试失败: 收款方账户不存在",
  "data": null
}
```

---

## Transfer Status Query

### Query Transfer Status

Queries the status of a transfer using the merchant order number.

**Endpoint**: `GET /api/payment/transfer-status`

**Query Parameters**:

- `outBizNo` (string): Merchant order number

**Request Example**:

```
GET /api/payment/transfer-status?outBizNo=COMP_20240115_1001
```

**Response**:

```json
{
  "code": 200,
  "message": "Success",
  "data": "success"
}
```

**Possible Status Values**:

- `pending`: 待处理
- `processing`: 处理中
- `success`: 成功
- `failed`: 失败
- `cancelled`: 已取消

---

## Data Models

### PaymentBatch

```typescript
interface PaymentBatch {
  id: number; // Primary key
  batchNo: string; // Unique batch number (e.g., BATCH_20240115001)
  batchName: string; // Batch display name
  paymentType: string; // Payment type code (salary/bonus/reimbursement)
  totalAmount: number; // Total amount in batch
  totalCount: number; // Total number of payments
  successCount: number; // Number of successful payments
  failedCount: number; // Number of failed payments
  status: string; // Batch status code
  remark: string; // Additional remarks
  submitTime: string; // Submission timestamp
  approveTime: string; // Approval timestamp
  processStartTime: string; // Processing start timestamp
  processEndTime: string; // Processing end timestamp
}
```

### PaymentRecord

```typescript
interface PaymentRecord {
  id: number; // Primary key
  batchNo: string; // Associated batch number
  paymentType: string; // Payment type code
  amount: number; // Payment amount
  currency: string; // Currency code (CNY)
  status: string; // Payment status code
  alipayOrderNo: string; // Merchant order number
  alipayTradeNo: string; // Alipay transaction number
  errorCode: string; // Error code (if failed)
  errorMsg: string; // Error message (if failed)
  paymentTime: string; // Payment completion timestamp
  notificationTime: string; // Notification received timestamp
}
```

---

## Enums & Status Codes

### Batch Status (`BatchStatus`)

- `draft`: 草稿 - Batch created but not submitted
- `submitted`: 已提交 - Submitted for approval
- `approved`: 已审批 - Approved and ready for processing
- `processing`: 处理中 - Currently being processed
- `completed`: 已完成 - All payments completed successfully
- `failed`: 失败 - Batch processing failed

### Payment Status (`PaymentStatus`)

- `pending`: 待处理 - Payment created but not processed
- `processing`: 处理中 - Payment being processed by Alipay
- `success`: 成功 - Payment completed successfully
- `failed`: 失败 - Payment failed
- `cancelled`: 已取消 - Payment cancelled

### Payment Type (`PaymentType`)

- `salary`: 工资 - Regular salary payments
- `bonus`: 奖金 - Bonus payments
- `reimbursement`: 报销 - Expense reimbursements

---

## Security & Permissions

### Authentication

- All endpoints require a valid JWT Bearer token
- Token must be included in the Authorization header: `Authorization: Bearer <token>`

### Authorization

- Payment operations typically require `MANAGER` or `ADMIN` roles
- Sensitive financial data access may have additional restrictions
- Audit logging is enabled for all payment operations

### Data Security

- Payment records do not include recipient account details in API responses
- All financial operations are logged for compliance
- Transfer amounts and sensitive data are handled securely

### Idempotency

- Batch start operations are idempotent (safe to retry)
- Duplicate batch numbers are prevented at the service level
- Redis-based deduplication for payment operations

---

## Integration Notes

### Frontend Integration

For frontend payment batch list and details integration:

1. **List View**: Use `GET /api/payment/batch` with pagination and filtering
2. **Detail View**: Use `GET /api/payment/batch/{batchNo}` for batch overview
3. **Records View**: Use `GET /api/payment/batch/{batchNo}/records` for detailed records
4. **Real-time Updates**: Consider polling batch details for status updates during processing

### Alipay Integration

- Uses Alipay Open Platform SDK for bulk transfers
- Supports up to 1000 transfers per batch
- Async notification handling via webhook (`POST /api/alipay/notify`)
- Production requires valid Alipay merchant credentials

### Error Handling

- Failed payments can be retried individually
- Batch failures require investigation and potential resubmission
- Comprehensive error codes and messages for troubleshooting

### Performance Considerations

- Large batches (1000+ records) may take several minutes to process
- Use pagination for batch record lists
- Consider implementing WebSocket for real-time status updates

---

## Common Workflows

### Typical Batch Processing Flow

1. Create batch (via admin interface or import)
2. Submit for approval (`status: submitted`)
3. Approve batch (`status: approved`)
4. Start processing (`POST /batch/{batchNo}/start`)
5. Monitor progress (`status: processing`)
6. Handle failures (retry individual records)
7. Complete batch (`status: completed`)

### Error Recovery

1. Identify failed records (`GET /batch/{batchNo}/records?status=failed`)
2. Review error codes and messages
3. Fix underlying issues (recipient accounts, etc.)
4. Retry failed records (`POST /record/{id}/retry`)
5. Monitor retry results

---

_Last Updated: 2024-01-15_
_API Version: v1.0_
