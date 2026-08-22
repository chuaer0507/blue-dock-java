# License

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **License Key 是什么**

### 能力（怎么做）

- 申请与录入 License
- 在线授权（邮箱验证码登录 / 申请试用 / 自动续期）

## 核心概念

### License Key 是什么

## 定义
License Key 是 BlueDock 终端的授权凭证，决定一个部署允许多少注册用户、绑定哪台机器、有效期到何时。它是一段加密字符串，由官方根据「终端 SN + MAC + 人数 + 过期时间」签发。

后端通过 `api/system/license` 接口读写，存储在部署侧 License 文件（或等价安全配置）中。

## 关键属性

- **license（content）** — License 原文字符串
- **info.people** — 允许的最大用户数；0 表示无限制
- **info.sn** — 授权绑定的终端 SN
- **info.macAddresses** — 授权允许的 MAC 列表（数组）
- **info.expiredAt** — 过期时间（字符串，空字符串/0 表示永久）
- **machineSn** — 当前终端的 SN
- **macAddresses** — 当前服务器实际网卡 MAC 列表
- **userCount** — 当前非机器人、未禁用的活跃用户数

## 与其他概念的关系

- **小团队豁免**：`info.people <= 3` 时不校验 SN / MAC，相当于「3 人内永久免费」
- **超额提示**：`userCount > info.people` 时返回超额错误
- **绑定校验**：SN 不匹配 → SN 不匹配；MAC 不在白名单 → MAC 不匹配
- **过期校验**：当前时间 > `expiredAt` → License 已过期

## 使用场景
- 申请新的 License：见 `license.howto`
- 处理过期或失效：见 `license.expire`
- 管理后台「License」页会汇总 `error` 数组，展示所有不满足的规则

## 不支持 / 边界

- 3 人以下的部署不强制 License（不绑 SN / MAC，但仍受人数限制）
- 一个账号同一时刻只占用一个实例座位，换机需先在原实例「退出在线授权」释放
- 一份 License 不能拆给多个 BlueDock 终端共用
- 一份 License 仅对当前终端的 SN + MAC 有效，换机或换网卡需重新申请
- 不支持把 License 拆给多个独立部署共享
- 不能在终端外部直接编辑 License 文件，必须走管理端 API
- 在线授权与离线授权互斥：同一时刻只有一张生效 License；切到在线并登录后会接管 License 文件
- 离线授权（粘贴 License 原文）完全不受影响，没有自动续期
- 试用每个账号仅一次，时长由 App Store 管理员配置且硬上限 60 天
- 过期或人数超限不会立刻锁死功能，但会在管理端持续报错提示

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `license`
- API：[`api.md`](api.md) / [`docs/contract/api-contract.md`](../../contract/api-contract.md)
- 基础设施：[`docs/infra/license.md`](../../infra/license.md)
