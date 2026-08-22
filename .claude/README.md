# Claude Code 兼容层

内容源在 [`.agents/`](../.agents/README.md)。  
本目录仅保留 Claude Code 专属配置，以及指向 `.agents/` 的 symlink。

| 路径 | 说明 |
|------|------|
| `settings.json` | 权限、hooks、rules 入口（可提交） |
| `settings.local.json` | 本机权限（gitignore，勿提交） |
| `rules` → `../.agents/rules` | 项目规则 |
| `skills` → `../.agents/skills` | Agent Skills |
| `commands` → `../.agents/commands` | Slash commands |

请勿在此目录编辑规则或 skills；改 `.agents/`。
