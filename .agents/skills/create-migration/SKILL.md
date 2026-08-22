---
name: create-migration
description: 创建 Flyway 数据库迁移脚本
---

# 创建 Flyway 迁移脚本

实现前阅读 `.agents/rules/database.md` 与 `docs/data/database.md`。

## 先看版本号（必做）

读根 `pom.xml` 的 `<version>`：

| 版本 | 行为 |
| ---- | ---- |
| **`1.0.0`** 或更早（含 `*-SNAPSHOT`） | **开发期**：**不要**新建 `V{n+1}`。直接改既有 `bluedock-boot/.../db/migration/V{n}__*.sql`，并同步 `docs/data/database.md`。 |
| **已上生产**且本轮已升到 **> `1.0.0`** | **生产后**：新建 additive `V{next}`；禁止改已发布脚本。 |

## 检查清单（仅生产后增量）

- [ ] 文件名 `V{version}__{snake_case_description}.sql`？
- [ ] 物理表名带 **`bluedock_` 前缀**？
- [ ] 时间用 `DATETIME(3)` UTC？
- [ ] 软删用 `deleted_at`？
- [ ] **additive-only**？
- [ ] 已更新 `docs/data/database.md`？

## 文件位置

```
bluedock-boot/src/main/resources/db/migration/V{next_version}__{description}.sql
```

## 建表模板

```sql
CREATE TABLE bluedock_example (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    deleted_at  DATETIME(3)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
