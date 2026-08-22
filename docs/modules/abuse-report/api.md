# 举报 — API

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/complaint/lists` | 管理员 | `type?` · `status?` · `page`/`pageSize` → `{list,page,pageSize,total}` |
| POST | `/api/complaint/submit` | 登录成员 | body：`dialogId` · `type` · `reason` · `images?[{path}]` |
| POST | `/api/complaint/action` | 管理员 | body：`id` · `type`=`handle`\|`delete` |

类型受控：`10/20/30/40/50/60/70`。提交成功后桌面通知最近 10 名管理员（`NotifySendEvent` desktop）。详见 [overview](overview.md) · [infra 无单独篇，表见 database.md]。
