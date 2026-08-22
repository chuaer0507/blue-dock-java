---
name: check
description: 运行 BlueDock 全仓编译、测试与架构 / 契约抽查
---

# 代码检查

在仓库根目录按顺序执行。任一步失败则停下修复后再继续。Maven 工程尚未落地时，跳过 mvn，改为核对 `docs/` 与 `.agents/rules/` 一致性。

## 1. 编译

```bash
mvn clean compile
```

## 2. 测试

```bash
mvn test
```

单模块：`mvn -pl bluedock-<name> -am test`。

## 3. 依赖树（可选）

```bash
mvn -q dependency:tree
```

## 4. 架构与契约抽查

对照 [architecture.md](../../rules/architecture.md)：

- 依赖方向：`bluedock-boot → bluedock-{domain} → bluedock-common`
- Worker（`bluedock-worker-*`）无 HTTP
- 跨模块不直接调 Mapper
- 跨域异步走 Kafka

对照 [api-contract.md](../../rules/api-contract.md) 抽查变更的 Controller：

- 路径与契约一致
- `ResultModel<T>`
- JSON camelCase

对照 [doc-sync.md](../../rules/doc-sync.md)：相关 `docs/` 是否已更新。
