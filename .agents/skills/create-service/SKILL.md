---
name: create-service
description: 在领域模块中创建 Service 层（业务逻辑 + 事务边界）
---

# 创建领域 Service

实现前阅读 `docs/architecture/services.md` 对应模块要点。

## 检查清单

- [ ] Service 含业务逻辑与权限校验，Controller 仅做路由与校验？
- [ ] 写操作有 `@Transactional`？
- [ ] 跨模块不直接调 Mapper（走 Service 或 Kafka）？
- [ ] Redis Key 使用 `bluedock-common` 的 `RedisKeys`？
- [ ] 异步副作用走 Kafka（[messaging.md](../../rules/messaging.md)）？
- [ ] 业务异常用 `I18nKeys` + 双语 properties（[i18n.md](../../rules/i18n.md)），禁止硬编码文案？
- [ ] 权限与数据范围校验先于业务数据读取、修改、导出与敏感字段查看？
- [ ] 写操作按风险具备事务、幂等或统一并发控制；冲突与失败不静默降级？
- [ ] 第三方调用通过明确 Client 边界，并处理配置、超时、重试和异常映射？
- [ ] 新增或修改的类、`public` / `protected` 成员及非显然分支有必要中文注释，且无本次引入的 Warning？

## 文件位置

```
bluedock-<module>/src/main/java/com/bluedock/<module>/
├── service/<Resource>Service.java
├── service/impl/<Resource>ServiceImpl.java
├── mapper/<Resource>Mapper.java
└── entity/<Resource>Entity.java
```

## Service 模板

```java
package com.bluedock.<module>.service.impl;

import com.bluedock.<module>.entity.<Resource>Entity;
import com.bluedock.<module>.mapper.<Resource>Mapper;
import com.bluedock.<module>.service.<Resource>Service;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class <Resource>ServiceImpl implements <Resource>Service {

  private final <Resource>Mapper <resource>Mapper;

  @Override
  public List<<Resource>Entity> listAll() {
    return <resource>Mapper.selectActive();
  }

  @Override
  @Transactional
  public <Resource>Entity create(<Resource>Entity entity) {
    <resource>Mapper.insert(entity);
    return entity;
  }
}
```

## Mapper / Entity

- Mapper 继承 `BaseMapper`
- Entity `@TableName` 用逻辑名；物理前缀 `bluedock_` 由全局配置注入
- 时间字段 `DATETIME(3)` UTC
