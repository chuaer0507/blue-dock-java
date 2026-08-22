# Redis

版本：**8.2.8** Extended。用途：会话、缓存、在线、限流、锁、上传会话。**禁止**用 List/Stream 当跨域业务 MQ（走 Kafka）。

规则见 [`.agents/rules/redis.md`](../../.agents/rules/redis.md)。常量：`bluedock-common` → `RedisKeys`。

## Key 规范

| 类型 | 模式 |
| ---- | ---- |
| 业务缓存 | `bluedock:{domain}:{id}` |
| 鉴权 | `bluedock:auth:...` |
| 在线 | `bluedock:online:{userId}` |
| 锁 | `bluedock:lock:{resource}:{id}` |
| 限流 | `bluedock:rate:{userId}:{action}` |

## 常用 Key

| 用途 | Key | 类型 | TTL |
| ---- | --- | ---- | --- |
| 图形/登录验证码 | `bluedock:auth:captcha:{key}` | String | 5 min |
| 登录失败计数 | `bluedock:auth:login:fail:{ip}`（可选 `…:email:{email}`） | String 计数 | 15 min |
| Token 会话 | `bluedock:auth:token:{token}` | String | access TTL |
| Token 反查 | `bluedock:auth:token-hash:{md5}` | String | 同 access TTL |
| RSA 公钥缓存 | `bluedock:auth:pubkey:{keyId}` | String | 建议 1 h |
| 客户端密钥对（遗留常量，登录公钥已改用 `pubkey`） | `bluedock:auth:client-key:{clientId}` | String(JSON) | 约 90d |
| 扫码登录票据 | `bluedock:auth:qrCode:{code}` | String(JSON status/userId) | **30s** |
| 在线 / 桌面活跃 | `bluedock:online:{userId}` · `bluedock:pc:active:{userId}` | String | 心跳续期（online≈90s · pc≈60s；WS 注册/ping 写入，末连接断开 DEL） |
| WS 连接映射 | `bluedock:ws:user:{userId}`（Set sessionId）· `bluedock:ws:session:{sessionId}` | Set/String | 连接生命周期 |
| 分片上传会话 | `bluedock:upload:{uploadId}` | Hash（含 userId / scene / parentId / taskId） | 24 h |
| 分片已收集合 | `bluedock:upload:{uploadId}:chunks` | Set | 24 h（与会话同 TTL） |
| 项目写锁 | `bluedock:lock:project:{id}` | String NX PX | 短（5–30s） |
| 文件目录写锁 | `bluedock:lock:file:dir:{userId}:{parentId}` | String NX PX | 短（防并发上传死锁） |
| 接口限流 | `bluedock:rate:{userId}:{action}` | String/计数 | 窗口长度 |
| AI 操作结果 | `bluedock:ai:op:{requestId}` | String(JSON) | 约 60s |
| AI 流式凭证 | `bluedock:assistant:stream:{streamKey}` | String(JSON) | 约 10 min |
| 系统设置缓存 | `bluedock:setting:{group}` | String | 主动失效为主 |
| 会议分享链接 | `bluedock:meeting:share:{code}` | String(JSON) | 默认 6h |
| 会议游客信息 | `bluedock:meeting:tourist:{agoraUserId}` | String(JSON) | 默认 6h |
| 会议关房节流 | `bluedock:meeting:close:tick` | String | 10m |
| 未读邮件汇总节流 | `bluedock:email:unread:notice:tick` | String | 4m |
| 签到提醒调度互斥 | `bluedock:attendance:remind:tick` | String | 50s |
| 签到提醒当日幂等 | `bluedock:attendance:remind:sent:{day}:{userId}:{kind}` | String | 36h |
| 任务自动归档互斥 | `bluedock:task:autoArchive:tick` | String | 55m |
| 待办到期提醒互斥 | `bluedock:dialog:todoRemind:tick` | String | 50s |
| 待办到期提醒幂等 | `bluedock:dialog:todoRemind:sent:{todoId}` | String | 2d |
| 机器人 clearDay 清理互斥 | `bluedock:userBot:clearDay:tick` | String | 55m |
| 任务 AI 扫描互斥 | `bluedock:task:aiScan:tick` | String | 50s |
| 未领取提醒互斥 | `bluedock:task:unclaimedRemind:tick` | String | 50s |
| 未领取提醒当日幂等 | `bluedock:task:unclaimedRemind:sent:{day}` | String | 20h |
| 对话会话标题已生成 | `bluedock:dialog:sessionTitle:done:{dialogId}:{userId}:{sessionKey}` | String | 30d |
| Draw.io 图标搜索缓存 | `bluedock:drawio:iconSearch:{hash}` | String | 15d |
| 在线授权验证码 | `bluedock:license:online:code:{email}` | String | 10m |
| 在线授权 pending | `bluedock:license:online:pending:{token}` | String | 30m |
| 在线试用标记 | `bluedock:license:online:trial:{sn}` | String | ~400d |
| 通知幂等 | `bluedock:notify:idempotency:{eventId}` | String | 2d |
| APP 推送延时队列 | `bluedock:appPush:delay:queue` | ZSET（score=到期 ms） | 任务到期消费 |
| APP 推送延时载荷 | `bluedock:appPush:delay:job:{jobId}` | String(JSON) | 1h |
| APP 推送延时轮询锁 | `bluedock:appPush:delay:tick` | String | ~2s |
| 搜索索引幂等 | `bluedock:search:idempotency:{eventId}` | String | 2d |
| 搜索重建锁 | `bluedock:search:rebuild:lock` | String | 2h |
| 搜索重建进度 | `bluedock:search:rebuild:status` | String(JSON) | 24h |
| 机器人 Webhook 幂等 | `bluedock:userBot:webhook:idempotency:{eventId}` | String | 2d |
| 机器人 Webhook 回复幂等 | `bluedock:userBot:webhook:reply:idempotency:{eventId}` | String | 2d |
| 会话打开 Webhook 节流 | `bluedock:userBot:dialogOpen:{dialogId}:{userId}` | String | 1m |
| 文件打包元数据 | `bluedock:file:pack:{packId}` | String(JSON) | 2h |
| 导出下载票 | `bluedock:export:down:{key}` | String(JSON path/userId/name/size) | 24h |
| 导出幂等 | `bluedock:export:idempotency:{eventId}` | String | 2d |
| 导出私聊幂等 | `bluedock:export:notify:idempotency:{eventId}` | String | 2d |
| Office 编辑会话 | `bluedock:file:office:{token}` | String(JSON) | 默认 2h |

## 数据结构约定

- 锁：`SET key value NX PX ttl` + Lua 比对删除（或 Redisson）
- 在线：心跳续期；下线主动 DEL
- 上传：记录 userId、hash、分片位图/集合、目标 folderId

## 反模式

- 硬编码 key 字符串
- Redis List 推业务事件
- 无 TTL 的无限增长 key（除明确白名单）

## 与 MySQL

| 数据 | 权威源 |
| ---- | ------ |
| 项目 / 任务 / 消息 / 文件元数据 | MySQL |
| Token 会话、验证码、锁、上传进度 | Redis |
| 设置 | MySQL `bluedock_settings`；Redis 作读缓存 |
