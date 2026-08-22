# 机器人

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **机器人是什么**
- **内置系统机器人有哪些**
- **机器人 Webhook 接入**

### 能力（怎么做）

- 创建用户机器人
- 把机器人邀请进群
- 怎么 @ 机器人触发

## 核心概念

### 机器人是什么

## 定义
BlueDock 的机器人（bot / robot）是一种特殊用户账号（数据库 `users.bot = 1`），可以加入会话、收发消息、被 @ 触发，但不能登录 UI。它通常用于自动通知、外部系统接入、AI 对话。

## 三类机器人
BlueDock 把机器人分三类，能力依次递增：

- **内置系统机器人**（system bot）：邮箱以 `@bot.system` 结尾，由系统创建并维护，如「任务提醒」「审批」「签到打卡」「AI 助手」「会议通知」。普通用户不能新建或删除，仅管理员可改设置。详见 `bot.system-list`。
- **用户自建机器人**（UserBot）：任何登录用户都可在「应用 → 我的机器人」里创建，用于把外部系统消息推进 BlueDock，或拿到 token 后由外部代码代发消息。详见 `bot.create`。
- **Webhook 接入**：自建机器人配置 `webhookUrl` 后，收到的群消息 / @触发 / 成员变更等事件会被 POST 到该地址，外部服务可据此回复。详见 `bot.webhook`。

## 关键属性
- 机器人有独立 `userId`、`token`、头像、昵称
- 像普通用户一样被加入群（`bot.invite`）或被 @
- 群聊里必须 @ 机器人才会触发回复，单聊则任意消息都触发（`bot.mention`）
- 自建机器人可设 `clearDay`（消息保留天数，1-999，默认 90 天）；`UserBotClearDayScheduler` 按 `clear_at` 水位软删 bot 发出的过期消息

## 不支持
- 机器人之间不会互相触发（避免死循环），收到对方消息直接忽略
- 系统机器人不能由普通用户删除（删除会报「系统机器人不能删除」）
- 单个用户不能创建超过 50 个自建机器人（超出报「超过最大创建数量」）

### 内置系统机器人有哪些

BlueDock 自带一组系统机器人，邮箱统一以 `@bot.system` 结尾，由后端 `UserBot::systemBotName` 维护。普通用户能用，但不能创建或删除（详见 `bot.permission`）。

## 通知/提醒类
- `system-msg@bot.system`「系统消息」：系统公告、登录提醒、版本通知、**数据导出完成/失败私聊**
- `task-alert@bot.system`「任务提醒」：任务被分配、截止时间临近、状态变更
- `todo-alert@bot.system`「待办提醒」：个人待办到期提醒
- `meeting-alert@bot.system`「会议通知」：会议邀请、开始/结束提醒（需会议插件）
- `okr-alert@bot.system`「OKR 提醒」：OKR 周期推进、KR 更新（需 OKR 插件）
- `approval-alert@bot.system`「审批」：审批待办、结果通知（需审批插件）

## 互动/办公类
- `attendance@bot.system`「签到打卡」：单聊里发指令打卡，支持手动 / 定位 / 路由器 MAC / 人脸
- `anon-msg@bot.system`「匿名消息」：在他人单聊里以匿名身份发消息
- `bot-manager@bot.system`「机器人管理」：用 `/list` `/newbot` `/setname` 等斜杠指令管理自建机器人

## AI 对话类
- `ai-openai@bot.system`「ChatGPT」
- `ai-claude@bot.system`「Claude」
- `ai-deepseek@bot.system`「DeepSeek」
- `ai-gemini@bot.system`「Gemini」
- `ai-grok@bot.system`「Grok」
- `ai-ollama@bot.system`「Ollama」
- `ai-zhipu@bot.system`「智谱清言」
- `ai-qianwen@bot.system`「通义千问」
- `ai-wenxin@bot.system`「文心一言」

AI 机器人都需要在「系统设置 → AI 设置」里填 API Key 才能启用，未配置则报「机器人未启用」。

## 不支持
- 系统机器人的指令、行为由后端写死，无法自定义回复逻辑
- AI 机器人在群聊里必须 @ 才回复；私聊不需要 @

### 机器人 Webhook 接入

## 定义
Webhook 是把「机器人收到的事件」用 HTTP POST 推到外部服务的地址。外部服务回 `{"code":200,"message":"..."}` 时，BlueDock 会把 `message` 作为机器人的文本回复发回会话。

## 可订阅事件
后端常量见 `App\Models\UserBot`：

| 事件 key | 触发时机 |
|---|---|
| `message` | 机器人收到消息（单聊任意消息；群聊需 @ 机器人） |
| `dialogOpen` | 用户首次打开和机器人的会话 |
| `memberJoin` | 群聊里机器人或他人加入 |
| `memberLeave` | 群聊里成员离开 |

不勾任何事件时默认按 `[message]` 处理（参考 `normalizeWebhookEvents`）。

## 请求体字段
`event = message` 时主要字段：

- `event`: `message`
- `text`: 用户的纯文本指令
- `reply_text`: 若是引用回复，被引用消息的文本
- `token`: 机器人当次有效的 API token，可调 BlueDock API 代发消息
- `dialog_id` / `dialog_type` / `session_id`
- `messageId` / `messageUserId` / `mention`（是否被 @）
- `botUserId`: 机器人 userId
- `msg_user`: 发送方信息（userId / email / nickname / 临时 token）
- `extras`: JSON 字符串，含 `timestamp` 等
- `version`: 当前 BlueDock 版本（`1.0.0`）

## 设置
在「我的机器人」编辑面板填 `webhookUrl` 并勾事件；或单聊「机器人管理」里 `/webhook <bot_id> <url>`。

## 不支持
- 不支持鉴权签名（如 HMAC），请用 HTTPS + 服务端校验 `token`
- 不支持调用失败重试 / 死信队列；失败仅在后端 info 日志记录
- 不能区分 webhook 收到的消息是来自哪个具体群成员之外的额外字段

## 不支持 / 边界

- "@所有人 不算 @ 机器人，机器人不会响应「@所有人」"
- "群聊里不 @ 机器人则机器人不回复（避免噪音）"
- URL 长度最大 255 字符
- `webhookUrl` 必须以 `http://` 或 `https://` 开头，否则不发送
- 临时账号（temp）不能创建群，但可以把机器人邀进自己的群
- 全员群（all 群）/ 部门群 / 项目讨论组通常不允许手动加机器人，因为成员是系统自动维护
- 创建后不能改「是否支持会话」开关，需重建
- 单个用户最多 50 个自建机器人，超出报「超过最大创建数量」
- 单聊里发 `/...` 斜杠开头的指令，只有系统机器人才识别；自建机器人会直接忽略
- 普通用户最多创建 50 个自建机器人
- 机器人不能登录前端 UI，只能通过 token 调用 API 或被 @ 触发
- 机器人之间互相发的消息会被忽略，避免循环
- 机器人名称必须 2-20 字符，太短/太长会被拒
- 机器人本质是特殊 User（users.bot = 1），不能用普通账号登录网页
- 机器人邀进群后仍需 @ 它才会触发回复
- 用户自建机器人不识别斜杠指令（`/help` 等只对系统机器人有效）
- 用户自建机器人收到斜杠开头（`/...`）的消息直接忽略，不会触发 webhook
- 系统机器人不可删除；仅管理员能改昵称 / 头像
- 系统机器人名单写死在 `UserBot::systemBotName`，不支持自助新增类型
- 调用超时 30 秒，超时不重试
- 部分机器人依赖对应插件已安装（AI、审批等），否则不出现

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `bot`
- API：待写入 `api.md` / `docs/contract/api-contract.md`
- 数据：待写入 `data.md` / `docs/data/database.md`
