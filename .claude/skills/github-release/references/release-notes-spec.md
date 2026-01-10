# Release Notes 书写规范

## 总体原则

- **简洁、扁平、可追溯** - 让读者快速看懂更新内容及对应提交
- **GitHub 原生兼容** - 仅使用 GitHub Flavored Markdown
- **一级标题省略** - 由 GitHub Release 页面自动显示版本号

## 结构模板

```markdown
> [!NOTE]
> 可选警报块

## feat
* 功能描述 <commit-url>

## fix
* 修复描述 <commit-url>

## chore / docs
* 变更描述 <commit-url>

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/<prev>...<new>
```

## 内容分组

| 分组 | 内容范围 | 示例 |
|------|----------|------|
| `## feat` | 新增功能、改进、性能优化 | 添加相册日期映射接口 |
| `## fix` | Bug 修复、稳定性修正 | 修复相册时间线偏移问题 |
| `## chore / docs` | 构建、依赖、文档、脚本 | 更新 README |
| `## perf`（可选） | 性能优化（如需独立说明） | 优化同步性能提升 40% |

**分组优先级**：`breaking` > `feat` > `fix` > `chore/docs`

不需要的分组可省略。

## 条目书写规范

| 要点 | 说明 | 示例 |
|------|------|------|
| 语气 | "动词 + 对象 + 结果" | 支持原生镜像构建以提升启动速度 |
| 句号 | 不加句号 | ✅ 正确 |
| 链接 | 紧贴描述，空格分隔 | `<描述> <url>` |
| 重复前缀 | 不写 `feat:` 或 `fix:` | ✅ "更新依赖版本" |

## 警报块（Alert Block）

用于强调重要、警示或说明性内容，置于文件最上方。

| 类型 | 用途 | 显示效果 |
|------|------|----------|
| `> [!NOTE]` | 普通提示或版本说明 | 蓝色 |
| `> [!TIP]` | 提示技巧或使用建议 | 绿色 |
| `> [!IMPORTANT]` | 达成目标所必需的信息 | 紫色 |
| `> [!WARNING]` | 紧急信息或潜在问题警告 | 橙色 |
| `> [!CAUTION]` | 行动风险或副作用提醒 | 红色 |

**使用原则**：
- 每个版本最多 1-2 个警报块
- 禁止连续堆叠
- 必须置于正文之前
- 不得嵌套在列表或代码块中

## 完整示例

### 普通版本

```markdown
## feat
* 支持多账号同步 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/abc1234
* 添加相册筛选功能 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/def5678

## fix
* 修复登录状态丢失问题 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/ghi9012

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/0.10.0...0.10.1
```

### 含 Breaking Change

```markdown
> [!WARNING]
> 此版本配置格式有变更，请参考文档进行迁移

## feat
* **重构配置系统**，支持 YAML 格式 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/abc1234

## fix
* 修复内存泄漏问题 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/def5678

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/0.9.0...0.10.0
```

### 测试版本

```markdown
> [!NOTE]
> 此版本为测试版，可能存在不稳定因素

## feat
* 实验性支持 WebDAV 同步 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/abc1234

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/0.10.0...0.10.1-beta.1
```

## 过滤规则

生成时自动过滤以下无意义 commit：
- `update version`
- `merge branch`
- `Merge pull request`
- 纯版本号更新
