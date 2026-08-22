# 签到打卡

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **签到打卡是什么**
- **签到是插件还是内置功能**
- **签到提醒怎么发的**
- **签到规则与全局配置**

### 能力（怎么做）

- 怎么导出签到数据
- 怎么查看自己的签到记录
- 普通签到（手动签到）怎么做
- 个人签到设置怎么改
- WiFi 自动签到怎么用

## 核心概念

### 签到打卡是什么

## 定义
签到打卡是 BlueDock 内置的考勤记录功能，用于成员每天上下班打卡。打卡数据存到 `UserAttendanceRecord` 表，按天聚合多次打卡时间，并自动切分为「上班 / 下班」时段。功能由系统管理员在「签到设置」全局开关，开启后所有成员都可参与。

## 支持的签到方式
BlueDock 提供 4 种签到方式，管理员可在「签到设置 → 签到方式」勾选启用：

- **人脸签到（face）**：须 face 插件（`AttendanceFaceBridge`）；登记 `faceUploadObjectId`，刷脸 `faceCaptureObjectId` 或公开 `/public/attendance/face`
- **WiFi 签到（auto）**：办公网路由器（OpenWrt）定时上报 MAC，落到办公网即自动签到，详见 `attendance.wifi`
- **定位签到（locat）**：App 上报 `latitude`/`longitude`（`POST .../attendance/save`），落在允许半径内打卡；地图 Key 仅管理端配置
- **手动签到（manual）**：在「签到机器人」对话框输入指令打卡

## 涉及的数据
- `UserAttendanceRecord`：每天每次打卡记录（含时间数组）
- `UserAttendanceMac`：成员的 MAC 地址（最多 3 个）
- `UserAttendanceFace`：成员的人脸图片（仅 1 张）
- `attendanceSetting`：系统签到全局参数

## 相关概念
- 签到规则与配置：`attendance.rule`
- 签到提醒机制：`attendance.remind`
- 是否是插件：`attendance.plugin`

### 签到是插件还是内置功能

## 结论
签到打卡的**主体功能是 BlueDock 内置的**，不需要单独安装插件。模型 (`UserAttendanceRecord` / `UserAttendanceMac` / `UserAttendanceFace`)、签到机器人 (`attendance@bot.system`)、提醒任务 (`AttendanceRemindTask`)、设置接口都打包在主程序里，开箱即用。

## 各签到方式的依赖
- **手动签到（manual）**：无依赖，主程序自带
- **WiFi 签到（auto）**：无插件依赖，只需要管理员在 OpenWrt 路由器执行一键安装脚本
- **定位签到（locat）**：无插件依赖，但需要管理员在「签到设置」配置百度 / 高德 / 腾讯地图 Key，且只支持移动端 App
- **人脸签到（face）**：**需要安装 face 插件**（应用市场搜「Face attendance」），并配套人脸识别硬件设备

## 关联应用市场
- `face` 插件：人脸识别后端服务，未装时人脸上传 / 现场刷脸都会失败（人脸签到详细说明随 face 应用知识库提供）
- `approve` 插件：影响提醒筛选——已请假 / 外出审批的成员不会收到缺卡提醒

## 不支持
- 没有第三方「考勤」插件取代内置签到
- 没法只装签到不装签到机器人（机器人是系统自动创建的）
- 主程序版本升级后签到能力随版本走，无独立版本号

### 签到提醒怎么发的

## 定义
签到提醒是由 `AttendanceRemindTask` 异步任务通过签到机器人 (`attendance@bot.system`) 在群外私聊推送的两类消息，用于催员工按时打卡。任务每分钟跑一次，只在触发窗口内的当日推一次。

## 两种提醒
- **打卡提醒（in）**：上班时间前的「快到上班时间了，别忘了打卡哦」，提前分钟数由「签到打卡提醒」字段控制（默认 5 分钟）
- **缺卡提醒（exceed）**：上班时间过去后还没打卡发的「上班时间到了，你还没有打卡哦」，延后分钟数由「签到缺卡提醒」字段控制（默认 10 分钟）

提前 / 延后值都可在管理员「签到设置」里改，设为 0 则关闭对应提醒。

## 提醒对象筛选
任务按下面规则逐个判断每位在职非机器人成员，全部命中才推送：

- 当天还没有 `UserAttendanceRecord`（已打卡的不推）
- 过去 3 天内有过签到记录（排除新人 / 长期不打卡者）
- 没有请假 / 外出的审批正在生效（经 `AttendanceLeaveBridge`；无 approve 插件时不过滤）

## 关联设置
- 「签到设置 → 功能开启」必须为「开启」
- 「签到时间」第一段被视为上班时间，提醒以它为基准
- 节假日：跳过周六日 + 内置法定放假日（`ChinaPublicHolidays`，2025–2026 国办通知区间）

## 实现

- `AttendanceRemindScheduler` + `AttendanceRemindService`（`bluedock-user`）
- 投递：`MessengerAttendanceRemindBridge` → `attendance@bot.system` 单聊
- 配置项：`bluedock.attendance.remind-ms`（默认 60000）

### 签到规则与全局配置

## 入口
桌面端 / 移动端：「应用」→「签到打卡」→ 抽屉右上角「签到设置」（仅管理员可见）
也可走：管理后台 → 系统设置 → 签到

## 主要字段
配置存储在 `attendanceSetting` 系统设置项，关键字段：

- **功能开启（open）**：`open` / `close`，关闭后整套签到能力对所有人停用
- **签到时间（time）**：`[上班时间, 下班时间]`，如 `["09:00", "18:00"]`，提醒任务基于这两个时间点
- **最早可提前（advance）/ 最晚可延后（delay）**：上下班时间前后允许签到的分钟数，超过会被拒
- **签到打卡提醒（remindIn）**：上班前 N 分钟推「打卡提醒」
- **签到缺卡提醒（remindExceed）**：上班后 N 分钟推「缺卡提醒」
- **签到方式（modes）**：勾选启用的方式列表，从 `face` / `auto`（WiFi）/ `locat`（定位）/ `manual`（手动）中多选
- **允许修改（edit / face_upload）**：是否允许成员自行改 MAC / 上传人脸

## 子方式额外配置
- **人脸签到**：签到备注 + 重复打卡提醒开关
- **WiFi 签到**：路由器一键安装命令
- **定位签到**：百度 / 高德 / 腾讯三选一的地图 Key + 允许签到坐标 + 半径（50-5000 米）
- **手动签到**：签到备注

## 不支持
- 不支持按部门 / 角色配置不同上下班时间，全员一套
- 不支持配置自定义节假日；节假日由内置 `ChinaPublicHolidays.isHoliday()` 判断
- 不支持多班次 / 排班
- 改完设置即时生效，但已生成的签到记录不会回溯调整

## 不支持 / 边界

- 不是 KPI / 工资系统，仅记录打卡时间，不算迟到 / 早退分数
- 不是所有人都会收到提醒，新成员入职 3 天内无打卡记录不会被提醒
- 卸载 face 插件不会影响 WiFi / 定位 / 手动签到
- 已请假 / 外出审批通过的成员不会被提醒
- 应用市场没有名为「attendance」或「签到」的独立插件
- 数据无法跨企业 / 跨实例同步
- 法定节假日不会发提醒（主程序内置节假日表自动跳过）
- 签到不支持节假日 / 调休的精细配置，只识别基础节假日（自动跳过提醒）

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `attendance`
- API：[api.md](api.md) · [api-contract.md](../../contract/api-contract.md)
- 数据：[data.md](data.md) · [database.md](../../data/database.md)
