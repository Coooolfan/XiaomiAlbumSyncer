# 📘 Release Notes 书写规范

> 本规范旨在统一仓库的发布说明（Release Notes）书写风格，
> 以保证版本记录简洁、一致、便于追溯，并充分利用 GitHub 的 Markdown 扩展特性。

---

## 🧭 一、总体原则

1. **简洁、扁平、可追溯**
   - 让读者能快速看懂「本次更新做了什么」以及「对应的提交/PR」。
   - 使用简短描述 + 直接链接，避免长段文字。

2. **GitHub 原生兼容**
   - 仅使用 GitHub Flavored Markdown 支持的语法（包括 警报块 Alert Block）。
   - 不额外引入 HTML、表格或图片。

3. **最少维护成本**
   - 一级标题（ `# vX.Y.Z`）省略，由 GitHub Release 页面自动显示版本号。

---

## 🧱 二、结构模板

```markdown
> [!NOTE]
> 此版本包含重要信息。请根据提示检查是否需要迁移或更新配置。

## feat
* <功能或特性描述> <commit或PR链接> <commit或PR链接>

## fix
* <修复描述> <commit或PR链接>

## chore / docs
* <工程维护或文档描述> <commit或PR链接>

Full Changelog: https://github.com/<org>/<repo>/compare/<prev>...<curr>
```

### 说明：

- 各小节使用二级标题（ `##`）。
- 条目使用 `*`（星号）列表。
- 所有链接直接贴完整 URL，方便 GitHub 自动识别为超链接。
- 句末不加句号。
- 最后一行必须包含 `Full Changelog` 链接，统一格式。

---

## ⚠️ 三、警报块（Alert Block）使用规范

警报块用于强调 **重要、警示或说明性内容**。
它们是 GitHub Markdown 的扩展语法，支持五种类型：

| 类型               | 用途          | 示例语法                               | 显示效果         |
| :--------------- | :---------- | :--------------------------------- | :----------- |
| `> [!NOTE]`      | 普通提示或版本说明   | `> [!NOTE]\n> 此版本为测试版。`            | 蓝色 info      |
| `> [!TIP]`       | 提示技巧或使用建议   | `> [!TIP]\n> 可尝试新的 Native 构建选项。`   | 绿色 tip       |
| `> [!IMPORTANT]` | 达成目标所必需的信息  | `> [!IMPORTANT]\n> 请先更新依赖后再构建。`    | 紫色 important |
| `> [!WARNING]`   | 紧急信息或潜在问题警告 | `> [!WARNING]\n> 此版本配置不兼容旧版缓存。`    | 橙色 warning   |
| `> [!CAUTION]`   | 行动风险或副作用提醒  | `> [!CAUTION]\n> 强制清理缓存将导致历史记录丢失。` | 红色 caution   |

> **使用原则：**

```
- 如无必要，无需使用警报块。
- 每个版本说明最多出现一到两个警报块。 
- 禁止连续堆叠多个警报（易造成阅读负担）。 
- 警报块必须置于文件最上方、正文之前。 
- 不得嵌套在列表或代码块中。 
```

### ✅ 示例 1：普通提示版本

```markdown
> [!NOTE]
> 此版本包含数据库表结构变更，程序会自动执行迁移，您无需手动操作。
```

### ✅ 示例 2：含 breaking 变更

```markdown
> [!WARNING]
> 此版本包含不兼容变更：移除了 Maven 构建体系，全面转向 Gradle。
> 详情参考 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/397e8ecb4603d258de4b6ae906822cd224b02d02
```

---

## 🧩 四、内容分组规则

| 分组                | 内容范围         | 示例                      |
| :---------------- | :----------- | :---------------------- |
| `## feat`         | 新增功能、改进、性能优化 | 添加相册日期映射接口；支持原生镜像构建     |
| `## fix`          | Bug 修复、稳定性修正 | 修复相册时间线偏移问题             |
| `## chore / docs` | 构建、依赖、文档、脚本  | 更新 README；移除 Maven 构建系统 |
| （可选） `## perf`    | 性能优化（如需独立说明） | 优化同步性能提升 40%            |

> 不需要的分组可省略，保持简洁。

---

## ✍️ 五、条目书写规范

| 要点   | 说明                       | 示例                          |
| :--- | :----------------------- | :-------------------------- |
| 语气   | 使用“动词 + 对象 + 结果”         | 支持原生镜像构建以提升启动速度             |
| 句号   | 不加句号                     | ✅ 正确：不加句号                   |
| 链接   | 紧贴描述，以空格分隔               | `<描述> <url>`                |
| 多链接  | 最少 1 个，空格分隔            | `<描述> <url1> <url2>`      |
| 重复前缀 | 不写 `feat:` 或 `chore:` 前缀 | ✅ “更新依赖版本” ❌ “feat: 更新依赖版本” |

---

## 🧮 六、辅助说明

若提供完整 commit 列表，需要执行以下步骤：

1. 识别类别关键词（ `feat` / `fix` / `chore` / `docs` / `breaking` 等）；
2. 自动分组排序（优先级： `breaking` > `feat` > `fix` > `chore/docs`）；
3. 去重、过滤无意义 commit（如“update version”、“merge branch”等）；
4. 输出符合规范的 Markdown 文本。

---

## ✅ 七、完整示例

```markdown

## feat
* 支持原生镜像构建，更新数据库迁移与资源注册逻辑以适配原生环境 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/d3798d2a0403c90e463d6f0b2cca10921449b885
* 设置页合并外观与热力图配置为单卡片，背景与优化项改用 SelectButton 并本地持久化 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/74ec5a63af6958a4d57c9a5d33e26288f438980b https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/8a4d8980bf695e26257c93a5d3374ec5a6388f4a

## chore / docs
* 更新 README https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/bf79d84eb8750f73a30c0597a1746d80815f66c9
* 更新 native-image 资源配置与反射注册 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/fdaf96bb0f83b705bf35a6189f90104b09f5f0a6 https://github.com/Coooolfan/XiaomiAlbumSyncer/commit/b0f9f90f358104b09f5f0aaf9661afdb683b705b

Full Changelog: https://github.com/Coooolfan/XiaomiAlbumSyncer/compare/0.4.0...0.5.0
```

---

## 🪄 八、总结

| 项 | 规范 |
| :-- | :-- |
| 一级标题 | ❌ 不需要，GitHub 自动生成 |
| 分组标题 | `## feat`、 `## fix`、 `## chore / docs` |
| 列表符号 | 使用 `*`，每条一句话 |
| 链接格式 | 直接贴完整 URL |
| 警报块 | 用于重要说明，首行前置 |
| Full Changelog | 结尾必须有，格式固定 |
| 最小输入 | commit 列表 + 版本号（上/下） |
| 关键词自动分组 | breaking > feat > fix > chore |
