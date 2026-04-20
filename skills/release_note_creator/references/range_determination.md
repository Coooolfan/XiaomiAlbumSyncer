# 确定变更范围

## 目标

变更范围的判定原则是：让关注当前版本层级的用户，不会因为跳过更低层级的发布而漏看重要变更。

因此，当前 tag 的发布说明应当与“同级或者更高级”的历史 tag 相比较。

## tag 级别

自上而下，从高到低排序：

- `*.*.*`：正式版本（Release）
  - 例如：`1.0.0`、`1.1.0`、`2.0.0`
- `*.*.*-rc-*`：预发布版本（Release Candidate）
  - 例如：`1.0.0-rc-1`、`1.1.0-rc-2`
- `*.*.*-beta-*`：公测版本（Beta）
  - 例如：`1.0.0-beta-1`、`1.1.0-beta-2`
- `*.*.*-alpha-*`：内测版本（Alpha）
  - 例如：`1.0.0-alpha-1`、`1.1.0-alpha-2`

## 判定规则

1. 如果用户未说明，假设当前目标版本为正式版本（Release）。
2. 分页调用 `./skills/release_note_creator/scripts/git-tags.sh --pagesize <N> --offset <M>` 获取历史 tag 列表。
3. 从当前页中排除“当前正在生成发布说明的 tag 自身”。
4. 在当前页剩余 tag 中，按输出顺序查找第一个“级别大于等于当前目标 tag 级别”的历史 tag。
5. 如果当前页未找到，则继续翻页，直到找到符合条件的 tag。
6. 该 tag 即为前序 tag；发布说明的变更范围为“当前目标 tag 相对于该前序 tag 的全部变化”。

## 关键约束

- 前序 tag 必须是历史 tag，不能等于当前目标 tag 自身。
- 这里的“最新”指的是分页结果中最先遇到的符合条件的历史 tag；标准版本 tag 按版本优先级倒序排列。
- 不要为了找前序 tag 一次性展开全部 tag，按页查找并在命中后停止即可。
- “同级或者更高级”是为了保证更低层级发布中已经出现但当前受众可能没看过的内容，能够在当前发布说明中被补齐。

## 示例

### 场景 1：生成正式版本（Release）发布说明

```
用户：生成 1.1.0 的发布说明
流程：
1. 选定为正式版本（Release）
2. 分页调用 `./skills/release_note_creator/scripts/git-tags.sh` 获取历史 tag 列表
3. 存在以下 tag：1.0.0, 1.0.1-rc-1, 1.1.0-alpha-1
4. 满足“级别大于等于 Release”的历史 tag 只有 1.0.0
5. 1.0.0 为前序 tag，以该 tag 为基准，确定变更范围
```

### 场景 2：生成预发布版本（Release Candidate）发布说明

```
用户：生成 1.1.0-rc-1 的发布说明
流程：
1. 选定为预发布版本（Release Candidate）
2. 分页调用 `./skills/release_note_creator/scripts/git-tags.sh` 获取历史 tag 列表
3. 存在以下 tag：1.0.0, 1.0.1-rc-1, 1.1.0-alpha-1
4. 满足“级别大于等于 RC”的最新历史 tag 为 1.0.1-rc-1
5. 1.0.1-rc-1 为前序 tag，以该 tag 为基准，确定变更范围
```

### 场景 3：生成公测版本（Beta）发布说明

```
用户：生成 1.0.1-beta-2 的发布说明
流程：
1. 选定为公测版本（Beta）
2. 分页调用 `./skills/release_note_creator/scripts/git-tags.sh` 获取历史 tag 列表
3. 存在以下 tag：1.0.0, 1.0.1-rc-1, 1.0.1-beta-1, 1.1.0-alpha-1
4. 满足“级别大于等于 Beta”的最新历史 tag 为 1.0.1-beta-1
5. 1.0.1-beta-1 为前序 tag，以该 tag 为基准，确定变更范围
```

### 场景 4：生成内测版本（Alpha）发布说明

```
用户：生成 1.1.0-alpha-2 的发布说明
流程：
1. 选定为内测版本（Alpha）
2. 分页调用 `./skills/release_note_creator/scripts/git-tags.sh` 获取历史 tag 列表
3. 存在以下 tag：1.0.0, 1.0.1-rc-1, 1.0.1-beta-2, 1.1.0-alpha-1
4. 满足“级别大于等于 Alpha”的最新历史 tag 为 1.1.0-alpha-1
5. 1.1.0-alpha-1 为前序 tag，以该 tag 为基准，确定变更范围
```
