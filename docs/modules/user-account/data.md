# 账号 — 数据

| 表 | 要点 |
| -- | ---- |
| `bluedock_users` | PK `id`；email 唯一；identity；password 哈希；bot 标记 |
| `bluedock_auth_key_pairs` | 登录/改密 RSA 密钥对；`keyId` + PEM；`status=active` |
| `bluedock_user_devices` | Token 设备会话 |
| `bluedock_user_email_verifications` | 邮箱验证链接（`code`/`type`/`status`；30 分钟） |
| `bluedock_user_deletes` | 注销申请 / 记录 |

密码：**永不**出现在成功响应 `data` 中（见 `.agents/rules/password-wire.md`）。
