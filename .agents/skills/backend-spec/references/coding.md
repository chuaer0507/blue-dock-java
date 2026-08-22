# 编码规范

> 命名、注释、数据库、异常与代码质量约定。

## 目录

- [1. 命名规范](#1-命名规范)
- [2. 注释规范](#2-注释规范)
- [3. 数据库规范](#3-数据库规范)
- [4. 异常、日志与响应规范](#4-异常日志与响应规范)
- [5. 代码质量与可维护性规范](#5-代码质量与可维护性规范)

## 1. 命名规范

### 1.1 目录与包命名

- 实体目录统一使用 `model`
- 禁止使用 `do` 作为目录名
- 枚举统一放 `enums`
- XML 统一放 `resources/mapper/**`
- 顶层模块统一使用 `infrastructure`、`persistence`、`business`、`web`
- 多入口项目把 `web` 升级为 `interfaces`
- 第三方接入规模不大时统一放 `infrastructure/client`；规模明显扩大后再拆 `integration/client`
- 包路径优先按 `层 + 业务模块` 组织，不要只按技术类型全局平铺
- 即使工程只有一个 `src/main/java`，也必须先按业务模块分包，再在模块内部做分层展开
- `web` / `interfaces` 默认按 `api/业务模块/层` 组织，例如 `api/order/controller`、`api/order/request`;多入口或存在独立后台时再补 `mq/order/consumer`、`admin/order/controller` 等
- `business` 推荐按业务模块拆目录，例如 `order/command`、`order/query`、`order/service`、`order/domain`
- `domain` 下优先使用 `manager`、`validator`、`policy` 表达业务规则角色，不要堆在一个 `service` 包里
- `persistence` 推荐按业务模块拆目录，例如 `order/model`、`order/repo`、`order/mapper`、`order/query`
- 包名、目录名统一使用小写英文语义词，避免 `common`、`util2`、`temp`、`misc` 这类无业务语义命名
- 禁止在单一 `src` 下做全局 `controller / service / repo / mapper / dto / vo` 平铺
- 无用目录、占位目录、废弃目录要直接删除，不要为了“以后可能会用”长期保留
- 同一业务模块下，目录层级优先保持稳定，不要同一处同时出现 `dto`、`param`、`request` 三套近义目录
- Web 入参统一使用 `request`，出参优先使用 `response` 或直接复用 `VO` 规则，内部编排输入统一使用 `command` / `query`
- 能表达业务边界时，目录名优先用完整业务词，如 `settlement`、`reconciliation`、`withdraw`，不要只写模糊缩写

- 项目根包(root package)默认形如 `com.{org}.{projectName}`，根包末段使用项目名称
- 项目名称转包名的规则：横线、下划线、空格全部去掉，字母全部小写，不允许数字开头，例如项目 `vibe-coding-platform` 对应根包末段 `vibecodingplatform`，完整根包 `com.{org}.vibecodingplatform`
- 业务模块在根包下展开，例如 `com.{org}.vibecodingplatform.order`、`com.{org}.vibecodingplatform.user`，不允许跳出根包定义业务包
- 项目根包必须与 Maven 坐标对齐：`groupId` 取根包（如 `com.{org}.vibecodingplatform`），`artifactId` 取 `{projectName}` 或 `{projectName}-infrastructure` / `{projectName}-business` 等模块切分；Maven 坐标与 Java 根包不允许出现两套不同命名
- 禁止使用脚手架默认或占位根包：`com.example.*`、`com.demo.*`、`com.test.*`、`com.your.*`、`io.github.example.*` 等
- 禁止占用 Java 平台与主流框架的命名空间：`java.*`、`javax.*`、`jakarta.*`、`org.springframework.*`、`org.apache.*`、`com.alibaba.*` 等不允许用作项目根包
- 老项目继续沿用既有根包即可，不强制为对齐规范回改根包，避免破坏 ClassLoader、Bean 扫描路径、序列化兼容

### 1.2 类命名

- 实体类：`XxxDO`
- 枚举类：`XxxEnum`
- Mapper：`XxxMapper`
- 仓储接口：`XxxRepo`
- 仓储实现：`XxxRepoImpl`
- 命令对象：`XxxCommand`
- 查询对象：`XxxQuery`
- 传输对象：`XxxDTO`
- 视图对象：`XxxVO`
- 转换器：`XxxConvert`
- 服务接口：`XxxService`
- 服务实现：`XxxServiceImpl`
- Web 装配器：`XxxWebAssembler` 或 `XxxAssembler`
- 领域管理器：`XxxManager`
- 规则校验器：`XxxValidator`
- 业务策略：`XxxPolicy`
- 业务断言工具：默认使用 `BizAssert`，模块专用断言工具可使用 `XxxBizAssert`

补充说明：

- `XxxApplicationService`、`XxxApplicationServiceImpl` 仅作为老项目兼容命名保留，不作为新规范默认推荐
- 同一模块内不要混用 `XxxService` 和 `XxxApplicationService` 两套命名
- `XxxWebAssembler` 与 `XxxConvert` 是两类不同角色，不要混成一个类

### 1.3 接口路径

- 默认接口路径统一使用 `/api/**`，这是所有项目的默认前缀，不需要刻意区分前后台
- 路径统一使用短横线风格，如 `/api/confirm-order`
- 禁止接口路径使用驼峰命名
- 业务模块的基础路径常量必须明确体现 `/api` 语义，例如 `/api/order`、`/api/project`
- 只有项目明确存在独立后台管理端、且后台与前台需要在路径层就做隔离时，才额外引入 `/admin/**`，并在模块 README 或 ADR 中说明为什么需要后台体系
- 一旦引入 `/admin/**`，后台 Controller、Assembler、常量、权限码必须显式带 `Admin` 前缀以示区分，例如 `AdminOrderController`、`OrderApiPaths.ADMIN_BASE`、`OrderPermissionCodes.ADMIN_ORDER_CONFIRM`
- 不允许把后台接口和前台接口混在同一个 Controller 中，也不允许只靠路径前缀区分而不做命名隔离

### 1.4 常量类命名

禁止出现魔法字符串和魔法数字。常量类统一按类别命名，不要把所有内容塞进 `Constants`：

- 错误码：`XxxErrorCodes`
- 错误消息：`XxxErrorMessages`
- 权限码：`XxxPermissionCodes`
- 风控规则码：`XxxRiskRuleCodes`
- 分布式锁 Key：`XxxLockKeys`
- 防重复提交 Key：`XxxNoRepeatKeys`
- API 路径：`XxxApiPaths`
- 安全路径：`XxxSecurityPaths`

禁止命名：

- `Constants`
- `CommonConstants`
- `Const`
- `ConfigConst`
- `OrderConstant`
- `SystemConstant`

### 1.5 常量字段命名

常量字段统一使用 `UPPER_SNAKE_CASE`。

补充规则：

- 锁 Key 前缀常量统一以 `_PREFIX` 结尾
- 路径常量优先用语义命名，如 `ADMIN_BASE`、`CONFIRM_ORDER`
- 正则或路径匹配常量优先以 `_PATTERN` 结尾
- 业务前缀、版本前缀、对象存储目录前缀等短字符串只要承载业务语义，也属于魔法字符串，例如 `"v"`、`"video-cut/export/"`

### 1.6 常量值格式

- 错误码值：`UPPER_SNAKE_CASE`
- 权限码值：`lower-hyphen-case`
- 防重复 Key 值：`lower:colon:case`
- 锁 Key 前缀值：`lower:colon:case`
- 接口路径值：`/segment-name`
- 安全匹配值：`/segment/**`

### 1.7 常量类实现规则

常量类必须满足：

- 使用 `final class`
- 提供私有构造方法
- 不允许实例化
- 不允许把多个类别混在同一个类里
- 可以提供少量 Key 拼装方法，但方法名必须是完整业务语义
- 对版本号、对象存储路径、任务快照 Key 等带业务含义的字符串拼装，优先提供语义化常量和辅助方法，不要在业务代码里直接写字面量前缀

### 1.8 枚举命名

- 枚举类统一使用 `XxxEnum` 结尾
- 枚举项统一使用 `UPPER_SNAKE_CASE`
- 状态、类型、来源、原因等有限取值判断，优先收口到枚举静态方法，例如 `OrderStatusEnum.isPending(status)`
- 不要在业务代码中重复散写 `XxxEnum.SOME_STATUS.getCode().equals(value)` 这类判断
- 枚举静态判断内部默认使用 `ObjectUtil.equal(...)` 做空安全比较，避免在同一模块里混用 `equals(...)`、`Objects.equals(...)` 与其他工具风格

## 2. 注释规范

### 2.1 默认要求

- AI 生成或修改代码时，中文注释属于强制要求，不需要等待用户单独提出
- 注释默认语言统一为中文，禁止在同一套后端规范代码中随意混用英文注释
- 注释必须服务于“可维护性”和“业务可读性”，不要写成噪音
- 对关键业务字段，必须补齐字段级中文注释，且注释语义要清晰到足以支撑维护、联调和评审
- 类、`public` / `protected` 方法、Repo 方法、关键业务字段、关键分支的中文注释都属于强制检查项
- Controller 类和每个公开接口方法都属于强制注释对象，不能因为注解路径已经存在就省略方法注释

### 2.2 类级注释

以下类型默认必须写类级中文注释：

- 业务 Service / ServiceImpl
- Domain Manager / Validator / Policy
- Repo / RepoImpl
- Controller / Assembler / Convert
- 自定义注解与 AOP 切面
- 第三方 Client / Properties / Adapter
- 自定义异常、统一响应、全局异常处理类
- 关键枚举、重要常量类

类级注释至少说明：

- 当前类职责
- 所在分层
- 关键协作对象
- 边界约束或注意事项

类可以按需要使用注解，例如 `@Builder`、`@Getter`、`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@Slf4j`、`@Service`、`@Component`、`@Configuration` 等，但注解不能替代类注释本身，也不能替代关键字段和关键方法的语义说明。

### 2.3 方法注释

以下方法默认必须写中文方法注释：

- `public` / `protected` 方法
- Controller 对外接口方法、回调入口方法、消费入口方法
- Repo 查询方法和状态更新方法
- Assembler 的公开装配方法
- Convert 的公开转换方法
- Domain 核心规则方法
- 第三方调用适配方法
- 涉及事务、分布式锁、幂等、防抖、权限、审计、风控的方法

方法注释建议包含：

- 方法用途
- 参数语义
- 返回值语义
- 可能抛出的业务异常
- 事务、锁、重试、回调、副作用等约束

### 2.4 字段注释

以下字段必须补充中文注释：

- 业务语义不直观的领域字段
- 金额、状态、来源、原因等关键字段
- 锁 Key 前缀、权限码、风控规则码、错误码、错误消息
- 第三方配置项、签名字段、回调验签字段

字段注释要优先说明以下信息中的一项或多项：

- 字段业务含义，而不只是字面翻译
- 金额单位、比例口径、是否含税、是否允许负数
- 状态字段的取值语义、流转阶段、前后置条件
- 来源字段的生成方式、同步来源、回填来源
- 标识字段的唯一性口径、关联对象、使用边界
- 时间字段的时区语义、业务时点、系统时点

简单 DTO / VO / Request / Response 中名称非常直观、且没有额外业务语义的字段，可以在类注释已说明上下文时不重复逐字段展开；但只要字段承担状态、金额、权限、风控、外部映射、兼容分支等关键语义，就必须写清楚。

#### 2.4.1 字段注释示例

推荐写法示例：

```java
public class OrderDO {

    /**
     * 订单确认状态。
     * 仅允许使用 {@link OrderConfirmStatusEnum} 中定义的状态值。
     *
     * @see OrderConfirmStatusEnum
     */
    private Integer confirmStatus;

    /**
     * 订单应付金额，单位为元，含两位小数。
     * 该金额为用户侧应付口径，已包含优惠抵扣，不允许为负数。
     */
    private BigDecimal payableAmount;

    /**
     * 订单来源渠道。
     * 该字段记录订单首次创建来源，例如后台录单、用户自主下单、第三方平台导入。
     */
    private String sourceType;

    /**
     * 外部支付流水号。
     * 该值由支付渠道回调返回，用于关联渠道侧支付结果与本地支付单。
     */
    private String externalPayNo;

    /**
     * 业务完成时间。
     * 使用业务事件实际完成时点，不等同于记录更新时间。
     */
    private LocalDateTime completedAt;
}
```

不推荐写法示例：

```java
public class OrderDO {

    /** 确认状态 */
    private Integer confirmStatus;

    /** 金额 */
    private BigDecimal payableAmount;

    /** 来源 */
    private String sourceType;
}
```

判断标准：

- 好的字段注释要回答“这个字段在业务上到底代表什么”
- 如果字段存在枚举值、金额口径、状态前置条件、外部来源、时区语义，注释中应直接写出来
- 状态、类型、来源、原因、渠道等有限取值字段，优先抽成 `XxxEnum`，字段注释通过 `@see` 或 `{@link ...}` 指向对应枚举
- 注释不能只把字段名翻译成中文，否则对维护、联调和评审帮助很弱

#### 2.4.2 注解与字段注释配合示例

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateResultVO {

    /**
     * 订单确认状态。
     * 仅允许使用 {@link OrderConfirmStatusEnum} 中定义的状态值。
     *
     * @see OrderConfirmStatusEnum
     */
    private Integer confirmStatus;

    /**
     * 订单应付金额，单位为元，含两位小数。
     */
    private BigDecimal payableAmount;
}
```

约束说明：

- `@Data`、`@Getter`、`@Builder` 等注解可以正常使用
- 注解用于减少样板代码，不承担业务语义表达职责
- 只要字段本身承载关键业务语义，仍然要写清晰的字段注释

#### 2.4.3 纯数据载体对象的注解约束

`Request`、`Response`、`Command`、`Query`、`VO` 这类纯数据载体对象，默认遵循以下规则：

- 同时需要 getter / setter 的可变数据载体，默认优先使用 `@Data`
- 只需要只读访问的对象，优先使用 `@Getter`
- 对 `Command`、`DTO`、`VO`、内部快照载荷、Convert 转换目标等对象，如果主要通过集中装配、不可变表达或一次性构造完成赋值，默认优先考虑 `@Builder`
- 如果 `Command`、`Query`、`VO` 等对象在当前项目中需要开放常规 getter / setter 并承担可变数据载体角色，仍然优先使用 `@Data`
- 枚举默认使用 `@Getter` 即可，不要给枚举加 `@Setter`
- 不要为纯字段读写手写 getter / setter
- 只有在框架兼容要求、序列化约束、访问控制定制、字段派生逻辑等场景下，才允许手写访问方法
- 即使用了注解，类注释和关键字段中文注释仍然不能省略
- 不要为了回避 `convert` 层，直接在 Service / Controller 里写大段 setter 链式装配

推荐写法示例：

```java
/**
 * 后台确认订单请求参数。
 */
@Data
public class ConfirmOrderRequest {

    /**
     * 订单号。
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 确认备注。
     */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
```

不推荐写法示例：

```java
public class ConfirmOrderRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
```

补充示例：

```java
/**
 * 确认订单命令。
 */
@Getter
@Builder
public class ConfirmOrderCommand {

    /**
     * 订单号。
     */
    private final String orderNo;

    /**
     * 确认备注。
     */
    private final String remark;
}
```

```java
/**
 * 导出任务快照。
 */
@Getter
@Builder
public class ExportTaskPayloadDTO {

    /**
     * 导出任务主键。
     */
    private final Long exportJobId;

    /**
     * 项目主键。
     */
    private final Long projectId;
}
```

```java
/**
 * 订单状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatusEnum {

    /**
     * 待处理。
     */
    PENDING(1, "待处理"),

    /**
     * 已确认。
     */
    CONFIRMED(2, "已确认");

    private final Integer code;
    private final String description;
}
```

### 2.5 行内注释

行内注释只写“为什么”，重点覆盖：

- 状态机与状态流转前置条件
- 金额口径与精度处理
- 数据范围收敛点
- 分布式锁粒度与锁键拼装依据
- 幂等键选择依据
- 事务边界与补偿点
- 验签、重试、降级、兼容分支
- 复杂 SQL 条件、排序、分页口径

### 2.6 禁止事项

禁止以下注释：

- 只复述代码字面含义
- 与实现不一致的过期注释
- `TODO 后续处理`、`业务逻辑`、`处理数据` 这类空泛注释
- 大段注释掉的旧代码
- 中英文混杂但缺乏统一规范的注释

### 2.7 修改代码时的要求

- 只要修改了某个类或方法，就要同步检查并补齐其中文注释
- 代码重构后必须同步更新注释，禁止保留旧语义注释
- 评审时把“注释是否覆盖关键语义”作为必查项

## 3. 数据库规范

业务表默认字段：

- `created_at`
- `updated_at`
- `is_deleted`
- `deleted_at`

建议补齐：

- `created_by`
- `updated_by`

统一规则：

- 逻辑删除统一走全局配置
- 唯一索引必须把 `is_deleted` 纳入唯一键
- 数据库字符集统一 `utf8mb4`
- 索引围绕查询条件、排序字段、唯一约束设计
- 禁止把数据范围配置成 SQL 字符串片段

## 4. 异常、日志与响应规范

统一原则：

- 所有接口异常统一由 `GlobalExceptionHandler` 收口
- 不允许每个 Controller 单独写大段 `try/catch`
- 异常分类必须明确
- 前后台统一使用同一套响应结构
- 统一响应优先使用带泛型的响应体，例如 `CommonResponse<T>`，不要到处使用原始类型
- 简单参数前置校验统一优先使用项目级 `Validate` helper，例如 `Validate.notNull(...)`、`Validate.notBlank(...)`、`Validate.isTrue(...)`、`Validate.equal(...)`、`Validate.notEqual(...)`，不要在业务代码中散写重复的 `if (...) { throw ... }`
- 对字段存在性、开关状态、活动任务冲突、重复提交等简单守卫式失败分支，也默认优先使用 `Validate`，而不是裸写 `if + throw`
- 对“查无数据”“状态非法”“更新失败”等需要稳定业务错误码的失败语义，应使用 `BizException` 或项目统一业务异常断言能力，不要直接使用裸 `Validate`

推荐异常类：

- `BizException`
- `PermissionException`
- `DataScopeException`
- `RiskControlException`
- `ThirdPartyCallbackException`
- `FileUploadException`
- `FinanceException`

日志要求：

- `BizException` 默认按 `warn` 级别记录
- 数据库异常、未知运行时异常默认按 `error` 级别记录
- 日志必须带 `TraceId`
- 禁止打印密码、Token、验证码、银行卡号等敏感信息

异常处理细则：

- 不要直接抛裸 `RuntimeException`
- `catch` 后如重新抛出，必须补充上下文语义
- 第三方异常要映射成业务可理解的异常类型
- Controller 只负责抛出语义明确的异常，不负责拼装复杂错误响应
- `Validate` 触发的参数校验异常要由统一异常处理器收口
- 不要把所有 `NullPointerException` 一律映射为参数错误，避免把真实程序错误误判成前置校验失败
- 示例和默认规范中，不建议通过 `@ExceptionHandler(NullPointerException.class)` 全局把 `NullPointerException` 直接映射为 `PARAM_ERROR`
- 如果项目需要稳定返回参数错误或业务错误码，优先改用项目级断言 helper、`BizException`、或不会落入全局 `NullPointerException` 分支的统一异常写法
- 默认不要直接依赖底层库原生 `Validate.notNull(...)` 的异常行为；如项目已经统一封装为项目级 `Validate.notNull(...)`，则应以该统一 helper 的稳定异常契约为准

#### 4.1 `Validate` 与 `BizException` 使用边界

- 这里的 `Validate` 默认指项目统一封装后的前置校验 helper，不要求业务代码直接拼 Apache Commons Lang3 与 Hutool 底层调用
- 简单前置校验、简单守卫式失败、基础存在性校验，默认优先使用项目级 `Validate.notNull(...)`、`Validate.notBlank(...)`、`Validate.isTrue(...)`、`Validate.equal(...)`、`Validate.notEqual(...)`
- 对简单空值校验，默认直接使用 `Validate.notNull(value, "...不能为空")`，不要在业务代码里反复手写 `Validate.isTrue(ObjectUtil.isNotNull(value), "...不能为空")`
- 对简单相等/不等等基础守卫，默认直接使用 `Validate.equal(...)`、`Validate.notEqual(...)`，不要在业务代码里反复手写 `Validate.isTrue(ObjectUtil.equal(...), "...")`、`Validate.isTrue(ObjectUtil.notEqual(...), "...")`
- 需要明确业务错误码、区分业务异常类别、保留稳定异常契约时，使用 `BizException` 或项目内的专用业务异常
- 不要为了简单空值判断专门 new 一个 `BizException`
- 不要把原本需要错误码的业务失败全部降级成只有文案的 `Validate`

推荐示例：

```java
// 简单守卫失败，直接使用项目级 Validate helper。
Validate.notNull(command, "确认订单命令不能为空");
Validate.notBlank(command.getOrderNo(), "订单号不能为空");
Validate.notEqual(command.getCurrentStatus(), OrderStatusEnum.CANCELLED.getCode(), "已取消订单不允许确认");

// 需要明确错误码时，使用 BizException 或统一业务断言能力。
BizAssert.notNull(orderDO, OrderErrorCodes.ORDER_NOT_FOUND, OrderErrorMessages.ORDER_NOT_FOUND);
```

#### 4.2 项目级 `Validate` 统一前置校验能力

- 对高频复用的基础前置校验，默认应先抽成项目级统一 helper，再在业务代码中复用
- 默认提供 `notNull(...)`、`notBlank(...)`、`isTrue(...)`、`equal(...)`、`notEqual(...)` 等高频方法，不要把底层工具组合模板散落到每个 ServiceImpl
- 如果项目底层依赖 Apache Commons Lang3、Hutool 或其他断言工具，应收口在 helper 内部，不要要求业务代码反复拼装
- 项目级 `Validate` 应暴露稳定异常契约，例如统一抛 `IllegalArgumentException` 或项目约定的参数异常，而不是让调用方依赖底层库的偶发异常行为

#### 4.3 `BizAssert` 统一业务断言能力

- 需要稳定业务错误码、稳定异常契约、统一业务失败翻译时，推荐提供项目级业务断言工具
- 默认命名使用 `BizAssert`；只有模块边界明确且断言能力确实隔离时，才使用 `XxxBizAssert`
- 默认放在 `infrastructure/exception/assert` 或等价的全局异常基础设施目录中，不要散落到具体业务模块的 `util`
- `BizAssert` 只负责把统一断言失败翻译成业务异常，不承载具体业务规则本身
- `BizAssert` 常见能力包括 `notNull(...)`、`isTrue(...)`、`notBlank(...)` 等，但方法命名和异常契约必须在项目内保持一致

## 5. 代码质量与可维护性规范

### 5.1 单一职责与抽象层级

- 一个类尽量聚焦一个主要职责
- 一个方法只做一个抽象层级的事情
- 当一个方法同时混杂“参数校验 + 业务编排 + 状态流转 + 持久化细节 + 第三方调用”时，必须拆分

#### 5.1.1 重复模式自动抽离约束

- 只要某个低层模板已经明确会复用，或非常明显后续还会继续出现，就应尽早抽离到合适抽象中，不要等复制扩散后再统一收口
- 常见需要自动抽离的模式包括：前置校验模板、业务断言模板、枚举状态判断、对象组装、锁 Key 拼装、查询条件启停、第三方请求模板、结果解析模板
- 识别启发式：如果一行业务代码同时出现两层及以上底层 helper 组合，并且带有业务提示文案、错误码或异常语义，默认就应视为抽离候选，而不是继续内联
- 抽离位置要贴近真实职责：参数守卫优先进项目级 `Validate`，业务错误码断言优先进 `BizAssert`，状态判断优先进枚举静态方法，对象转换优先进 `Convert`，三方调用模板优先进 `client`
- 自动抽离不等于堆全局大杂烩；不要把跨层、跨语义的内容全部塞进 `CommonUtil`、`Helper`、`Utils`
- 当模式只在一个小模块稳定复用时，优先抽到模块内最近的拥有者，不要过早升成全局基础设施

#### 5.1.2 对象装配与 Convert 约束

- `Assembler` 和 `Convert` 是两层不同能力，不能混用
- `Assembler` 默认位于 `web / interfaces`，负责 `Request -> Command`、`VO -> Response` 等入口层装配
- `convert` 是默认业务层配套能力，不是可有可无的装饰层
- `DO -> VO`、`DO -> DTO`、内部 `DTO` 快照组装等跨对象转换，默认优先进入 `XxxConvert`
- `Convert` 内部可以使用 `Builder` 完成对象构造
- 只有在转换逻辑一次性、局部、极短，且抽 `convert` 反而降低可读性时，才允许直接在当前方法里写一个很短的 `Builder`
- 不要把几十行对象装配散写在 Controller、Assembler、ServiceImpl 里

#### 5.1.3 ServiceImpl 编排格式

`ServiceImpl`、`ApplicationServiceImpl` 这类用例编排方法，默认遵循以下结构：

- `// 校验`：收口参数前置校验、存在性校验、状态合法性校验、权限前置判断
- `// 创建`、`// 更新`、`// 查询`：执行当前用例的核心业务动作
- `// 响应`：返回主键、结果对象或转换后的响应数据

推荐写法：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long saveTimeline(SaveTimelineCommand saveTimelineCommand) {
    // 校验
    validateSaveTimelineCommand(saveTimelineCommand);

    // 创建
    TimelineVersionDO timelineVersionDO = buildTimelineVersionDO(saveTimelineCommand);
    timelineVersionRepo.insert(timelineVersionDO);

    // 响应
    return timelineVersionDO.getId();
}
```

约束说明：

- 这类阶段注释应体现“编排阶段”，不要写成空泛注释
- 能抽到 `validateXxx()`、`buildXxx()`、`saveXxx()`、`queryXxx()` 的逻辑，优先抽出复用
- `ServiceImpl` 负责串联，不负责塞满所有实现细节

### 5.2 方法体控制

- 优先使用卫语句和早返回，降低嵌套深度
- 普通业务方法默认不应超过 3 层嵌套
- 方法过长、分支过多、可读性下降时，优先抽私有方法或专用组件
- 复杂业务优先抽取 `XxxManager`、`XxxValidator`、`XxxPolicy`
- 多个用例重复出现的校验、构造、状态流转、快照生成逻辑，优先抽成可复用方法或领域组件

### 5.3 代码复用、DDD 与抽象

- 相同业务语义的校验逻辑，不要在多个 `ServiceImpl` 中重复散写，优先收口到 `Validator`、`Manager` 或复用私有方法
- 相同业务语义的状态流转，不要在多个方法里各写一套，优先收口到领域模型、`Manager` 或 `Policy`
- 相同业务语义的对象转换，不要在多个地方重复 new / set，优先收口到 `Assembler` 或 `Convert`
- 抽象必须服务于稳定业务语义，不要为了“看起来分层很多”而制造空壳抽象
- DDD 的重点是边界清晰、职责收口、规则聚合，不是机械堆叠术语或目录
- 对已存在且稳定的 skill 规范代码，优先延续现有抽象，不要轻易推翻已有分层和命名口径

### 5.4 空值与返回值

- 列表、分页、批量结果默认不返回 `null`
- 单对象 Repo 查询默认允许返回 `null`，由 `ServiceImpl`、`domain` 或统一业务断言能力翻译成业务语义
- 如项目已经统一使用 `Optional` 表达单对象查询结果，则同一模块内保持一致，不要混用 `null` 和 `Optional`
- 不要把 Repo 返回的 `null` 继续向 `web / interfaces` 的对外契约暴露
- 项目默认引入 Hutool 作为通用工具库。空值、空串、空集合判断以及常见工具能力，默认优先使用 Hutool，不要在同一模块中混用多套工具风格，也不要自己散写零散工具函数

#### 5.4.1 判空工具统一约束

- 除非存在明确的特殊情况，否则对象空值判断强制使用 `ObjectUtil.isNull(...)`、`ObjectUtil.isNotNull(...)`
- 除非存在明确的特殊情况，否则对象相等判断强制使用 `ObjectUtil.equal(...)`、`ObjectUtil.notEqual(...)`
- 除非存在明确的特殊情况，否则字符串空白判断强制使用 `StrUtil.isBlank(...)`、`StrUtil.isNotBlank(...)`
- 仅在业务确实只需要区分空串与非空串时，才使用 `StrUtil.isEmpty(...)`、`StrUtil.isNotEmpty(...)`
- 除非存在明确的特殊情况，否则集合判空强制使用 `CollUtil.isEmpty(...)`、`CollUtil.isNotEmpty(...)`
- 对 `Boolean` 装箱值、三态标记位、可空布尔开关，默认使用 `BooleanUtil.isTrue(...)`、`BooleanUtil.isFalse(...)`，不要散写 `Boolean.TRUE.equals(flag)`、`Boolean.FALSE.equals(flag)`
- `Map`、数组、Bean、JSON、日期、ID、URL 等通用能力，默认优先使用 Hutool 对应工具，例如 `MapUtil`、`ArrayUtil`、`BeanUtil`、`JSONUtil`、`DateUtil`、`IdUtil`、`URLUtil`
- 一次性、轻量 HTTP 请求或简单第三方调用，默认优先使用 Hutool 的 `HttpUtil` 或 `HttpRequest`，但仍应放在 `infrastructure/client` 或 `integration/client` 等统一边界内，并显式满足超时、重试、域名白名单、协议校验与 SSRF 防护要求
- 特殊情况仅限：现有框架 API 强约束、历史模块兼容成本过高、或项目基础设施已经统一封装成更高层能力
- 同一模块内不要混用多套空值、字符串、集合工具，也不要自己手写重复 helper
- 同一模块内不要混用 `ObjectUtil.equal(...)`、`Objects.equals(...)`、`Boolean.TRUE.equals(...)` 等多套判断风格
- 对 Query / Repo / Mapper 条件拼装，默认强制使用上述工具方法表达条件启停，不要手写冗长判空链

推荐示例：

```java
@Override
public Page<EmployeeDO> page(String employeeName, String mobile, Integer status, long current, long size) {
    return employeeMapper.selectPage(
        new Page<>(current, size),
        Wrappers.<EmployeeDO>lambdaQuery()
            .like(StrUtil.isNotBlank(employeeName), EmployeeDO::getEmployeeName, employeeName)
            .eq(StrUtil.isNotBlank(mobile), EmployeeDO::getMobile, mobile)
            .eq(ObjectUtil.isNotNull(status), EmployeeDO::getStatus, status)
            .orderByDesc(EmployeeDO::getId)
    );
}
```

不推荐片段：

```java
.like(employeeName != null && !employeeName.isBlank(), EmployeeDO::getEmployeeName, employeeName)
.eq(mobile != null && !mobile.isBlank(), EmployeeDO::getMobile, mobile)
.eq(status != null, EmployeeDO::getStatus, status)
```

### 5.5 校验分层

- `web / interfaces` 负责格式校验、必填校验、长度校验、枚举值校验
- `domain` 或 `validator` 负责业务不变量、状态合法性、金额口径校验
- `business`、`domain`、`validator` 中的简单前置条件校验，优先统一使用项目级 `Validate` helper
- 对空值校验统一优先使用 `Validate.notNull(...)`
- 对字符串非空校验统一优先使用 `Validate.notBlank(...)`
- 对基础相等/不等守卫统一优先使用 `Validate.equal(...)`、`Validate.notEqual(...)`
- 对布尔条件校验统一优先使用 `Validate.isTrue(...)`
- 不要在多个业务方法里重复散写 `if (...) { throw new BizException(...) }` 作为基础前置校验模板
- `if` 可以用于正常业务分支，但对简单守卫式失败不要写成 `if (...) { throw ... }`
- 对需要稳定业务错误码的失败语义，不要直接使用裸 `Validate`，应使用 `BizException` 或项目统一业务异常断言能力翻译
- Repo 不承载业务规则校验，也不承载“更新失败”“状态非法”“查无数据”这类业务异常翻译
- 单对象 Repo 查询默认允许返回 `null`，由 `ServiceImpl`、`domain` 或统一业务断言能力翻译成业务语义
- Repo 返回值默认按操作类型统一：`insert` 返回主键或已回填主键的实体，`update` / `delete` / 状态更新默认返回 `boolean`，`count` 返回计数值，`exists` 返回 `boolean`
- Repo 写操作默认返回 `boolean`、影响行数、主键等数据库结果；复杂条件写操作如调用方需要区分失败原因，可返回显式结果对象、枚举或其他 typed result

推荐示例：

```java
/**
 * 更新员工信息。
 *
 * @param employeeDO 员工实体
 * @return 是否更新成功
 */
public boolean update(EmployeeDO employeeDO) {
    return employeeMapper.updateById(employeeDO) > 0;
}
```

```java
/**
 * 更新员工。
 *
 * @param command 更新员工命令
 */
@Override
@Transactional(rollbackFor = Exception.class)
public void updateEmployee(UpdateEmployeeCommand command) {
    // 校验
    Validate.notNull(command, "更新员工命令不能为空");
    EmployeeDO employeeDO = employeeRepo.findById(command.getEmployeeId());
    BizAssert.notNull(employeeDO, AccountErrorCodes.EMPLOYEE_NOT_FOUND, AccountErrorMessages.EMPLOYEE_NOT_FOUND);

    // 更新
    employeeConvert.fillForUpdate(command, employeeDO);
    boolean updated = employeeRepo.update(employeeDO);
    BizAssert.isTrue(updated, AccountErrorCodes.EMPLOYEE_UPDATE_FAILED, AccountErrorMessages.EMPLOYEE_UPDATE_FAILED);
}
```

不推荐片段：

```java
if (employeeMapper.updateById(employeeDO) != 1) {
    throw new BizException(AccountErrorCodes.EMPLOYEE_UPDATE_FAILED, "员工更新失败");
}
```

补充说明：

- 简单参数存在性、基础布尔守卫、格式前置条件仍然优先使用项目级 `Validate`
- 不要在业务代码里反复手写 `Validate.isTrue(ObjectUtil.isNotNull(...), "...不能为空")` 这类可封装模板
- 不要在业务代码里反复手写 `Validate.isTrue(ObjectUtil.equal(...), "...")`、`Validate.isTrue(ObjectUtil.notEqual(...), "...")` 这类可封装模板
- 其他高频重复模板也应按职责自动抽离，例如状态判断进枚举、对象组装进 `Convert`、锁键拼装进 `XxxLockKeys`、第三方调用模板进 `client`
- 需要稳定业务错误码的“查无数据”“状态非法”“更新失败”等失败语义，必须在 `ServiceImpl`、`domain` 或统一业务断言能力中显式翻译

#### 5.5.1 注解校验消息规范

- `@NotNull`、`@NotBlank`、`@Size` 等注解中的 `message` 必须使用面向业务的中文提示
- 不要直接把字段名、英文属性名、技术字段名塞进提示文案
- 提示语优先回答“用户或调用方缺少了什么业务信息”，而不是“哪个字段名为空”

推荐示例：

```java
/**
 * 查询时间轴命令。
 */
@Data
public class GetTimelineQuery {

    /**
     * 项目主键。
     */
    @NotNull(message = "项目不能为空")
    private Long projectId;
}
```

```java
/**
 * 保存时间轴命令。
 */
@Data
public class SaveTimelineCommand {

    /**
     * 时间轴 DSL JSON。
     */
    @NotBlank(message = "时间轴不能为空")
    private String timelineDslJson;
}
```

不推荐片段：

```java
@NotNull(message = "projectId不能为空")
@NotBlank(message = "timelineDslJson不能为空")
```

补充说明：

- 反例片段只展示问题点，不作为可复制模板，避免把错误写法再次带回生成结果

### 5.6 事务与一致性

- 事务默认放在 `business` 编排层
- 不要把事务散落在 Controller、Repo、Client
- 涉及资金链路、状态流转、流水写入时，要明确事务边界和补偿策略
- 涉及回调幂等时，要显式说明幂等键来源

### 5.7 异常处理质量

- 不允许吞异常
- 只有在“转换异常语义、补充上下文、重试、降级、日志增强”时才允许 `catch`
- 业务异常与系统异常必须区分清楚
- 不要把所有异常都映射成同一个模糊错误码

### 5.8 查询与性能

- 批量处理优先批量查库、批量写库，避免循环内查库形成 N+1
- 多表联查、统计、导出、对账统一进入 XML
- 分页查询必须保证排序口径明确
- 对高频接口明确缓存、锁、幂等或防抖策略

### 5.9 可读性与可评审性

- 常量、枚举、异常、响应结构要语义清晰
- 不允许散写业务字面量
- 装配器与转换器保持纯粹，不写业务规则
- 重要类、方法、关键分支必须带中文注释
- 代码提交前至少完成一次“命名 / 注释 / 边界 / 异常 / 空值 / 重复逻辑”自检

### 5.10 金额与时间

- 金额统一使用 `BigDecimal`
- 禁止使用 `double` / `float` 做金额计算
- 时间语义统一使用明确类型，如 `LocalDateTime`、`LocalDate`
- 不要跨层用裸字符串承载复杂时间语义
