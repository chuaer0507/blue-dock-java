---
description: 在领域模块中创建 REST Controller
argument-hint: <module> <ResourceName> <api-prefix>
---

调用 [create-controller](../skills/create-controller/SKILL.md) 技能，在 `bluedock-$1/` 下创建 `$2Controller.java`。

- `$1`：领域模块短名（如 `project`）
- `$2`：资源 PascalCase 名（如 `Project`）
- `$3`：API 路径前缀（如 `/api/project`）

按 [api-contract.md](../rules/api-contract.md)；完成后按 [doc-sync.md](../rules/doc-sync.md) 更新 `docs/contract/api-contract.md`。
