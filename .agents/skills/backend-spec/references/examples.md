# 目录模板与代码示例

> 常见实现示例，不构成对具体框架或中间件的强制绑定；项目已有等价能力时优先沿用。

## 目录

- [1. 类模板清单](#1-类模板清单)
- [2. 单流程标准写法](#2-单流程标准写法)
- [3. 代码示例](#3-代码示例)

## 1. 类模板清单

### 1.1 infrastructure 层常见参考组件

```text
infrastructure
├─ security
│  ├─ SecurityConfig.java
│  ├─ JwtTokenService.java
│  └─ JwtAuthenticationFilter.java
├─ mybatis
│  ├─ MybatisPlusConfig.java
│  └─ CommonMetaObjectHandler.java
├─ lock
│  ├─ DistributedLockExecutor.java
│  └─ RedisDistributedLockExecutor.java
├─ debounce
│  ├─ NoRepeatSubmit.java
│  └─ NoRepeatSubmitAspect.java
├─ permission
│  ├─ PermissionCheck.java
│  └─ PermissionCheckAspect.java
├─ log
│  ├─ OperationLog.java
│  ├─ SensitiveOperationLog.java
│  └─ OperationLogAspect.java
├─ risk
│  ├─ RiskCheck.java
│  └─ RiskCheckAspect.java
├─ exception
│  ├─ BizException.java
│  ├─ code
│  │  └─ CommonErrorCodes.java
│  ├─ assert
│  │  ├─ Validate.java
│  │  └─ BizAssert.java
│  ├─ PermissionException.java
│  ├─ RiskControlException.java
│  └─ GlobalExceptionHandler.java
├─ response
│  └─ CommonResponse.java
└─ client
   └─ ai
      ├─ AiClient.java
      ├─ AiProperties.java
      └─ XxxAiClient.java
```

### 1.2 persistence 层常见目录模板

```text
persistence
└─ order
   ├─ model
   │  └─ OrderDO.java
   ├─ enums
   │  └─ OrderStatusEnum.java
   ├─ mapper
   │  ├─ OrderMapper.java
   │  └─ OrderQueryMapper.java
   ├─ repo
   │  └─ OrderRepo.java
   ├─ repo/impl
   │  └─ OrderRepoImpl.java
   └─ mapper/xml
      └─ OrderQueryMapper.xml
```

### 1.3 business 层常见目录模板

```text
business
└─ order
   ├─ command
   │  └─ ConfirmOrderCommand.java
   ├─ query
   │  └─ OrderPageQuery.java
   ├─ vo
   │  └─ OrderVO.java
   ├─ dto
   │  └─ OrderExportTaskPayloadDTO.java
   ├─ convert
   │  └─ OrderConvert.java
   ├─ service
   │  └─ OrderService.java
   ├─ service/impl
   │  └─ OrderServiceImpl.java
   ├─ constant
   │  ├─ OrderErrorCodes.java
   │  ├─ OrderErrorMessages.java
   │  ├─ OrderPermissionCodes.java
   │  ├─ OrderRiskRuleCodes.java
   │  ├─ OrderLockKeys.java
   │  └─ OrderNoRepeatKeys.java
   └─ domain
      ├─ manager
      │  └─ OrderManager.java
      ├─ validator
      │  └─ OrderValidator.java
      └─ policy
         └─ OrderPolicy.java
```

### 1.4 web / interfaces 层常见目录模板

默认场景下，接口路径统一使用 `/api/**`，目录默认按 `web/api/...` 组织：

```text
web
└─ api
   └─ order
      ├─ controller
      │  └─ OrderController.java
      ├─ request
      │  └─ ConfirmOrderRequest.java
      └─ assembler
         └─ OrderWebAssembler.java
```

只有项目明确存在独立后台管理端时，才额外补 `web/admin/...` 子树，后台 Controller、Assembler 显式带 `Admin` 前缀：

```text
web
├─ api
│  └─ order
│     ├─ controller
│     │  └─ OrderController.java
│     ├─ request
│     │  └─ ConfirmOrderRequest.java
│     └─ assembler
│        └─ OrderWebAssembler.java
└─ admin
   └─ order
      ├─ controller
      │  └─ AdminOrderController.java
      ├─ request
      │  └─ AdminConfirmOrderRequest.java
      └─ assembler
         └─ AdminOrderWebAssembler.java
```

### 1.5 integration 层常见目录模板

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

## 2. 单流程标准写法

以“后台确认完单”为例，标准链路如下：

1. `web` 接收请求并完成参数校验。
2. `web` 通过 `Assembler` 转成 `business` Command。
3. `business` 统一处理权限、防抖、日志、事务。
4. `business` 在业务主键维度执行统一并发控制策略，例如分布式锁。
5. `business` 调用 `persistence` 查询数据。
6. `domain` 校验状态并处理状态流转。
7. `business` 通过 `Convert` 完成 `DO / DTO / VO` 的对象装配；只有特别短的一次性装配才允许直接 `Builder`。
8. `persistence` 使用项目既有数据访问组件落库。
9. AOP 或事件统一记录日志、审计和风控信息。

## 3. 代码示例

以下示例只展示一种可参考实现风格，不构成对特定框架、中间件或 ORM 的强制绑定。

### 3.1 基础实体示例

```java
/**
 * 业务基础实体。
 * <p>
 * 统一沉淀审计字段和逻辑删除字段，供各业务实体继承。
 */
public class BaseDO {

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人。
     */
    private Long createdBy;

    /**
     * 更新人。
     */
    private Long updatedBy;

    /**
     * 逻辑删除标记。
     */
    private Integer isDeleted;

    /**
     * 删除时间。
     */
    private LocalDateTime deletedAt;
}
```

### 3.2 常量与枚举示例

```java
/**
 * 订单业务错误码常量。
 */
public final class OrderErrorCodes {

    /**
     * 订单不存在。
     */
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

    /**
     * 订单状态不允许确认。
     */
    public static final String ORDER_STATUS_INVALID = "ORDER_STATUS_INVALID";

    /**
     * 订单确认失败。
     */
    public static final String ORDER_CONFIRM_FAILED = "ORDER_CONFIRM_FAILED";

    private OrderErrorCodes() {
    }
}
```

```java
/**
 * 订单业务错误消息常量。
 */
public final class OrderErrorMessages {

    /**
     * 订单不存在。
     */
    public static final String ORDER_NOT_FOUND = "订单不存在";

    /**
     * 当前订单状态不允许确认。
     */
    public static final String ORDER_STATUS_INVALID = "订单状态不允许确认";

    /**
     * 订单确认失败。
     */
    public static final String ORDER_CONFIRM_FAILED = "订单确认失败";

    private OrderErrorMessages() {
    }
}
```

```java
/**
 * 订单权限码常量。
 */
public final class OrderPermissionCodes {

    /**
     * 确认订单权限。
     */
    public static final String ORDER_CONFIRM = "order-confirm";

    private OrderPermissionCodes() {
    }
}
```

```java
/**
 * 订单风控规则码常量。
 */
public final class OrderRiskRuleCodes {

    /**
     * 订单确认风控规则。
     */
    public static final String ORDER_CONFIRM_AUDIT = "ORDER_CONFIRM_AUDIT";

    private OrderRiskRuleCodes() {
    }
}
```

```java
/**
 * 订单防重复提交 Key 常量。
 */
public final class OrderNoRepeatKeys {

    /**
     * 确认订单防重 Key 前缀。
     */
    public static final String ORDER_CONFIRM = "api:order:confirm";

    private OrderNoRepeatKeys() {
    }
}
```

```java
/**
 * 订单分布式锁 Key 常量。
 */
public final class OrderLockKeys {

    /**
     * 确认订单锁前缀。
     */
    public static final String CONFIRM_ORDER_PREFIX = "order:confirm:";

    private OrderLockKeys() {
    }

    /**
     * 构造确认订单锁 Key。
     *
     * @param orderNo 订单号
     * @return 分布式锁 Key
     */
    public static String confirmOrder(String orderNo) {
        return CONFIRM_ORDER_PREFIX + orderNo;
    }
}
```

```java
/**
 * 时间轴版本常量。
 */
public final class TimelineVersionConstants {

    /**
     * 时间轴版本前缀。
     */
    public static final String VERSION_PREFIX = "v";

    private TimelineVersionConstants() {
    }

    /**
     * 构造时间轴版本号。
     *
     * @param versionCount 当前版本数量
     * @return 下一个时间轴版本号
     */
    public static String nextVersion(Integer versionCount) {
        return VERSION_PREFIX + (versionCount + 1);
    }
}
```

```java
/**
 * 订单接口路径常量。
 */
public final class OrderApiPaths {

    /**
     * 订单接口基础路径。
     * 默认使用 `/api/**`,所有项目通用。
     */
    public static final String API_BASE = "/api/order";

    /**
     * 确认订单路径。
     */
    public static final String CONFIRM_ORDER = "/confirm-order";

    private OrderApiPaths() {
    }
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
    CONFIRMED(2, "已确认"),

    /**
     * 已拒绝。
     */
    REJECTED(3, "已拒绝");

    private final Integer code;
    private final String description;

    /**
     * 判断是否为待处理状态。
     *
     * @param code 状态编码
     * @return 是否为待处理
     */
    public static boolean isPending(Integer code) {
        return ObjectUtil.equal(PENDING.getCode(), code);
    }
}
```

### 3.3 Controller / Request / Assembler / Convert 示例

```java
/**
 * 订单管理控制器。
 * <p>
 * 只负责请求接收、参数校验、命令装配和统一响应，不直接承载业务规则。
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(OrderApiPaths.API_BASE)
public class OrderController {

    private final OrderService orderService;
    private final OrderWebAssembler orderWebAssembler;

    /**
     * 确认订单。
     *
     * @param request 确认订单请求
     * @return 通用成功响应
     */
    @PostMapping(OrderApiPaths.CONFIRM_ORDER)
    public CommonResponse<Void> confirmOrder(@Valid @RequestBody ConfirmOrderRequest request) {
        ConfirmOrderCommand command = orderWebAssembler.toConfirmOrderCommand(request);
        orderService.confirmOrder(command);
        return CommonResponse.success();
    }
}
```

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

```java
/**
 * 订单 Web 装配器。
 * <p>
 * 负责入口层 `Request` 与业务层 `Command` 之间的装配。
 */
@Component
public class OrderWebAssembler {

    /**
     * 将确认订单请求转换为业务命令。
     *
     * @param request 请求对象
     * @return 确认订单命令
     */
    public ConfirmOrderCommand toConfirmOrderCommand(ConfirmOrderRequest request) {
        return ConfirmOrderCommand.builder()
            .orderNo(request.getOrderNo())
            .remark(request.getRemark())
            .build();
    }
}
```

```java
/**
 * 订单对象转换器。
 * <p>
 * 负责业务层 `DO / DTO / VO` 之间的转换。
 */
@Component
public class OrderConvert {

    /**
     * 将时间轴版本实体转换为导出任务快照。
     *
     * @param exportJobId 导出任务主键
     * @param timelineVersionDO 时间轴版本实体
     * @param outputObjectKey 导出结果对象存储 Key
     * @return 导出任务快照
     */
    public ExportTaskPayloadDTO toExportTaskPayloadDTO(
        Long exportJobId,
        TimelineVersionDO timelineVersionDO,
        String outputObjectKey
    ) {
        return ExportTaskPayloadDTO.builder()
            .exportJobId(exportJobId)
            .projectId(timelineVersionDO.getProjectId())
            .timelineVersionId(timelineVersionDO.getId())
            .timelineDslJson(timelineVersionDO.getTimelineDslJson())
            .outputObjectKey(outputObjectKey)
            .build();
    }
}
```

### 3.4 Command / Service / Domain 示例

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
 * 订单业务服务。
 */
public interface OrderService {

    /**
     * 确认订单。
     *
     * @param command 确认订单命令
     */
    void confirmOrder(ConfirmOrderCommand command);
}
```

```java
/**
 * 订单领域管理器。
 * <p>
 * 负责封装订单聚合内的状态校验和状态流转规则。
 */
@Component
public class OrderManager {

    /**
     * 校验订单是否允许确认。
     *
     * @param orderDO 订单实体
     */
    public void validateCanConfirm(OrderDO orderDO) {
        BizAssert.isTrue(
            OrderStatusEnum.isPending(orderDO.getStatus()),
            OrderErrorCodes.ORDER_STATUS_INVALID,
            OrderErrorMessages.ORDER_STATUS_INVALID
        );
    }
}
```

### 3.5 ServiceImpl 示例

```java
/**
 * 订单业务服务实现。
 * <p>
 * 负责后台确认订单用例的统一编排，包括权限、防重、事务和分布式锁控制。
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final OrderManager orderManager;
    private final DistributedLockExecutor distributedLockExecutor;

    /**
     * 确认订单。
     *
     * @param command 确认订单命令
     */
    @Override
    @PermissionCheck(code = OrderPermissionCodes.ORDER_CONFIRM, dataScope = true)
    @NoRepeatSubmit(keyPrefix = OrderNoRepeatKeys.ORDER_CONFIRM, expireSeconds = 5)
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(ConfirmOrderCommand command) {
        Validate.notNull(command, "确认订单命令不能为空");
        Validate.notBlank(command.getOrderNo(), "订单号不能为空");
        distributedLockExecutor.executeWithLock(
            OrderLockKeys.confirmOrder(command.getOrderNo()),
            3000L,
            10000L,
            () -> {
                // 校验
                // 先按业务主键查询订单，确保后续状态校验和落库都围绕同一订单展开。
                OrderDO orderDO = orderRepo.findByOrderNo(command.getOrderNo());
                BizAssert.notNull(orderDO, OrderErrorCodes.ORDER_NOT_FOUND, OrderErrorMessages.ORDER_NOT_FOUND);

                // 状态流转前先做领域校验，避免非法状态重复确认。
                orderManager.validateCanConfirm(orderDO);

                // 更新
                // 在持有分布式锁期间完成状态更新，防止并发确认造成重复写入。
                boolean updated = orderRepo.confirmOrder(
                    orderDO.getId(),
                    command.getRemark(),
                    OrderStatusEnum.CONFIRMED.getCode()
                );
                BizAssert.isTrue(updated, OrderErrorCodes.ORDER_CONFIRM_FAILED, OrderErrorMessages.ORDER_CONFIRM_FAILED);

                // 响应
            }
        );
    }
}
```

### 3.6 Repo / RepoImpl / XML 示例

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

```java
/**
 * 订单仓储接口。
 */
public interface OrderRepo {

    /**
     * 根据订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单实体，查无数据时返回 null，由业务层翻译成业务语义
     */
    OrderDO findByOrderNo(String orderNo);

    /**
     * 确认订单状态。
     *
     * @param id 订单主键
     * @param remark 确认备注
     * @param status 目标状态
     * @return 是否更新成功
     */
    boolean confirmOrder(Long id, String remark, Integer status);
}
```

```java
/**
 * 订单仓储实现。
 * <p>
 * 负责订单数据访问，不承载业务规则。
 */
@Repository
@RequiredArgsConstructor
public class OrderRepoImpl implements OrderRepo {

    private final OrderMapper orderMapper;

    /**
     * 根据订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单实体，查无数据时返回 null，由业务层翻译成业务语义
     */
    @Override
    public OrderDO findByOrderNo(String orderNo) {
        return orderMapper.selectOne(
            Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderNo, orderNo)
        );
    }

    /**
     * 确认订单状态。
     *
     * @param id 订单主键
     * @param remark 确认备注
     * @param status 目标状态
     * @return 是否更新成功
     */
    @Override
    public boolean confirmOrder(Long id, String remark, Integer status) {
        return orderMapper.update(
            null,
            Wrappers.lambdaUpdate(OrderDO.class)
                .eq(OrderDO::getId, id)
                .set(OrderDO::getStatus, status)
                .set(OrderDO::getConfirmRemark, remark)
        ) > 0;
    }
}
```

```xml
<!--
  订单列表查询。
  说明：
  1. 仅用于复杂多表联查；
  2. 排序口径固定为主键倒序；
  3. 查询条件中的状态字段由业务层明确传入。
-->
<select id="selectOrderPage" resultType="com.example.order.dto.OrderPageDTO">
    SELECT
        o.id,
        o.order_no,
        o.status,
        u.nickname AS user_name
    FROM biz_order o
    LEFT JOIN biz_user u ON u.id = o.user_id
    WHERE o.status = #{status}
    ORDER BY o.id DESC
</select>
```

### 3.7 第三方 Client 示例

```java
/**
 * AI 能力客户端。
 */
public interface AiClient {

    /**
     * 调用 AI 聊天能力。
     *
     * @param prompt 提示词
     * @return 厂商返回文本
     */
    String chat(String prompt);
}
```

```java
/**
 * 某厂商 AI Client 适配实现。
 * <p>
 * 这里只做厂商请求封装，不承载业务规则。
 */
@Component
@RequiredArgsConstructor
public class XxxAiClient implements AiClient {

    private final AiProperties aiProperties;

    /**
     * 调用厂商 AI 接口。
     *
     * @param prompt 提示词
     * @return 厂商返回文本
     */
    @Override
    public String chat(String prompt) {
        // 这里只做厂商请求封装，不写业务规则。
        return "mock-response";
    }
}
```

### 3.8 Validate 示例

```java
/**
 * 统一前置校验工具。
 * <p>
 * 负责收口高频基础守卫校验，避免在业务代码里反复拼装底层断言模板。
 */
public final class Validate {

    private Validate() {
    }

    /**
     * 断言对象非空。
     *
     * @param value   待校验对象
     * @param message 失败提示
     */
    public static void notNull(Object value, String message) {
        if (ObjectUtil.isNull(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串非空白。
     *
     * @param value   待校验字符串
     * @param message 失败提示
     */
    public static void notBlank(CharSequence value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言两个值相等。
     *
     * @param left    左值
     * @param right   右值
     * @param message 失败提示
     */
    public static void equal(Object left, Object right, String message) {
        if (ObjectUtil.notEqual(left, right)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言两个值不相等。
     *
     * @param left    左值
     * @param right   右值
     * @param message 失败提示
     */
    public static void notEqual(Object left, Object right, String message) {
        if (ObjectUtil.equal(left, right)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言条件成立。
     *
     * @param expression 条件表达式
     * @param message    失败提示
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

### 3.9 BizAssert 示例

```java
/**
 * 统一业务断言工具。
 * <p>
 * 负责把通用断言失败翻译成稳定的业务异常，不承载具体业务规则。
 */
public final class BizAssert {

    private BizAssert() {
    }

    /**
     * 断言对象非空。
     *
     * @param value   待校验对象
     * @param code    业务错误码
     * @param message 业务错误提示
     */
    public static void notNull(Object value, String code, String message) {
        if (ObjectUtil.isNull(value)) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言条件成立。
     *
     * @param expression 条件表达式
     * @param code       业务错误码
     * @param message    业务错误提示
     */
    public static void isTrue(boolean expression, String code, String message) {
        if (!expression) {
            throw new BizException(code, message);
        }
    }
}
```

### 3.10 全局异常处理示例

```java
/**
 * 全局异常处理器。
 * <p>
 * 统一收口接口异常并返回标准响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验异常。
     *
     * @param exception 参数校验异常
     * @return 失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse<Void> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        return CommonResponse.fail(CommonErrorCodes.PARAM_ERROR, CommonErrorMessages.PARAM_ERROR);
    }

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BizException.class)
    public CommonResponse<Void> handleBizException(BizException exception) {
        return CommonResponse.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理统一前置校验异常。
     *
     * @param exception 前置校验异常
     * @return 失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResponse<Void> handleValidateException(IllegalArgumentException exception) {
        return CommonResponse.fail(CommonErrorCodes.PARAM_ERROR, exception.getMessage());
    }

    /**
     * 处理未知异常。
     *
     * @param exception 未知异常
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception exception) {
        return CommonResponse.fail(CommonErrorCodes.SYSTEM_ERROR, CommonErrorMessages.SYSTEM_ERROR);
    }
}
```
