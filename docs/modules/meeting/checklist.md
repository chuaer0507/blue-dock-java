# 会议 — 验收清单

## 后端（已落地）

- [x] 创建 / 加入：`GET /api/users/meeting/open`（create / join + Agora token）
- [x] 分享链接：`link`（Redis TTL 6h）
- [x] 游客：`tourist` + shareKey 入会
- [x] 邀请：`invitation`（meeting-alert 机器人会议卡片）
- [x] 从对话发起：create 带 `userIds` 写会议卡片
- [x] 结束 / 自动关房：`CloseMeetingRoomScheduler` + 卡片 `dialog.message.update`
- [x] 会中聊天：走对话消息通道（非独立会议 IM）

> 本仓只验收后端 API / token / 卡片；Agora 音视频 UI 不在范围。

详见 [overview.md](overview.md) · [api.md](api.md) · [meeting-agora.md](../../infra/meeting-agora.md)。
