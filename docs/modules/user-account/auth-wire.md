# 登录密码加密与图形验证码

鉴权铁律：RSA-OAEP + 失败阈值验证码；路径保持既有 `api/users/*`（向前兼容）。

**实现状态**：**已落地**（`bluedock-auth`：`bluedock_auth_key_pairs` + RSA-OAEP-SHA256 + needCode/codeJson/codeImage + 失败阈值验证码）。

**关联**：[password-wire.md](../../../.agents/rules/password-wire.md) · [api-contract.md](../../contract/api-contract.md) · [redis.md](../../data/redis.md)

---

## 1. 目标

| 项 | 约定 |
| -- | ---- |
| 密码 wire | **凡请求字段 `password` 均为 RSA-OAEP-SHA256 密文**，且同传 **`keyId`**；禁止明文上送 |
| 公钥 | 客户端先取公钥再加密；`keyId` 失效返回专用错误码，清缓存重试 |
| 验证码 | 登录失败达阈值后强制；图形码存 Redis TTL **5min**；一次性消费 |
| 响应 | **禁止**回传 `password` / `passwordHash`（仅公开用户视图） |
| 路径 | 保留：`/api/users/login*`、`/api/users/key/client` 等 |

---

## 2. API（wire camelCase）

### 2.1 公钥

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/users/key/client` | 匿名 | 返回 `{ keyId, publicKey, algorithm: "RSA-OAEP-SHA256" }` |

- 服务端密钥对：MySQL `bluedock_auth_key_pairs` + Redis 缓存 `bluedock:auth:pubkey:{keyId}`（TTL 1h）；启动时若无 active 密钥则自动生成 RSA-2048。
- 算法：**RSA-OAEP-SHA256**（非 PKCS1 v1.5）。

### 2.2 是否需要验证码

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/users/login/needCode` | 匿名 | `{ need: boolean }`；按客户端 IP 读失败计数 |

### 2.3 图形验证码

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/users/login/codeJson` | 匿名 | **推荐**；`{ key, imageBase64 }`（`imageBase64` 带 `data:image/png;base64,` 前缀） |
| GET | `/api/users/login/codeImage` | 匿名 | PNG 流；`Set-Cookie: bluedock_captcha_key` + 响应头 `X-Captcha-Key` |

- Redis：`bluedock:auth:captcha:{key}` → 答案字符串，TTL **5 min**，校验成功后删除。
- 答案比对：**忽略大小写**。

### 2.4 登录

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET/POST | `/api/users/login` | 匿名 | 见下表；成功 `{ token, refreshToken, user }`（user 无 password） |

| 字段 | 必填 | 说明 |
| ---- | ---- | ---- |
| `email` | 是 | 登录账号（邮箱） |
| `password` | 是 | **RSA 密文**（不再接受明文） |
| `keyId` | 是 | 公钥版本 |
| `captchaCode` / `code` | 条件 | 当 `needCode=true` 或服务端判定需验证码时必填 |
| `captchaKey` / `codeKey` | 条件 | 与 `codeJson` 的 `key` 对应；`codeImage` 也可读 Cookie `bluedock_captcha_key` |

**传参方式**：

- **POST 推荐**：`Content-Type: application/json`，字段放 JSON body（camelCase）
- **兼容**：GET query；POST `application/x-www-form-urlencoded` / query

**推荐字段**：`captchaKey` + `captchaCode`；同时接受历史 `code` / `codeKey` 作别名（**仅入参别名**，不增加第二路径）。

服务端：`WirePasswordResolver` 按 `keyId` 解密 → BCrypt 比对（LDAP 场景解密后明文送目录）。

### 2.5 改密 / 管理员建用户

凡写接口带 `password` 的（`editPassword`、`createUser`、导入初始密码等）同样 **`password` + `keyId`**，同一套公钥（接口落地时复用）。

---

## 3. 错误码与客户端行为

| 语义 | code | message key | 客户端 |
| ---- | ---- | ----------- | ------ |
| 需验证码 | `-3`（`ErrorCodes.CAPTCHA_REQUIRED`） | `auth.captcha_required` | 展示验证码并带上 key/code 重试 |
| 公钥失效 | `-11`（`ErrorCodes.PUBLIC_KEY_INVALID`） | `auth.public_key_invalid` | 清公钥缓存，重新 `key/client` 后重试 |
| 账密错误 | `1100` | `auth.failed` | 累加失败计数；达阈值后下次强制验证码 |
| 验证码错误 | `1100` | `auth.captcha_invalid` | 刷新验证码 |

登录失败计数：

| Key | TTL | 说明 |
| --- | --- | ---- |
| `bluedock:auth:login:fail:{ip}` | 15 min | 失败次数；阈值 **≥3** |

成功登录：清除该 IP 失败计数。

---

## 4. 落地说明

| 组件 | 位置 |
| ---- | ---- |
| 表 | `bluedock_auth_key_pairs`（V1） |
| 公钥 | `GET /api/users/key/client` → `PublicKeyService` |
| 解密 | `WirePasswordResolver` / `RsaPasswordDecryptor` |
| 验证码 | `CaptchaService` + `needCode` / `codeJson` / `codeImage` |
| 登录 | `AuthService.login`：解密 → 阈值验证码 → BCrypt/LDAP |

仍待：批量导入等写密接口落地时复用同一套 `password`+`keyId`；前端 `packages/shared` 同步登录/改密/建用户请求类型。

`editPassword` / `createUser` / `import`：**已落地**（RSA + `keyId`；LDAP 回写见 [ldap.md](../../infra/ldap.md)；导入确认行须加密 password）。

---

## 5. 验收清单

- [x] 无 `keyId` / 密文缺失 → 拒绝
- [x] `keyId` 失效 → `code=-11`，客户端可重拉公钥并重试
- [x] 连续失败达阈值（≥3）→ `needCode=true`，无验证码 → `code=-3`
- [x] `codeJson` 一次一码，用后失效
- [x] 登录成功响应无 `password` 字段
- [x] LDAP 用户：RSA 解密后目录认证仍可用

---

## 6. Access / Refresh（无感续期）

| 项 | 约定 |
| -- | ---- |
| 登录成功 | `{ token, refreshToken, user }`；`token` 为短效 access |
| 续期 | `GET\|POST /api/users/token/refresh?refreshToken=` → `{ token, refreshToken }`（轮换旧 refresh） |
| Access 失效 | Bearer 无效/过期 → 信封 `code=-2`（`TOKEN_EXPIRED`）；客户端单飞 refresh 后重试原请求 |
| 无 Bearer | `code=1001` |
| 登出 | 吊销当前 access 及其绑定 refresh |
| TTL | `bluedock.jwt.access-ttl-seconds`（默认 7200）· `refresh-ttl-seconds`（默认 2592000） |

---

## 7. 自助注册 / 忘记密码（邮箱 OTP）

| 项 | 约定 |
| -- | ---- |
| 发码 | `GET /api/users/email/code?email=&type=reg\|reset`；Redis TTL **10min**；冷却 60s；无 SMTP → `devCode` |
| 注册 | `POST /api/users/register`：`email` + RSA `password`+`keyId` + `emailCode` [+ `nickname`/`invite`] |
| 重置 | `POST /api/users/password/reset`：`email` + `emailCode` + RSA `password`+`keyId` |
| reg | `close` 拒注册；`invite` 校验 `systemSetting.inviteCode`；`open` 无邀请码 |
| 成功 | 注册默认签发 `token`+`refreshToken`；若 `emailSetting.regVerify=open` 则仅建号并 `requireEmailVerify=true` |

