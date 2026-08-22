---
description: 在领域模块中创建 Service 层
argument-hint: <module> <ServiceName>
---

调用 [create-service](../skills/create-service/SKILL.md) 技能，在 `bluedock-$1/` 下创建 `$2Service`（及配套 Mapper / Entity 若需要）。

- `$1`：领域模块短名（如 `project`）
- `$2`：服务 PascalCase 名（如 `Project`）

完成后按 [doc-sync.md](../rules/doc-sync.md) 视情况更新 `docs/architecture/services.md`。
