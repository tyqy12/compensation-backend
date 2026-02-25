# File API

文件上传、下载和管理 API，支持本地存储和 MinIO 云存储。

Base Path: `/api/v1/files`

## 上传文件

### 单文件上传

- **POST** `/api/v1/files/upload?category=general`
- Content-Type: `multipart/form-data`
- Parameter: `file` (File)
- Parameter: `category` (String, optional) - 文件分类，如 `employee`, `id_card`, `salary_slip`
- Responses:
  - 200 OK: `{ code: 0, data: "http://..." }`
  - 400 Bad Request: 文件为空或类型不支持
  - 413 Payload Too Large: 文件大小超出限制

### 批量上传

- **POST** `/api/v1/files/upload/batch?category=general`
- Content-Type: `multipart/form-data`
- Parameter: `files` (File[])
- Responses:
  - 200 OK: `{ code: 0, data: ["url1", "url2", ...] }`

## 获取文件

### 获取文件 URL

- **GET** `/api/v1/files/url?fileKey=employee/2024/01/11/xxx.jpg`
- Responses:
  - 200 OK: `{ code: 0, data: "http://..." }`

## 删除文件

### 删除文件

- **DELETE** `/api/v1/files?fileKey=employee/2024/01/11/xxx.jpg`
- Responses:
  - 200 OK
  - 404 Not Found

## 支持的文件类型

| 类型 | 扩展名 |
|------|--------|
| 图片 | jpg, jpeg, png, gif |
| 文档 | pdf, doc, docx, xls, xlsx |
| 压缩 | zip |

## 存储配置

### 本地存储 (开发环境)

```yaml
file-storage:
  active: local
  local:
    base-path: /data/files
    base-url: http://localhost:8080/files
```

### MinIO 存储 (生产环境)

```yaml
file-storage:
  active: minio
  minio:
    endpoint: http://minio:9000
    bucket: compensation
    access-key: xxx
    secret-key: xxx
```

## 文件大小限制

- 默认最大文件大小: 10MB
- 可通过 `file-storage.max-file-size` 配置
