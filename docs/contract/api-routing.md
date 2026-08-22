# API 路由约定

## 形态

兼容现有客户端习惯的动态路由：

| 形态 | 映射 | 示例 |
| ---- | ---- | ---- |
| `api/{resource}/{action}` | `ResourceController.action()` | `api/project/lists` |
| `api/{resource}/{action}/{sub}` | `action__sub()`（双下划线） | `api/project/invite/join` → `invite__join` |

- 路径段最多两层 action（一个 `__`）
- **一接口一路径**，禁止别名
- HTTP 方法以契约表为准（历史接口多为 GET 传参，新接口优先 REST 语义：读 GET、写 POST/PUT/DELETE）

## 资源前缀归属

| 前缀 | 模块 | 说明 |
| ---- | ---- | ---- |
| `users` | user-account / org / attendance / bot / favorite / meeting | 账号与杂项 |
| `project` | project / task | 项目、列、任务、工作流、标签、模板 |
| `dialog` | messenger | 会话与消息 |
| `file` | file | 文件树、共享、版本 |
| `upload` | upload | 分片上传 |
| `report` | report | 工作报告 |
| `dashboard` | dashboard | 团队仪表盘 |
| `system` | system-setting / infra | 系统设置与上传辅助 |
| `license` | license | 授权 |
| `assistant` | assistant | AI 助手 |
| `search` | search | 全局搜索 |
| `apps` | micro-app | 角标等 |
| `complaint` | abuse-report | 举报 |
| `approve` | data-export（审批插件） | 审批导出等；数据由 `ApproveExportBridge` 提供 |
| `public` | attendance 等 | 匿名/公开 |

完整路径表见 [api-contract.md](api-contract.md)（按模块填表；首版可从旧系统对照同步）。

## 响应信封

统一 `ResultModel<T>`：

```json
{ "code": 0, "message": "", "data": {} }
```

- 业务错误：HTTP 200 + `code != 0`
- 字段 camelCase（见 `.agents/rules/json-naming.md`）

## 鉴权

- 默认：`Authorization: Bearer <token>`（Redis 会话，见 `RedisKeys.accessToken`）
- 业务未登录：HTTP 200 + `code=1001`（无 Bearer）
- Access 过期/无效：HTTP 200 + `code=-2`（可带 refreshToken 无感续期）
- **匿名白名单**（首版）：
  - `/api/users/login`、`/api/users/login/*`
  - `/api/users/key/client`
  - `/api/users/logout`
  - `/api/users/token/refresh`
  - `/api/users/register`
  - `/api/users/email/code`
  - `/api/users/password/reset`
  - `/api/users/register/needInvite`
  - `/api/users/email/verification`
  - `/api/privacy`（隐私政策 HTML）
  - `/api/system/version`
  - `/api/project/invite/info`
  - `/api/public/**`
  - 非 `/api/**`（如 Actuator）
- 其余 `/api/**` 必须有效 token
