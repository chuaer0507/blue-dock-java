---
name: backend-spec
description: >-
  Java 后端项目设计、实现、重构与代码审查的统一规范。在 AI 编码助手需要构建或修改遵循
  「模块优先」组织、顶层模块命名优先 `infrastructure / persistence / business / web`、
  多入口使用 `interfaces`、大型第三方接入可选 `integration`、细化命名与注释、安全基线、
  可维护性治理，以及模块完成后全量 API 回归的 Java 后端时使用。
---

# Java 后端规范

## 如何应用本规范

> 本文档是 Java 后端的**架构边界与命名基线**——**不能替代工程判断**。应像资深后端工程师一样：先设计再实现，在边界内选择专家级方案。

对每一项非琐碎任务：

1. **架构优先，代码其次。** 先说清归属模块、跨越边界、复用还是扩展既有原语。
2. **规则是边界，不是脚本。** 与更清晰的业务表达冲突时，优先清晰表达并显式说明偏离。
3. **扩展架构，勿模仿反模式。** 邻近文件违规时，仍产出符合规范的版本并标出不一致。
4. **选专家级方案，勿堆样板。** 勿过早 `Manager / Helper / Util`；三行相似优于过早抽象。
5. **选定前摆出权衡（2–3 句）。** 再提交最贴合既有并发 / 规模 / 可观测性的方案。
6. **严谨程度与风险匹配。** 热路径（计费 / 鉴权 / 资金）提高严谨度；系统边界严格执行安全基线。
7. **宁删勿堆。** 删死字段、过时注释、无用包装；勿给自己刚引入的东西留 `TODO`。
8. **显式假设与缺口。** 规范要求的 helper 不存在时提议创建，勿默默内联。
9. **质疑静默回退。** 空 `catch`、失败变成功、鉴权失败 `return null` 等须显式决策。
10. **假设审阅者更聪明。** 名称带单位；分支带意图；一类一责。

## 规则等级

| 等级 | 含义 |
|------|------|
| `MUST`（必须） | 新项目与改动区域默认强制 |
| `RECOMMENDED`（推荐） | 优先采用；存量可渐进 |
| `OPTIONAL`（可选） | 按业务 / 流量 / 团队 / 合规选用 |

## 工作流

1. 先按业务目标拆模块；即使最终一个 `src/main/java`，也先按业务分包再分层。禁止全局平铺 `controller / service / repo / mapper / dto`。
2. 设计或改代码前，只读相关参考：
   - 架构与边界 → [references/architecture.md](references/architecture.md)
   - 命名 / 注释 / 校验 / 质量 → [references/coding.md](references/coding.md)
   - 安全 / 三方 / 并发 / 交付 → [references/security-delivery.md](references/security-delivery.md)
   - 安全基线 → [references/secure-baseline.md](references/secure-baseline.md)
   - 演进与治理 → [references/governance.md](references/governance.md)
   - 清单模板 → [references/checklists.md](references/checklists.md)
   - 示例 → [references/examples.md](references/examples.md)
3. 顶层优先：`infrastructure / persistence / business / web`；多入口时用 `interfaces` 替代 `web`。
4. 三方规模大时再拆 `integration`。
5. HTTP 请求/响应只在 `web` 或 `interfaces/http`；`business` 编排；强规则进 `domain`；`persistence` 只返数据语义，业务异常由 `ServiceImpl` / 业务断言翻译。
6. 命名：常量按类别（`XxxErrorCodes`、`XxxLockKeys`…）；枚举 `XxxEnum`；路径短横线、默认 `/api/**`；根包 `com.{org}.{projectName}`，禁止脚手架包。
7. 三方 Client 默认 `infrastructure/client`（或 `integration/client`）。
8. 业务在 `business`；回调 / MQ / Job 入口在 `web` 或 `interfaces`。
9. 区分 `Assembler`（入口）与 `Convert`（业务）；极短一次性 Builder 可跳过 `convert`。
10. 禁止魔法字符串 / 数字与 `Constants` 大杂烩。
11. 单业务链路单一并发策略，集中收口。
12. 中文注释强制：类、`public`/`protected`、Controller 端点、Repo、Convert、关键字段、非显然分支。
13. 载体对象优先 Lombok（`@Data` / `@Getter` / `@Builder`）；枚举仅 `@Getter`。
14. 简单守卫用项目级 `Validate`；稳定业务错误码用 `BizException` / `BizAssert`；校验文案用业务中文。
15. 删除无用 / 占位 / demo 目录。
16. 维护 `.gitignore` 与仓库卫生。
17. 密钥经 `application-{profile}.yml` + `XxxProperties`；禁止硬编码。
18. 模块完成后对该模块**全部 API** 回归，不只测本次改动。

## 参考导航

| 文件 | 用途 |
|------|------|
| [architecture.md](references/architecture.md) | 分层与模块边界 |
| [coding.md](references/coding.md) | 命名、注释、质量 |
| [security-delivery.md](references/security-delivery.md) | 鉴权、并发、三方、交付 |
| [secure-baseline.md](references/secure-baseline.md) | 密钥、上传、回调、SSRF |
| [governance.md](references/governance.md) | 兼容、ADR、门禁 |
| [checklists.md](references/checklists.md) | PR / 评审 / 上线清单 |
| [examples.md](references/examples.md) | 示例 |
| [index.md](references/index.md) | 主题导航 |
