# 文件 — 验收清单

- [x] 个人树：新建文件夹 / 上传文件 / 上传文件夹（文件夹批量上传依赖客户端多次调用）
- [x] ≥10MB 分片；断点续传（同浏览器）；秒传
- [x] 重命名、移动、复制、删除（软删子树）；`trash` / `restore` 回收站恢复
- [x] 共享（读写权限）；退出共享
- [x] 公开链接（登录默认；`allowGuest=1` 允许游客解析）
- [x] 内容编辑与历史版本恢复（含 `content/upload`、`content/office`）
- [x] Office token 预览/编辑（`office/token`；Compose profile `onlyoffice` 可选起 Document Server）
- [x] 搜索文件名
- [x] 单目录子项上限拒绝继续上传
- [x] 文件夹/多文件打包下载（`download/pack`）

详见 [overview.md](overview.md) · [api.md](api.md) · [../upload/overview.md](../upload/overview.md)。
