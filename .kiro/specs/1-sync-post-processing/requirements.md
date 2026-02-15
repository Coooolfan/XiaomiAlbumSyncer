# 需求文档：同步后处理

## 简介

本功能为 Xiaomi Album Syncer 项目的同步功能（SyncService）添加完整的后处理支持。原有版本支持全量下载功能，包含 SHA1 校验、EXIF 时间填充、文件系统时间更新等后处理步骤。新增的同步功能目前只实现了文件下载，缺少这些关键的后处理步骤。本需求旨在为同步功能补充完整的后处理能力，确保同步下载的文件与全量下载的文件具有相同的质量保证。

## 术语表

- **SyncService**: 同步服务，负责云端到本地的增量同步（新增、删除、修改）
- **Asset**: 资产，代表一个相册中的照片、视频或音频文件
- **SHA1**: 安全哈希算法 1，用于验证文件完整性的哈希值
- **EXIF**: 可交换图像文件格式，存储在图像文件中的元数据
- **DateTaken**: 拍摄时间，照片或视频的拍摄日期和时间
- **FileService**: 文件服务，提供文件操作和 SHA1 校验功能
- **VerificationStage**: SHA1 校验阶段处理器
- **ExifProcessingStage**: EXIF 处理阶段处理器
- **FileTimeStage**: 文件系统时间处理阶段处理器
- **CrontabConfig**: 定时任务配置，包含后处理相关的开关选项
- **SyncOperation**: 同步操作类型（ADD/DELETE/UPDATE）

## 需求

### 需求 1：SHA1 校验集成

**用户故事：** 作为系统管理员，我希望同步下载的文件能够进行 SHA1 校验，以确保文件完整性和数据一致性。

#### 验收标准

1. WHEN 同步操作下载新文件（ADD 操作）且配置启用 SHA1 校验 THEN 系统应当对下载的文件执行 SHA1 校验
2. WHEN 同步操作更新文件（UPDATE 操作）且配置启用 SHA1 校验 THEN 系统应当对更新后的文件执行 SHA1 校验
3. WHEN SHA1 校验失败 THEN 系统应当记录错误信息并将该同步详情标记为失败
4. WHEN SHA1 校验成功 THEN 系统应当继续执行后续的后处理步骤
5. WHEN 配置未启用 SHA1 校验 THEN 系统应当跳过 SHA1 校验步骤

### 需求 2：EXIF 时间填充集成

**用户故事：** 作为摄影爱好者，我希望同步下载的照片能够自动填充 EXIF 拍摄时间，以便照片管理软件能够正确显示拍摄时间。

#### 验收标准

1. WHEN 同步操作下载新照片（ADD 操作）且配置启用 EXIF 时间重写 THEN 系统应当根据资产的 dateTaken 填充照片的 EXIF 时间元数据
2. WHEN 同步操作更新照片（UPDATE 操作）且配置启用 EXIF 时间重写 THEN 系统应当根据资产的 dateTaken 填充更新后照片的 EXIF 时间元数据
3. WHEN EXIF 时间填充失败（例如非 JPG 文件）THEN 系统应当记录警告信息但不中断后续处理
4. WHEN EXIF 时间填充成功 THEN 系统应当继续执行后续的后处理步骤
5. WHEN 配置未启用 EXIF 时间重写 THEN 系统应当跳过 EXIF 时间填充步骤
6. WHEN 配置启用 EXIF 时间重写但未指定有效时区 THEN 系统应当跳过 EXIF 时间填充步骤并记录警告

### 需求 3：文件系统时间更新集成

**用户故事：** 作为用户，我希望同步下载的文件的创建和修改时间能够与拍摄时间一致，以便在文件管理器中按时间排序时更加直观。

#### 验收标准

1. WHEN 同步操作下载新文件（ADD 操作）且配置启用文件系统时间重写 THEN 系统应当将文件的创建和修改时间设置为资产的 dateTaken
2. WHEN 同步操作更新文件（UPDATE 操作）且配置启用文件系统时间重写 THEN 系统应当将更新后文件的创建和修改时间设置为资产的 dateTaken
3. WHEN 文件系统时间更新失败 THEN 系统应当记录错误信息但不中断整个同步流程
4. WHEN 文件系统时间更新成功 THEN 系统应当完成该文件的所有后处理步骤
5. WHEN 配置未启用文件系统时间重写 THEN 系统应当跳过文件系统时间更新步骤

### 需求 4：后处理流程编排

**用户故事：** 作为开发者，我希望后处理步骤能够按照正确的顺序执行，并且能够优雅地处理各个步骤的失败情况。

#### 验收标准

1. THE 系统应当按照以下顺序执行后处理步骤：文件下载 → SHA1 校验 → EXIF 时间填充 → 文件系统时间更新
2. WHEN 任何后处理步骤失败 THEN 系统应当在 SyncRecordDetail 中记录失败状态和错误信息
3. WHEN 后处理步骤失败 THEN 系统应当继续处理其他文件而不中断整个同步任务
4. WHEN 所有后处理步骤成功完成 THEN 系统应当在 SyncRecordDetail 中将 isCompleted 标记为 true
5. THE 系统应当复用现有的 VerificationStage、ExifProcessingStage 和 FileTimeStage 实现

### 需求 5：配置选项支持

**用户故事：** 作为系统管理员，我希望能够通过配置选项控制同步功能的后处理行为，以适应不同的使用场景。

#### 验收标准

1. THE 系统应当读取 CrontabConfig 中的 checkSha1 配置项来决定是否执行 SHA1 校验
2. THE 系统应当读取 CrontabConfig 中的 rewriteExifTime 配置项来决定是否执行 EXIF 时间填充
3. THE 系统应当读取 CrontabConfig 中的 rewriteFileSystemTime 配置项来决定是否执行文件系统时间更新
4. THE 系统应当读取 CrontabConfig 中的 rewriteExifTimeZone 配置项作为 EXIF 时间填充的时区参数
5. WHEN 配置项为 false 或未设置 THEN 系统应当跳过对应的后处理步骤

### 需求 6：错误处理和日志记录

**用户故事：** 作为运维人员，我希望系统能够详细记录后处理过程中的错误和警告，以便排查问题。

#### 验收标准

1. WHEN 后处理步骤开始执行 THEN 系统应当记录 INFO 级别的日志
2. WHEN 后处理步骤成功完成 THEN 系统应当记录 INFO 级别的日志
3. WHEN 后处理步骤失败 THEN 系统应当记录 ERROR 级别的日志并包含异常堆栈信息
4. WHEN EXIF 时间填充遇到非 JPG 文件 THEN 系统应当记录 WARN 级别的日志
5. WHEN 后处理步骤失败 THEN 系统应当在 SyncRecordDetail 的 errorMessage 字段中记录错误信息

### 需求 7：DELETE 操作的后处理

**用户故事：** 作为开发者，我希望明确 DELETE 操作不需要执行后处理步骤，以避免不必要的处理开销。

#### 验收标准

1. WHEN 同步操作为 DELETE THEN 系统应当跳过所有后处理步骤（SHA1 校验、EXIF 时间填充、文件系统时间更新）
2. WHEN 同步操作为 DELETE THEN 系统应当仅执行文件删除操作并记录同步详情
3. THE 系统应当确保 DELETE 操作的 SyncRecordDetail 记录中 isCompleted 字段正确反映删除操作的成功或失败状态
