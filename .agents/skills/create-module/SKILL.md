---
name: create-module
description: 按项目规范创建 Maven 领域子模块
---

# 创建 Maven 领域子模块

实现前阅读 `docs/architecture/services.md` 确认模块职责与 API 归属。

## 检查清单

- [ ] 模块名为领域短名（`project`、`messenger`，非 `desktop`/`web`）？
- [ ] 父 `pom.xml` 已注册 `<module>bluedock-<name></module>`？
- [ ] 子模块仅依赖 `bluedock-common`（跨域走 Service 或 Kafka）？
- [ ] `bluedock-boot/pom.xml` 已引入新模块？
- [ ] Worker 模块（`bluedock-worker-*`）无 `controller/` 目录？
- [ ] 已按 [doc-sync.md](../../rules/doc-sync.md) 更新 `docs/architecture/services.md`？

## 目录结构

```
bluedock-<name>/
├── pom.xml
└── src/
    ├── main/java/com/task/<name>/
    │   ├── controller/     # REST（Worker 模块无此目录）
    │   ├── service/
    │   ├── mapper/
    │   ├── entity/
    │   └── dto/
    └── test/java/com/task/<name>/
```

Worker：`bluedock-worker-notify` → 包 `com.bluedock.worker.notify`。

## 子模块 pom.xml 模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.bluedock</groupId>
    <artifactId>bluedock-parent</artifactId>
    <version>${revision}</version>
  </parent>

  <artifactId>bluedock-<name></artifactId>
  <name>bluedock-<name></name>

  <dependencies>
    <dependency>
      <groupId>com.bluedock</groupId>
      <artifactId>bluedock-common</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

## 合法模块参考

见 [modules.md](../../rules/modules.md)。

## 完成后

```bash
mvn -pl bluedock-<name> -am compile
mvn -pl bluedock-<name> test
```
