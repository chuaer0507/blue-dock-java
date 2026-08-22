# LDAP（基础设施）

产品说明见 [modules/ldap/overview.md](../modules/ldap/overview.md)。

## 原则

- **按需同步**：登录 / 注册时认证并合并本地用户；**无定时全量拉取**
- 配置存 `bluedock_settings`（分组 `thirdAccessSetting`）
- LDAP 用户密码不落本地明文；本地 password 占位随机串；`identity` 含 `ldap`

## 配置项（camelCase wire）

| 键 | 说明 |
| -- | ---- |
| `ldapOpen` | 总开关 `open`/`close` |
| `ldapHost` / `ldapPort` | 默认端口 389 |
| `ldapUserDn` / `ldapPassword` | 管理员 Bind |
| `ldapBaseDn` | 搜索基准 |
| `ldapLoginAttr` | cn / uid / mail / sAMAccountName / userPrincipalName |
| `ldapSyncLocal` | 本地账号登录成功后反向写入 LDAP |

管理端：`GET|POST /api/system/setting/thirdAccess` · `GET .../testLdap`。

## 登录时同步（已落地）

```
本地密码 OK
  └─ ldapOpen + ldapSyncLocal=open 且本地尚无 ldap identity
       → 目录无同邮箱则 create inetOrgPerson → 本地打 ldap identity
本地密码失败 / 无本地用户
  └─ ldapOpen → 管理员 Bind → loginAttr 搜 → 用户 DN Bind
       → 取 mail + displayName → 本地查找/创建 → 合并 identity + 刷新 nickname
```

| 项 | 状态 |
| -- | ---- |
| 配置读写 / testLdap | ✅ |
| `LdapAuthenticator` + `JndiLdapAuthenticator` | ✅ |
| LDAP 认证登录 + 新建/合并用户 | ✅ |
| 每次 LDAP 登录刷新 `nickname` | ✅ |
| `ldapSyncLocal` 反向创建目录条目 | ✅ |
| 本地改密回写 LDAP `userPassword` | ✅（`editPassword` → `LdapAuthenticator.updatePassword`） |

实现：`AuthService` · `JndiLdapAuthenticator` · `UserProfileService.editPassword`。

## 不做

- OAuth / SAML / OIDC（本文件范围外）
- 多域、OU→部门一键导入
- 定时全量拉取按钮
- JPEG 头像同步（暂不落盘）
