# 个人设置 — 验收清单

## 后端（已落地）

- [x] 改资料 `editData`（含 `lang`）
- [x] 改密码 `editPassword`（RSA + LDAP 回写；system 禁改）
- [x] 改邮箱 / 邮箱验证 `email/edit|send|verification`
- [x] 应用排序 `appSort` / `appSort/save`
- [x] 注销 `delete/account`（warning + confirm）
- [x] 设备 list / logout / edit
- [x] 个性标签 `users/tags/lists|add|update|delete|recognize`
- [x] `api/privacy` HTML（匿名；可替换 `static/privacy.html`）

## 明确不做

- [x] 无全局时段免打扰云端配置
- [x] 无细粒度「资料对谁可见」开关
- [x] 不按 desktop/web 拆后端模块

> 本仓只验收后端 API。

详见 [overview.md](overview.md) · [api.md](api.md)。
