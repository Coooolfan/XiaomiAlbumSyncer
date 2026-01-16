# expressionTargetPath 使用指南

`expressionTargetPath` 允许你自定义下载文件的保存路径，并通过 `${...}` 插值填充动态信息。

- 当 `expressionTargetPath` 为空、只包含空白，或**不包含任何支持的插值项**时，会自动回退到旧逻辑：
  - 非音频：`targetPath/album/fileName`
  - 音频：`targetPath/album/{assetId}_{fileName}`（避免录音重名覆盖）
- 当 `expressionTargetPath` 有效时，`targetPath` 不参与任何逻辑（相对路径保持相对，需自行写全路径或接受相对路径行为）。
- 时间格式化使用 `crontab.config.timeZone` 对应的时区（解析失败则使用系统默认时区）。
- 时间格式化遵循 Java `DateTimeFormatter` 的标准格式（如 `yyyy-MM-dd`、`yyyy/MM`）。
- `album` / `fileName` / `title` 会自动做路径安全处理（移除非法字符）。

## 插值项一览

### 基础信息

| 插值 | 说明 |
| --- | --- |
| `${crontabId}` | 任务 ID |
| `${crontabName}` | 任务名称 |
| `${historyId}` | 本次运行历史 ID |
| `${album}` / `${albumName}` | 相册名称（已做路径安全处理） |
| `${fileName}` | 文件名（已做路径安全处理） |
| `${fileStem}` | 去掉扩展名的文件名（基于 `${fileName}`） |
| `${fileExt}` | 文件扩展名（不含点） |
| `${assetId}` | 资源 ID |
| `${assetType}` | 资源类型（小写，如 `image`/`video`/`audio`） |
| `${sha1}` | 资源 SHA1 |
| `${title}` | 资源标题（已做路径安全处理） |
| `${size}` | 资源大小（字节） |
| `${downloadEpochMillis}` | 下载开始时间毫秒时间戳 |
| `${takenEpochMillis}` | 拍摄时间毫秒时间戳 |
| `${downloadEpochSeconds}` | 下载开始时间秒时间戳 |
| `${takenEpochSeconds}` | 拍摄时间秒时间戳 |

> 路径安全处理会将 `\ / : * ? " < > |` 以及换行、制表符等替换为 `_`。

### 时间格式化

通过以下前缀进行时间格式化（使用 Java `DateTimeFormatter` 的格式）：

- `${download_<pattern>}`：基于下载开始时间（`history.startTime`）
- `${taken_<pattern>}`：基于拍摄时间（`asset.dateTaken`）

示例（以 `2024-05-06T12:00:00Z` 为例）：

- `${download_YYYYMM}` → `202405`
- `${download_yyyy-MM-dd}` → `2024-05-06`
- `${taken_yyyy/MM}` → `2024/05`

> 若格式错误，会保留原始 `${...}` 内容不替换。

## 规则说明

- 时间格式化**只对**以下插值生效：`${download_<pattern>}`、`${taken_<pattern>}`。
- `${download}` 不是有效的时间插值：如果模板里只有 `${download}` 之类的**非支持插值**，会被视为无效模板并回退旧逻辑；如果模板同时包含有效插值，则 `${download}` 会原样保留在结果路径中。

## 示例

### 示例 1：按相册与月份归档（绝对路径）

```
/data/${album}/${download_YYYYMM}/${fileName}
```

解析结果（以相册 `旅行`, 文件 `photo.jpg` 为例）：

```
/data/旅行/202405/photo.jpg
```

### 示例 2：写入绝对路径

```
/data/${assetType}/${taken_yyyy/MM}/${fileStem}.${fileExt}
```

### 示例 3：音频文件保持原名

```
${album}/${fileName}
```

音频 `rec.m4a` 会得到（相对路径）：

```
Recordings/rec.m4a
```

如需自行避免重名，可在模板里拼上 `${assetId}` 等字段。

## 兼容逻辑说明

- `expressionTargetPath` 为空、空白或不包含支持的插值项时，仍会使用旧逻辑：
  - 非音频：`targetPath/album/fileName`
  - 音频：`targetPath/album/{assetId}_{fileName}`
- 当 `expressionTargetPath` 有效时，不会自动拼接 `targetPath`。
