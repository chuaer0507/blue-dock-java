# 通知

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **邮件通知是什么**
- **邮件通知场景**
- **APP 推送 Alias 用户绑定**
- **APP 推送是什么**
- **APP 推送触发场景**
- **桌面通知是什么**
- **Dock 角标与任务栏**
- **移动端通知是什么**

### 能力（怎么做）

- 配置邮件 SMTP 服务器
- 测试邮件发送
- 用户侧关闭邮件通知
- 配置 APP 推送
- 单个会话免打扰
- 开启或关闭桌面通知（客户端本地）
- 移动端按时段免打扰（客户端本地；无云端偏好）
- 移动端通知权限同步（`appPush/alias`）

## 核心概念

### 邮件通知是什么

## 定义
邮件通知是BlueDock 通过**管理员配置的 SMTP**（`emailSetting`，入口同上传类设置）向用户邮箱发送系统通知的能力。系统级配置含 SMTP 服务器、端口、账号、密码、忽略地址、未读消息提醒规则等。详见 [infra/email.md](../../infra/email.md)。

## 关键属性
- **全局开关**：未配 SMTP 时邮件链路不工作（不报错，也不发出）
- **注册验证**：`regVerify` = open 时新注册账号需邮箱验证才能登录，修改邮箱/删除账号也走验证码
- **未读消息提醒**：`noticeMessage` = open 时按时间范围 `messageUnreadTimeRanges` 把未读消息汇总成邮件发送
- **忽略地址**：`ignoreAddr` 列表中的邮箱永远不收邮件（如内部测试号、机器人号）
- **发件人**：系统别名（System Alias）+ SMTP 账号，如 `BlueDock <noreply@example.com>`

## 触发邮件的场景
具体场景见 `email-notice.scenarios`。

## 与其他通知的关系
- **APP 推送**：见 `push-notice.concept`，独立通道
- **桌面通知**（Electron）：本地系统通知，不走邮件
- **移动端通知**：iOS/Android 推送，不走邮件

邮件、APP 推送、桌面通知三个通道**并行触发**，互不影响。

### 邮件通知场景

## 定义
BlueDock 仅在少数系统级事件中通过邮件触达用户。所有事件都走管理员配置的 SMTP 通道。

## 全部触发场景
| 场景 | 收件人 | 触发条件 |
|---|---|---|
| 注册邮箱验证 | 新注册用户 | `regVerify` 开启时，注册即发验证链接 |
| 修改邮箱验证码 | 用户原邮箱 | 用户提交「修改邮箱」时发 6 位验证码（30 分钟有效）|
| 注销账号验证码 | 用户当前邮箱 | 用户提交「删除账号」时发验证码 |
| 未读消息汇总 | 启用通知的用户 | `noticeMessage` 开启且在 `messageUnreadTimeRanges` 时间范围内，未读消息超过指定分钟数时按用户汇总发送 |
| 测试邮件 | 管理员指定地址 | 管理员点「邮件发送测试」时发一封测试 |

## 未读消息邮件的精细规则
- 单聊未读 ≥ `messageUnreadUserMinute` 分钟才汇入下次邮件
- 群聊未读 ≥ `messageUnreadGroupMinute` 分钟才汇入下次邮件
- 任一值 = -1 → 该类型完全不发
- 仅 `text / file / record / meeting` 这 4 类消息会被汇总
- 标记为静默（silence）的消息不进邮件
- 忽略地址列表里的用户永远不收

## 不在邮件范围内
- 任务创建 / 状态变更 / 截止提醒
- 审批通过 / 驳回 / 抄送
- 项目邀请
- @提及 / 引用 / 评论

以上靠 `push-notice.concept` 和站内消息（WebSocket）通知。

### APP 推送 Alias 用户绑定

## 定义
`bluedock_user_push_aliases` 是BlueDock 用来记录「某用户的某台移动设备应该接收哪些推送」的绑定表。每次移动 APP 启动登录后会调用 `api/users/appPush/alias` 注册或更新这条记录，后端按 `user_id → alias` 反向查到所有活跃设备来发推送。

## 关键字段
| 字段 | 说明 |
|---|---|
| userId | BlueDock 用户 ID |
| alias | 推送 alias，用户在推送通道侧的唯一标识 |
| platform | ios / android（其他平台调用会被拒） |
| device | 设备型号 |
| device_hash | 设备指纹，关联 UserDevice 表 |
| version | APP 版本号（含 versionName） |
| userAgent（落库 `user_agent`） | userAgent |
| is_notified | 系统通知权限（0=未授予 / 1=已授予） |
| updated_at | 最后活跃时间；超过 30 天的别名不再被推送选中 |

## 注册逻辑
APP 启动 → 调 `api/users/appPush/alias?action=update`：
1. 校验 platform 必须是 ios / android
2. 校验 alias 长度 2-20 字符
3. 校验 isDebug=0（调试模式拒绝）
4. 表里同 `userId + alias + platform` 存在则更新；不存在则插入

退出登录时可调 `action=remove` 删别名。

## 多设备规则
- 同一 userId 可绑定多台设备，每个设备一条记录
- 推送时按 platform 分组，每个用户每平台最多取最近 5 个 alias 一起发（按 updated_at 倒序）
- 角标数取自该用户未读且非静默消息总数（WebSocketDialogMessageRead）

## 解绑
- 用户卸载 APP 后系统不会主动删除别名记录；该别名后续推送会在通道侧失败，不影响其他设备
- 30 天未活跃自动从推送目标中剔除（但记录仍在表里）
- 管理员可在数据库直接清理 `bluedock_user_push_aliases`

### APP 推送是什么

## 定义
APP 推送是 BlueDock 通过移动推送通道向 APP（iOS / Android）发送的离线消息通知。即便 APP 被后台杀掉或网络断开后再回来，推送也能触达手机系统通知栏。

## 关键属性
- **通道**：APP 推送（上游开放接口），分 iOS / Android 两套独立配置（`iosKey`、`iosSecret`、`androidKey`、`androidSecret`）
- **别名机制**：用户每次登录移动端会注册一个 alias 到 `bluedock_user_push_aliases` 表，详见 `push-notice.alias`
- **触发**：默认配置下，对方收到新会话消息（且非自己发的、非静默）就会触发
- **PC 在线优化**：若用户 PC 端 60 秒内活跃过，APP 推送会延迟 10 秒；这期间消息被读则跳过推送
- **角标**：iOS 直接传 `badge`；Android 传 `set_badge`（最大 99）

## 与其他通知的关系
- 跟`email-notice.concept`：邮件是 SMTP，APP 推送是独立推送通道，两条独立链路
- 跟`desktop-notify.concept`：桌面通知靠 Electron 本地 `Notification`，与 APP 推送无关
- 跟`mobile-notify.concept`：APP 内的浮层通知，前台收到推送时显示

## 不支持
- 没有「按消息类型订阅」（无法只推@提及不推普通消息）
- 不能自定义推送音效（用系统默认）
- AI 助手消息也不例外：同样走推送，标题取自定义昵称或「AI 助手」

### APP 推送触发场景

## 定义
BlueDock 仅在用户**收到新会话消息**且满足若干前置条件时触发 APP 推送。推送内容为「发件人 + 消息预览」，点击进入对应会话。

## 触发条件（全部满足才推）
1. 管理员后台「APP 推送」总开关 = open
2. APP 推送 iOS / Android 至少一端配了 key/secret
3. 收件人 `bluedock_user_push_aliases` 表 30 天内有效别名
4. 消息发送者 ≠ 收件人本人
5. 该会话对收件人的 `silence` 标记 = 0（非免打扰）
6. 消息本身没带 silence=true 静默标志
7. 收件人未禁用（`disable_at` 为 NULL）

## PC 在线时的延迟逻辑
- 收件人 PC 端 60 秒内有心跳：先**不推**，把任务放进 10 秒延迟队列
- 10 秒后再检查该消息是否已读：已读则跳过，未读才推
- 设计目的：用户在电脑前已经看到消息时不再叨扰手机

## 推送内容
- **单聊**：标题 = 发件人昵称；正文 = 消息预览
- **群聊**：标题 = 群名；正文 = `昵称: 消息预览`
- **AI 助手**：标题取 `msg.nickname` 自定义昵称或默认「AI 助手」
- 附加数据：`dialog_id`、`message_id`、`badge`（未读总数）

## 不触发推送的情形
- 任务更新、审批结果、@提及通过同一条聊天消息送达，那条消息会推；但脱离聊天消息的「纯系统事件」（如签到提醒 TodoRemindTask）按内部参数决定（默认不推送本人）
- 标记为已读的旧消息
- 静默发送的系统消息

## 调试推送
若想确认是否推送，看 `bluedock_app_push_logs` 表 `request_body` / `response_body` / `status` / `skip_reason`。

### 桌面通知是什么

## 定义
桌面通知是 BlueDock 桌面端（Electron 客户端）和 Web 版在新消息到达时，通过操作系统原生通知 API 弹出的提示框。桌面端调用 `new Notification()`（Node 端），Web 版用浏览器 Notification API。

## 关键属性
- **触发**：新消息到达时由前端 `pages/manage.vue` 调用 `openNotification` 走 IPC 给主进程
- **内容**：标题（单聊=昵称 / 群聊=群名）、正文（消息预览）、图标（发送者头像）
- **快捷回复**：桌面端通知支持 hasReply=true 直接在通知框输入回复，回填到 BlueDock
- **点击行为**：点通知会把主窗口拉前并打开对应会话
- **Dock 角标 / 任务栏**：macOS 显示 Dock badge 数字，Windows 任务栏闪烁，托盘可显示未读数

## 平台差异
| 系统 | 通知风格 | Dock/Tray |
|---|---|---|
| macOS | 通知中心 | Dock badge + 托盘 Title 文字 |
| Windows | 操作中心 | 任务栏闪烁（窗口失焦时） |
| Linux | libnotify | 仅通知 |

## 与其他通知的关系
- `push-notice.concept` APP 推送只走移动 APP，桌面端不参与
- `email-notice.concept` 邮件只用于汇总未读或系统验证，与桌面通知并行
- 浏览器 Web 版桌面通知由浏览器实现，关闭浏览器即失效

## 不支持
- 不支持自定义通知音效（用系统默认）
- 不支持自定义通知时长（受系统通知中心控制）
- 不能按会话单独配置桌面通知开关（要总开关或会话级免打扰 `push-notice.silent`）

## 不支持 / 边界

- Dock badge 只在 macOS 显示；Windows 不显示数字角标
- BlueDock APP 内没有「全局通知总开关」按钮，要靠系统通知权限或会话级免打扰
- BlueDock 不内嵌任何「不推送某类消息」的细粒度开关，免打扰按会话 mute/silence；移动时段静音仅客户端本地
- BlueDock 内部没有「一键关闭全部桌面通知」开关；要靠系统级勿扰或会话级免打扰
- Linux 通常没有 Dock badge 与任务栏闪烁（依桌面环境而异）
- Web 版关闭浏览器通知后无法在 APP 内重新开启，需到浏览器设置中改
- Web 版需用户在浏览器对话框中点「允许」才能弹出通知，否则只能在 APP 内提示
- Web 端 / 桌面端不写 `bluedock_user_push_aliases`，因此不通过 APP 推送
- Web 端、桌面端（Electron）不走 APP 推送，靠 WebSocket + 本地通知
- 不支持替换为其他推送服务（如 FCM、个推、极光）
- 不支持选择 SSL/TLS 加密方式，默认使用 STARTTLS（端口决定）
- 不能按场景细分订阅（如「只关 SMTP 未读邮件、保留验证邮件」）
- 任务分配、审批通知、@提及不会单独发邮件，只通过站内消息和 APP 推送
- 任务创建 / 分配 / 截止提醒不直接发 APP 推送，通过聊天消息携带
- 任务栏闪烁只在 Windows 平台 + 窗口失焦时生效；macOS 不闪
- 免打扰只对该单个会话生效，不影响其他会话
- 免打扰只屏蔽 APP 推送和未读邮件，不屏蔽 WebSocket 站内消息（消息列表仍能看到）
- 关闭 BlueDock 进程后不会有通知（与移动端不同，没有后台守护服务）
- 关闭后 Dock 角标和任务栏闪烁仍会更新（属未读状态展示，不是通知）
- 关闭通知权限不会取消推送别名注册，重新打开权限即刻恢复
- 别名 30 天未活跃即不再用于推送（updated_at 过期）
- 卸载重装会重新申请通知权限；以前的免打扰会话状态仍保留在云端
- 审批通过 / 驳回不直接发 APP 推送，通过聊天消息携带
- 密码字段不能为空（即便部分 SMTP 服务允许匿名）
- 当环境变量 SYSTEM_SETTING=disabled 时禁止从界面修改推送设置（密钥会脱敏显示）
- 当环境变量 SYSTEM_SETTING=disabled 时禁止从界面修改邮箱设置
- 必须同时填 iOS 和 Android 的 key/secret 才能两端都收到；只填一端则只推一端
- 把账号邮箱清空不可行——系统多处校验需要邮箱（注册验证、密码重置）
- 收件人为忽略地址列表中的邮箱会立即报错「收件人地址错误或已被忽略」
- 时段免打扰（移动端本地）不影响桌面端通知；桌面端要单独关 OS/浏览器权限
- 时段免打扰只屏蔽本地通知和振动，消息正常到达 APP 消息列表（无云端时段 API）
- 普通用户没有「一键退订」开关，只能联系管理员把邮箱加入「忽略地址」
- 未授予系统通知权限时，连前台浮层通知都不显示（依赖 setVibrate 系统调用会被拒）
- 未读消息邮件只汇总指定时间窗口内的未读，不是每条消息都发
- 未配置 SMTP 时所有邮件功能都不发邮件，但不会报错给用户
- 未配置 APP 推送 appkey 时所有推送链路不工作，但站内消息正常
- 桌面通知不走 APP 推送通道，二者完全独立
- 没有「按周日生效」「按节假日生效」的细分；客户端时段是每天循环
- 没有「按时段免打扰单个会话」，只有会话级 mute / silence
- 测试不会落库到任何日志表，失败信息只在前端弹窗显示
- 测试邮件用的是表单当前未保存的值，不强制要求先保存
- 移动端通知由两部分组成：离线 APP 推送 + 前台 APP 内浮层；二者不能任选其一
- 自己发的消息不会推给自己
- 调试模式（isDebug=1）的 APP 不会注册别名，避免污染生产推送
- 邮件发送依赖第三方 SMTP，BlueDock 自身不内置邮件服务器
- 邮件发送失败不会重试，也不会通知发起方
- 邮件通道只用于系统通知（注册验证 / 改邮箱 / 未读消息 / 删除账号验证），不能用作客户营销邮件
- 默认 production_mode=true，意味必须用正式签名的 APP 包，开发版 APP 收不到

## 相关文档

- 验收细项：[checklist.md](checklist.md) · [`CHECKLIST.md`](../CHECKLIST.md) → `notify`
- API：[api.md](api.md)
- 推送 / 邮件：[app-push.md](../../infra/app-push.md) · [email.md](../../infra/email.md)
