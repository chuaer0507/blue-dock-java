---
description: REST/WS JSON 字段一律 camelCase，禁止 snake_case wire key
globs: "**/*.{java,ts,tsx,md}"
alwaysApply: true
---

# JSON 命名（camelCase · 禁止简写）

API 请求体、响应 `data`、WebSocket 帧 `data` 内业务字段 **一律 camelCase 全词**，与契约字段名一致。

**绝对不要简写单词**（禁止 `userId`/`userImage`/`telephone`/`parentId`/`fileId`/`body`/`keyId`/`priorityLevel` 等）。完整词表与白名单见 [docs/contract/naming.md](../../docs/contract/naming.md)。

## 铁律

| 层 | 命名 |
| -- | ---- |
| JSON wire（HTTP body / WS `data`） | **camelCase 全词**：`pageSize`、`projectId`、`userId`、`userImage`、`priorityLevel` |
| Java / TS 属性 | 与 JSON **同形**（**禁止** `@JsonProperty` 把简写挂到全词字段上当别名） |
| Query 参数 | **camelCase 全词**（`page`、`pageSize`、`userId`） |
| 物理表 / 列 / Redis key 段 | snake_case **全词**（见 [database.md](database.md)、[redis.md](redis.md)） |
| Kafka Topic | 点分小写（见 [messaging.md](messaging.md)） |
| WS 帧根 `type` | SCREAMING_SNAKE（业务事件名，例外） |

## 分页

列表响应建议：

```json
{
  "items": [],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "totalSize": 100,
    "totalPage": 5
  }
}
```

禁止：顶层 `page`/`pageSize`；用 `list` 代替 `items`；在 `meta` 内塞业务扩展字段。

## 敏感字段（禁止读响应 wire）

**`password` 不得出现在任何读路径 JSON**（含 `""` / `null`）。写请求可带 RSA 密文 `password` + `keyId`。见 [password-wire.md](password-wire.md)。

## 反模式

```java
// ❌
@JsonProperty("page_size") int pageSize
@JsonProperty("project_id") String projectId

// ✅
int pageSize;
String projectId;
```

## 唯一例外

- **第三方协议原文**可保留其固有 key，并在模型旁注释「第三方 wire」。
- **WS 帧根 `type`** 为 SCREAMING_SNAKE，`data` 内仍为 camelCase。

## 文档

禁止简写词表：[docs/contract/naming.md](../../docs/contract/naming.md)。  
契约见 [docs/contract/api-contract.md](../../docs/contract/api-contract.md)；领域命名见 [docs/contract/domain-naming.md](../../docs/contract/domain-naming.md)。改 JSON 形态须同步前端 `packages/shared`。
