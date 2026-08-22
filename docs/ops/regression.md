# 回归清单（入口）

细项以 [modules/CHECKLIST.md](../modules/CHECKLIST.md) 与各模块 `checklist.md` 为准。本页列**发布前必跑**后端冒烟。

> 本文档**已定稿**。下列 `[ ]` 为**发版执行勾选**，不是文档缺口；自动化覆盖见 CI / `make test`，不替代本表手工路径。  
> **本仓只验收后端接口**；客户端打包 / UI 不在此勾选。

## 自动化（每次 PR）

| 门禁 | 覆盖 |
| ---- | ---- |
| `mvn test` / CI `ci-check` | 单测 + 文档脚手架 |
| `ci-compose-smoke` | 联调 Compose 全栈（`sync-env-tag` + `--env-file .env.dev` + `staging-core-smoke`） |
| `ci-k8s-manifest` | Kustomize 预检（staging/prod） |

本地：`make test` · `make smoke`（日常：`make dev-up` + `bash deploy/scripts/dev-apps.sh` 后经 Nginx `:18080`；联调全栈 Compose 则 `BASE_URL=http://localhost make smoke`）。

## 冒烟（每 PR / 每日 · 手工）

- [ ] 登录 / 登出 / Token 过期
- [ ] 创建团队项目 → 自动项目群 → 加成员同步
- [ ] 创建任务 → 改期 → 完成 → 归档
- [ ] 单聊发文本 / 图片；撤回
- [ ] 文件上传（小文件 + ≥10MB 分片）
- [ ] 全局搜索（有引擎 / 降级两种）

## 权限

- [ ] 普通成员打不开系统设置
- [ ] 项目管理员不能转让拥有者
- [ ] visibility=2 任务对非任务成员不可见
- [ ] 离职交接：`disable` + `handoverUserId` → 归属迁移 + 交接人收到桌面/system-msg

## 异步

- [ ] 通知类操作后 Kafka 有消息；worker 消费成功
- [ ] 多实例下 WS 仍能收到（fanout）
