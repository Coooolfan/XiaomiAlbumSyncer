---
name: github-release
description: "Create GitHub releases with properly formatted release notes. Use when: (1) Publishing a new version/release, (2) Generating release notes from commits, (3) User mentions release, 发版, 发布版本, or asks to create a GitHub release."
---

# GitHub Release

通过浏览器完成 GitHub Release 发布，自动生成符合规范的 Release Notes。

## 工作流程

1. **收集信息**
   - 获取当前版本号和上一版本号（版本号不带 `v` 前缀，如 `0.10.1`）
   - 获取两个版本间的 commit 列表：`git log <prev>..HEAD --oneline`

2. **生成 Release Notes**
   - 按规范格式生成（详见 [release-notes-spec.md](references/release-notes-spec.md)）
   - 分类优先级：`breaking` > `feat` > `fix` > `chore/docs`
   - 过滤无意义 commit（如 "update version"、"merge branch"）

3. **发布**
   - 打开浏览器访问：`https://github.com/Coooolfan/XiaomiAlbumSyncer/releases/new`
   - 填写 Tag（版本号）、Title、Release Notes
   - 必要时请求用户介入确认

4. **截图存档**
   - 发布成功后，获取页面快照（`take_snapshot`）
   - 找到 `main` 元素的 uid，对其截图（`take_screenshot` 的 `uid` 参数）
   - 保存路径：`~/Downloads/release-<version>-<YYYYMMDD>.png`

## Release Notes 格式速查

```markdown
> [!NOTE]
> 可选的警报块（测试版/breaking change 说明）

## feat
* 功能描述 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/<hash>

## fix
* 修复描述 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/<hash>

## chore / docs
* 变更描述 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/<hash>

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/<prev>...<new>
```

**格式要点**：
- 条目以 `*` 开头，重要内容用 `**加粗**`
- 句末不加句号
- 每条后跟 commit 完整 URL
- 结尾必须有 Full Changelog 链接

## 参考资料

- [Release Notes 完整规范](references/release-notes-spec.md) - 详细格式说明、警报块用法、完整示例