# 签到 — API

前缀见契约。

## 已实现

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/system/setting/attendance` | Bearer 管理员 | 全局签到设置（`mapKey`/`reportKey` 掩码；`faceUpload`） |
| `POST /api/system/setting/attendance` | Bearer 管理员 | 保存设置 JSON |
| `GET /api/users/attendance/get` | Bearer | 个人视图：开关、方式、MAC、定位、`hasFace`/`facePlugin`/`faceUpload`、今日记录 |
| `POST /api/users/attendance/save` | Bearer | `macAddresses?`；`punch=1` 手动；`latitude`+`longitude` 定位；`faceUploadObjectId` 登记人脸；`faceCaptureObjectId` 刷脸打卡 |
| `GET /api/users/attendance/list` | Bearer | 月度记录；`yearMonth=yyyy-MM` 可选 |
| `GET /api/public/attendance/install` | 匿名 | WiFi 一键安装命令提示 |
| `GET\|POST /api/public/attendance/report` | 匿名 | `macAddress`+`key` WiFi 自动打卡 |
| `POST /api/public/attendance/face` | 匿名 | `userId`+`faceCaptureObjectId`+`key` 设备刷脸打卡 |
| `GET /api/system/attendance/export` | Bearer 管理员 | 异步导出 |
| `GET /api/system/attendance/download` | Bearer | 下载 CSV（Redis key 24h，仅请求者） |

打卡写入 `bluedock_user_attendance_records.times`：`[{at,mode,section,latitude?,longitude?}]`（mode=`manual`/`auto`/`locat`/`face`）。

### 定位

须 `modes` 含 `locat`；`locationLatitude`/`locationLongitude`/`locationRadius`(50–5000m)；Haversine 超半径拒打卡。

### 人脸

- 表：`bluedock_user_attendance_faces`（每用户 1 张，`upload_object_id`）
- 登记 / 刷脸均须 `AttendanceFaceBridge.available()`；无插件 → `attendance.face_plugin_missing`
- 登记：`faceUploadObjectId` + `faceUpload=open`；刷脸：`faceCaptureObjectId` → `bridge.match` 后 `mode=face`
- 设备：`POST /api/public/attendance/face`（`reportKey`）

### 导出参数

| 参数 | 说明 |
| ---- | ---- |
| `userId` / `userIds` | 成员 id，逗号分隔，≤100 |
| `date` | 日期起止，如 `2026-01-01,2026-01-31`，≤35 天 |
| `time` | 班次，如 `09:00,18:00`（写入 CSV「班次」列；Kafka `timeType`） |

流程：校验 → Kafka `bluedock.export.run`（`kind=attendance`）→ Worker CSV → 桌面通知含 download key。

### 提醒任务

- 跳过：周末、法定放假日、请假/外出（`AttendanceLeaveBridge`，可选）
- 法定节假日：`ChinaPublicHolidays`（2025–2026）
- 请假过滤：无 Bean 不拦截；有插件则 `skippedLeave`

详见 [overview.md](overview.md)。
