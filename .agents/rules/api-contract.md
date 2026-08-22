---
description: API 契约 — REST 路径、ResultModel、与前端 shared 对齐
globs: "**/*Controller.java,**/controller/**"
alwaysApply: false
---

# API 契约规则

唯一契约来源：[docs/contract/api-contract.md](../../docs/contract/api-contract.md)。客户端经契约对齐，不靠猜字段。

## 路由规范

```java
@RestController
@RequestMapping("/api/project")
public class ProjectController {
  // 一资源一 prefix；禁止多路径别名
}
```

- 业务路径与契约表一致（如 `/api/project`、`/api/dialog`、`/api/file`）
- **路径唯一**：每个接口只有一个 prefix，禁止 `@RequestMapping` 多路径或历史别名
- 路由细则见 [docs/contract/api-routing.md](../../docs/contract/api-routing.md)

## 响应格式

统一 `ResultModel<T>`。

**JSON 字段名一律 camelCase**。禁止 `@JsonProperty("snake_case")`。细则见 [json-naming.md](json-naming.md)。

**`message` 多语言**：业务失败文案按 `Accept-Language` 返回 zh 或 en；抛错须用 `I18nKeys`，禁止硬编码。细则见 [i18n.md](i18n.md)。

```java
return ResultModel.ok(data);
return ResultModel.fail(StatusCodeEnum.PROJECT_NOT_FOUND);
```

## 请求头（必须识别）

| Header | 用途 |
| ------ | ---- |
| `Authorization` | `Bearer <accessToken>` |
| `Accept-Language` | 错误文案国际化（`zh` / `en`；细则 [i18n.md](i18n.md)） |
| `X-Request-ID` | 链路追踪 |
| `X-Device-ID` | 设备会话（可选） |

## 鉴权白名单

登录 / 注册 / 验证码 / 公开分享链接等匿名路径以契约为准；其余需 access JWT。

## 密码

- **请求**：body 含 `password` 须 RSA-OAEP + `keyId`（见 [password-wire.md](password-wire.md)）
- **响应**：**禁止** `data` 回传 `password` / `passwordHash`

## 反模式

```java
// ❌ 路径别名 / 破坏契约
@GetMapping({"/lists", "/list"})

// ❌ 返回裸对象，无 ResultModel
@GetMapping("/all")
public List<Project> list() { ... }

// ❌ 响应 data 带 password
return ResultModel.ok(userWithPassword);
```
