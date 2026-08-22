# 会议（Agora）

产品说明见 [modules/meeting/overview.md](../modules/meeting/overview.md)。

## 职责拆分

| 层 | 职责 |
| -- | ---- |
| `bluedock-user` MeetingService | 会议记录、分享链接、游客缓存、邀请 |
| `bluedock-messenger` MeetingInviteBridge | 会议卡片消息 + 结束态同步 |
| Agora | RTC token；RESTful 查频道空闲 |

## 落地状态

- 表 `bluedock_meetings` / `bluedock_meeting_messages`（V1）
- `api/users/meeting/open|link|tourist|invitation`
- Redis：`bluedock:meeting:share:{code}` · `bluedock:meeting:tourist:{agoraUserId}` · `bluedock:meeting:close:tick`
- Token：配置了 `app-id` + `app-certificate` 时签发正式 token；否则 `allow-dev-token=true` 返回 `dev.{channel}.{uid}`
- 自动关房：`CloseMeetingRoomScheduler`（Agora REST 或开发旁路）
- **管理端配置落库**：`GET/POST api/system/setting/meeting` → `bluedock_settings.name=meetingSetting`；运行时 `MeetingRuntimeConfig` = YAML 默认 + DB 覆盖

## 配置

### YAML（`application.yml` / `bluedock.meeting.*`）

| 键 | 说明 |
| -- | ---- |
| `bluedock.meeting.enabled` | 总开关 |
| `bluedock.meeting.app-id` / `app-certificate` | Agora RTC 凭证 |
| `bluedock.meeting.api-key` / `api-secret` | Agora RESTful（关房查频道） |
| `bluedock.meeting.allow-dev-token` | 无证书时开发令牌（生产关闭） |
| `bluedock.meeting.allow-close-without-rest` | 无 REST 时按空闲直接关房（仅开发） |
| `bluedock.meeting.close-idle-minutes` | 默认 10 |
| `bluedock.meeting.close-check-ms` | 调度轮询间隔，默认 60000 |
| `bluedock.meeting.channel-salt` | channel 派生盐 |
| `bluedock.meeting.share-base-url` | 分享链接前缀 |
| `bluedock.meeting.share-ttl-hours` | 默认 6 |

### 管理端 JSON（`meetingSetting`）

与 OSS / SMTP / aiBot 同类管理员可配：

| Method | Path | 说明 |
| ------ | ---- | ---- |
| GET | `/api/system/setting/meeting` | 当前配置；`appCertificate` / `apiKey` / `apiSecret` / `channelSalt` 已设则 `********` |
| POST | `/api/system/setting/meeting` | 保存；上述字段空或 `********` 保留原值；`SYSTEM_SETTING=disabled` 禁写 |

字段（camelCase）：`enabled`、`appId`、`appCertificate`、`apiKey`、`apiSecret`、`allowDevToken`、`allowCloseWithoutRest`、`closeIdleMinutes`、`channelSalt`、`shareBaseUrl`、`shareTtlHours`。非空值覆盖 YAML。运行时 `MeetingRuntimeConfig` 经 `loadRaw()` 读**原文**。

## 关键字段

| 字段 | 说明 |
| ---- | ---- |
| meetingId（落库 `meeting_id`） | 11 位大写字母数字 |
| channel | `BlueDock:` + md5(meetingId+salt)[16:] |
| userId | 创建人 |
| end_at | 关房后写入 |

## 安全

- Token 短时有效、按 channel + uid 签发
- 分享链接 Redis TTL
- 游客能力受限
