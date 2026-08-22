# 账号

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **用户信息字段**
- **注册是否需要邀请码**

### 能力（怎么做）

- 注销账号
- 设备管理
- 邮箱验证
- 批量导入用户
- 登录图形验证码
- 扫码登录
- 登录账号
- 退出登录
- 修改密码
- 注册账号
- 搜索用户

## 核心概念

### 用户信息字段

## 定义
BlueDock 用户对象由 `api/users/info` 返回，包含账号、身份、个人资料三类字段。`info__departments` 单独返回当前用户的部门列表。

## 关键字段
| 字段 | 含义 | 是否可改 |
|---|---|---|
| `userId` | 用户 ID（自增主键） | 否，注册即定 |
| `email` | 登录邮箱 | 否，自助不可改 |
| `nickname` | 昵称（2-20 字） | 是，editdata |
| `userImage`（落库 `user_img`） | 头像 URL | 是，editdata |
| `telephone` | 联系电话（6-20，全局唯一） | 是，editdata |
| `profession` | 职位/职称（2-20 字） | 是，editdata |
| `birthday` | 生日（YYYY-MM-DD） | 是，editdata |
| `address` | 地址（≤ 100 字） | 是，editdata |
| `introduction` | 个人简介（≤ 500 字） | 是，editdata |
| `lang` | 界面语言（zh/en/...） | 是，editdata |
| `identity` | 身份标记数组（admin/ldap/temporary/...） | 否，系统设定 |
| `department` | 所属部门 ID 数组 | 否，由管理员调整 |
| `department_name` | 部门名（拼接） | 否，由 department 推导 |
| `department_owner` | 是否默认部门下第一级负责人 | 否 |
| `managedDepartments` | 可切换负责人视角的部门 | 否 |
| `last_ip` / `last_at` | 最近一次登录的 IP / 时间 | 自动写 |
| `onlineIp` / `onlineAt`（落库 `online_ip` / `online_at`） | 最近一次活跃 IP / 时间 | 自动写 |
| `loginCount`（落库 `login_count`） | 登录次数 | 自动累加 |
| `mustChangePassword`（落库 `must_change_password`） | 是否需在下次登录强制改密码 | editPassword 后清零 |
| `email_verify` | 邮箱是否已验证（1/0） | 邮箱验证后置 1 |

## identity 常见值
- `admin`：系统管理员
- `ldap`：LDAP 同步过来的账号
- `temporary`：临时账号
- `bot`：机器人账号
- `system`：系统/演示账号（受保护）

## 与「部门」的关系
- 一个用户可属多个部门
- `info__departments` 返回最多 10 个部门，且把「当前用户作为负责人的部门」排在最前
- 部门管理在系统管理员的「团队管理 → 部门管理」中维护

### 注册是否需要邀请码

## 定义
BlueDock 的注册方式由系统设置 `system.reg` 控制，共三档：

| 取值 | 行为 |
|---|---|
| `open` | 任何人填邮箱+密码即可注册 |
| `invite` | 必须填正确的邀请码才能注册 |
| `close` | 完全关闭注册，登录页不显示注册入口 |

## 邀请码是什么
- 管理员在「系统设置 → 注册设置」中配置一个字符串作为 `reg_invite`
- 该字符串只有一个；所有想注册的人共用
- 注册时把这个字符串填到「邀请码」字段提交，后端比对 `Request.invite == setting.reg_invite`

## 接口判定
前端可调 `api/users/register/needInvite` 拿到 `{ need: true/false }`，据此决定登录页注册 tab 是否展示「邀请码」输入框：
- `need=true` → reg=invite，显示邀请码输入框
- `need=false` → reg=open（reg=close 时注册入口本身就该隐藏）

## 与「项目邀请加入」的区别
- **本概念**：决定能否成为 BlueDock 用户（账号级别）
- **项目邀请**：已是用户后，被加入某个项目（项目级别）
两者完全独立。系统管理员是注册阶段的守门人，项目负责人是项目阶段的守门人。

## 怎么修改
- 系统管理员（userIsAdmin）→「系统设置」→「注册设置」→ 切换 reg 模式 / 修改 reg_invite 字符串
- 修改即时生效，无需重启

## 不支持 / 边界

- 不支持「同一二维码多端复用」，扫码成功并被消费后立即失效
- 不支持「永久禁止某设备再登录」；登出后用账密重新登又会创建新设备记录
- 不支持在文件里指定头像 / 电话等扩展字段，列只有邮箱、昵称、初始密码、职位
- 不支持用导入更新已有用户；同邮箱已存在的行会标错跳过
- 个人简介 ≤ 500 字，地址 ≤ 100 字，职位 2-20 字
- 二维码 code 30 秒内有效；过期需刷新登录页重新生成
- 二维码 code 必须 ≥ 32 字符，被篡改/截断会报「参数错误」
- 仅支持 xls / xlsx / csv 文件，单次最多导入 500 条
- 单次最多返回 100 条，超过需翻页（page + pageSize）
- 同一浏览器清缓存/换浏览器/换无痕模式都算新设备
- 图形验证码仅用于登录风控，不能用于注册/找回密码
- 在「设备」里把别的设备登出不会改你当前的登录状态；登出自己当前设备需用 `user-account.logout`
- 多次失败后系统会强制要求填验证码（`user-account.login-codeImage`）
- 开启「注册需邮箱验证」时，未验证邮箱的账号无法登录，必须先完成验证（`user-account.email-verify`）
- 开启「注册需邮箱验证」时，注册后必须先验证邮箱才能登录
- 必须先预览解析确认，再确认导入；直接 import 不带 rows 会报「没有可导入的数据」
- 搜索结果只含基础字段（basicField），完整资料需调 user/info 或 get_users_basic
- 改完密码后所有现有 token 仍然有效，但 LDAP 同步会同步更新 LDAP 密码
- 新旧密码不能相同，会提示「新旧密码一致」
- 昵称必须 2-20 字；< 2 提示「昵称不可以少于2个字」，> 20 提示「昵称最多只能设置20个字」
- 忘记密码：自助走邮箱 OTP + RSA 新密码（`users/email/code?type=reset` → `users/password/reset`）；亦可由管理员在团队管理里改密
- 没开启 regVerify 时，注册后无需验证邮箱
- 注册方式为「关闭」时所有人都无法自助注册，需管理员手动创建账号
- 注册方式为「邀请码」时必须填正确邀请码，否则报「请输入正确的邀请码」
- 注销不会立即物理删除所有数据；用户基础信息缓存在 user_deletes 表保留以维持历史记录可读
- 注销后无法自助恢复，只能由系统管理员从 `user_deletes` 表里手动还原（详见 `user-account.delete-restore`）
- 注销后该邮箱可以被重新注册（但与原账号无关联）
- 注销账号确认（`user-account.delete`）也走邮箱验证，但用的是不同 type 的验证码
- 系统/演示账号（system）禁止改密码，会提示「演示账号不允许修改密码」
- 系统账号（system）禁止注销
- 联系电话长度 6-20，且全系统不可重复
- 被登录端轮询拿到的是用户 token，会创建一条新登录记录（不是会话共享）
- 设备列表上限由 UserDevice::$deviceLimit 控制，超过会按时间淘汰最旧设备
- 账号被停用（disable_at 非空）会提示「帐号已停用」，需联系管理员
- 输入正确的账号密码后再没必要每次都填验证码；只有触发风控后才强制要求
- 退出后未发送/草稿消息不会保留
- 退出登录不是注销账号；账号还在，下次还能登录（注销见 `user-account.delete`）
- 退出登录只清除当前设备的 token / 设备记录，不影响其它设备
- 邀请码与「邀请同事加入项目」是两回事；这里指的是系统级开放注册的密钥
- 邀请码只有一个全局值，不区分用户、不限次数、不过期
- 邮箱、密码各自最长 32 字符，超过提示「帐号或密码错误」
- 邮箱长度和密码长度都不能超过 32 个字符
- 邮箱（email）不能自助修改；如需换邮箱只能注销后重新注册（`user-account.delete`）
- 验证不通过会直接拒绝，没有「邀请码错了几次锁定」的限制
- 验证码区分大小写；填错会反复要求重输
- 验证链接 30 分钟内有效，过期需重新登录/注册触发新邮件
- 验证链接是一次性的，已使用过的链接会提示「链接已经使用过」
- 默认排除机器人（bot=0），需要机器人时 bot=1/2
- 默认排除离职用户（disable=0），如要看离职用户须 disable=1 或 disable=2（含离职）

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `user-account`
- **登录加密 / 验证码**：[auth-wire.md](auth-wire.md)
- API：[api.md](api.md) · [api-contract.md](../../contract/api-contract.md)
- 数据：[data.md](data.md) · [database.md](../../data/database.md) · [redis.md](../../data/redis.md)
