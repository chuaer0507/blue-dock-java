# ID 生成策略

## 定稿（1.0 前）

| 项 | 约定 |
| -- | ---- |
| 业务主键类型 | **`BIGINT`**（无符号语义，Java 用 `long`） |
| 生成方式 | **Snowflake**（或兼容的分布式号段），集中在 `bluedock-common` 的 `IdGenerator` |
| API wire | **JSON string**（所有 `BIGINT` 业务 ID 统一序列化为十进制字符串；请求 query 参数与 JSON body 同时接受数字文本） |
| 物理列 | `id BIGINT NOT NULL`（`bluedock_users.id`）；API wire 仍称 **`userId`** |

不采用 DB `AUTO_INCREMENT` 作为唯一来源（多实例 / 分库不友好）；单机开发可用雪花 workerId=1。

## 特殊 ID

| 对象 | 说明 |
| ---- | ---- |
| 超级管理员 | 约定 `bluedock_users.id = 1` 为系统首个注册用户（与权限文档一致；API wire 仍称 userId） |
| 机器人用户 | `bot=1` 的特殊 userId；系统机器人预置固定段或种子数据 |
| 会议号 `meetingId` | **业务可见码**（如 11 位字母数字），不是表 PK；表 PK 仍为 BIGINT |
| 邀请码 / 分享码 | 独立短码字段，非主键 |
| 上传 `uploadId` | UUID 字符串（会话级，Redis） |

## 外键

- 列名：`{entity}_id`（`project_id`、`dialog_id`、`task_id`）
- 用户外键物理列：**`user_id`**（及 `owner_user_id` / `archived_user_id` / `deleted_user_id`）；API / JSON wire 仍用 camelCase **`userId`**（与前端契约一致，勿把 DB 列名泄漏到 wire）
- DB 层可不建物理 FOREIGN KEY（便于归档/软删）；以应用层保证引用完整

## 备选（未采用）

| 方案 | 为何不做默认 |
| ---- | ------------ |
| UUID v7 | API/客户端历史为数字 ID；改 string 成本高 |
| 纯 AUTO_INCREMENT | 水平扩展与预分配困难 |

若未来绿字段（新微服务）需要 UUID，须在契约中显式声明，勿与既有 BIGINT 混用同一资源路径。

## 客户端精度约束

雪花 ID 已可超过 JavaScript 的 `Number.MAX_SAFE_INTEGER`，客户端不得将业务 ID 转换为 `number`、参与数值比较或以数值类型保存。路由、Query Key、HTTP 参数与领域模型均使用十进制字符串；仅分页、排序值、状态码等非 ID 数值保留 JSON number。
