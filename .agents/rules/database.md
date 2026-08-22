---
description: MySQL 命名、Flyway 迁移
globs: "**/db/**,**/*Mapper.java,**/*.sql,**/entity/**"
alwaysApply: false
---

# 数据库规则

详见 [docs/data/database.md](../../docs/data/database.md)。

## 命名规范

| 规则 | 示例 |
| ---- | ---- |
| 物理表名 | **`bluedock_` 前缀** + snake_case：`bluedock_projects`、`bluedock_tasks`、`bluedock_dialogs` |
| 逻辑表名（代码） | `@TableName("projects")`；MyBatis-Plus 全局 `table-prefix: bluedock_` |
| 业务主键 | `id` **UUID v7** 或 BIGINT（定稿见 id-generation）；API 与文档一致 |
| 外键 | `{entity}_id`：`project_id`、`user_id`、`dialog_id` |
| 列名 | **全词 snake_case**，禁止简写（`telephone` 非 `telephone`；`message_id` 非 `message_id`；`key_id` 非 `keyId`）。词表见 [naming.md](../../docs/contract/naming.md) |
| 枚举 | VARCHAR 存 `.name` |
| 软删 | `deleted_at` NULL 表示有效 |

## 时间

- 时间：UTC 存 `DATETIME(3)`

## 迁移（Flyway）

路径：`bluedock-boot/src/main/resources/db/migration/V{n}__{description}.sql`

| 阶段 | 判定 | 改表怎么做 |
| ---- | ---- | ---------- |
| **开发阶段** | 版本为 **`1.0.0`**（含 SNAPSHOT；或 `0.x`） | **直接改**既有 `V{n}__*.sql`，不要另开 ALTER 版 |
| **上生产后** | 正式 `1.0.0` 已在生产跑过 | **先升版本号**；只新增 additive `V{n+1}`，禁止改已发布脚本 |

同步更新 `docs/data/database.md`。

## 反模式

```sql
-- ❌ 物理表无前缀 / 金额用 DOUBLE（若有金额字段）
CREATE TABLE projects (...);

-- ✅
CREATE TABLE bluedock_projects (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
