# SQL 脚本使用边界

## 目录职责

- `schema.sql`：全量建表脚本（包含 `DROP TABLE IF EXISTS`），仅用于全新空库初始化。
- `init_clean.sql`：清库并重建脚本（包含批量 `DROP TABLE`），仅用于一次性本地重置或演示环境重置。
- `migrations/*.sql`：增量迁移脚本，用于已有数据库的结构演进和数据修复。
- `seed/*.sql`：初始化/补充种子数据脚本。

## 执行原则

1. 任何已有业务数据的环境（开发共享库、测试库、预发、生产）禁止执行 `schema.sql` 与 `init_clean.sql`。
2. 已上线或长期运行环境只允许执行 `migrations/*.sql` 的增量变更。
3. 新增数据库变更时优先提交 migration，不在 `schema.sql` 上做“仅线上生效”的临时修补。
