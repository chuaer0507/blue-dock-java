# 日历

日历**不是**独立实体：数据源为任务的 `startAt` / `endAt`。无单独 `api/calendar/*`。

| 能力 | API | 状态 |
| ---- | --- | ---- |
| 时间窗任务 | `GET api/project/task/calendar?start=&end=` | 已落地（当前用户参与的主任务） |
| 拖动改期 | `api/project/task/update` | 已有 |
| 筛选 | 客户端过滤 + 列表 API | — |
| 会议 | 独立模块，默认不进日历 | 未开始 |

`start` / `end`：`yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`（UTC 语义与任务字段一致）。

不支持：iCal 订阅导出（首版）。详见 [overview.md](overview.md)。
