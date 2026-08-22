# LDAP — API

配置挂在系统第三方设置（契约 `api/system/setting/thirdAccess`）。

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `GET/POST api/system/setting/thirdAccess` | 读写 LDAP 配置（管理员） | 已落地 |
| `GET api/system/setting/thirdAccess/testLdap` | Bind 测试 | 已落地 |
| 登录时 LDAP 认证钩子 | `AuthService` + `JndiLdapAuthenticator` | 已落地 |
| 登录时昵称同步 / `ldapSyncLocal` 反向写入 | 同上 | 已落地 |
| 改密回写 `userPassword` | `GET /api/users/editPassword` | 已落地 |

配置存 `bluedock_settings.name=thirdAccessSetting`，字段 camelCase（`ldapOpen` · `ldapSyncLocal` 等）。
