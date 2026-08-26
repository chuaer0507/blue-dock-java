# 即时通讯 — API

前缀 `api/dialog`。完整表见 [api-contract.md](../../contract/api-contract.md)。

## 已实现

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/dialog/lists` | Bearer | 当前用户会话列表（隐藏会话除外）；任务群按任务可见性过滤 |
| `GET /api/dialog/beyond` | Bearer | 列表外会话（已隐藏）；任务群同上 |
| `GET /api/dialog/search` | Bearer | 搜会话；`key` · `take?`（名/摘要/单聊对方）；任务群同上 |
| `GET /api/dialog/search/tag` | Bearer | 搜个人标签会话；`key?`（空=全部有标签）· `take?` |
| `GET /api/dialog/one` | Bearer | 会话详情；`dialogId` |
| `GET /api/dialog/user` | Bearer | 成员 userId 列表 |
| `GET /api/dialog/telephone` | Bearer | 单聊对方电话；`dialogId`；临时账号不可查；→ `{ telephone, add }`（`add` 为 notice） |
| `GET /api/dialog/open/user` | Bearer | 打开/创建单聊；`userId` |
| `GET /api/dialog/open/event` | Bearer | 打开会话事件（bot `dialogOpen`）；`dialogId` → 会话视图 |
| `GET /api/dialog/session/create` | Bearer | AI 新会话；`dialogId` · `title?` → `DialogSessionView` |
| `GET /api/dialog/session/list` | Bearer | AI 会话列表；`dialogId` |
| `GET /api/dialog/session/open` | Bearer | 切换当前 AI 会话；`dialogId` · `sessionId` |
| `POST /api/dialog/session/rename` | Bearer | 重命名；`dialogId` · `sessionId` · `title` |
| `GET /api/dialog/todo` | Bearer | 当前用户在会话上的消息待办；`dialogId` · `includeDone?`（默认 0） |
| `GET /api/dialog/top` | Bearer | 置顶；`dialogId` · `top`（0/1） |
| `GET /api/dialog/hide` | Bearer | 隐藏/恢复会话；`dialogId` · `isHidden?`（默认 1；`0`=恢复到列表） |
| `GET /api/dialog/config` | Bearer | 配置；`dialogId` → `isMuted`/`isTop`/`isHidden`/`tag`/`isChatMuted`/`color` |
| `POST /api/dialog/config/save` | Bearer | 保存；`isMuted?`/`tag?`/`isChatMuted?`（群禁言仅群主/管理员，普通群） |
| `GET /api/dialog/message/silence` | Bearer | 免打扰快捷开关；`dialogId` · `isSilent`（默认 1）；同 `isMuted` |
| `GET /api/dialog/message/list` | Bearer | 消息列表；`dialogId` · `beforeId?` · `take?`；AI 单聊按当前用户会话键隔离，任务群须对挂接任务可见 |
| `GET /api/dialog/message/latest` | Bearer | 多会话增量；`dialogs` JSON 数组（`id`/`dialogId`+`latestId?`，≤5）· `take?`（默认 25，≤50） |
| `GET /api/dialog/message/one` | Bearer | 单条消息；`messageId` |
| `GET /api/dialog/message/detail` | Bearer | 详情；`messageId` · `onlyUpdateAt?`；file/image 附带 `file` 元数据 |
| `GET /api/dialog/message/download` | Bearer | 附件；`messageId` · `down=yes|preview`（preview/非本地回退 JSON `url`） |
| `GET /api/dialog/message/mergeDetail` | Bearer | 合并转发详情；`messageId` → `{ messages }` |
| `GET /api/dialog/message/dot` | Bearer | 清红点；`messageId` → `{ messageId, dot:0 }` |
| `GET /api/dialog/message/checked` | Bearer | 文本清单项；`dialogId`·`messageId`·`index`·`checked`（0/1）；仅本人 |
| `POST /api/dialog/message/stream` | Bearer | 通知用户听流；`userId`·`streamUrl`·`source?`（`ai` 时规范化 `/ai…`） |
| `GET /api/dialog/message/mark` | Bearer | 会话标记；`dialogId`·`type=read\|unread`·`afterMessageId?`；read 清未读/`markUnread`，unread 仅置 `markUnread=1`；→ 未读快照 |
| `GET /api/dialog/message/tag` | Bearer | 消息标注切换；`messageId`；禁 `tag`/`todo`/`notice`；→ `{ messageId, tag, add }`（`tag`=标注者 userId，0=取消）+ WS `dialog.message.update`；消息视图含 `tagUserId` |
| `GET /api/dialog/message/color` | Bearer | 当前用户会话颜色；`dialogId`·`color?`（空=清除，≤32）；→ `DialogConfigView`（含 `color`） |
| `GET /api/dialog/message/translation` | Bearer | 翻译；`messageId`·`language`·`force?`；仅 text/record；按语言缓存；依赖 AI；→ `{ messageId, language, content }` |
| `POST /api/dialog/message/sendText` | Bearer | 发文本；`dialogId` · `text` · `replyId?`；群内 `@用户`/`@所有人` 累加成员 `mention`；任务群内 `@#` 经 `TaskMentionBridge` 写关联 |
| `GET /api/dialog/message/sendFileId` | Bearer | 发文件/图片（本人网盘 `fileId`）；载荷 JSON |
| `GET /api/dialog/message/sendTaskId` | Bearer | 发任务卡片；`dialogId` · `taskId` · `note?`/`text?` · `replyId?`；须对任务可见；任务群内自动关联源任务 |
| `POST /api/dialog/message/sendFile` | Bearer | 直传；multipart `files` · `dialogId` · `filename?` · `replyId?` |
| `POST /api/dialog/message/image64` | Bearer | Base64 发图；`dialogId` · `image`（data-URL 或裸 base64）· `filename?` · `replyId?`；≤5MB |
| `POST /api/dialog/message/sendSticker` | Bearer | 在线表情发图；`dialogId` · `src` · `name?` · `replyId?`；服务端拉图≤5MB |
| `POST /api/dialog/message/sendFiles` | Bearer | 群发；multipart `files`（≤20）· `dialogId` 或 `dialogIds`（逗号分隔，≤20）→ 消息列表 |
| `POST /api/dialog/message/sendNotice` | Bearer | notice；`dialogId` 或 `dialogIds` · `notice`（≤500）· `silence?` · `source?` |
| `POST /api/dialog/message/sendAnon` | Bearer | 匿名消息；`userId` · `text`（≤2000）；经 `anon-msg@bot.system`；受 `anonMessage` 开关 |
| `POST /api/dialog/message/sendBot` | Bearer | 机器人 markdown；`userId` · `text` · `botType?`（默认 system-msg）· `botName?` · `silence?` |
| `POST /api/dialog/message/sendTemplate` | Bearer | 模板卡片；`dialogId`/`dialogIds` · `content`（JSON 数组 `[{content,style}]`，各项 content/style ≤300）· `title?` · `silence?` · `source?` |
| `POST /api/dialog/message/sendApprove` | Bearer | 审批卡片（`approval-alert` 机器人，静默）；`toUserId` · `type`（approve_reviewer/notifier/submitter/comment_notifier）· `action?` · `isFinished?` · `data?` · `title?` |
| `POST /api/dialog/message/sendRecord` | Bearer | 语音；`dialogId` · `base64`（`data:audio/mp3\|wav;base64,…`）· `duration`（ms，≥600）· `replyId?`；落 `chat/{dialogId}/…`；→ type=`record` |
| `POST /api/dialog/message/convertRecord` | Bearer | 录音转写（不落消息）；同上 base64/duration · `dialogId?`（上下文）· `translate?`；依赖 AI；→ `{ text }` |
| `GET /api/dialog/message/voiceToText` | Bearer | 已有语音转写；`messageId`；结果写 `body.text`/`textUserId`；→ 消息视图 |
| `POST /api/dialog/message/sendAiAssistant` | Bearer | AI 助手发信；`dialogId` 或 `taskId` · `text` · `textType?`（md）· `nickname?` · `silence?`；发送者 `ai-openai@bot.system` |
| `POST /api/dialog/message/sendLocation` | Bearer | 位置；`dialogId` · `type`（baidu/amap/tencent）· `lng` · `lat` · `title` · `distance?` · `address?` · `thumb?` |
| `GET\|POST /api/dialog/message/aiGenerate` | Bearer | **废弃占位**；→ `{ deprecated: true }` |
| `GET\|POST /api/dialog/message/webhookMessageToAi` | Bearer | **废弃占位**；→ `{ deprecated: true }` |
| `GET\|POST /api/dialog/message/applied` | Bearer | **废弃占位**；→ `{ deprecated: true }` |
| `GET /api/dialog/sticker/search` | Bearer | 在线表情；`key?`；→ `{ list:[{name,src,height,width}] }`（外网失败为空列表） |
| `GET /api/dialog/message/forward` | Bearer | 逐条转发；`messageIds` · `dialogIds` |
| `GET /api/dialog/message/mergeForward` | Bearer | 合并转发到单会话；`messageIds` · `dialogId` |
| `GET /api/dialog/message/emoji` | Bearer | 表情回复/取消（`symbol`+`cancel`）或仅查列表 |
| `GET /api/dialog/message/emojiMap` | Bearer | 批量表情聚合；`messageIds`（逗号分隔，最多 100）→ `[{messageId,emojis}]` |
| `GET /api/dialog/message/top` · `topInfo` | Bearer | 消息置顶/取消；查询会话置顶列表 |
| `GET /api/dialog/message/todo` · `todoList` · `done` | Bearer | 设/取消/列表/完成待办 |
| `POST /api/dialog/message/todoRemind` | Bearer | 设置提醒时间 `remindAt`（空=清除） |
| `POST /api/dialog/message/vote` | Bearer | 发起：`dialogId`+`title`+`options`；投票：`messageId`+`option`；结束：`messageId`+`end=1` |
| `POST /api/dialog/message/wordChain` | Bearer | 发起：`dialogId`+`title`；参与：`messageId`+`text`；停止：`messageId`+`stop=1` |
| `GET /api/dialog/message/read` | Bearer | 已读至 `messageId`（可空=全部）；清未读 |
| `GET /api/dialog/message/unread` | Bearer | 未读会话列表 |
| `GET /api/dialog/message/readList` | Bearer | 单条消息谁已读/未读 |
| `GET /api/dialog/message/withdraw` | Bearer | 撤回自己的消息；`messageId`；受系统 `messageRecallLimit`（分钟）限制，0=不限；自聊与机器人作者豁免 |
| `GET /api/dialog/group/add` | Bearer | 建普通群；`userIds`（含自己至少 2 人）；不可含机器人 |
| `GET /api/dialog/group/edit` | Bearer | 改名/头像；群主或管理员 |
| `GET /api/dialog/group/addUser` | Bearer | 加人；不可含机器人 |
| `GET /api/dialog/group/deleteUser` | Bearer | 踢人；或成员退出（`userIds`=自己，群主不可直接退） |
| `GET /api/dialog/group/transfer` | Bearer | 转让群主 |
| `GET /api/dialog/group/addDeputy` · `deleteDeputy` | Bearer | 任命/罢免管理员；→ 当前管理员 `userId` 列表 |
| `GET /api/dialog/group/deputies` | Bearer | 普通群管理员 `userId` 列表 |
| `GET /api/dialog/group/disband` | Bearer | 解散（仅群主） |
| `GET /api/dialog/group/searchUser` | Bearer · 系统管理员 | 按群名搜普通个人群；`key?`；→ `{list:[DialogView…]}` 最多 20 |
| `GET /api/dialog/common/list` | Bearer | 本人普通个人群；`targetUserId?` 共同群；`onlyCount=yes`→`{total}`；否则分页 `{list,page,pageSize,total}`（默认 20，最大 100） |
| `POST /api/dialog/okr/add` | Bearer | 创建/复用 OKR 评论群；`okrId` · `name?` · `userIds?`；`group_type=okr`；自动加入 `okr-alert@bot.system` |
| `POST /api/dialog/okr/push` | Bearer | OKR 提醒机器人发文；`dialogId` 或 `okrId` · `text`（markdown）；调用者须为成员 |

消息发送后写入 `bluedock_dialog_message_reads`（发送者已读，其他人 `read_at` 空）；WS 经 Kafka fanout。
群聊文本中的用户 @：`<span class="mention user" data-id="{userId}">` / `[:@:{userId}:…]`；@所有人：`class="mention all"` / `data-id="all"` / `[:@:0:]`。命中成员（非发送者、非机器人）累加 `bluedock_dialog_users.mention` 与 `mention_ids`；fanout 可带 `mentionUserIds`。单聊不额外累计。机器人 webhook 用同一解析器；`@所有人` 对 bot 视为 mention。
任务群文本若含 `<span class="mention task" data-id="…">` 或 `[:#:id:name:]`，由 `TaskMentionBridge`（bluedock-task）为挂在该 `dialogId` 上的任务建立双向关联。

投票 `body` JSON：`{title,multiple,ended,options:[{text,votes:[userId…]}]}`。接龙：`{title,stopped,items:[{userId,text,at}]}`。投/接后推 `dialog.message.update`。

系统群：`group_type=project|task|department`，由领域模块经 `*GroupBridge` 维护；任务群按需 `GET /api/project/task/dialog`。OKR 评论群：`group_type=okr`，`link_id=okrId`，经 `okr/add` · `okr/push`。

## 废弃占位

| URL | 说明 |
| --- | ---- |
| `GET\|POST /api/dialog/message/aiGenerate` | 旧会话 AI 生成；返回 `{deprecated:true}` |
| `GET\|POST /api/dialog/message/webhookMessageToAi` | 旧 webhook 转 AI；同上 |
| `GET\|POST /api/dialog/message/applied` | 旧「已应用」；同上 |

权限见 [permissions.md](permissions.md)。
