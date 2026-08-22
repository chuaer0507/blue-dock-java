# License（基础设施）

产品说明见 [modules/license/overview.md](../modules/license/overview.md)。

## 职责

| 项 | 说明 |
| -- | ---- |
| 存储 | `bluedock.system.license-path`（默认 `./data/secrets/license.json`） |
| 离线 API | `GET /api/license/status` · `POST /api/system/license` |
| 在线授权 | `email/send` · `login` · `login/confirm` · `trial` · `refresh` · `logout`（**local** 本机模拟；**remote** 调官方商店 HTTP） |
| 校验 | 人数上限、过期、`people==0\|\|>3` 时 SN/MAC |

## 在线授权（local）

默认 `bluedock.system.license-online-mode=local`：

1. `email/send` → Redis 验证码（TTL 10m）；响应含 `devCode` 便于联调
2. `login` → 校验码 → pending token（TTL 30m）
3. `login/confirm` → 写入 `people=10`、绑本机 SN/MAC、`expiredAt=+1y`、`online=true`
4. `trial` → 每 SN 一次（Redis 标记）；`people≤3`、`expiredAt=+trialDays`（默认 14，硬上限 60）
5. `refresh` → 已在线时刷新 SN/MAC
6. `logout` → 回退 `people=3` 占位并清 `online`

`remote` + 未配 `license-online-url` → `license.online_unavailable`。

## 在线授权（remote）

配置：`bluedock.system.license-online-mode=remote` · `license-online-url`（商店根 URL）。

实现类：`LicenseOnlineClient`（`POST` JSON，超时约 20s）。路径相对根 URL：

| 本仓 API | 上游 | 请求要点 | 成功响应要点 |
| -------- | ---- | -------- | ------------ |
| `email/send` | `POST /v1/license/email/send` | `{email}` | `{sent,expiresIn?}`（无 `devCode`） |
| `login` | `POST /v1/license/login` | `{email,code}` | `{token,email?,expiresIn?}` 或包在 `data` |
| `login/confirm` | `POST /v1/license/confirm` | `{token,sn,macAddresses}` | License 字段（见下） |
| `trial` | `POST /v1/license/trial` | `{email?,sn,macAddresses}` | 同上 |
| `refresh` | `POST /v1/license/refresh` | `{sn,macAddresses,onlineEmail,license}` | 同上 |
| `logout` | `POST /v1/license/logout` | `{sn,onlineEmail,license}` | 任意；失败仍允许本机回退小团队档 |

落盘字段与离线同形：`people` / `sn` / `macAddresses`（兼容 `macs`）/ `expiredAt`（兼容 `expired_at`）/ `online*` / `license`。可包在 `data`，或 `license` 为对象。

错误体可带 `messageKey` / `error` / `code`（值为 `license.*` 时映射本仓 i18n）；否则 `license.online_unavailable`。HTTP 非 2xx 或 `ok`/`success`=false 同理。

`/api/license/**` 匿名可访问（契约 any）。

## 离线录入格式

`POST /api/system/license?license=` 支持：

1. **结构化 JSON**（推荐，不依赖专有二进制解码）：

```json
{
  "license": "opaque-or-empty",
  "people": 10,
  "sn": "SN-…",
  "macAddresses": ["AA:BB:CC:DD:EE:FF"],
  "expiredAt": "2099-12-31"
}
```

2. **Base64(JSON)**：同上结构。
3. **纯原文**：按试用档写入（`people=0`，不绑 SN/MAC）。

保存时若属付费档（`people==0` 或 `>3`）且 `sn` 为空 → `license.invalid`；SN/MAC 与本机不符 → 对应错误。

## status 响应要点

| 字段 | 说明 |
| ---- | ---- |
| `info.people` / `info.sn` / `info.macAddresses` / `info.expiredAt` | 当前 License |
| `userCount` | 非机器人且未禁用用户数 |
| `machineSn` / `macAddresses` | 本机指纹（`bluedock.system.machine-sn` 可覆盖 SN） |
| `error[]` | 本地化文案（超额 / 过期 / SN / MAC） |
| `ok` | `error` 为空 |
| `trial` | `1 ≤ people ≤ 3` |
| `online` / `onlineEmail` / `onlineMode` | 在线授权态 |

## 扩容守卫

`LicenseCapacity.assertCanAddUser()`：LDAP 首次建本地用户等路径调用；超额或过期拒绝。

## 规则摘要

- `1 ≤ people ≤ 3`：小团队豁免 SN/MAC 强绑定
- `people > 0 && userCount > people` → 超额
- `expiredAt` 已过（空 / `forever` / `0` 视为不过期）→ 过期
- SN / MAC 不匹配分别报错（付费档）

## Java 注意

- 机器指纹：`MachineFingerprint`；容器可设 `bluedock.system.machine-sn`
- 在线：`OnlineLicenseService` + `LicenseOnlineClient`；错误文案走 `Messages` + `I18nKeys.LICENSE_*`
