---
description: 创建 Flyway 数据库迁移脚本
argument-hint: <description>
---

调用 [create-migration](../skills/create-migration/SKILL.md) 技能。先读根 `pom.xml` 版本（见 [database.md](../rules/database.md)）：

- **`1.0.0` 或更早**（含 SNAPSHOT）：**不要**新建文件——直接改既有 `V{n}__*.sql` 建表脚本。
- **正式 `1.0.0` 已上生产**且本轮已升到 **> `1.0.0`**：按规范新建 additive `V{n+1}`。

描述：`$ARGUMENTS`（snake_case，如 `add_projects_owner_index`）

完成后按 [doc-sync.md](../rules/doc-sync.md) 更新 `docs/data/database.md`。
