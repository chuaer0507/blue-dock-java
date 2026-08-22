# 应用市场 — API

本仓 AppStore 提供 **注册表闭环**（无容器编排 / 无外部商店 catalog）：

| URL | 说明 |
| --- | ---- |
| `GET api/system/apps/catalog` | 官方内置目录；含 `installed` 标记 |
| `GET api/system/apps/installed` | 已安装列表（含强制存在的 `appstore` + `version`） |
| `POST api/system/apps/install` | `{id,name?,secret?,version?,menus?}`；仅 `id` 时可从 catalog 补全；空 secret 本地生成；联动 `microAppMenu`；可选生命周期 Hook |
| `POST api/system/apps/update` | 更新已安装应用的 name/secret/version/menus；刷新 microAppMenu；可选 Hook |
| `POST api/system/apps/uninstall` | 标记卸载、清角标、移除 microAppMenu 入口；不可卸 `appstore`；可选 Hook（失败不回滚） |

角标密钥取自 `bluedock_installed_apps.secret`。自定义入口仍可用 `api/system/microAppMenu` 手工维护。

## 可选生命周期 Hook

配置（默认关闭）：

| Key | 说明 |
| --- | ---- |
| `bluedock.apps.lifecycle-hook-url` / `BLUEDOCK_APPS_LIFECYCLE_HOOK_URL` | 完整 URL；空则跳过 |
| `bluedock.apps.lifecycle-hook-timeout-ms` | 默认 8000 |
| `bluedock.apps.lifecycle-hook-fail-open` | 默认 `true`：失败仅日志；`false` 时 install/update 抛 `apps.lifecycle_hook_failed` 并尽力回滚 |

`POST` JSON（不含 secret）：

```json
{ "event": "install|update|uninstall", "appId": "okr", "name": "OKR", "version": "1.0.0", "at": "…" }
```

侧车可用该回调拉镜像 / 启停容器；**本仓不内置 Docker 编排**。

**不做**：本进程内 Docker 拉取、插件 `user_onboard` Hook、外部商店 remote catalog。
