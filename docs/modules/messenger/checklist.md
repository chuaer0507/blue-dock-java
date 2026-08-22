# 即时通讯 — 验收清单

## 会话
- [x] 打开单聊；创建普通群（`open/user` · `group/add`）
- [x] 群：改名、加人、踢人、转让、解散、退出（`group/edit|addUser|deleteUser|transfer|disband`；退出=deleteUser 自身；不可拉机器人入群）；管理员搜个人群 `group/searchUser`；共同群 `common/list`
- [x] 项目群 / 任务群 / 部门群成员随源数据同步（部门 / 项目自动；任务按需 `task/dialog` + visibility 同步；任务群消息可见经 `TaskDialogAccessBridge`）
- [x] 置顶、隐藏、免打扰（普通群 `mute`）；标注（`tag` + `search/tag`）
- [x] 群禁言 `isChatMuted`（`bluedock_dialog_configs` + 系统 `userPrivateChatMute`/`userGroupChatMute`/`allGroupMute`）
- [x] `open/event`（bot dialogOpen）；会话待办 `dialog/todo`
- [x] 列表外 `beyond`、会话搜索 `search`
- [x] OKR 评论群：`okr/add` · `okr/push`（`group_type=okr` + `okr-alert` 机器人）

## 消息
- [x] 文本 / 图片 / 文件（`sendFile` / `image64` / `sendSticker` / `sendFiles` 群发 + `sendFileId`）/ 任务卡片（`sendTaskId`）/ 引用 / @ 用户与 @所有人（`mention`/`mentionIds` + WS `mentionUserIds`）
- [x] 撤回（时限内；`messageRecallLimit`/`messageRecallLimit` 分钟；0=不限；自聊与机器人作者豁免）
- [x] 投票、接龙（状态存落库 `body` JSON；`dialog.message` / `dialog.message.update`）
- [x] 待办设置 / 完成 / 提醒
- [x] 消息置顶、表情回复、转发 / 合并转发
- [x] 已读回执；未读数正确（`msg/read` · `unread` · `readList` + `bluedock_dialog_message_reads`）
- [x] 标记已读/未读（`message/mark`）；消息标注（`message/tag`）；会话色（`message/color`）；翻译缓存（`message/translation`）

## 搜索与历史
- [x] 搜会话（`search` / `search/tag`）；消息全文搜见 search 模块
- [x] 历史分页加载（`msg/list`：`beforeId` + `take`）

## 机器人
- [x] 系统机器人推送可见（任务 AI 建议卡片经 `ai-openai@bot.system` + `TaskAiDialogBridge`）；不可对机器人单聊对发；不可拉机器人入普通群
- [x] `sendBot` · `sendAnon` · `sendNotice`
- [x] `sendTemplate` · `sendApprove`
- [x] `sendRecord` · `convertRecord` · `voiceToText`
- [x] `sendAiAssistant` · `sendLocation`
- [x] `sticker/search` · `message/sendSticker`
- [x] `aiGenerate` · `webhookMessageToAi` · `applied`（废弃占位 `{deprecated:true}`）

## 待实现

（无；契约路径已齐，废弃项见 api.md）

详见 [overview.md](overview.md) · [permissions.md](permissions.md) · [api.md](api.md)。
