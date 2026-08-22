# 签到 — 数据

| 物理表 | 说明 |
| ------ | ---- |
| `bluedock_user_attendance_records` | 按用户+日；`times` JSON 打卡点 |
| `bluedock_user_attendance_macs` | 用户 MAC（≤3）；WiFi 上报匹配 |
| `bluedock_user_attendance_faces` | 用户人脸（≤1）；`upload_object_id` → `bluedock_upload_objects` |
| 设置 | `bluedock_settings.name=attendanceSetting` JSON（含定位 / `faceUpload` / `mapKey` 等） |
| 法定放假日 | 代码内置 `ChinaPublicHolidays`（非表）；覆盖 2025–2026 国办放假区间 |

跨模块桥接（可选 Bean）：`AttendanceRemindBridge`、`AttendanceLeaveBridge`、`AttendanceFaceBridge`。

总览见 [database.md](../../data/database.md)。
