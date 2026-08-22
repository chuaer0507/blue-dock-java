# 实时通道（WebSocket）

即时同步：消息、未读、任务字段变更、在线状态、会议信令等。

## 形态

```
客户端 ⇄ Nginx（Upgrade）⇄ bluedock-boot / bluedock-realtime
                              ↑
                    Kafka bluedock.realtime.fanout（多实例广播消费）
```

- 协议：Spring WebSocket，路径 **`/ws`**
- 鉴权：握手时校验 `?token=` 或 `Authorization: Bearer`；绑定 `userId`
- 客户端形态：可选 query `client` / `platform`（`desktop`/`electron`/`mac`/`windows`/`linux`/`web`/`ios`/`android`）；桌面类写入 `bluedock:pc:active`
- 心跳：客户端发 `ping` 文本或 `{"type":"ping"}`，服务端回 `{"type":"pong"}` 并续期 presence

## 落地状态（P1）

| 能力 | 状态 |
| ---- | ---- |
| `/ws` 握手鉴权 + 本机会话注册 | 已落地（`bluedock-realtime`） |
| Redis 在线 / PC 活跃 presence | 已落地：`bluedock:online:{userId}`（90s）· `bluedock:pc:active:{userId}`（60s，desktop 系）；`ping` 续期 |
| 在线细事件 `presence.online` / `presence.offline` | 已落地：首连/末断向会话对端扇出（`PresencePeerLookup`，上限 200）；`GET /api/users/presence` |
| Kafka `bluedock.realtime.fanout` 发布 / 消费推送 | 已落地 |
| messenger `dialog.message` / `dialog.message.update` / `dialog.message.withdraw` / `dialog.message.stream` | 已接入（事务 afterCommit；会议结束 / 投票 / 接龙推 update；stream 通知指定用户听流） |
| assistant `operation` 派发 / `operationResult` 回包 | 已接入（结果 Redis `bluedock:ai:op:{requestId}`） |
| apps `appBadge` | 已接入（角标变更扇出） |
| task `task.created` / `task.updated` / `task.deleted` | 已接入（`TaskService` 写路径 afterCommit；扇出项目成员） |
| column `column.created` / `column.updated` / `column.deleted` | 已接入（`ProjectService` 列 CRUD） |
| project `project.sort` | 已接入（`ProjectSortService`；`onlyColumn` + 任务排序载荷） |
| 在线细事件（好友列表 presence 推送帧） | 已落地（会话对端扇出；非全站通讯录广播） |

## 客户端帧（camelCase）

下行：

```json
{"type":"dialog.message","eventId":"...","data":{"dialogId":1,"message":{...}}}
```

| type | data 要点 |
| ---- | --------- |
| `dialog.message` | `dialogId` · `message`（与 REST `DialogMessageView` 对齐；视图内正文字段为 `body`） |
| `dialog.message.update` | `dialogId` · `message`（投票/接龙为完整 `DialogMessageView`；会议结束另带顶层 `id`，`message` 为 payload） |
| `dialog.message.withdraw` | `dialogId` · `messageId` |
| `operation` | `requestId` · `action` · `payload`（可选 `fd`） |
| `appBadge` | `appId` · `menuKey` · `count` · `dot` |
| `task.created` / `task.updated` | `projectId` · `taskId` · `columnId` · `parentId` · `name` · `task`（`TaskView`） |
| `task.deleted` | `projectId` · `taskId` · `columnId` · `parentId` · `name` · `deleted=true`（跨项目 move 时原项目亦发 deleted） |
| `column.created` / `column.updated` | `projectId` · `columnId` · `column`（`ProjectColumnView`） |
| `column.deleted` | `projectId` · `columnId` · `name` · `deleted=true` |
| `project.sort` | `projectId` · `onlyColumn` · `sort`（与 `POST /api/project/sort` 载荷同形） |
| `presence.online` / `presence.offline` | `userId` · `online`（true/false）；扇出对象为共享会话的对端 |
| `pong` | 心跳应答 |

上行（客户端 → 服务端）：

| type | data 要点 |
| ---- | --------- |
| `ping` | 心跳 |
| `operationResult` | `requestId` · `success` · `result` · `error` |

## 多实例扇出

1. 写请求落库
2. 事务提交后发布 `RealtimeFanoutEvent` → Topic `bluedock.realtime.fanout`
3. **每个 boot 实例使用独立 `groupId`**（`bluedock-realtime-{uuid}`），保证广播
4. 各实例消费后只推**本机** `WsSessionRegistry` 持有的连接

禁止用 Redis pub/sub 代替 Kafka 做跨域业务事件。Redis 仅缓存连接映射：

| Key | 说明 |
| --- | ---- |
| `bluedock:ws:user:{userId}` | Set&lt;sessionId&gt; |
| `bluedock:ws:session:{sessionId}` | userId |
| `bluedock:online:{userId}` | 任意端在线；注册 / ping 续期约 90s；最后连接断开 DEL |
| `bluedock:pc:active:{userId}` | 桌面端活跃；仅 desktop 类 client；约 60s；供 APP 推送延时 |

## 与推送

- 在线：优先 WS
- 离线：走 [notify](../modules/notify/overview.md) → Kafka `bluedock.notify.send`

## 相邻文档

- [messaging.md](messaging.md)
- [modules/messenger/overview.md](../modules/messenger/overview.md)
