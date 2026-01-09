# GitHub Release 发布

## Release Note 格式

```markdown
## fix
* 修复描述 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/<hash>

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/<prev>...<new>
```

- 分类：`## feat` / `## fix` / `## chore / docs`
- 条目以 `*` 开头，重要内容用 `**加粗**`
- 每条后跟 commit 完整 URL

## 注意事项

- 查看历史格式：进入 `/releases/edit/<version>` 编辑页面看 Markdown 源码
- 版本号不带 v 前缀（如 `0.10.1`）
- 发布后 GitHub Actions 自动构建二进制文件
