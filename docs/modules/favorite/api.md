# 收藏与最近 — API

| 路径 | 说明 | 状态 |
| ---- | ---- | ---- |
| `GET api/users/favorites` | 分页列表；`type`=task/project/file/message | 已落地 |
| `POST api/users/favorite/toggle` | 切换收藏 | 已落地 |
| `POST api/users/favorite/remark` | 修改备注 | 已落地 |
| `POST api/users/favorites/clean` | 清理（可按 type） | 已落地 |
| `GET api/users/favorite/check` | 是否已收藏 | 已落地 |
| `GET api/users/task/browse` | 任务浏览历史；`limit`≤50 | 已落地 |
| `GET api/users/task/browseSave` | 显式记录任务浏览；`taskId` | 已落地 |
| `POST api/users/task/browseClean` | 清理任务浏览；`keepCount`（默认 100，0=全清） | 已落地 |
| `GET api/users/recent/browse` | 最近访问；`type`=task/file/task_file/message_file；分页 | 已落地 |
| `POST api/users/recent/delete` | 删除最近访问；`id`=recordId | 已落地 |

## 表

- `bluedock_user_favorites`：唯一 `(userId, fav_type, ref_id)`
- `bluedock_user_task_browses`：唯一 `(userId, task_id)`；打开任务详情自动 upsert
- `bluedock_user_recent_items`：唯一 `(userId, target_type, target_id, source_type, source_id)`
- `bluedock_task_files`：任务附件元数据

## 自动写入

| 触发 | 写入 |
| ---- | ---- |
| `TaskService.one` / `browseSave` | `task_browses` + recent(`task`) |
| `FileService.one` | recent(`file`) |
| `TaskFileService.detail` | recent(`task_file`，source=`project_task`) |

`message_file` 列表可展示；打开消息附件时的写入钩子待 messenger 接入。

响应 JSON 为 camelCase（`pageSize` / `recordId` / `deletedCount` 等）。
