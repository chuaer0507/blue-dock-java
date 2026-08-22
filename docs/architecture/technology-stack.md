# 技术选型

本文固定 **BlueDock** 生产栈各组件版本（截至 **2026-08-06**）。  
原则：后端取**最新 LTS（或等价稳定线）** + Kafka 当前支持 minor。本仓**只文档化后端**。

| 组件 | 版本 | 线 | 说明 |
| ---- | ---- | -- | ---- |
| Java | **25**（LTS） | LTS | OpenJDK / Temurin；`pom` / CI `java-version: 25` |
| Spring Boot | **4.1.0** | 当前稳定 | 一等支持 Java 25（Framework 7） |
| MyBatis-Plus | **3.5.14** | 当前稳定 | `mybatis-plus-spring-boot4-starter` |
| MySQL | **9.7.2**（LTS） | LTS | Compose 镜像 `mysql:9.7.2` |
| Redis | **8.2.8** Extended | Extended≈LTS | Compose `redis:8.2.8`；安全支持至 2030-09 |
| Kafka | **4.3.1** | 社区滚动 | Compose `apache/kafka:4.3.1`（KRaft） |
| Nginx | **1.30.4** stable | stable≈LTS | Compose `nginx:1.30.4` |

> Redis 官方称 **Extended**（非 LTS 字样）。生产钉 **8.2.8**，不跟 Standard 最新（如 8.8 / 8.10）。  
> Kafka **无官方 LTS**；生产钉 **4.3.1**（Compose / 文档与镜像一致）。

---

## 1. 总览

| 层级 | 技术 | 版本 | 职责 |
| ---- | ---- | ---- | ---- |
| 语言 / 运行时 | OpenJDK（Temurin） | **25** | API、WS、后台任务；可用虚拟线程 |
| 应用框架 | Spring Boot | **4.1.0** | Web、Security、Data、Scheduling、Actuator |
| Web | Spring MVC | 7.x | REST `api/{resource}/{action}` |
| 实时 | Spring WebSocket | 7.x | 即时同步 |
| ORM | MyBatis-Plus | **3.5.14** | `mybatis-plus-spring-boot4-starter`；见 [architecture.md](architecture.md) |
| 连接池 | HikariCP | 内置 | MySQL |
| 缓存 / 锁 | Spring Data Redis + Redisson | 随 Boot | 会话、缓存、分布式锁 |
| 消息队列 | Apache Kafka + `spring-kafka` | **4.3.1** | 跨域异步、WS 扇出、通知、索引同步 |
| 主库 | MySQL | **9.7.2** | InnoDB，utf8mb4，业务强一致 |
| 热数据 | Redis | **8.2.8** Extended | 缓存、在线状态、限流；**不做业务事件总线** |
| 接入层 | Nginx | **1.30.4** | 反代、TLS、WS 升级、静态/限流 |
| 构建 | Maven 3.9+ | — | 多模块 |
| 容器 JDK | Eclipse Temurin JRE | **25** | 多阶段 Docker |

本仓布局：

```
BlueDock/              # 后端 + docs + deploy
  bluedock-boot / bluedock-*    # 领域模块与可执行入口
  bluedock-worker-*         # 异步 Worker
  docs/                 # 设计与契约
  deploy/               # Compose / Nginx / 脚本
```

---

## 2. 后端

### 2.1 Java 25 + Spring Boot 4.1.0

| 考量 | 说明 |
| ---- | ---- |
| LTS | Java 25 为 2025-09 起最新 LTS，Premier 至约 2030 |
| 框架 | Boot **4.1.0** 基于 Spring Framework 7，一等支持 Java 25 |
| 并发 | 虚拟线程适合 WS 长连接与 IM 扇出 |
| 契约 | REST / WS 以 [api-contract.md](../contract/api-contract.md) 为唯一来源 |

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.0</version>
</parent>
```

### 2.2 数据与消息

| 组件 | 版本 | 说明 |
| ---- | ---- | ---- |
| MySQL | 9.7.2 | Flyway 迁移见 `bluedock-boot/.../db/migration` |
| Redis | 8.2.8 | Key 常量 `RedisKeys` |
| Kafka | 4.3.1 | Topic 常量 `KafkaTopics`；跨域异步，禁止 Redis 当 MQ |
| Nginx | 1.30.4 | `/api`、`/ws` 反代；见 `deploy/nginx` |

反向代理须透传：

```nginx
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

客户端经 Nginx 调本仓 API / WS；客户端工程本身不在本仓文档。

---

## 3. 明确不做 / 延后

| 项 | 说明 |
| -- | ---- |
| MariaDB | 生产统一 **MySQL 9.7.2** |
| Redis 当 MQ | 跨域业务事件必须走 **Kafka** |
| Redis Standard 最新 | 生产钉 Extended **8.2.8** |
| Nginx mainline | 生产钉 **1.30.4** |
| 本仓维护客户端壳 / UI | 否；只提供后端 API 与契约 |

---

## 4. 版本钉选与升级策略

| 策略 | 说明 |
| ---- | ---- |
| 小版本 | 文档与 Compose **写死补丁号**；升补丁须同步改文档 + `docker-compose*.yml` |
| 大版本 / LTS 换代 | 单独评审（下一 Java LTS 预计 29 / 2027-09） |
| 文档 | 变更版本时同步改本文件表头日期与表格 |

相关： [architecture.md](architecture.md) · [deployment.md](../ops/deployment.md) · [api-contract.md](../contract/api-contract.md)
