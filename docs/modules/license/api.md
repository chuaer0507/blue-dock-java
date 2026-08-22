# License — API

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `GET /api/license/status` | 当前 License、用户数、本机 `machineSn`/MAC、error[]；含 `online`/`onlineEmail` | 已落地 |
| `POST /api/system/license` | 管理员离线写入（结构化 JSON / 原文） | 已落地 |
| `GET /api/license/email/send` | 在线授权：发邮箱验证码（local 返回 `devCode`；remote 调商店） | 已落地 |
| `GET /api/license/login` | `email`+`code` → pending `token` | 已落地 |
| `GET /api/license/login/confirm` | `token` → 落盘在线 License（绑本机 SN/MAC） | 已落地 |
| `GET /api/license/trial` | 试用一次（local 默认 14 天 / 3 人；remote 由商店签发） | 已落地 |
| `GET /api/license/refresh` | 刷新在线 License 绑定指纹 | 已落地 |
| `GET /api/license/logout` | 退出在线授权，回退小团队占位（remote 尽力通知商店） | 已落地 |

配置：`bluedock.system.license-online-mode=local|remote` · `license-online-url` · `license-trial-days`（≤60）。

`remote` 未配 `license-online-url` → `license.online_unavailable`。上游端点见 [infra/license.md](../../infra/license.md) §remote。

存储：`bluedock.system.license-path`（默认 `./data/secrets/license.json`）。详见 [infra/license.md](../../infra/license.md)。
