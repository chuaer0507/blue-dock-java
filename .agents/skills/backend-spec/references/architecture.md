# 架构与分层边界

> 工程结构、顶层模块命名、模块拆分与分层职责边界。

## 目录

- [1. 目标](#1-目标)
- [2. 通用技术原则](#2-通用技术原则)
- [3. 使用方式](#3-使用方式)
- [4. 顶层模块命名与工程结构](#4-顶层模块命名与工程结构)
- [5. 模块拆分规则](#5-模块拆分规则)
- [6. 分层职责与边界](#6-分层职责与边界)

## 1. 目标

本文档作为 Java 后端项目的通用规范参考，聚焦工程结构、分层边界、命名、注释、代码质量与交付要求。

目标如下：

- 统一工程结构与顶层模块命名
- 统一分层边界与职责
- 统一命名规范与常量体系
- 统一默认中文注释标准
- 统一代码质量、自检与回归口径

## 2. 通用技术原则

本文档不强制绑定特定 JDK 版本、框架版本或基础设施选型，优先约束工程规范和实现边界。

通用原则：

- 优先沿用项目现有稳定技术栈，不要为了套规范强行升级 JDK、框架或中间件版本
- 版本选择以团队基线、生产环境约束、兼容性要求为准
- 能通过已有框架、ORM、Mapper、Client 适配层实现的能力，优先复用，不要重复造轮子
- 数据访问、接口鉴权、日志审计、缓存、消息、第三方集成等技术实现可以按项目现状落地，但必须遵守本文档的分层、命名、注释和质量约束

编码与资源要求：

- 源码、配置、SQL、XML、YAML、Properties、Markdown、脚本统一使用 `UTF-8`
- 禁止提交 `GBK`、`ANSI`、`BOM` 文件

## 3. 使用方式

使用本文档时，优先关注以下内容：

- 新项目或大改造时，先看 `顶层模块命名与工程结构`、`模块拆分规则`、`分层职责与边界`
- 日常开发或重构时，重点看 `命名规范`、`注释规范`、`代码质量与可维护性规范`
- 接入第三方、权限、日志、风控或支付等能力时，再阅读对应专题章节
- 做代码评审、自测或提测时，重点看 `测试与交付规范`

## 4. 顶层模块命名与工程结构

新项目顶层模块命名统一优先使用：

- `infrastructure`
- `persistence`
- `business`
- `web`
- `integration`，可选，仅当第三方接入很多时拆分

补充规则：

- 如果项目同时存在 HTTP、MQ、定时任务、Webhook 等多入口，`web` 升级为 `interfaces`
- `integration` 属于基础设施边界，不是业务层
- 不推荐新项目继续使用 `framework / model / service / application` 作为顶层模块名
- `business` 是顶层业务模块名，模块内部依然可以保留 `service / service/impl` 目录
- `persistence` 是顶层数据访问模块名，模块内部实体目录依然统一使用 `model`
- 如果工程实际模块名需要带项目名前缀，保持整体一致即可，例如 `order-infrastructure`、`order-persistence`

标准结构：

```text
project-parent
├─ infrastructure
├─ persistence
├─ business
├─ web
└─ integration (optional)
```

多入口项目结构：

```text
project-parent
├─ infrastructure
├─ persistence
├─ business
├─ interfaces
└─ integration (optional)
```

旧命名映射关系：

- `framework` -> `infrastructure`
- `model` -> `persistence`
- `service` -> `business`
- `application` -> `web`

项目根包(root package)说明：

- 项目根包默认形如 `com.{org}.{projectName}`，根包末段使用项目名称(去除横线、下划线、空格，字母小写)
- 顶层模块在根包下展开，例如根包 `com.{org}.vibecodingplatform`，顶层模块对应 `com.{org}.vibecodingplatform.infrastructure`、`com.{org}.vibecodingplatform.business`，业务子模块再向下展开，如 `com.{org}.vibecodingplatform.business.order`
- 项目根包与 Maven 坐标必须一致，`groupId` 取根包，`artifactId` 取项目名或模块切分名
- 禁止使用 `com.example.*`、`com.demo.*` 等脚手架默认根包
- 详见 [coding.md](coding.md) 第 7.1 节

## 5. 模块拆分规则

模块默认按“项目目标”或“业务需求”拆分，再在模块内部按分层结构展开。

强制规则：

- 先按业务模块拆分，再按技术层落包
- 即使项目最终只落在一个 `src/main/java` 下，也必须先按业务模块划分包，再在模块内落 `controller / request / assembler / service / domain / repo` 等分层目录
- 新需求如果业务边界清晰，必须单独成模块
- 禁止把无关需求全部塞进同一个“大杂烩模块”
- 禁止只按 `controller / service / repo` 做全局平铺，不做业务模块划分
- 禁止在单一 `src` 下做全局 `controller / service / repo / mapper / dto / vo` 平铺
- `infrastructure` 只放基础设施能力，不承载具体业务模块
- `integration` 只放第三方接入能力，不承载具体业务规则
- `web` 或 `interfaces` 只负责入口适配，不承载核心业务规则

### 5.1 目录治理与清理

目录治理默认遵守以下规则：

- 目录要有明确职责和稳定拥有者，不能只为“以后可能会用到”而预留
- 无用目录、废弃目录、一次性调试目录、迁移完成后的兼容目录应直接删除，不要长期保留
- 禁止保留空目录、占位目录、无语义目录，例如 `temp`、`tmp`、`demo`、`test2`、`misc`、`backup`
- `common`、`shared`、`base` 这类目录只有在职责边界稳定且团队已有统一约定时才允许存在，否则优先落到明确分层或明确业务模块
- 代码迁移或重构后，如果原目录已经不再承载真实职责，要同步清理，而不是留下“历史壳子”

## 6. 分层职责与边界

### 6.1 infrastructure 层职责

负责基础设施能力：

- 基础配置
- ORM / Mapper / SQL 基础配置
- 逻辑删除全局配置
- 分页
- 防抖
- 幂等
- 锁能力封装
- 权限与数据范围基础能力
- 审计日志
- 登录日志与操作日志
- 风控
- TraceId
- JWT
- 缓存
- HTTP Client
- 全局异常
- 业务断言基础能力

推荐目录：

```text
infrastructure
├─ config
├─ web
├─ security
├─ permission
├─ datascope
├─ audit
├─ log
├─ risk
├─ hardening
├─ mybatis
├─ trace
├─ exception
├─ exception/code
├─ exception/assert
├─ response
├─ util
├─ redis
├─ lock
├─ idempotent
├─ debounce
└─ client
```

### 6.2 persistence 层职责

负责数据访问层：

- 数据库实体
- 枚举
- Mapper
- Repo
- RepoImpl
- DTO
- QueryCondition
- XML SQL

推荐目录：

```text
persistence
└─ order
   ├─ model
   ├─ enums
   ├─ mapper
   ├─ repo
   ├─ repo/impl
   ├─ dto
   ├─ query
   └─ mapper/xml
```

### 6.3 business 层职责

负责业务用例编排：

- Command
- Query
- VO
- DTO
- Convert
- Service
- ServiceImpl
- Domain

推荐目录：

```text
business
└─ order
   ├─ command
   ├─ query
   ├─ vo
   ├─ dto
   ├─ convert
   ├─ service
   ├─ service/impl
   └─ domain
      ├─ manager
      ├─ validator
      └─ policy
```

补充约束：

- `convert` 属于默认需要的业务层目录，用于承接跨对象装配
- 只有在对象装配是一次性、局部、非常短的小段 `Builder` 时，才允许不单独落 `convert`
- 默认命名统一使用 `XxxService`、`XxxServiceImpl`
- 老项目如果当前已经稳定使用 `ApplicationService`、`ApplicationServiceImpl`，可以在本模块内保持一致，但不再作为新规范的默认推荐命名
- `convert` 放在 `business`，不要把它挪到 `web`
- `convert` 主要负责 `DO / DTO / VO / Command / Query` 等业务层对象转换，不替代入口层 `Assembler`

### 6.4 web / interfaces 层职责

负责接口入口层：

- Controller
- Request / Response
- 参数校验
- 鉴权接入
- Assembler
- 回调、Webhook、消息消费、定时任务入口

默认接口路径统一使用 `/api/**`，不区分前后台。只有项目明确存在独立后台管理端、且必须在路径层就做隔离时，才在 `web/` 下额外引入 `admin/` 子树。

默认推荐目录：

```text
web
└─ api
   └─ order
      ├─ controller
      ├─ request
      └─ assembler
```

存在独立后台管理端时的推荐目录：

```text
web
├─ api
│  └─ order
│     ├─ controller
│     ├─ request
│     └─ assembler
└─ admin
   └─ order
      ├─ controller
      ├─ request
      └─ assembler
```

多入口项目推荐目录：

```text
interfaces
├─ http
├─ webhook
├─ mq
└─ job
```

### 6.5 integration 层职责

仅在第三方接入特别多时拆分，负责：

- 三方 Client 聚合
- 厂商配置与鉴权
- 回调验签与请求签名
- SDK 适配与容错封装
- 共享第三方模板能力

禁止：

- 业务规则编排
- 业务状态流转
- Controller / Request / Response
- 直接承载业务权限判断

推荐目录：

```text
integration
└─ client
   ├─ oss
   │  ├─ OssClient.java
   │  ├─ OssProperties.java
   │  └─ XxxOssClient.java
   ├─ tts
   │  ├─ TtsClient.java
   │  ├─ TtsProperties.java
   │  └─ XxxTtsClient.java
   └─ ai
      ├─ AiClient.java
      ├─ AiProperties.java
      └─ XxxAiClient.java
```

### 6.6 边界强制规则

`web / interfaces` 只负责：

- 接收请求
- 参数校验
- 调用 Assembler
- 调用 Business Service
- 返回统一响应

`web / interfaces` 禁止：

- 写 SQL
- 写金额计算
- 写状态机
- 写数据范围拼装
- 直接调用 Mapper
- 直接调用厂商 SDK

`business` 只负责：

- 用例编排
- 事务控制
- 跨聚合协同
- 调 Repo
- 调 Domain
- 调 Convert
- 决定是否调用第三方 Client

`business` 禁止：

- 直接写 SQL
- 直接调 Mapper
- 直接散写错误码、权限码、锁 key、操作名称等业务字面量
- 直接写厂商 SDK 初始化、签名和底层重试逻辑
- 把应由 Convert 承接的跨对象装配大段散写在 Controller 或 ServiceImpl 中

`Assembler` 与 `Convert` 的职责区分：

- `Assembler` 位于 `web / interfaces`，负责入口层对象装配，例如 `ConfirmOrderRequest -> ConfirmOrderCommand`
- `Convert` 位于 `business`，负责业务层对象转换，例如 `ExportJobDO -> ExportJobDetailVO`、`TimelineVersionDO -> ExportTaskPayloadDTO`
- `Assembler` 不承接大段业务层 `DO / DTO / VO` 转换
- `Convert` 不直接承担 HTTP 入参接收语义

`domain` 负责：

- 状态机
- 金额规则
- 不变量校验
- 聚合内规则封装

`domain` 建议至少具备：

- `XxxManager`
- `XxxValidator`
- `XxxPolicy`

`persistence` 只负责：

- 单表简单查询
- 单表状态更新
- XML 查询代理

`persistence` 统一规则：

- 单表简单查询优先使用项目既有 ORM / Mapper 查询能力
- 单表状态更新优先使用项目既有数据访问组件能力
- 多表联查、统计、导出、对账统一进入明确的数据访问实现，例如 SQL 文件、Mapper XML 或查询实现类
- Repo / RepoImpl 只承接数据访问语义，不直接把“员工更新失败”“订单状态非法”“数据不存在”这类结果翻译成业务异常
- 单对象 Repo 查询默认允许返回 `null` 表示查无数据；如项目已经统一使用 `Optional`，在同一模块内保持一致即可
- Repo 返回的单对象空结果由 `ServiceImpl`、`domain` 或项目统一业务断言能力翻译成业务语义，不要直接把 `null` 暴露到 `web / interfaces` 的对外契约
- Repo 返回值默认按数据库操作类型统一：`insert` 返回主键或持久化后的实体，`update` / `delete` / 状态流转更新默认返回 `boolean`，`count` 返回计数值，`exists` 返回 `boolean`
- 复杂条件写操作如调用方需要区分失败原因，可返回显式结果对象、枚举或其他 typed result
- Repo 不负责把数据库结果翻译成业务错误码或业务异常，由 `ServiceImpl`、`domain` 或项目统一业务断言能力负责结果翻译
- Repo 查询方法必须写中文注释

`integration` 或 `infrastructure/client` 只负责：

- SDK 初始化与配置
- 鉴权、签名、验签
- 超时、重试、容错封装
- 第三方异常映射

`integration` 或 `infrastructure/client` 禁止：

- 业务规则判断
- 直接拼装 Controller Request / Response
- 直接承载业务状态流转
