# 微应用 — API

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `POST api/system/microAppMenu` | `type=get\|save`；save 限管理员；设置项 `microAppMenu` | 已落地 |
| `GET api/users/appSort` · `POST …/save` | 个人应用排序 `{base,admin}` | 已落地 |
| `POST api/apps/badge/set` | 插件密钥鉴权，写角标 + WS `appBadge` | 已落地 |
| `POST api/apps/badge/clear` · `GET …/list` | 当前用户清零 / 快照 | 已落地 |
| `GET/POST api/system/apps/{catalog,installed,install,update,uninstall}` | 安装注册表闭环（联动 microAppMenu；可选 HTTP lifecycle Hook；无本进程 Docker） | 已落地 |

表：`bluedock_app_badges` · `bluedock_user_app_sorts` · `bluedock_installed_apps`。`appstore` 视为始终已安装且不可卸载。
