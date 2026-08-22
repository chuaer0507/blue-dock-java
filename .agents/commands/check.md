---
description: 运行全部代码检查（compile / test / 架构抽查）
---

按 skill [check](../skills/check/SKILL.md) 执行：

```bash
mvn clean compile
mvn test
mvn -q dependency:tree
```

通过后按 [architecture.md](../rules/architecture.md) 核对依赖方向；按 [api-contract.md](../rules/api-contract.md) 抽查 Controller；按 [doc-sync.md](../rules/doc-sync.md) 确认文档已同步。
