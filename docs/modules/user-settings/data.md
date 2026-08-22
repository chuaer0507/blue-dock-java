# 个人设置 — 数据

无独立「settings」聚合表；能力散落在用户主表与附属表。

## 表

| 逻辑名 | 物理表 | 要点 |
| ------ | ------ | ---- |
| users | `bluedock_users` | 资料字段、`lang`、`identity`、`email_verify`、`must_change_password` |
| email_verifications | `bluedock_user_email_verifications` | `type`=reg\|edit\|delete；30min；一次性 |
| user_tags | `bluedock_user_tags`（V1） | 个性标签；`user_id` 被贴人 · `creator_user_id` · name≤20；软删 |
| app_sorts | `bluedock_user_app_sorts` | 个人应用排序 JSON |
| devices | `bluedock_user_devices`（及会话 Redis） | 设备列表 / 踢下线 |
| tag_recognitions | `bluedock_user_tag_recognitions`（V1） | 认可；唯一 `(tag_id,user_id)` |

## Redis

| 用途 | Key 约定 | 说明 |
| ---- | -------- | ---- |
| 登录会话 | `bluedock:session:*`（见 RedisKeys） | 注销 / 踢设备时撤销 |
| 邮箱验证码冷却 | 随 email 验证服务 | send/edit 冷却 |

## 注销副作用（实现口径）

- 主资料软删 / 匿名化、token 与设备会话清除
- 其创建的任务 / 消息等业务行保留，展示为「已注销用户」
- 邮箱保护期见实现（防立刻重用）

详见 [overview.md](overview.md)。
