# 个人设置 — API

前缀多为 `api/users/*`。完整表见 [api-contract.md](../../contract/api-contract.md)。本模块聚合「当前用户对自己账号」的设置入口；管理员操作见 [user-account](../user-account/api.md)。

## 已落地

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/users/editData` | Bearer | 改自己的资料：`nickname` / `userImage` / `profession` / `tel` / `birthday` / `address` / `introduction` / `lang` |
| `GET /api/users/editPassword` | Bearer | 改密：RSA `oldPassword`+`password`+`keyId`；规则见 [auth-wire.md](../user-account/auth-wire.md) |
| `GET /api/users/email/edit` | Bearer | 申请改邮箱；确认走 `email/verification` |
| `GET /api/users/email/send` | Bearer | 重发注册邮箱验证 |
| `GET /api/users/appSort` | Bearer | 个人应用排序 |
| `POST /api/users/appSort/save` | Bearer | 保存排序；`sorts` JSON（按 base/admin 分组） |
| `GET /api/users/delete/account` | Bearer | 注销：`type=warning\|confirm`；`email`+`reason`；confirm 时验证码或 RSA 密码 |
| `GET /api/users/device/list` | Bearer | 登录设备列表 |
| `GET /api/users/device/logout` | Bearer | 踢设备 |
| `GET /api/users/device/edit` | Bearer | 改设备展示名等 |
| `GET /api/users/tags/lists` | Bearer | 个性标签列表；`userId` 缺省自己 |
| `POST /api/users/tags/add` | Bearer | 新增；`userId` 被贴标签用户（缺省自己）+ `name`≤20 |
| `POST /api/users/tags/update` | Bearer | 改名；仅创建者；`id`+`name` |
| `POST /api/users/tags/delete` | Bearer | 删除；创建者或系统管理员；`id` |
| `POST /api/users/tags/recognize` | Bearer | 认可/取消；`id` → `{recognized,recognizeCount}` |
| `GET /api/privacy` | 匿名 | 隐私政策 HTML（`text/html`；资源 `static/privacy.html`） |

### 个性标签规则

| 项 | 说明 |
| -- | ---- |
| 上限 | 每位被贴用户最多 **100** 条（软删不计） |
| 重名 | 同一 `userId` 下活跃标签名唯一 |
| 目标 | 不可贴机器人 / 不存在用户 |
| 出站 | `id`/`userId`/`creatorUserId`/`name`/`recognizeCount`/`recognized` |

## 规划中 / 未实现

（无；隐私政策已由 `GET /api/privacy` 承接，可替换 `static/privacy.html`）

## 明确本仓不做（客户端本地）

| 能力 | 归属 |
| ---- | ---- |
| 主题深浅色 / 跟随系统 | bluedock-web 本地 |
| 键盘 / 快捷键 / 发送键行为 | Electron / EEUI 本地 |
| 时段静音（移动推送） | 客户端本地；无云端偏好 API |
| 时区展示 | 客户端；任务时间存 UTC |

## 与相邻模块

| 能力 | 文档 |
| ---- | ---- |
| 登录 / 管理员操作 / 导入 | [user-account/api.md](../user-account/api.md) |
| 签到个人登记（人脸/MAC） | [attendance](../attendance/overview.md) |
| 会话免打扰 | [messenger](../messenger/api.md) mute/silence |
| APP 推送别名开关 | [notify](../notify/checklist.md) `appPush/alias` |

响应 JSON camelCase；读响应禁止 `password` / `passwordHash`。
