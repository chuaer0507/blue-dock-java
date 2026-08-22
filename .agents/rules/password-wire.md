---
description: 密码传输 — 凡 wire 上的 password 必须 RSA-OAEP 加密；禁止响应回传
globs: "**/*Service.java,**/*Controller.java,**/dto/**"
alwaysApply: false
---

# 密码铁律

## 1. 请求：RSA 传输

**凡 HTTP/WS 请求体字段 `password` 均为 RSA-OAEP-SHA256 密文 + `keyId`。** 禁止明文上送；禁止服务端对 wire password 直接 BCrypt / 落库而不经解密器。（旧名 `keyId` 已废止，见 [naming.md](../../docs/contract/naming.md)）

## 2. 响应：禁止回传 password

**凡成功响应的 `data`（列表、详情、create/update 回显）不得出现 `password` / `passwordHash`。**

| 场景 | 允许 | 禁止 |
| ---- | ---- | ---- |
| 用户资料 | （无密码字段） | `password`、`passwordHash` |
| 登录成功 | token / 用户公开信息 | 密码明文或 hash |

## 3. 验密：服务端执行

- 登录 / 改密：服务端解密后 BCrypt 比对
- **禁止**客户端本地比对密码 hash

## 反模式

```java
// ❌ 响应回传 password
return ResultModel.ok(userEntity); // entity 含 passwordHash

// ❌ 明文落库 wire password
user.setPassword(request.getPassword());
```

契约定稿见 [docs/modules/user-account/auth-wire.md](../../docs/modules/user-account/auth-wire.md) 与 [docs/contract/api-contract.md](../../docs/contract/api-contract.md)。

算法 RSA-OAEP-SHA256、失败阈值验证码、`keyId` 失效重拉公钥；路径用 task 的 `api/users/*`。
