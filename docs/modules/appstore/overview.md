# 应用市场

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **应用市场是什么**

### 能力（怎么做）

- 安装一个插件
- 卸载一个插件
- 更新一个插件

## 核心概念

### 应用市场是什么

## 定义
应用市场（AppStore）是 BlueDock 的插件管理后台，让系统管理员一键安装 / 卸载 / 更新各种功能插件，例如 AI 助手、审批、签到、OnlyOffice 等。其本体是一个名为 `appstore` 的微应用，注册在 `application/admin` 位置（见 `store/mutations.js` 第 396 行）。

## 关键属性
- **微应用形态**：管理端通过应用中心进入；后端以注册表 `bluedock_installed_apps` 判定是否安装
- **后端校验**：`InstalledAppService.isInstalled(appId)`；`appstore` 恒为已安装
- **未安装**：角标等接口抛「应用未安装」
- **目录**：`GET /api/system/apps/catalog` 返回内置官方插件清单（无外部商店）
- **装/更/卸**：`install` / `update` / `uninstall` 写注册表；成功后联动 `microAppMenu`
- **生命周期 Hook**：可选 HTTP 回调（`bluedock.apps.lifecycle-hook-url`）；Docker 编排仍由侧车实现，本仓不拉镜像
- **不做**：本进程 Docker / Shell 执行 / `user_onboard` / 外部商店 remote catalog

## 插件类型
- 官方内置目录：ai、approve、attendance、face、office、drawio、minder、okr、search、fileview
- 社区插件：仍可用自定义 `id` 手工 install（无 catalog 补全）

## 与「微应用菜单」的区别
- **应用市场**：管理插件「装/卸/更新」注册表
- **微应用菜单**：插件装好后注册到「应用」页的菜单项；install/update/uninstall 会自动合并 / 移除

## 不支持
- 不支持卸载 `appstore` 自身
- 不支持普通成员浏览未装插件列表（catalog 需管理员）
- 不支持在本进程内安装任意 Docker 镜像（无编排；可用 Hook 通知侧车）

## 相关文档

- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `appstore`
- API：[api.md](api.md)
- 微应用：[micro-app/api.md](../micro-app/api.md)
