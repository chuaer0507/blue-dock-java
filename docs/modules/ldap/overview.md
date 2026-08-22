# LDAP

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **LDAP 集成是什么**

### 能力（怎么做）

- 配置 LDAP
- 同步 LDAP 用户

## 核心概念

### LDAP 集成是什么

## 定义
LDAP（Lightweight Directory Access Protocol）集成让 BlueDock 用企业已有的 LDAP / Active Directory 账号体系做认证。开启后用户在登录页输入企业域账号 + 密码，BlueDock 通过 LDAP 协议向目录服务器认证，认证成功后在本地自动创建或合并账号。

实现使用 LDAP 客户端库对接目录服务。设置存在 `bluedock_settings` 的第三方接入分组。

## 关键属性

- **ldapOpen** — 总开关；非 `open` 时所有 LDAP 调用直接 short-circuit 返回
- **ldap_host / ldap_port** — 目录服务器地址（端口默认 389）
- **ldap_user_dn / ldap_password** — 管理员 Bind DN 与密码，用于搜索用户
- **ldap_base_dn** — 搜索基准 DN，限定查找范围
- **ldap_login_attr** — 登录属性，可选 `cn` / `uid` / `mail` / `sAMAccountName` / `userPrincipalName`，默认 `cn`
- **ldap_sync_local** — 本地账号反向写入 LDAP 的开关

## 工作流程

1. 用户在 BlueDock 登录页输入企业账号 + 密码
2. 后端用管理员 Bind 搜索 `loginAttr=用户名` 的 entry
3. 拿到该 entry 的真实 DN，用「DN + 用户输入的密码」二次 Bind
4. Bind 成功 → 从 entry 中提取邮箱（按 `mail / cn / uid / userPrincipalName` 顺序）
5. 本地按 email 查找用户：找不到则注册（本地密码随机），找到则合并
6. 同步昵称、头像（`jpegPhoto` 字段）到本地账号

## 与其他概念的关系

- **本地账号**：本地账号若没 `ldap` identity，被 LDAP 用户合并时会打上 `ldap` 标
- **同步本地**（`ldap_sync_local=open`）：本地用户登录或注册时反向把账号写到 LDAP，便于统一管控
- **连接复用**：目录连接池化时，用户 Bind 成功后须恢复管理员 Bind，避免污染下一请求

配置入口见系统设置「第三方接入」或 LDAP 配置页。

## 不支持 / 边界

- BlueDock 不主动批量拉取 LDAP 用户，所有同步都是登录 / 注册时按需触发
- LDAP 用户密码不存到本地，本地密码用随机串占位
- LDAP 用户没邮箱属性就无法首次登录（会抛「LDAP 用户缺少邮箱属性」）
- SYSTEM_SETTING=disabled 时禁止保存配置
- 不支持 OAuth / SAML / OIDC（这页只讲 LDAP）
- 不支持多 LDAP 域，只能配置 1 个 default connection
- 不支持把 LDAP 的组织架构（OU）一键导入到 BlueDock 部门表
- 反向同步只在「ldap_sync_local=open」时生效
- 本地用户改密码后：若账号含 `ldap` identity 且目录开启，会回写 LDAP `userPassword`（`editPassword`）
- 测试连接接口只对当前请求生效，不会落库
- 登录属性枚举受限，不在白名单内的值会回退为 cn
- 端口未填或非法时会被强制设为 389

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `ldap`
- API：待写入 `api.md` / `docs/contract/api-contract.md`
- 数据：待写入 `data.md` / `docs/data/database.md`
