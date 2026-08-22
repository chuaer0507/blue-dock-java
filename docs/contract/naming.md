# 命名规范（禁止简写）

本文件为 **API wire / Java 属性 / 物理库列** 的统一命名铁律。与 [domain-naming.md](domain-naming.md)、[`.agents/rules/json-naming.md`](../../.agents/rules/json-naming.md)、[`.agents/rules/database.md`](../../.agents/rules/database.md) 配套；冲突时以本文件「禁止简写」为准。

## 分层形态

| 层 | 形态 | 示例 |
| -- | ---- | ---- |
| JSON / Query / WS `data` | **camelCase 全词** | `userId`、`userImage`、`priorityLevel`、`parentId` |
| Java 属性 / 方法 | 与 wire **同形** | `getUserId()`、`userImage` |
| 物理表 | **`bluedock_` + snake_case 全词** | `bluedock_users`、`bluedock_projects` |
| 物理列 | **snake_case 全词**（无表前缀） | `user_id`、`user_img`、`priority_level`、`parent_id` |

## 铁律：绝对不要简写单词

1. **禁止**截断、缩写、拼音首字母凑合（如 `tel`、`msg`、`img`、`pid`、`fid`、`rid`、`az`、`num`、`ext`、`ua`、`kid`、`userid` 粘写）。
2. **禁止**单字母前缀拼语义（如 `priorityLevel` → 必须 `priorityLevel`）。
3. **布尔语义**优先 `is` / `allow` / `has` 前缀全词：`isBot`、`isShared`、`allowGuest`、`isMuted`。
4. **计数语义**用 `*Count`：`loginCount`、`unreadCount`、`downloadCount`、`openCount`。
5. **同一概念一套名**：wire / Java / 列三者只差大小写与分隔符，禁止「库用全称、API 仍用简写」。

### 允许的既有技术缩写（仅此白名单）

| 形式 | 说明 |
| ---- | ---- |
| `Id` / `_id` | 标识符后缀（`userId`、`project_id`） |
| `Url` / `_url` | 统一资源定位 |
| `Ip` / `_ip` | 网络地址 |
| `Uuid` | 通用唯一标识 |
| `Json` / `Http` / `Jwt` / `Rsa` / `Smtp` / `Ldap` | 协议或格式专名 |
| `Ai` | 产品领域既有专名（如 `aiAutoAnalyze`） |

白名单外的截断词 **一律扩写**。新增字段不得再引入简写。

## 简写 → 全词对照表（强制）

| 禁止（旧） | Wire / Java | 物理列 |
| ---------- | ----------- | ------ |
| `userId` / `userIds` | `userId` / `userIds` | `user_id` |
| `userImage` | `userImage` | `user_img` |
| `tel` | `telephone` | `telephone` |
| `az` | `nameAz` | `name_az` |
| `changePassword` / `changePass`（强制改密） | `mustChangePassword` | `must_change_password` |
| `meetingId` | `meetingId` | `meeting_id` |
| `shareKey` | `shareKey` | （参数/缓存，无列则仅 wire） |
| `pid`（父目录） | `parentId` | `parent_id` |
| `fid` | `fileId` | `file_id` |
| `rid` | `reportId` | `report_id` |
| `priorityLevel` / `priorityName` / `priorityColor` | `priorityLevel` / `priorityName` / `priorityColor` | `priority_level` / `priority_name` / `priority_color` |
| `loop` / `loopAt` | `loop` / `loopAt` | `loop`（MySQL 保留字用反引号）/ `loop_at` |
| `imgs` | `images` | `images` |
| `new_images` | `newImages` | —（助手 session/save 请求体；落库会话 `images` JSON） |
| `msg`（消息正文） | `body` | `body` |
| `DialogMsg`（类型/类名） | `DialogMessage` | 表 `bluedock_dialog_messages`（见下） |
| `findMsg` / `insertMsg` / `listMsgs` / `msgTop` | `findMessage` / `insertMessage` / `listMessages` / `messageTop` | — |
| `msgType` | `messageType` | —（局部变量 / SQL 别名 `message_type`） |
| `lastMsg` / `last_msg` | `lastMessage` | `last_message` |
| `noticeMsg` | `noticeMessage` | —（邮件设置 JSON） |
| `softDeleteMsg` / `initMsgReads` / … | `softDeleteMessage` / `initMessageReads` / … | — |
| `task_user_checkin_*` / `task_user_check_in_*` | — | `task_user_attendance_*` |
| `task_meeting_msgs` | — | `bluedock_meeting_messages` |
| `messageId` / `message_id` | `messageId` | `message_id` |
| `kid` | `keyId` | `key_id` |
| `bot`（标志） | `isBot` | `is_bot` |
| `ua` | `userAgent` | `user_agent` |
| `num`（打开次数） / `webhook_num` | `openCount` / `webhookCount` | `open_count` / `webhook_count` |
| `ext` | `extension` | `extension` |
| `mac` | `macAddress` | `mac_address` |
| `thumb` | `thumbnail` | `thumbnail` |
| License / 设备 wire `mac` | `macAddresses` | —（列表）；单值用 `macAddress` |
| `oldPassword` | `oldPassword` | — |
| `mute`（个人免打扰） | `isMuted` | `is_muted` |
| `chatMute`（群禁言） | `isChatMuted` | `is_chat_muted` |
| `top` / `hide` | `isTop` / `isHidden` | `is_top` / `is_hidden` |
| `unread` / `mention`（计数） | `unreadCount` / `mentionCount` | `unread_count` / `mention_count` |
| `top`（DialogView 字段） | `isTop` | （会话列表取自 `is_top`） |
| `ext`（文件 JSON） | `extension` | `extension` |
| `msgUnread*`（邮件设置） | `messageUnread*` | — |
| SQL 列 `bot` | `isBot` | `is_bot` |
| `deputy`（标志） | `isDeputy` | `is_deputy` |
| `silence` | `isSilent` | `is_silent` |
| `share`（标志） | `isShared` | `is_shared` |
| `guest`（允许访客） | `allowGuest` | `allow_guest` |
| `owner`（**角色枚举** 0/1/2） | `owner` | `owner`（完整词，不是布尔，勿改成 `isOwner`） |
| `personal`（布尔） | `isPersonal` | `is_personal` |
| `login_num` / `loginNum` | `loginCount` | `login_count` |
| `line_ip` / `line_at` | `onlineIp` / `onlineAt` | `online_ip` / `online_at` |
| `download`（计数） | `downloadCount` | `download_count` |
| `desc` | `description` | `description` |
| `encrypt`（密码算法字段） | `passwordEncrypt` | `password_encrypt` |
| `tagids` | `tagIds` | —（请求参数） |
| `timerange` | `timeRange` | —（请求参数） |
| `removeids` | `removeUserIds` | —（请求参数） |
| `dialogids` | `dialogIds` | —（请求参数） |
| `macs` | `macAddresses` | —（请求参数 / License status 本机列表；列仍 `mac_address`） |
| `ldap_open` / `ldap_host` … | `ldapOpen` / `ldapHost` … | —（`thirdAccessSetting` JSON） |
| `ignore_addr` / `smtp_*`（设置 snake） | `ignoreAddr` / `smtpHost` … | —（`emailSetting` JSON） |
| `menu_items` / `visible_to` / `keep_alive` … | `menuItems` / `visibleTo` / `keepAlive` … | —（`microAppMenu` JSON） |
| `expired_at`（落盘设置） | `expiredAt` | —（License 落盘 JSON；外部原文仍可读 `expired_at`） |
| `dedup` | `idempotency` | —（Redis key 段：`bluedock:…:idempotency:{eventId}`） |
| `msgRevLimit` / `msgEditLimit` | `messageRecallLimit` / `messageEditLimit` | —（系统设置 JSON） |
| `remindin` / `remindexceed` | `remindIn` / `remindExceed` | —（签到设置 JSON） |

路径、Kafka topic、Redis key **段名**同样避免简写；**新建路径不得再引入厂商/简写段**。

### 路径段扩写示例（强制）

| 禁止（旧路径段） | 全词（新） |
| ---------------- | ---------- |
| `needcode` / `codeimg` / `codejson` | `needCode` / `codeImage` / `codeJson` |
| `reg/needinvite` | `register/needInvite` |
| `editpass` / `createuser` / `editdata` | `editPassword` / `createUser` / `editData` |
| `msg/*` | `message/*` |
| `checkin` / `checkIn` / `signin`（应用卡片 value） | `attendance` |
| `imgupload` / `fileupload` / `imgview` | `imageUpload` / `fileUpload` / `imageView` |
| `reindex` | `rebuild` |
| `apppush` / `aibot` / `cnip` | `appPush` / `aiBot` / `chinaIp` |
| `adddeputy` / `deldeputy` / `del` | `addDeputy` / `deleteDeputy` / `delete` |
| `easylists` / `subdata` / `analysave` | `easyLists` / `subtaskData` / `analysisSave` |
| `match_elements` / `last_submitter` | `matchElements` / `lastSubmitter` |
| `microapp_menu` / `thirdaccess` | `microAppMenu` / `thirdAccess` |
| `browse_save` / `appsort` | `browseSave` / `appSort` |
| `template_*` / `ai_*` / `content_history` | camelCase（`templateList` / `aiGenerate` / `contentHistory`） |
| `aibotSetting` | `aiBotSetting`（`bluedock_settings.name`） |
| `search:reindex` | `search:rebuild`（Redis key 段） |
| `wordchain`（消息 type / 方法） | `wordChain` |
| `qrcode` | `qrCode`（类 `QrCodeLoginService`；Redis `bluedock:auth:qrCode:`） |
| `openai_key` / `*_models`（AI 设置） | `openaiKey` / `openaiModels` |
| `ios_key` / `android_key`（appPush） | `iosKey` / `androidKey` |
| `bluedock:bot:`（Redis） | `bluedock:userBot:` |
| `/api/users/bot` · `task.bot.webhook` | `/api/users/userBot` · `bluedock.userBot.webhook` |
| `dialog_open` / `member_join` / `bot_uid` / `msg_uid` | `dialogOpen` / `memberJoin` / `botUserId` / `messageUserId` |
| `findByTaskTypeMsg` / `getImgs` / `runCheckin` / `runCheckIn` | `findByTaskTypeMessage` / `getImages` / `runAttendance` |
| `BotWebhook*`（类） | `UserBotWebhook*` |
| `allocateUid` / `botUid` | `allocateAgoraUserId` / `botUserId` |
| `dialog.msg*`（WS type / i18n） | `dialog.message*` |
| `task.easylist_userIds` | `task.easy_list_user_ids` |
| `SystemBots` / `task.sub_*`（i18n） | `SystemUserBots` / `task.subtask_*` |
| `subData`（路径/方法） | `subtaskData` |
| `allowExts` | `allowExtensions` |
| `generateDesc` / `incrLinkNum` / `findLinkByRidUser` | `generateDescription` / `incrementLinkOpenCount` / `findLinkByReportIdAndUserId` |
| `listMacs` / `parseMacs` / `isMac` | `listMacAddresses` / `parseMacAddresses` / `isMacAddress` |
| `bluedock:meeting:tourist:{uid}` | `bluedock:meeting:tourist:{agoraUserId}` |
| `msgs`（会议 open/invitation wire） | `messages` |
| `appid` / `uid`（会议 open wire） | `appId` / `agoraUserId` |
| `ym`（签到 list 查询参数） | `yearMonth` |
| `down`（导出下载路径） | `download` |
| `addSub` | `addSubtask` |
| `cn`（chinaIp 响应布尔） | `isChina` |
| `setadmin` / `clearadmin` / `settemp` / `cleartemp` | `setAdmin` / `clearAdmin` / `setTemporary` / `clearTemporary` |
| identity `temp` | `temporary` |
| `user.op_temp_admin` | `user.op_temporary_admin` |
| 局部 `uid` / `ext` / `ua` / `extOf` | `userId`（或 `*UserId`）/ `extension` / `userAgent` / `extensionOf` |

## 刻意保留（0.x 例外）

| 项 | 原因 |
| -- | ---- |
| Java 包名 `apppush`（及需 camel 的目录） | 全小写惯例；APFS 不可改成 `appPush` |
| 上游推送 HTTP 协议字段（`alias_type` 等） | 第三方协议 |
| 物理库列 snake（`user_id`、`bot_id`、`webhook_url`…） | 分层形态；wire 已 camel |
| 系统机器人邮箱前缀（`system-msg@bot.system` 等） | 存量身份标识；本轮已将 `check-in@` 改为 `attendance@` |
| LDAP 属性名 `cn` / `uid` | 目录协议字段，非业务 wire |
| JDBC `RowMapper` 参数名 `rowNum` | Spring 框架惯例 |
| 签到 modes 值 `locat` | 产品历史 mode token；设置字段用全词 `locationLatitude` 等 |

### 签到 → attendance（本轮）

| 禁止（旧） | Wire / Java / 路径 | 物理列 / 表 |
| ---------- | ------------------- | ----------- |
| `checkin` / `checkIn` | `attendance` | — |
| `signin`（应用中心系统应用 value） | `attendance` | — |
| `checkInSetting` | `attendanceSetting` | — |
| `CheckIn*`（类） | `Attendance*` | — |
| `check-in@bot.system` | `attendance@bot.system` | — |
| `bluedock:checkIn:…` | `bluedock:attendance:…` | — |
| `task_user_check_in_*` / `check_date` / `checkDate` | `attendanceDate` | `task_user_attendance_*` / `attendance_date` |
| `check_in.*`（i18n） | `attendance.*` | — |
| Kafka `kind=checkIn` | `kind=attendance` | — |

### Java 包名说明

- 包路径保持 **全小写**（语言惯例）：`com.bluedock.user.attendance`、`com.bluedock.user.app.sort`。
- APFS 默认大小写不敏感，**不可**把包目录改成 camelCase（如 `appPush` 会与 `apppush` 冲突）。
- 类名 / API / Redis 段 / 设置项用全词：`AttendanceSettingService`、`/api/.../attendance`、`bluedock:attendance:…`、`attendanceSetting`。

## 开发阶段改名策略

当前版本 `0.x` / `1.0.0-SNAPSHOT`：**直接改** Flyway `V1`、Java DTO、Controller 参数与 `docs/contract/api-contract.md`，不保留简写别名。  
上生产后：只允许 additive 迁移 + 契约版本策略（见 database 规则）。

## 文档同步

改名后必须同步：

- 本文件词表（若增删条目）
- `docs/contract/api-contract.md` 与相关 `docs/modules/*/api.md`
- `docs/data/database.md`
- 前端 `packages/shared`（跨仓）
