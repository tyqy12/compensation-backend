# Payment Module API Guide

This document outlines the minimal payment endpoints exposed by this service.
All paths are relative to the server context path `/api`.

## Endpoints

- GET `/payment/batch/{batchNo}`
  - Returns batch details (id, batchNo, status, totals, timestamps).

- GET `/payment/batch/{batchNo}/records?status=processing|success|failed|pending|cancelled`
  - Lists payment records in a batch; sanitized fields only (no recipient account/name).

- POST `/payment/batch/{batchNo}/start`
  - Triggers asynchronous batch transfer via Alipay. Returns immediately.

- GET `/payment/record/{id}`
  - Returns a single payment record (sanitized).

- POST `/payment/record/{id}/retry`
  - Retries a single transfer; returns mock tradeNo in dev if using simulated flow.

- GET `/payment/transfer-status?outBizNo=...`
  - Queries transfer status for a merchant order number.

- POST `/alipay/notify`
  - Alipay async notify callback endpoint. Returns `success` on handled, otherwise `failure`.

## Notes

- Security: `/alipay/notify` is publicly accessible (see SecurityConfig). Other payment endpoints require auth.
- Idempotency: Batch start is idempotent at service level through dedup keys in Redis.
- Status values are enum codes from `PaymentStatus`.
- This module uses `AlipayService` (mocked locally) to perform operations; integrate real SDK and signature verification for production.

## Example

```
POST /api/payment/batch/BATCH_20250101/start
GET  /api/payment/batch/BATCH_20250101
GET  /api/payment/batch/BATCH_20250101/records?status=processing
POST /api/payment/record/123/retry
GET  /api/payment/transfer-status?outBizNo=COMP_..._ABCD1234
```
