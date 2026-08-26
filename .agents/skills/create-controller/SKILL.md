---
name: create-controller
description: 在领域模块中创建 REST Controller，对齐 API 契约
---

# 创建 REST Controller

实现前对照 `docs/contract/api-contract.md` 确认路由、请求体、响应形态。

## 检查清单

- [ ] 路径与契约一致？
- [ ] 统一返回 `ResultModel<T>`，禁止裸对象？
- [ ] Controller 无业务逻辑（逻辑在 Service）？
- [ ] `@RequestMapping` 仅一个 prefix（禁止别名）？
- [ ] JSON camelCase？
- [ ] 错误 `message` 走 `I18nKeys`（见 [i18n.md](../../rules/i18n.md)），禁止硬编码？
- [ ] 所有读取、修改、导出与敏感字段查看都经 Service 完成权限及数据范围校验？
- [ ] Controller、端点、请求/响应字段与非显然分支已补齐必要中文注释？
- [ ] 已按 [doc-sync.md](../../rules/doc-sync.md) 更新 `docs/contract/api-contract.md`？

## 文件位置

```
bluedock-<module>/src/main/java/com/bluedock/<module>/controller/<Resource>Controller.java
bluedock-<module>/src/main/java/com/bluedock/<module>/dto/<Resource>Request.java
bluedock-<module>/src/main/java/com/bluedock/<module>/dto/<Resource>Response.java
```

## Controller 模板

```java
package com.bluedock.<module>.controller;

import com.bluedock.common.model.ResultModel;
import com.bluedock.<module>.dto.<Resource>Response;
import com.bluedock.<module>.service.<Resource>Service;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/<prefix>")
@RequiredArgsConstructor
public class <Resource>Controller {

  private final <Resource>Service <resource>Service;

  @GetMapping("/lists")
  public ResultModel<List<<Resource>Response>> lists() {
    return ResultModel.ok(<resource>Service.listAll());
  }

  @GetMapping("/{id}")
  public ResultModel<<Resource>Response> one(@PathVariable String id) {
    return ResultModel.ok(<resource>Service.getById(id));
  }
}
```

## 完成后

1. 确认 `bluedock-boot` 已引入该模块
2. `mvn -pl bluedock-<module> -am compile`
3. 执行并记录该模块全部 REST API 回归
4. 更新 `docs/contract/api-contract.md`
