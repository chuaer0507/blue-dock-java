# 分阶段落地与迁移

## 阶段建议（实现进度唯一口径）

与 `docs/README.md` 中「产品优先级 P0/P1」区分：下表表示 **Java 代码落地顺序**。

| 阶段 | 内容 | 状态 |
| ---- | ---- | ---- |
| P0 | 文档骨架 + Maven + auth/user/project/task CRUD（含成员/邀请/子任务） | **基本完成** |
| P1 | messenger（dialog）+ file/upload + WS | **基本完成**：dialog（含已读/转发/表情/置顶/待办/投票/接龙）+ 项目/任务/部门群桥接 + file 全路径 + upload；`/ws` + Kafka fanout |
| P2 | dashboard/calendar/report/meeting/attendance | **完成**：dashboard / calendar / report / meeting；attendance（手动/WiFi/定位 + 提醒 + 节假日 + 请假/人脸桥接；**识别算法依赖 face 插件**，本仓仅桥接） |
| P3 | search 索引 + notify workers + LDAP/License | **基本完成**：search（docs/OS + 全量重建）+ workers；License；LDAP；邮件/APP 推送通道 |
| P4 | assistant + 微应用/市场 + 客户端支撑 API | **基本完成**：assistant；微应用；AppStore catalog/install/update/uninstall + microAppMenu 联动 + 可选 HTTP 生命周期 Hook；version/device/appPush/key/socket/prefetch；chinaIp（CDN 头 + 可选 GeoIP MMDB）；WS presence（online/pc:active + `presence.*` 扇出 + `/api/users/presence`）；APP 推送投递见 P3 |
| P5 | 组织底座补齐（部门 / 收藏 / 机器人…） | **基本完成**：部门群桥接 + 收藏 + bot CRUD/Webhook + 浏览/最近访问（含 task_file） |

### 开放项（本仓外、发版执行、或能力缺口）

对照总表见 **[api-contract.md「能力缺口（parity）」](../contract/api-contract.md#能力缺口parity)**（REST 主路径与实现缺口 P0–P2 已齐）。

| 项 | 说明 | 文档 |
| -- | ---- | ---- |
| 发版回归冒烟 | 手工勾选未跑；自动化见 CI / `make test` / `make smoke` | [regression.md](regression.md) |
| face 插件 | 人脸签到识别算法；本仓仅 `AttendanceFaceBridge` | [attendance/overview](../modules/attendance/overview.md) |
| approve 插件 | 请假/外出过滤缺卡提醒；无 Bean 不拦截 | 同上 |

> 实现缺口 P0–P2 调度与页面端点已齐；剩余主要为插件与发版回归。旧库导入步骤见下文「数据迁移」（非开放缺口）。

## 数据迁移（若从旧库导入）

1. 表重命名映射：`web_socket_dialogs` → `bluedock_dialogs` 等（见 [database.md](../data/database.md)）
2. 列类型对齐：时间 → `DATETIME(3)` UTC；JSON 字段校验
3. ID：保持 BIGINT 原值，避免破坏会话/文件外链
4. 密码哈希：确认算法兼容或强制改密
5. 文件二进制：对象存储 / 磁盘路径迁移脚本
6. 回填搜索索引：全量 rebuild

无旧库时：首次启动 `SuperAdminBootstrapRunner` 写入 `bluedock_users.id=1` 超管（凭据见 `deploy/.env.*` 的 `#admin账号` / `#admin密码`）+ 可选演示数据即可。

## 风险

- 消息表量大：按 dialog_id 分批
- 在线 Token：迁移窗口强制全员重新登录更简单
