# 需求文档：同步功能增强

## 简介

本需求文档定义了 Xiaomi Album Syncer 项目中同步功能的增强特性。在 spec 1 中，我们为同步功能添加了后处理支持（SHA1 校验、EXIF 填充、文件系统时间更新）。现在需要添加更多原有全量下载功能支持的特性，使同步功能与全量下载功能保持一致。

同步功能允许用户将云端相册的变化（新增、修改、删除）同步到本地文件夹。本次增强将添加文件类型过滤、跳过已存在文件、自定义文件路径表达式、并发处理优化和时间线差异对比优化等特性。

## 术语表

- **System**: 指 Xiaomi Album Syncer 系统
- **Sync_Service**: 同步服务，负责检测云端变化并同步到本地
- **Asset**: 资产，指云端的图片、视频或音频文件
- **Crontab**: 定时任务配置
- **CrontabConfig**: 定时任务配置对象，包含各种配置项
- **SyncFolder**: 同步文件夹，存储同步下来的文件
- **Timeline**: 时间线，记录相册中每天的资产数量
- **Flow**: Kotlin 协程的异步流，用于并发处理
- **Expression_Target_Path**: 自定义文件路径表达式，支持变量插值

## 需求

### 需求 1：文件类型过滤

**用户故事：** 作为用户，我希望能够选择性地同步图片、视频或音频文件，以便节省存储空间和带宽。

#### 验收标准

1. WHEN 检测同步变化时，THE System SHALL 根据 downloadImages 配置过滤图片类型资产
2. WHEN 检测同步变化时，THE System SHALL 根据 downloadVideos 配置过滤视频类型资产
3. WHEN 检测同步变化时，THE System SHALL 根据 downloadAudios 配置过滤音频类型资产
4. WHEN downloadImages 为 false 时，THE System SHALL 排除所有 AssetType.IMAGE 类型的资产
5. WHEN downloadVideos 为 false 时，THE System SHALL 排除所有 AssetType.VIDEO 类型的资产
6. WHEN downloadAudios 为 false 时，THE System SHALL 排除所有 AssetType.AUDIO 类型的资产
7. WHEN 过滤应用于 ADD 操作时，THE System SHALL 只同步符合类型过滤条件的新增资产
8. WHEN 过滤应用于 UPDATE 操作时，THE System SHALL 只同步符合类型过滤条件的修改资产
9. WHEN 过滤应用于 DELETE 操作时，THE System SHALL 只删除符合类型过滤条件的资产

### 需求 2：跳过已存在文件

**用户故事：** 作为用户，我希望系统能够跳过已存在且大小匹配的文件，以便避免重复下载和节省时间。

#### 验收标准

1. WHEN skipExistingFile 配置为 true 时，THE System SHALL 在 ADD 操作前检查文件是否已存在
2. WHEN skipExistingFile 配置为 true 时，THE System SHALL 在 UPDATE 操作前检查文件是否已存在
3. WHEN 文件已存在且大小与云端资产大小匹配时，THE System SHALL 跳过下载操作
4. WHEN 文件已存在但大小不匹配时，THE System SHALL 执行下载操作覆盖文件
5. WHEN 文件不存在时，THE System SHALL 执行下载操作
6. WHEN skipExistingFile 配置为 false 时，THE System SHALL 始终执行下载操作
7. WHEN 跳过下载时，THE System SHALL 仍然记录同步详情并标记为已完成

### 需求 3：自定义文件路径表达式

**用户故事：** 作为用户，我希望能够使用变量自定义文件存储路径，以便按照我的需求组织文件结构。

#### 验收标准

1. WHEN expressionTargetPath 配置不为空时，THE System SHALL 使用表达式生成文件路径
2. WHEN expressionTargetPath 配置为空时，THE System SHALL 使用默认路径（syncFolder/album/fileName）
3. THE System SHALL 支持以下变量：${crontabId}, ${crontabName}, ${historyId}, ${album}, ${albumName}, ${fileName}, ${fileStem}, ${fileExt}, ${assetId}, ${assetType}, ${sha1}, ${title}, ${size}
4. THE System SHALL 支持时间格式化变量：${download_YYYYMM}, ${download_YYYY-MM-DD}, ${taken_YYYYMM}, ${taken_YYYY-MM-DD} 等
5. WHEN 表达式中包含非法字符时，THE System SHALL 使用 sanitizeSegment 方法清理路径片段
6. WHEN 表达式解析失败或结果为空时，THE System SHALL 回退到默认路径
7. WHEN 表达式中包含不支持的变量时，THE System SHALL 保留原始 ${} 标记
8. THE System SHALL 使用 Path.normalize() 规范化生成的路径

### 需求 4：并发处理优化

**用户故事：** 作为用户，我希望系统能够并发处理多个文件，以便提高同步性能和缩短同步时间。

#### 验收标准

1. THE System SHALL 使用 downloaders 配置项控制下载并发数
2. THE System SHALL 使用 Kotlin Flow 的 flatMapMerge 实现并发下载
3. WHEN 处理 ADD 操作时，THE System SHALL 并发下载多个资产
4. WHEN 处理 UPDATE 操作时，THE System SHALL 并发下载多个资产
5. WHEN 单个文件下载失败时，THE System SHALL 隔离错误不影响其他文件的下载
6. WHEN 单个文件下载失败时，THE System SHALL 记录错误信息到同步详情
7. THE System SHALL 确保并发数不超过 downloaders 配置值
8. THE System SHALL 在所有并发任务完成后更新同步记录

### 需求 5：时间线差异对比优化

**用户故事：** 作为用户，我希望系统能够基于时间线进行增量检测，以便减少 API 调用次数和提高检测效率。

#### 验收标准

1. WHEN diffByTimeline 配置为 true 时，THE System SHALL 使用时间线差异对比方法检测变化
2. WHEN diffByTimeline 配置为 false 时，THE System SHALL 使用全量对比方法检测变化
3. WHEN 使用时间线差异对比时，THE System SHALL 获取相册的最新时间线快照
4. WHEN 使用时间线差异对比时，THE System SHALL 对比历史时间线快照找出有变化的日期
5. WHEN 使用时间线差异对比时，THE System SHALL 只刷新有变化日期的资产数据
6. THE System SHALL 在同步完成后保存最新的时间线快照到 CrontabHistory
7. WHEN 没有历史时间线快照时，THE System SHALL 使用空时间线作为基准
8. THE System SHALL 使用 AssetService.refreshAssetsByDiffTimeline 方法实现时间线差异刷新

### 需求 6：配置项集成

**用户故事：** 作为用户，我希望所有新增的配置项都能正确集成到系统中，以便通过配置文件或 API 进行配置。

#### 验收标准

1. THE System SHALL 在 CrontabConfig 中保留现有的 downloadImages, downloadVideos, downloadAudios 配置项
2. THE System SHALL 在 CrontabConfig 中保留现有的 skipExistingFile 配置项
3. THE System SHALL 在 CrontabConfig 中保留现有的 expressionTargetPath 配置项
4. THE System SHALL 在 CrontabConfig 中保留现有的 downloaders 配置项
5. THE System SHALL 在 CrontabConfig 中保留现有的 diffByTimeline 配置项
6. THE System SHALL 确保所有配置项都有合理的默认值
7. THE System SHALL 在同步服务中正确读取和应用这些配置项

### 需求 7：错误处理和日志

**用户故事：** 作为开发者，我希望系统能够提供详细的错误处理和日志记录，以便排查问题和监控系统运行状态。

#### 验收标准

1. WHEN 文件类型过滤时，THE System SHALL 记录被过滤的资产数量
2. WHEN 跳过已存在文件时，THE System SHALL 记录跳过的文件信息
3. WHEN 路径表达式解析失败时，THE System SHALL 记录警告日志并回退到默认路径
4. WHEN 并发下载失败时，THE System SHALL 记录详细的错误信息和堆栈跟踪
5. WHEN 时间线差异对比时，THE System SHALL 记录对比结果和需要刷新的日期
6. THE System SHALL 在同步开始和结束时记录关键信息（模式、文件数量、耗时等）
7. THE System SHALL 为每个同步操作记录详细的同步详情（操作类型、文件路径、是否完成、错误信息）
