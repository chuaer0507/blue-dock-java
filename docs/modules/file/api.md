# 文件 — API

前缀 `api/file`。分片见 [upload](../upload/overview.md)。

| 组 | URL |
| -- | --- |
| 树 / CRUD | `lists` · `one` · `fetch` · `add` · `copy` · `move` · `remove` · `trash` · `restore` · `search` |
| 内容 / 版本 | `content` · `content/save` · `content/upload` · `content/history` · `content/restore` · `content/office` |
| Office | `office/token` |
| 共享 / 链接 | `share` · `share/update` · `share/out` · `link` |
| 打包 | `download/pack` |
| 二进制 | `raw`（鉴权流式，非信封） |

**已落地**：文件树 CRUD · `fetch`/`search` · `trash`/`restore`（软删回收）· `raw` · `content`/`save`/`history`/`restore` · `content/upload`（`id`+`uploadId` 合并分片覆盖）· `office/token` · `content/office`（下载/回调回写，匿名可达）· `share*` · `link` · `download/pack` · `api/upload/*`。

配置：`bluedock.office.*`（`enabled` / `document-server-url` / `jwt-secret` / `public-base-url`；开发默认 `allow-dev-token=true`）。本地可选 Compose profile `onlyoffice`（见 [office-onlyoffice.md](../../infra/office-onlyoffice.md)）。

完整表：[api-contract.md](../../contract/api-contract.md)。
