# 设计文档：同步后处理

## 概述

本设计为 Xiaomi Album Syncer 的同步功能（SyncService）添加完整的后处理支持，包括 SHA1 校验、EXIF 时间填充和文件系统时间更新。设计目标是复用现有的后处理 Stage 实现（VerificationStage、ExifProcessingStage、FileTimeStage），并将其集成到同步流程中，确保同步下载的文件与全量下载的文件具有相同的质量保证。

### 设计原则

1. **代码复用**：最大化复用现有的 Stage 实现，避免重复代码
2. **配置驱动**：通过 CrontabConfig 配置项控制后处理行为
3. **错误隔离**：单个文件的后处理失败不应影响其他文件的处理
4. **渐进式处理**：按照固定顺序执行后处理步骤（下载 → SHA1 → EXIF → 文件时间）
5. **详细记录**：在 SyncRecordDetail 中记录每个步骤的成功或失败状态

## 架构

### 当前架构分析

项目中存在两种下载模式：

1. **全量下载模式（CrontabPipeline）**：
   - 使用 Kotlin Flow 实现的并发流水线
   - 通过 `flatMapMerge` 实现多阶段并发处理
   - 每个 Stage 独立处理，失败时通过 `catch` 捕获并跳过后续处理
   - 使用 CrontabHistoryDetail 记录处理状态

2. **同步模式（SyncService）**：
   - 使用传统的顺序处理方式
   - 通过 `forEach` 遍历变化的资产列表
   - 使用 SyncRecordDetail 记录同步详情

### 集成方案

由于同步模式采用顺序处理，我们将创建一个后处理协调器（PostProcessingCoordinator），负责：

1. 接收下载完成的文件信息
2. 按顺序调用各个 Stage 进行处理
3. 处理每个 Stage 的异常，确保错误隔离
4. 返回处理结果（成功/失败及错误信息）

```
SyncService.executeSync()
    ↓
下载文件 (downloadToSync)
    ↓
PostProcessingCoordinator.process()
    ↓
    ├─→ VerificationStage (SHA1 校验)
    ├─→ ExifProcessingStage (EXIF 时间填充)
    └─→ FileTimeStage (文件系统时间更新)
    ↓
记录结果 (recordSyncDetail)
```

## 组件和接口

### 1. PostProcessingCoordinator（新增）

后处理协调器，负责编排后处理步骤的执行。

```kotlin
@Managed
class PostProcessingCoordinator(
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val systemConfigService: SystemConfigService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 执行后处理流程
     * @param asset 资产对象
     * @param filePath 文件路径
     * @param config 定时任务配置
     * @return 后处理结果
     */
    fun process(asset: Asset, filePath: Path, config: CrontabConfig): PostProcessingResult {
        // 实现后处理流程编排
    }
}

/**
 * 后处理结果
 */
data class PostProcessingResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val sha1Verified: Boolean = false,
    val exifFilled: Boolean = false,
    val fsTimeUpdated: Boolean = false
)
```

### 2. PostProcessingContext（新增）

为了适配现有的 Stage 接口（它们期望接收 CrontabHistoryDetail），我们需要创建一个临时的上下文对象。

```kotlin
/**
 * 后处理上下文
 * 用于在同步流程中适配现有的 Stage 接口
 */
data class PostProcessingContext(
    val asset: Asset,
    val filePath: String,
    val config: CrontabConfig,
    var sha1Verified: Boolean = false,
    var exifFilled: Boolean = false,
    var fsTimeUpdated: Boolean = false
)
```

### 3. SyncService 修改

在 `executeSync` 方法中，为 ADD 和 UPDATE 操作添加后处理调用。

```kotlin
// 在 downloadToSync 之后添加后处理
changes.addedAssets.forEach { asset ->
    try {
        val album = crontab.albums.find { it.id == asset.album.id }
            ?: throw IllegalStateException("找不到资产对应的相册: ${asset.album.id}")
        
        val filePath = Path(syncFolder.toString(), album.name, asset.fileName)
        
        // 下载文件
        downloadToSync(asset, album, syncFolder, crontab.accountId)
        
        // 执行后处理
        val postProcessingResult = postProcessingCoordinator.process(
            asset, 
            filePath, 
            crontab.config
        )
        
        // 记录同步详情
        recordSyncDetail(
            syncRecord.id, 
            asset, 
            album, 
            SyncOperation.ADD, 
            syncFolder, 
            postProcessingResult.success, 
            postProcessingResult.errorMessage
        )
    } catch (e: Exception) {
        log.error("处理新增资产失败: ${asset.fileName}", e)
        val album = crontab.albums.find { it.id == asset.album.id }
        if (album != null) {
            recordSyncDetail(
                syncRecord.id, 
                asset, 
                album, 
                SyncOperation.ADD, 
                syncFolder, 
                false, 
                e.message
            )
        }
    }
}
```

### 4. Stage 适配

现有的 Stage 实现期望接收 `CrontabHistoryDetail` 对象，但同步流程中没有这个对象。我们有两种适配方案：

**方案 A：创建临时的 CrontabHistoryDetail 对象**
- 优点：无需修改现有 Stage 代码
- 缺点：需要创建不必要的数据库记录或使用 mock 对象

**方案 B：提取 Stage 的核心逻辑到独立方法**
- 优点：更清晰的职责分离，避免不必要的对象创建
- 缺点：需要修改现有 Stage 代码

**推荐方案 B**，为每个 Stage 添加一个不依赖 CrontabHistoryDetail 的处理方法：

```kotlin
// VerificationStage 添加
fun verifySha1(asset: Asset, filePath: Path): Boolean {
    log.info("开始校验资源 {} 的 SHA1", asset.id)
    val sha1 = computeSha1(filePath)
    if (!sha1.equals(asset.sha1, ignoreCase = true)) {
        throw RuntimeException("资源 ${asset.id} 的 SHA1 校验失败，期望 ${asset.sha1} 实际 $sha1")
    }
    log.info("资源 {} 的 SHA1 校验成功", asset.id)
    return true
}

// ExifProcessingStage 添加
fun fillExifTime(asset: Asset, filePath: Path, systemConfig: SystemConfig, timeZone: String?) {
    if (timeZone == null) {
        log.warn("未指定有效的时区，填充 EXIF 时间操作将被跳过")
        return
    }
    
    val config = ExifRewriteConfig(
        Path(systemConfig.exifToolPath),
        timeZone.toTimeZone()
    )
    
    log.info("开始处理资源 {} 的 EXIF 时间", asset.id)
    try {
        rewriteExifTime(asset, filePath, config)
    } catch (e: RuntimeException) {
        if (e.message?.contains("Not a valid JPG") ?: false) {
            log.warn("资源 {} 的 EXIF 处理失败, 将跳过后续处理", asset.id, e)
        } else {
            throw e
        }
    }
    log.info("资源 {} 的 EXIF 时间处理完成", asset.id)
}

// FileTimeStage 添加
fun updateFileSystemTime(asset: Asset, filePath: Path) {
    log.info("开始处理资源 {} 的文件系统时间", asset.id)
    rewriteFSTime(filePath, asset.dateTaken)
    log.info("资源 {} 的文件系统时间处理完成", asset.id)
}
```

## 数据模型

### SyncRecordDetail 扩展（可选）

当前的 SyncRecordDetail 只记录操作是否完成和错误信息。为了更详细地追踪后处理状态，可以考虑添加字段：

```kotlin
interface SyncRecordDetail {
    // ... 现有字段 ...
    
    /**
     * SHA1 是否已校验
     */
    val sha1Verified: Boolean?
    
    /**
     * EXIF 是否已填充
     */
    val exifFilled: Boolean?
    
    /**
     * 文件系统时间是否已更新
     */
    val fsTimeUpdated: Boolean?
}
```

**注意**：这是可选的增强功能。如果不添加这些字段，可以通过 `isCompleted` 和 `errorMessage` 来判断整体处理状态。

### 数据库迁移

如果选择扩展 SyncRecordDetail，需要创建数据库迁移脚本：

```sql
-- V0.14.0__sync_post_processing.sql
ALTER TABLE sync_record_detail 
ADD COLUMN sha1_verified BOOLEAN DEFAULT NULL,
ADD COLUMN exif_filled BOOLEAN DEFAULT NULL,
ADD COLUMN fs_time_updated BOOLEAN DEFAULT NULL;
```

## 正确性属性

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的正式陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*


### 属性 1：配置驱动的后处理执行

*对于任何*同步操作（ADD 或 UPDATE）和任何配置组合，当配置启用某个后处理步骤时，该步骤应当被执行；当配置禁用时，该步骤应当被跳过。

**验证：需求 1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 3.1, 3.2, 3.5, 5.1, 5.2, 5.3**

### 属性 2：SHA1 校验正确性

*对于任何*下载的文件，当执行 SHA1 校验时，计算出的 SHA1 值应当与资产对象中的 sha1 字段匹配。

**验证：需求 1.1, 1.2, 1.3**

### 属性 3：SHA1 校验失败处理

*对于任何*SHA1 校验失败的文件，系统应当在 SyncRecordDetail 中记录失败状态（isCompleted = false）和错误信息，并且不继续执行后续的后处理步骤。

**验证：需求 1.3**

### 属性 4：EXIF 时间填充正确性

*对于任何*支持 EXIF 的图片文件，当执行 EXIF 时间填充时，写入的 EXIF 时间应当与资产的 dateTaken 字段一致（考虑时区转换）。

**验证：需求 2.1, 2.2**

### 属性 5：EXIF 时间填充错误容忍

*对于任何*不支持 EXIF 的文件（如非 JPG 文件），当尝试填充 EXIF 时间时，系统应当记录警告日志但不中断后续处理步骤。

**验证：需求 2.3**

### 属性 6：文件系统时间更新正确性

*对于任何*文件，当执行文件系统时间更新时，文件的最后修改时间应当被设置为资产的 dateTaken 值。

**验证：需求 3.1, 3.2**

### 属性 7：后处理步骤执行顺序

*对于任何*需要后处理的文件，后处理步骤应当严格按照以下顺序执行：SHA1 校验 → EXIF 时间填充 → 文件系统时间更新。

**验证：需求 4.1**

### 属性 8：后处理失败隔离

*对于任何*包含多个文件的同步任务，当某个文件的后处理失败时，其他文件的处理应当继续进行，不受影响。

**验证：需求 4.3**

### 属性 9：后处理成功标记

*对于任何*文件，当所有启用的后处理步骤都成功完成时，SyncRecordDetail 的 isCompleted 字段应当为 true。

**验证：需求 4.4**

### 属性 10：后处理失败记录

*对于任何*后处理步骤失败的文件，系统应当在 SyncRecordDetail 中记录失败状态（isCompleted = false）和详细的错误信息（errorMessage）。

**验证：需求 4.2**

### 属性 11：时区参数传递

*对于任何*启用 EXIF 时间重写的配置，当 rewriteExifTimeZone 为 null 或无效时，系统应当跳过 EXIF 时间填充并记录警告日志。

**验证：需求 2.6, 5.4**

### 属性 12：DELETE 操作跳过后处理

*对于任何*DELETE 操作，系统应当跳过所有后处理步骤（SHA1 校验、EXIF 时间填充、文件系统时间更新），仅执行文件删除。

**验证：需求 7.1, 7.2**

### 属性 13：DELETE 操作状态记录

*对于任何*DELETE 操作，SyncRecordDetail 的 isCompleted 字段应当正确反映删除操作的成功或失败状态。

**验证：需求 7.3**

### 属性 14：日志记录完整性

*对于任何*后处理步骤，系统应当在步骤开始时记录 INFO 日志，成功时记录 INFO 日志，失败时记录 ERROR 日志（包含异常堆栈）。

**验证：需求 6.1, 6.2, 6.3**

## 错误处理

### 错误分类

1. **可恢复错误**：
   - EXIF 时间填充失败（非 JPG 文件）：记录警告，继续后续处理
   - 文件系统时间更新失败：记录错误，但不影响文件的可用性

2. **不可恢复错误**：
   - SHA1 校验失败：文件完整性问题，标记为失败，停止该文件的后续处理
   - 文件下载失败：无法继续后处理

### 错误处理策略

1. **单文件错误隔离**：
   - 使用 try-catch 包裹每个文件的处理逻辑
   - 单个文件失败不影响其他文件的处理
   - 在 SyncRecordDetail 中记录每个文件的处理结果

2. **后处理步骤错误处理**：
   - SHA1 校验失败：抛出异常，停止该文件的后续处理
   - EXIF 填充失败：捕获特定异常（"Not a valid JPG"），记录警告，继续处理
   - 文件时间更新失败：记录错误，但标记文件处理完成

3. **日志记录**：
   - INFO：步骤开始和成功完成
   - WARN：可恢复的错误（如 EXIF 填充失败）
   - ERROR：不可恢复的错误（如 SHA1 校验失败）

### 错误恢复

对于失败的文件，用户可以：
1. 查看 SyncRecordDetail 中的 errorMessage 了解失败原因
2. 修复问题后，重新执行同步任务
3. 对于 SHA1 校验失败的文件，系统会重新下载

## 测试策略

### 单元测试

单元测试用于验证特定的示例和边界情况：

1. **PostProcessingCoordinator 测试**：
   - 测试各种配置组合下的后处理流程
   - 测试错误处理逻辑
   - 测试步骤执行顺序

2. **Stage 适配方法测试**：
   - 测试 verifySha1 方法的正确性
   - 测试 fillExifTime 方法的正确性
   - 测试 updateFileSystemTime 方法的正确性

3. **边界情况测试**：
   - 空文件的 SHA1 校验
   - 非 JPG 文件的 EXIF 填充
   - 无效时区的处理
   - DELETE 操作不执行后处理

### 属性测试

属性测试用于验证通用属性在所有输入下的正确性：

1. **配置驱动测试**（属性 1）：
   - 生成随机的 CrontabConfig 配置
   - 生成随机的资产和操作类型
   - 验证后处理步骤的执行与配置一致

2. **SHA1 校验测试**（属性 2, 3）：
   - 生成随机的文件内容和 SHA1 值
   - 验证校验结果的正确性
   - 验证失败时的错误记录

3. **EXIF 时间填充测试**（属性 4, 5）：
   - 生成随机的图片文件和 dateTaken 值
   - 验证 EXIF 时间的正确性
   - 验证非 JPG 文件的错误容忍

4. **文件时间更新测试**（属性 6）：
   - 生成随机的文件和 dateTaken 值
   - 验证文件时间戳的正确性

5. **执行顺序测试**（属性 7）：
   - 记录每个步骤的执行时间戳
   - 验证执行顺序的正确性

6. **错误隔离测试**（属性 8）：
   - 在批量处理中模拟某个文件失败
   - 验证其他文件继续处理

7. **状态记录测试**（属性 9, 10, 13）：
   - 生成随机的处理结果
   - 验证 SyncRecordDetail 的状态记录正确性

8. **DELETE 操作测试**（属性 12）：
   - 生成 DELETE 操作
   - 验证后处理步骤被跳过

9. **日志记录测试**（属性 14）：
   - 捕获日志输出
   - 验证日志级别和内容的正确性

### 测试配置

- 每个属性测试至少运行 100 次迭代
- 使用随机生成器生成测试数据
- 每个测试应当标注对应的属性编号和需求编号

### 集成测试

1. **端到端同步测试**：
   - 模拟完整的同步流程（ADD、UPDATE、DELETE）
   - 验证后处理步骤的正确执行
   - 验证 SyncRecord 和 SyncRecordDetail 的记录

2. **配置变更测试**：
   - 测试不同配置下的同步行为
   - 验证配置变更后的正确性

3. **错误场景测试**：
   - 模拟各种错误情况
   - 验证错误处理和恢复机制

## 实现注意事项

### 性能考虑

1. **顺序处理**：
   - 同步模式采用顺序处理，不需要考虑并发问题
   - 后处理步骤按顺序执行，简化了错误处理逻辑

2. **文件 I/O 优化**：
   - SHA1 计算使用缓冲读取，避免一次性加载大文件
   - EXIF 工具调用使用进程池，避免频繁创建进程

3. **数据库操作**：
   - 批量插入 SyncRecordDetail 记录
   - 使用事务确保数据一致性

### 兼容性

1. **向后兼容**：
   - 不修改现有的 CrontabConfig 结构
   - 不影响全量下载模式的功能
   - 可选的 SyncRecordDetail 字段扩展

2. **配置默认值**：
   - 默认情况下，后处理步骤与全量下载保持一致
   - 用户可以通过配置选择性启用或禁用

### 代码组织

1. **职责分离**：
   - PostProcessingCoordinator 负责编排
   - Stage 负责具体的处理逻辑
   - SyncService 负责同步流程控制

2. **代码复用**：
   - 最大化复用现有的 Stage 实现
   - 提取通用的处理逻辑到独立方法

3. **可测试性**：
   - 依赖注入，便于单元测试
   - 清晰的接口定义，便于 mock

## 部署和迁移

### 数据库迁移

如果选择扩展 SyncRecordDetail 模型，需要执行数据库迁移：

```bash
# 迁移脚本会自动执行
# V0.14.0__sync_post_processing.sql
```

### 配置迁移

现有的 CrontabConfig 配置无需修改，新功能会自动使用现有的配置项：
- `checkSha1`
- `rewriteExifTime`
- `rewriteExifTimeZone`
- `rewriteFileSystemTime`

### 回滚计划

如果需要回滚：
1. 恢复 SyncService 代码到之前的版本
2. 如果扩展了 SyncRecordDetail，数据库字段可以保留（不影响功能）
3. 重新部署应用

## 监控和日志

### 关键指标

1. **后处理成功率**：
   - SHA1 校验成功率
   - EXIF 填充成功率
   - 文件时间更新成功率

2. **处理时间**：
   - 每个后处理步骤的平均耗时
   - 整体同步任务的耗时

3. **错误统计**：
   - 各类错误的发生频率
   - 错误分布（按文件类型、操作类型）

### 日志级别

- **INFO**：正常的处理流程
- **WARN**：可恢复的错误（如 EXIF 填充失败）
- **ERROR**：不可恢复的错误（如 SHA1 校验失败）

### 日志内容

每条日志应包含：
- 资产 ID
- 操作类型（ADD/UPDATE/DELETE）
- 处理步骤
- 结果（成功/失败）
- 错误信息（如果失败）

## 未来扩展

1. **并发后处理**：
   - 如果性能成为瓶颈，可以考虑将后处理改为并发执行
   - 需要引入类似 CrontabPipeline 的 Flow 架构

2. **后处理重试**：
   - 对于失败的后处理步骤，提供手动或自动重试机制
   - 类似于现有的 `executeCrontabExifTime` 和 `executeCrontabRewriteFileSystemTime`

3. **后处理状态查询**：
   - 提供 API 查询每个文件的后处理状态
   - 支持按状态筛选和统计

4. **自定义后处理步骤**：
   - 允许用户配置自定义的后处理步骤
   - 提供插件机制扩展后处理能力
