# 文件 — 数据模型

总览见 [database.md](../../data/database.md)。分片会话见 [redis.md](../../data/redis.md) / [upload](../upload/overview.md)。

## files（`bluedock_files`）

| 列 | 说明 |
| -- | ---- |
| id | BIGINT PK |
| parent_id | 父文件夹；根为 0（API wire `parentId`） |
| cid | 复制来源（可选） |
| name / type / ext | 名称与类型 |
| size | 字节 |
| hash | 内容哈希（秒传） |
| user_id | 拥有者（共享目录内仍记目录主；API wire `userId`） |
| created_user_id | 实际上传者（API/领域仍见 createdId） |
| share | 是否共享 |
| deleted_at | 软删 / 回收站（`trash` 列根；`restore` 清标记） |

约束：同一目录直接子项数量上限（产品约定，如 300）。

## file_contents（`bluedock_file_contents`）

| 列 | 说明 |
| -- | ---- |
| file_id | 文件 ID（API wire 常作 `fileId`） |
| content / text | 正文或抽取文本（搜索） |
| size / user_id | 版本大小与编辑者 |
| deleted_at | 历史版本软删 |

每次保存新增版本行；`content/restore` 将历史版再插入为最新版（中间版本保留）。表已在 V1 落地。

## file_users / file_links

| 表 | 说明 |
| -- | ---- |
| `bluedock_file_users` | 共享成员；`file_id`；`permission` 0=只读 / 1=读写；单文件最多 100 人；V1 已建 |
| `bluedock_file_links` | 公开链接；`file_id` + `code`；`allow_guest`；刷新则软删旧码再建；V1 已建 |

## 存储路径（示意）

`uploads/file/{type}/{yyyyMM}/{fileId}/`；临时分片 `uploads/tmp/chunks/{userId}/{uploadId}/`。

物理引擎由管理端 `oss`（`bluedock_settings`）决定，见 [oss-settings.md](../../infra/oss-settings.md)。**凡正式上传须有 DB 行**（网盘 `bluedock_files`；系统/管理上传库 `bluedock_upload_objects`，见 [upload-objects.md](../../infra/upload-objects.md)）。
