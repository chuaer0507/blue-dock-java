# 账号 — API

前缀 `api/users`。完整表见 [api-contract.md](../../contract/api-contract.md)。

## 已实现（骨架）

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET\|POST /api/users/login` | 匿名 | `email` + RSA `password` + `keyId`（[+ 验证码]）；POST 推荐 JSON body，亦兼容 form/query；成功 `{ token, refreshToken, user }`（无 password）。见 [auth-wire.md](auth-wire.md) |
| `GET /api/users/login/qrCode` | 匿名 / 可选 Bearer | 扫码登录；见下表 |
| `GET /api/users/register/needInvite` | 匿名 | `{ need }`；`systemSetting.reg==invite` 时为 true |
| `POST /api/users/register` | 匿名 | 自助注册：RSA `password`+`keyId`+`emailCode`；`reg=close` 拒绝；invite 模式校验 `invite` |
| `GET /api/users/email/code` | 匿名 | 邮箱 OTP：`type=reg\|reset`；Redis TTL 10min；无 SMTP 回 `devCode` |
| `POST /api/users/password/reset` | 匿名 | 校验 OTP 后写入 RSA 新密码 |
| `GET /api/users/token/expire` | Bearer | 当前 access token `{ ttlSeconds, expireAt }`（expireAt=UTC 毫秒） |
| `GET\|POST /api/users/token/refresh` | 匿名 | `refreshToken` → `{ token, refreshToken }`（轮换）；失败 `code=-2` |
| `GET /api/users/email/send` | Bearer | 重发邮箱验证链接（SMTP 未配时返回 `devCode`） |
| `GET /api/users/email/edit` | Bearer | 申请改邮箱：`email`；向新地址发链接 |
| `GET /api/users/email/verification` | 匿名 | 确认链接：`code`；30 分钟有效、一次性 |
| `GET /api/users/key/client` | 匿名 | RSA 公钥 `{ keyId, publicKey, algorithm }`（登录/改密前拉取） |
| `GET /api/users/login/needCode` | 匿名 | `{ need }`；按失败计数判定是否强制验证码 |
| `GET /api/users/login/codeJson` | 匿名 | `{ key, imageBase64 }`（推荐） |
| `GET /api/users/login/codeImage` | 匿名 | PNG 流 + Cookie/`X-Captcha-Key` |
| `GET /api/users/info` | Bearer | 当前登录用户公开资料 |
| `GET /api/users/basic` | Bearer | 指定用户公开资料（`userId`） |
| `GET /api/users/extra` | Bearer | 扩展资料（`userId` 缺省自己；另含 `nameAz`/`emailVerify`/`isBot`） |
| `GET /api/users/editData` | Bearer | 修改自己的资料（nickname/userImage/profession/tel/birthday/address/introduction/lang） |
| `GET /api/users/editPassword` | Bearer | 改密：RSA `oldPassword`+`password`+`keyId`；`must_change_password` 清零；LDAP 用户回写目录 |
| `GET /api/users/lists` | Bearer 管理员 | 会员列表；`key`/`keys` 搜邮箱昵称电话；`page`/`pageSize`≤100；`bot=1` 含机器人 |
| `POST /api/users/createUser` | Bearer 管理员 | 创建用户：`email`+`nickname`+RSA `password`+`keyId`；可选 `profession`/`identity` |
| `GET /api/users/operation` | Bearer 管理员 | 操作用户：`type`+`userId`（见下表） |
| `GET /api/users/search` | Bearer | 搜索会员（基础字段）；见下表 |
| `GET /api/users/search/ai` | Bearer | AI 系统机器人列表（`ai-*@bot.system`） |
| `GET /api/users/import/template` | Bearer 管理员 | 下载 CSV 模板 |
| `POST /api/users/import/preview` | Bearer 管理员 | 上传 CSV / xls / xlsx 预览（`file`）；响应不含 password |
| `POST /api/users/import` | Bearer 管理员 | 确认导入：`rows[]`+RSA `password`+`keyId` |
| `GET /api/users/logout` | 匿名（可选 Bearer） | 撤销 token |

鉴权：`Authorization: Bearer <token>`；由 `BearerAuthFilter` 校验 Redis 会话。白名单见 [api-routing.md](../../contract/api-routing.md)。

出站字段见 `UserPublicView`（camelCase，禁止 password）。`telephone` 非空时全局唯一（应用层校验）。列表项见 `UserAdminView`（另含 `bot`/`disableAt`）。

**密码 / 验证码铁律**：全文见 [auth-wire.md](auth-wire.md)。登录、**`editPassword`**、**`createUser`** 均强制 RSA + `keyId`。

### editPassword 规则

| 项 | 说明 |
| -- | ---- |
| 参数 | `oldPassword`、`password`（新）、`keyId`（同一公钥） |
| 长度 | 新密码 6–32 |
| 禁止 | `identity` 含 `system`；新旧相同 |
| LDAP | 含 `ldap` 且目录开启时：`userPassword` 回写失败则整单失败；旧密可走本地 BCrypt 或目录认证 |

### createUser / lists / operation

| 项 | 说明 |
| -- | ---- |
| createUser | 邮箱 ≤32；昵称 2–20；密码 6–32；落库 `must_change_password=1`、`email_verify=1`；受 License 扩容守卫 |
| identity | 可选 `admin`/`ldap`/`temporary`；自动剔除 `system`/`bot`/`disable` |
| lists | 默认排除机器人；返回 `{list,total,page,pageSize}` |
| operation | `type`=`setAdmin`/`clearAdmin`/`setTemporary`/`clearTemporary`/`disable`/`enable`；`disable` 另须 `handoverUserId`；返回 `UserAdminView` |

#### operation 规则

| type | 行为 | 限制 |
| ---- | ---- | ---- |
| `setAdmin` | identity 加 `admin` | 目标含 `temporary` 须先 `clearTemporary` |
| `clearAdmin` | 去掉 `admin` | 不可对自己操作；不可操作 `system` / `userId=1` |
| `setTemporary` | 加 `temporary` 并去掉 `admin` | 不可操作 system/bot |
| `clearTemporary` | 去掉 `temporary` | — |
| `disable` | 交接归属 → 加 `disable`、去 `admin`，写 `disableAt` | 不可对自己；不可 system/bot；**须** `handoverUserId`（在职非机器人） |
| `enable` | 去 `disable`，清空 `disableAt` | — |

#### disable 交接（`handoverUserId`）

经 `UserDisableHandoverBridge` 分模块迁移（不转移 `admin` 身份）：

| 域 | 行为 |
| -- | ---- |
| 项目 | 负责项目移交交接人；离职用户退出全部项目成员；个人项目冲突时降为团队再移交 |
| 任务 | 负责任务写入交接人为负责人；清除离职用户任务成员与可见名单 |
| 部门 | 负责人改交接人；清除离职用户部门管理员与成员；同步部门群 |
| 通知 | 事务提交后：`NotifySendEvent.CHANNEL_DESKTOP` + `SystemMsgDmBridge` 私聊交接人 |

### search / search/ai

| 项 | 说明 |
| -- | ---- |
| 出站 | `UserSearchView`：`userId`/`email`/`nickname`/`profession`/`userImage`/`nameAz`（无 password） |
| `key` | 昵称或邮箱模糊；亦接受 `keys` |
| `disable` | `0` 排除离职（默认）、`1` 含离职、`2` 仅离职 |
| `isBot` | `0` 排除机器人（默认）、`1` 含机器人、`2` 仅机器人 |
| `projectId` | 仅该项目成员；`noProjectId` 排除该项目成员 |
| `nameAz` | 排序方向 `asc`/`desc`（按 `name_az`） |
| 分页 | 有 `page` → `{list,total,page,pageSize}`（默认 pageSize=10，≤100）；否则 `take`（默认 10，≤100）→ `{list}` |
| search/ai | `take`≤100；返回已落库的 `ai-*@bot.system` 机器人 |

### import

| 项 | 说明 |
| -- | ---- |
| 列 | `email,nickname,password,profession`（CSV / xls / xlsx；首 sheet；表头须含 email） |
| 上限 | 单次 ≤500 行 |
| preview | multipart `file`；返回 `{rows,total,okCount}`，行含 `line/email/nickname/profession/ok/error`，**无 password** |
| import | JSON `{keyId, rows:[{email,nickname,password,profession,keyId?}]}`；`password` 为 RSA 密文；返回 `{rows,created,failed}` |
| 规则 | 不更新已有邮箱；重复/非法行标错跳过；受 License 扩容守卫；空 `rows` → `user.import_empty` |

### login/qrCode

| type | 鉴权 | 说明 |
| ---- | ---- | ---- |
| `create`（默认） | 匿名 | 生成 `code`（≥32）+ `status=waiting`；Redis TTL **30s** |
| `confirm` / `login` | Bearer | 手机端确认；票据 → `confirmed`（不发桌面 token） |
| `status` / `poll` | 匿名 | `waiting` 继续轮询；`confirmed` → 签发**新** token+user（记设备）并消费；过期/已用报错 |

| 项 | 说明 |
| -- | ---- |
| 复用 | 不支持；消费后再次 status → `auth.qr_code_used` |
| code | 截断/&lt;32 → `auth.qr_code_invalid` |

### email 验证

| 项 | 说明 |
| -- | ---- |
| 表 | `bluedock_user_email_verifications`（`type`=reg\|edit；`status` 0/1） |
| send | 当前用户未验证时发 `reg` 链接；30 分钟内冷却 |
| edit | 向新邮箱发 `edit` 链接；确认后改 `email` 并置 `email_verify=1` |
| verification | 匿名；过期/已用拒绝；`regVerify=open` 时未验证禁登录 |
| SMTP | 未配置不抛错，响应带 `devCode` 便于联调 |

## 个人设置相关（已落地，文档归 user-settings）

| URL | 说明 |
| --- | ---- |
| `editData` / `editPassword` / `email/*` | 资料与邮箱 |
| `appSort` · `appSort/save` | 应用排序 |
| `delete/account` | 注销（warning/confirm） |
| `device/list` · `device/logout` · `device/edit` | 设备 |
| `tags/lists` · `add` · `update` · `delete` · `recognize` | 个性标签 |

详见 [user-settings/api.md](../user-settings/api.md)。

### 分享选择器 / 年度报告

| URL | 说明 |
| -- | ---- |
| `GET /api/users/share/list` | 分享选择器；`type`=file\|text（默认 file）· `parentId?` 下钻文件夹 · `key?` 搜会话/用户补齐；项含 `type`/`url`/`icon`/`name`/`extend`/`sort?`（camelCase：`uploadFileId`/`dialogIds`/`textType`） |
| `GET /api/users/annual/report` | 个人年度报告；`year?` 默认当前年；含 `user`/`hireDate`/`tenureDays`/`latestOnlineTime`/`longestChat`/`chatAiNum`/`fileCreatedNum`/`projects`/`tasks` |

权限：管理类接口需系统管理员。详见 [overview.md](overview.md) · [permissions.md](permissions.md)。
