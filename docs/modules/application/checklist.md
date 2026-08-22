# 应用中心 — 验收清单

后端提供菜单与排序数据（客户端导航不在本仓验收）。

- [x] 三类入口概念：系统应用 / 管理员应用 / 微应用 — [overview.md](overview.md)
- [x] 管理员自定义全员菜单：`POST /api/system/microAppMenu`
- [x] 个人排序：`GET|POST /api/users/appSort`
- [x] 与 AppStore 安装联动：install/update/uninstall 合并 microAppMenu — [appstore/api.md](../appstore/api.md)

详见 [overview.md](overview.md) · [micro-app/api.md](../micro-app/api.md)。
