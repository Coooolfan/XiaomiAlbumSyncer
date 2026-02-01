# 设计文档：灵活的同步与归档模式配置

## 1. 概述

### 1.1 设计目标

本设计文档描述了对 XiaomiAlbumSyncer 云端同步与智能归档功能的扩展方案。该扩展在 spec 2 的基础上，为用户提供更灵活的同步模式和归档模式配置选项。

核心目标：

1. **灵活的同步模式**：支持"仅新增"和"同步所有变化"两种模式
2. **扩展的归档模式**：新增"关闭归档"选项，与现有的时间和空间模式共存
3. **向后兼容**：确保现有配置平滑迁移，不影响已有用户
4. **配置遵循计划**：所有操作严格遵循 Crontab 中的账号、相册、路径配置
5. **用户友好**：提供清晰的 UI 界面和配置验证

### 1.2 核心概念

- **同步模式（SyncMode）**：
  - `ADD_ONLY`：仅新增模式，只下载云端新增的文件
  - `SYNC_ALL_CHANGES`：同步所有变化模式，同步新增、修改、删除

- **归档模式（ArchiveMode）**：
  - `DISABLED`：关闭归档（新增）
  - `TIME`：根据时间归档（已存在）
  - `SPACE`：根据空间阈值归档（已存在）

### 1.3 与 spec 2 的关系

本 spec 是对 spec 2 的功能扩展，而非替代：

- **复用**：复用 spec 2 中的 SyncService、ArchiveService、数据模型
- **扩展**：扩展 CrontabConfig 数据模型，添加新的配置字段
- **增强**：增强 SyncService 以支持不同的同步模式
- **兼容**：保持与现有功能的完全兼容

## 2. 架构设计

### 2.1 整体架构

本扩展不改变 spec 2 的整体架构，仅在以下层面进行扩展：

1. **数据模型层**：扩展 CrontabConfig，新增 SyncMode 枚举
2. **服务层**：增强 SyncService 和 ArchiveService 的逻辑判断
3. **控制层**：无需修改，复用现有 API
4. **前端层**：扩展配置界面，添加新的选择器和条件显示

### 2.2 模块职责变化

#### 2.2.1 SyncService 增强

- 根据 `syncMode` 决定是否处理删除和修改操作
- `ADD_ONLY` 模式：只处理新增
- `SYNC_ALL_CHANGES` 模式：处理新增、删除、修改

#### 2.2.2 ArchiveService 增强

- 根据 `archiveMode` 决定是否执行归档
- `DISABLED` 模式：直接返回，不执行任何操作
- `TIME` 和 `SPACE` 模式：按原有逻辑执行


## 3. 组件和接口设计

### 3.1 数据模型

#### 3.1.1 新增枚举类型

##### SyncMode（同步模式枚举）

```kotlin
/**
 * 同步模式枚举
 */
enum class SyncMode {
    /**
     * 仅新增模式：只下载云端新增的文件到本地
     */
    ADD_ONLY,
    
    /**
     * 同步所有变化模式：同步云端的新增、修改、删除到本地
     */
    SYNC_ALL_CHANGES
}
```

#### 3.1.2 扩展现有枚举

##### ArchiveMode（归档模式枚举扩展）

```kotlin
/**
 * 归档模式枚举
 */
enum class ArchiveMode {
    /**
     * 关闭归档（新增）
     */
    DISABLED,
    
    /**
     * 基于时间（已存在）
     */
    TIME,
    
    /**
     * 基于空间阈值（已存在）
     */
    SPACE
}
```

#### 3.1.3 扩展 CrontabConfig

```kotlin
@Serialized
data class CrontabConfig(
    // ... 现有配置字段 ...
    
    // ========== 同步配置 ==========
    
    /**
     * 是否启用同步功能
     */
    val enableSync: Boolean = false,
    
    /**
     * 同步模式（新增字段）
     * 默认为 ADD_ONLY 以保持向后兼容
     */
    val syncMode: SyncMode = SyncMode.ADD_ONLY,
    
    /**
     * 同步文件夹名称（相对于 targetPath）
     */
    val syncFolder: String = "sync",
    
    // ========== 归档配置 ==========
    
    /**
     * 是否启用归档功能（保留用于向后兼容）
     */
    val enableArchive: Boolean = false,
    
    /**
     * 归档模式（扩展字段）
     * 新增 DISABLED 选项
     */
    val archiveMode: ArchiveMode = ArchiveMode.TIME,
    
    /**
     * 保留天数（时间模式）
     */
    val archiveDays: Int = 30,
    
    /**
     * 云空间阈值百分比（空间模式）
     */
    val cloudSpaceThreshold: Int = 90,
    
    /**
     * 归档文件夹名称（相对于 targetPath）
     */
    val backupFolder: String = "backup",
    
    /**
     * 归档后是否删除云端
     */
    val deleteCloudAfterArchive: Boolean = true,
    
    /**
     * 归档前是否需要确认
     */
    val confirmBeforeArchive: Boolean = true,
)
```

### 3.2 服务接口设计

#### 3.2.1 SyncService 增强

现有的 `SyncService` 接口不需要修改，但实现需要增强：

```kotlin
interface SyncService {
    /**
     * 执行同步任务
     * 
     * 增强说明：
     * - 根据 crontab.config.syncMode 决定同步行为
     * - ADD_ONLY: 只处理新增的资产
     * - SYNC_ALL_CHANGES: 处理新增、删除、修改的资产
     * 
     * @param crontabId 定时任务 ID
     * @return 同步记录 ID
     */
    suspend fun executeSync(crontabId: Long): Long
    
    // ... 其他方法保持不变 ...
}
```

#### 3.2.2 ArchiveService 增强

现有的 `ArchiveService` 接口不需要修改，但实现需要增强：

```kotlin
interface ArchiveService {
    /**
     * 预览归档计划
     * 
     * 增强说明：
     * - 如果 archiveMode == DISABLED，返回空计划
     * - 否则按原有逻辑执行
     * 
     * @param crontabId 定时任务 ID
     * @return 归档计划
     */
    suspend fun previewArchive(crontabId: Long): ArchivePlan
    
    /**
     * 执行归档任务
     * 
     * 增强说明：
     * - 如果 archiveMode == DISABLED，直接返回（不执行归档）
     * - 否则按原有逻辑执行
     * 
     * @param crontabId 定时任务 ID
     * @param confirmed 是否已确认
     * @return 归档记录 ID
     */
    suspend fun executeArchive(crontabId: Long, confirmed: Boolean): Long
    
    // ... 其他方法保持不变 ...
}
```

### 3.3 前端接口设计

#### 3.3.1 配置界面组件

前端需要在现有的 Crontab 配置界面中添加以下元素：

1. **同步模式选择器**：
   - 类型：单选按钮组或下拉选择器
   - 选项：
     - "仅新增"（ADD_ONLY）- 默认选项
     - "同步所有变化"（SYNC_ALL_CHANGES）
   - 位置：在同步配置区域，enableSync 开关下方

2. **归档模式选择器**：
   - 类型：单选按钮组或下拉选择器
   - 选项：
     - "关闭归档"（DISABLED）
     - "根据时间归档"（TIME）
     - "根据空间归档"（SPACE）
   - 位置：在归档配置区域，替换或增强现有的 enableArchive 开关

3. **条件显示逻辑**：
   - 当 `archiveMode === 'TIME'` 时，显示"保留天数"输入框
   - 当 `archiveMode === 'SPACE'` 时，显示"空间阈值"输入框
   - 当 `archiveMode === 'DISABLED'` 时，隐藏所有归档相关配置


## 4. 核心算法设计

### 4.1 增强的同步算法

#### 4.1.1 同步执行逻辑增强

```kotlin
/**
 * 执行同步操作（增强版）
 * 
 * 算法步骤：
 * 1. 获取 Crontab 配置
 * 2. 检测云端变化
 * 3. 根据 syncMode 决定处理哪些变化
 * 4. 执行相应的同步操作
 * 5. 记录同步历史
 */
suspend fun executeSync(crontabId: Long): Long {
    val crontab = crontabRepository.findById(crontabId)
    val config = crontab.config
    
    // 创建同步记录
    val syncRecord = createSyncRecord(crontab)
    
    try {
        // 1. 检测变化
        val changes = detectChanges(crontabId)
        
        // 2. 处理新增（两种模式都需要）
        changes.addedAssets.forEach { asset ->
            downloadToSync(asset, config.syncFolder)
            recordSyncDetail(syncRecord, asset, SyncOperation.ADD)
        }
        
        // 3. 根据同步模式决定是否处理删除和修改
        when (config.syncMode) {
            SyncMode.ADD_ONLY -> {
                // 仅新增模式：不处理删除和修改
                // 记录日志：跳过删除和修改操作
                logger.info("SyncMode is ADD_ONLY, skipping ${changes.deletedAssets.size} deletions and ${changes.updatedAssets.size} updates")
            }
            
            SyncMode.SYNC_ALL_CHANGES -> {
                // 同步所有变化模式：处理删除和修改
                
                // 处理删除
                changes.deletedAssets.forEach { asset ->
                    deleteFromSync(asset, config.syncFolder)
                    recordSyncDetail(syncRecord, asset, SyncOperation.DELETE)
                }
                
                // 处理修改
                changes.updatedAssets.forEach { asset ->
                    deleteFromSync(asset, config.syncFolder)
                    downloadToSync(asset, config.syncFolder)
                    recordSyncDetail(syncRecord, asset, SyncOperation.UPDATE)
                }
            }
        }
        
        // 4. 更新同步记录状态
        updateSyncRecord(syncRecord, SyncStatus.COMPLETED)
        
        return syncRecord.id
    } catch (e: Exception) {
        updateSyncRecord(syncRecord, SyncStatus.FAILED, e.message)
        throw e
    }
}
```

### 4.2 增强的归档算法

#### 4.2.1 归档预览逻辑增强

```kotlin
/**
 * 预览归档计划（增强版）
 * 
 * 算法步骤：
 * 1. 获取 Crontab 配置
 * 2. 检查归档模式
 * 3. 如果是 DISABLED，返回空计划
 * 4. 否则按原有逻辑计算归档计划
 */
suspend fun previewArchive(crontabId: Long): ArchivePlan {
    val crontab = crontabRepository.findById(crontabId)
    val config = crontab.config
    
    // 检查归档模式
    if (config.archiveMode == ArchiveMode.DISABLED) {
        // 归档已关闭，返回空计划
        return ArchivePlan(
            archiveBeforeDate = LocalDate.now(),
            assetsToArchive = emptyList(),
            estimatedFreedSpace = 0L
        )
    }
    
    // 按原有逻辑计算归档计划
    return when (config.archiveMode) {
        ArchiveMode.TIME -> calculateTimeBasedArchive(crontabId)
        ArchiveMode.SPACE -> calculateSpaceBasedArchive(crontabId)
        ArchiveMode.DISABLED -> throw IllegalStateException("Should not reach here")
    }
}
```

#### 4.2.2 归档执行逻辑增强

```kotlin
/**
 * 执行归档操作（增强版）
 * 
 * 算法步骤：
 * 1. 获取 Crontab 配置
 * 2. 检查归档模式
 * 3. 如果是 DISABLED，直接返回（不执行归档）
 * 4. 否则按原有逻辑执行归档
 */
suspend fun executeArchive(crontabId: Long, confirmed: Boolean): Long {
    val crontab = crontabRepository.findById(crontabId)
    val config = crontab.config
    
    // 检查归档模式
    if (config.archiveMode == ArchiveMode.DISABLED) {
        // 归档已关闭，不执行任何操作
        logger.info("Archive is disabled for crontab $crontabId, skipping archive operation")
        throw ArchiveDisabledException("归档功能已关闭")
    }
    
    // 检查是否需要确认
    if (config.confirmBeforeArchive && !confirmed) {
        throw ArchiveNotConfirmedException("归档操作需要用户确认")
    }
    
    // 按原有逻辑执行归档
    // ... 原有的归档逻辑 ...
}
```

### 4.3 配置迁移算法

#### 4.3.1 数据库迁移逻辑

```sql
-- 数据库迁移脚本：V0.14.0__flexible_sync_archive_modes.sql

-- 1. 为 CrontabConfig 添加 syncMode 字段（通过 Jimmer 的 @Serialized 自动处理）
-- 注意：Jimmer 的 @Serialized 注解会将整个对象序列化为 JSON 存储
-- 因此不需要显式的 ALTER TABLE 语句

-- 2. 更新现有数据的默认值
-- 由于 CrontabConfig 是 JSON 存储，需要在应用层处理迁移
-- 这里只是记录迁移逻辑，实际迁移在应用启动时执行

-- 迁移逻辑（伪代码）：
-- FOR EACH crontab IN crontabs:
--   IF crontab.config.syncMode IS NULL:
--     crontab.config.syncMode = SyncMode.ADD_ONLY
--   
--   IF crontab.config.enableArchive == false:
--     crontab.config.archiveMode = ArchiveMode.DISABLED
--   ELSE:
--     -- 保持原有的 archiveMode (TIME 或 SPACE)
--     PASS
```

#### 4.3.2 应用层迁移逻辑

```kotlin
/**
 * 配置迁移服务
 * 
 * 在应用启动时执行，确保所有现有配置都有正确的默认值
 */
class ConfigMigrationService(
    private val crontabRepository: CrontabRepository
) {
    
    /**
     * 执行配置迁移
     */
    fun migrateConfigs() {
        val allCrontabs = crontabRepository.findAll()
        
        allCrontabs.forEach { crontab ->
            var needsUpdate = false
            var updatedConfig = crontab.config
            
            // 迁移 syncMode
            if (updatedConfig.syncMode == null) {
                updatedConfig = updatedConfig.copy(syncMode = SyncMode.ADD_ONLY)
                needsUpdate = true
            }
            
            // 迁移 archiveMode
            if (updatedConfig.enableArchive == false && 
                updatedConfig.archiveMode != ArchiveMode.DISABLED) {
                updatedConfig = updatedConfig.copy(archiveMode = ArchiveMode.DISABLED)
                needsUpdate = true
            }
            
            // 保存更新
            if (needsUpdate) {
                crontabRepository.update(crontab.copy(config = updatedConfig))
                logger.info("Migrated config for crontab ${crontab.id}")
            }
        }
    }
}
```


## 5. 数据模型

### 5.1 实体关系

本扩展不引入新的实体，只扩展现有的 `CrontabConfig`：

```
Crontab (已存在)
  └── config: CrontabConfig (扩展)
        ├── syncMode: SyncMode (新增)
        ├── archiveMode: ArchiveMode (扩展，新增 DISABLED 选项)
        └── ... 其他现有字段
```

### 5.2 数据验证规则

#### 5.2.1 同步模式验证

```kotlin
/**
 * 验证同步模式配置
 */
fun validateSyncMode(config: CrontabConfig): ValidationResult {
    // syncMode 必须是有效的枚举值
    if (config.syncMode !in SyncMode.values()) {
        return ValidationResult.error("无效的同步模式")
    }
    
    return ValidationResult.success()
}
```

#### 5.2.2 归档模式验证

```kotlin
/**
 * 验证归档模式配置
 */
fun validateArchiveMode(config: CrontabConfig): ValidationResult {
    // archiveMode 必须是有效的枚举值
    if (config.archiveMode !in ArchiveMode.values()) {
        return ValidationResult.error("无效的归档模式")
    }
    
    // 如果是 TIME 模式，archiveDays 必须是正整数
    if (config.archiveMode == ArchiveMode.TIME && config.archiveDays <= 0) {
        return ValidationResult.error("保留天数必须是正整数")
    }
    
    // 如果是 SPACE 模式，cloudSpaceThreshold 必须在 1-100 之间
    if (config.archiveMode == ArchiveMode.SPACE) {
        if (config.cloudSpaceThreshold < 1 || config.cloudSpaceThreshold > 100) {
            return ValidationResult.error("空间阈值必须在 1-100 之间")
        }
    }
    
    return ValidationResult.success()
}
```

#### 5.2.3 配置一致性验证

```kotlin
/**
 * 验证配置一致性
 */
fun validateConfigConsistency(config: CrontabConfig): ValidationResult {
    // 如果 enableArchive 为 false，archiveMode 应该是 DISABLED
    if (!config.enableArchive && config.archiveMode != ArchiveMode.DISABLED) {
        // 这是一个警告，不是错误，因为我们会自动修正
        logger.warn("enableArchive is false but archiveMode is not DISABLED, will auto-correct")
    }
    
    // 如果 archiveMode 是 DISABLED，enableArchive 应该是 false
    if (config.archiveMode == ArchiveMode.DISABLED && config.enableArchive) {
        // 这是一个警告，不是错误，因为我们会自动修正
        logger.warn("archiveMode is DISABLED but enableArchive is true, will auto-correct")
    }
    
    return ValidationResult.success()
}
```

### 5.3 数据存储

由于 `CrontabConfig` 使用 Jimmer 的 `@Serialized` 注解，整个配置对象会被序列化为 JSON 存储在数据库中。因此：

1. **不需要修改数据库表结构**：新增的字段会自动包含在 JSON 中
2. **向后兼容**：旧的 JSON 数据在反序列化时会使用默认值
3. **灵活性**：可以轻松添加新的配置字段

示例 JSON 结构：

```json
{
  "expression": "0 0 * * * ?",
  "timeZone": "Asia/Shanghai",
  "targetPath": "/data/photos",
  "downloadImages": true,
  "downloadVideos": true,
  "enableSync": true,
  "syncMode": "ADD_ONLY",
  "syncFolder": "sync",
  "enableArchive": true,
  "archiveMode": "TIME",
  "archiveDays": 30,
  "cloudSpaceThreshold": 90,
  "backupFolder": "backup",
  "deleteCloudAfterArchive": true,
  "confirmBeforeArchive": true
}
```


## 6. 正确性属性

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的正式陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

### 6.1 同步模式属性

**属性 1：ADD_ONLY 模式仅处理新增**

*对于任何*配置为 ADD_ONLY 同步模式的 Crontab，当执行同步操作时，系统应该只下载云端新增的文件，而不删除或修改本地 syncFolder 中已存在的文件。

**验证：需求 1.4, 10.1, 10.2**

---

**属性 2：SYNC_ALL_CHANGES 模式同步所有变化**

*对于任何*配置为 SYNC_ALL_CHANGES 同步模式的 Crontab，当执行同步操作时，系统应该同步云端的所有变化（新增、删除、修改）到本地 syncFolder。

**验证：需求 1.5, 10.3, 10.4**

---

### 6.2 归档模式属性

**属性 3：DISABLED 模式不执行归档**

*对于任何*配置为 DISABLED 归档模式的 Crontab，当调用归档操作时，系统不应该移动任何文件到 backupFolder，也不应该删除任何云端照片。

**验证：需求 2.5**

---

**属性 4：TIME 模式按时间归档**

*对于任何*配置为 TIME 归档模式的 Crontab，当执行归档操作时，系统应该归档所有早于（当前日期 - 保留天数）的照片，且不归档晚于该日期的照片。

**验证：需求 3.3**

---

**属性 5：SPACE 模式按空间阈值归档**

*对于任何*配置为 SPACE 归档模式的 Crontab，当执行归档操作时，系统应该按时间从旧到新归档照片，直到云端空间使用率低于或等于配置的阈值百分比。

**验证：需求 4.3**

---

### 6.3 配置约束属性

**属性 6：同步操作遵循 Crontab 配置**

*对于任何*Crontab 配置，当执行同步操作时，系统应该只同步该 Crontab 指定的账号和相册，并将文件下载到配置的 syncFolder 路径。

**验证：需求 5.1, 5.2, 5.3**

---

**属性 7：归档操作遵循 Crontab 配置**

*对于任何*Crontab 配置，当执行归档操作时，系统应该只归档该 Crontab 指定账号和相册的照片，并从配置的 syncFolder 移动文件到配置的 backupFolder。

**验证：需求 5.4, 5.5, 5.6**

---

### 6.4 配置持久化属性

**属性 8：配置往返一致性**

*对于任何*Crontab 配置（包括 syncMode、archiveMode、archiveDays、cloudSpaceThreshold），保存后重新读取应该得到完全相同的配置值。

**验证：需求 6.1, 6.2, 6.3**

---

### 6.5 配置验证属性

**属性 9：保留天数验证正确性**

*对于任何*输入的保留天数值，如果该值不是正整数，验证应该失败并阻止保存配置。

**验证：需求 9.1, 9.3**

---

**属性 10：空间阈值验证正确性**

*对于任何*输入的空间阈值值，如果该值不在 1-100 之间，验证应该失败并阻止保存配置。

**验证：需求 9.2, 9.3**

---

### 6.6 向后兼容性属性

**属性 11：配置迁移默认值正确性**

*对于任何*没有 syncMode 字段的旧 Crontab 配置，迁移后应该自动设置 syncMode 为 ADD_ONLY。

**验证：需求 7.1**

---

**属性 12：归档模式迁移正确性**

*对于任何*旧 Crontab 配置，如果 enableArchive 为 false，迁移后 archiveMode 应该设置为 DISABLED；如果 enableArchive 为 true，迁移后应该保留原有的 archiveMode（TIME 或 SPACE）。

**验证：需求 7.3, 7.4**

---


## 7. 错误处理

### 7.1 配置验证错误

#### 7.1.1 无效的同步模式

- **场景**：用户尝试设置不存在的同步模式
- **处理**：
  1. 返回验证错误
  2. 提示用户选择有效的同步模式（ADD_ONLY 或 SYNC_ALL_CHANGES）
  3. 阻止保存配置

#### 7.1.2 无效的归档模式

- **场景**：用户尝试设置不存在的归档模式
- **处理**：
  1. 返回验证错误
  2. 提示用户选择有效的归档模式（DISABLED、TIME 或 SPACE）
  3. 阻止保存配置

#### 7.1.3 无效的保留天数

- **场景**：用户输入非正整数的保留天数
- **处理**：
  1. 返回验证错误："保留天数必须是正整数"
  2. 高亮显示错误的输入框
  3. 阻止保存配置

#### 7.1.4 无效的空间阈值

- **场景**：用户输入不在 1-100 范围内的空间阈值
- **处理**：
  1. 返回验证错误："空间阈值必须在 1-100 之间"
  2. 高亮显示错误的输入框
  3. 阻止保存配置

### 7.2 运行时错误

#### 7.2.1 归档已关闭但尝试执行归档

- **场景**：archiveMode 为 DISABLED 时调用 executeArchive
- **处理**：
  1. 抛出 `ArchiveDisabledException`
  2. 记录日志：归档功能已关闭
  3. 返回友好的错误消息给用户

#### 7.2.2 配置迁移失败

- **场景**：在应用启动时迁移配置失败
- **处理**：
  1. 记录详细的错误日志
  2. 跳过该配置，继续迁移其他配置
  3. 在管理界面显示迁移失败的配置列表
  4. 允许用户手动修复

### 7.3 数据一致性错误

#### 7.3.1 配置不一致

- **场景**：enableArchive 和 archiveMode 不一致
- **处理**：
  1. 记录警告日志
  2. 自动修正：
     - 如果 archiveMode == DISABLED，设置 enableArchive = false
     - 如果 enableArchive == false，设置 archiveMode = DISABLED
  3. 保存修正后的配置

### 7.4 错误恢复策略

#### 7.4.1 配置保存失败

- **场景**：保存配置到数据库失败
- **处理**：
  1. 回滚事务
  2. 保留原有配置
  3. 向用户显示错误消息
  4. 允许用户重试

#### 7.4.2 同步操作中的模式切换

- **场景**：同步操作进行中，用户修改了同步模式
- **处理**：
  1. 当前同步操作继续使用旧的配置
  2. 下次同步操作使用新的配置
  3. 不中断正在进行的同步操作

## 8. 测试策略

### 8.1 单元测试

#### 8.1.1 枚举测试

```kotlin
@Test
fun `SyncMode 枚举包含所有预期值`() {
    val modes = SyncMode.values()
    assertEquals(2, modes.size)
    assertTrue(SyncMode.ADD_ONLY in modes)
    assertTrue(SyncMode.SYNC_ALL_CHANGES in modes)
}

@Test
fun `ArchiveMode 枚举包含所有预期值`() {
    val modes = ArchiveMode.values()
    assertEquals(3, modes.size)
    assertTrue(ArchiveMode.DISABLED in modes)
    assertTrue(ArchiveMode.TIME in modes)
    assertTrue(ArchiveMode.SPACE in modes)
}
```

#### 8.1.2 配置验证测试

```kotlin
@Test
fun `验证保留天数为正整数`() {
    val config = CrontabConfig(archiveMode = ArchiveMode.TIME, archiveDays = -1)
    val result = validateArchiveMode(config)
    assertFalse(result.isSuccess)
    assertEquals("保留天数必须是正整数", result.errorMessage)
}

@Test
fun `验证空间阈值在 1-100 之间`() {
    val config1 = CrontabConfig(archiveMode = ArchiveMode.SPACE, cloudSpaceThreshold = 0)
    val result1 = validateArchiveMode(config1)
    assertFalse(result1.isSuccess)
    
    val config2 = CrontabConfig(archiveMode = ArchiveMode.SPACE, cloudSpaceThreshold = 101)
    val result2 = validateArchiveMode(config2)
    assertFalse(result2.isSuccess)
}
```

#### 8.1.3 配置迁移测试

```kotlin
@Test
fun `迁移时为旧配置设置默认 syncMode`() {
    // 创建没有 syncMode 的旧配置
    val oldConfig = CrontabConfig(/* ... 其他字段 ... */)
    
    // 执行迁移
    val migratedConfig = migrateConfig(oldConfig)
    
    // 验证默认值
    assertEquals(SyncMode.ADD_ONLY, migratedConfig.syncMode)
}

@Test
fun `迁移时根据 enableArchive 设置 archiveMode`() {
    // enableArchive = false 的情况
    val config1 = CrontabConfig(enableArchive = false, archiveMode = ArchiveMode.TIME)
    val migrated1 = migrateConfig(config1)
    assertEquals(ArchiveMode.DISABLED, migrated1.archiveMode)
    
    // enableArchive = true 的情况
    val config2 = CrontabConfig(enableArchive = true, archiveMode = ArchiveMode.TIME)
    val migrated2 = migrateConfig(config2)
    assertEquals(ArchiveMode.TIME, migrated2.archiveMode)
}
```

### 8.2 属性测试

属性测试用于验证通用属性在大量随机输入下都成立。每个属性测试应该运行至少 100 次迭代。

#### 8.2.1 同步模式属性测试

```kotlin
@Test
fun `属性 1：ADD_ONLY 模式仅处理新增`() = runTest {
    // 特性：3-flexible-sync-archive-modes，属性 1：ADD_ONLY 模式仅处理新增
    repeat(100) {
        // 生成随机的 Crontab 配置（syncMode = ADD_ONLY）
        val crontab = generateRandomCrontab(syncMode = SyncMode.ADD_ONLY)
        
        // 生成随机的云端变化（包含新增、删除、修改）
        val addedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        val deletedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        val updatedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        
        // 模拟云端变化
        mockCloudChanges(addedAssets, deletedAssets, updatedAssets)
        
        // 记录同步前的本地文件
        val beforeSync = listFilesInSync(crontab)
        
        // 执行同步
        syncService.executeSync(crontab.id)
        
        // 验证：新增的文件应该被下载
        addedAssets.forEach { asset ->
            val file = File(crontab.config.syncFolder, asset.fileName)
            assertTrue(file.exists(), "新增的文件应该被下载")
        }
        
        // 验证：删除的文件应该保留在本地
        deletedAssets.forEach { asset ->
            if (asset in beforeSync) {
                val file = File(crontab.config.syncFolder, asset.fileName)
                assertTrue(file.exists(), "删除的文件应该保留在本地")
            }
        }
        
        // 验证：修改的文件应该保持原样
        updatedAssets.forEach { asset ->
            if (asset in beforeSync) {
                val file = File(crontab.config.syncFolder, asset.fileName)
                val sha1 = calculateSha1(file)
                assertEquals(asset.originalSha1, sha1, "修改的文件应该保持原样")
            }
        }
    }
}

@Test
fun `属性 2：SYNC_ALL_CHANGES 模式同步所有变化`() = runTest {
    // 特性：3-flexible-sync-archive-modes，属性 2：SYNC_ALL_CHANGES 模式同步所有变化
    repeat(100) {
        // 生成随机的 Crontab 配置（syncMode = SYNC_ALL_CHANGES）
        val crontab = generateRandomCrontab(syncMode = SyncMode.SYNC_ALL_CHANGES)
        
        // 生成随机的云端变化
        val addedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        val deletedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        val updatedAssets = generateRandomAssets(count = Random.nextInt(1, 10))
        
        // 模拟云端变化
        mockCloudChanges(addedAssets, deletedAssets, updatedAssets)
        
        // 执行同步
        syncService.executeSync(crontab.id)
        
        // 验证：新增的文件应该被下载
        addedAssets.forEach { asset ->
            val file = File(crontab.config.syncFolder, asset.fileName)
            assertTrue(file.exists(), "新增的文件应该被下载")
        }
        
        // 验证：删除的文件应该从本地删除
        deletedAssets.forEach { asset ->
            val file = File(crontab.config.syncFolder, asset.fileName)
            assertFalse(file.exists(), "删除的文件应该从本地删除")
        }
        
        // 验证：修改的文件应该被更新
        updatedAssets.forEach { asset ->
            val file = File(crontab.config.syncFolder, asset.fileName)
            val sha1 = calculateSha1(file)
            assertEquals(asset.newSha1, sha1, "修改的文件应该被更新")
        }
    }
}
```

#### 8.2.2 归档模式属性测试

```kotlin
@Test
fun `属性 3：DISABLED 模式不执行归档`() = runTest {
    // 特性：3-flexible-sync-archive-modes，属性 3：DISABLED 模式不执行归档
    repeat(100) {
        // 生成随机的 Crontab 配置（archiveMode = DISABLED）
        val crontab = generateRandomCrontab(archiveMode = ArchiveMode.DISABLED)
        
        // 生成随机的照片数据
        val assets = generateRandomAssets(count = Random.nextInt(10, 50))
        setupAssetsInSync(crontab, assets)
        
        // 记录归档前的文件
        val beforeArchive = listFilesInSync(crontab)
        val beforeBackup = listFilesInBackup(crontab)
        
        // 尝试执行归档（应该抛出异常或直接返回）
        try {
            archiveService.executeArchive(crontab.id, confirmed = true)
        } catch (e: ArchiveDisabledException) {
            // 预期的异常
        }
        
        // 验证：syncFolder 中的文件应该保持不变
        val afterArchive = listFilesInSync(crontab)
        assertEquals(beforeArchive, afterArchive, "syncFolder 中的文件应该保持不变")
        
        // 验证：backupFolder 中的文件应该保持不变
        val afterBackup = listFilesInBackup(crontab)
        assertEquals(beforeBackup, afterBackup, "backupFolder 中的文件应该保持不变")
    }
}

@Test
fun `属性 4：TIME 模式按时间归档`() = runTest {
    // 特性：3-flexible-sync-archive-modes，属性 4：TIME 模式按时间归档
    repeat(100) {
        // 生成随机的保留天数
        val archiveDays = Random.nextInt(30, 180)
        val crontab = generateRandomCrontab(
            archiveMode = ArchiveMode.TIME,
            archiveDays = archiveDays
        )
        
        // 生成随机的照片数据（包含不同日期）
        val oldAssets = generateRandomAssetsWithDate(
            count = Random.nextInt(5, 15),
            daysAgo = Random.nextInt(archiveDays + 1, archiveDays + 100)
        )
        val newAssets = generateRandomAssetsWithDate(
            count = Random.nextInt(5, 15),
            daysAgo = Random.nextInt(0, archiveDays)
        )
        setupAssetsInSync(crontab, oldAssets + newAssets)
        
        // 执行归档
        archiveService.executeArchive(crontab.id, confirmed = true)
        
        // 计算归档截止日期
        val archiveBeforeDate = LocalDate.now().minusDays(archiveDays.toLong())
        
        // 验证：旧照片应该被归档
        oldAssets.forEach { asset ->
            val assetDate = asset.dateTaken.atZone(ZoneOffset.UTC).toLocalDate()
            if (assetDate.isBefore(archiveBeforeDate)) {
                val inBackup = File(crontab.config.backupFolder, asset.fileName).exists()
                val inSync = File(crontab.config.syncFolder, asset.fileName).exists()
                assertTrue(inBackup, "旧照片应该在 backup 中")
                assertFalse(inSync, "旧照片不应该在 sync 中")
            }
        }
        
        // 验证：新照片应该保留在 sync 中
        newAssets.forEach { asset ->
            val assetDate = asset.dateTaken.atZone(ZoneOffset.UTC).toLocalDate()
            if (!assetDate.isBefore(archiveBeforeDate)) {
                val inSync = File(crontab.config.syncFolder, asset.fileName).exists()
                val inBackup = File(crontab.config.backupFolder, asset.fileName).exists()
                assertTrue(inSync, "新照片应该在 sync 中")
                assertFalse(inBackup, "新照片不应该在 backup 中")
            }
        }
    }
}
```

#### 8.2.3 配置持久化属性测试

```kotlin
@Test
fun `属性 8：配置往返一致性`() = runTest {
    // 特性：3-flexible-sync-archive-modes，属性 8：配置往返一致性
    repeat(100) {
        // 生成随机的 Crontab 配置
        val originalConfig = generateRandomCrontabConfig(
            syncMode = SyncMode.values().random(),
            archiveMode = ArchiveMode.values().random(),
            archiveDays = Random.nextInt(1, 365),
            cloudSpaceThreshold = Random.nextInt(1, 101)
        )
        
        // 创建 Crontab
        val crontab = crontabRepository.create(
            Crontab {
                this.config = originalConfig
                // ... 其他必需字段 ...
            }
        )
        
        // 重新读取
        val loadedCrontab = crontabRepository.findById(crontab.id)
        val loadedConfig = loadedCrontab.config
        
        // 验证：所有配置字段应该一致
        assertEquals(originalConfig.syncMode, loadedConfig.syncMode)
        assertEquals(originalConfig.archiveMode, loadedConfig.archiveMode)
        assertEquals(originalConfig.archiveDays, loadedConfig.archiveDays)
        assertEquals(originalConfig.cloudSpaceThreshold, loadedConfig.cloudSpaceThreshold)
        assertEquals(originalConfig.syncFolder, loadedConfig.syncFolder)
        assertEquals(originalConfig.backupFolder, loadedConfig.backupFolder)
    }
}
```

#### 8.2.4 配置验证属性测试

```kotlin
@Test
fun `属性 9：保留天数验证正确性`() {
    // 特性：3-flexible-sync-archive-modes，属性 9：保留天数验证正确性
    repeat(100) {
        // 生成随机的无效保留天数（负数、零）
        val invalidDays = if (Random.nextBoolean()) {
            -Random.nextInt(1, 100)
        } else {
            0
        }
        
        val config = CrontabConfig(
            archiveMode = ArchiveMode.TIME,
            archiveDays = invalidDays
        )
        
        // 验证应该失败
        val result = validateArchiveMode(config)
        assertFalse(result.isSuccess, "无效的保留天数应该验证失败")
        assertTrue(result.errorMessage.contains("正整数"), "错误消息应该提示正整数")
    }
    
    // 验证有效的保留天数
    repeat(100) {
        val validDays = Random.nextInt(1, 365)
        val config = CrontabConfig(
            archiveMode = ArchiveMode.TIME,
            archiveDays = validDays
        )
        
        val result = validateArchiveMode(config)
        assertTrue(result.isSuccess, "有效的保留天数应该验证成功")
    }
}

@Test
fun `属性 10：空间阈值验证正确性`() {
    // 特性：3-flexible-sync-archive-modes，属性 10：空间阈值验证正确性
    repeat(100) {
        // 生成随机的无效空间阈值（<1 或 >100）
        val invalidThreshold = if (Random.nextBoolean()) {
            -Random.nextInt(1, 100)
        } else {
            Random.nextInt(101, 200)
        }
        
        val config = CrontabConfig(
            archiveMode = ArchiveMode.SPACE,
            cloudSpaceThreshold = invalidThreshold
        )
        
        // 验证应该失败
        val result = validateArchiveMode(config)
        assertFalse(result.isSuccess, "无效的空间阈值应该验证失败")
        assertTrue(result.errorMessage.contains("1-100"), "错误消息应该提示范围")
    }
    
    // 验证有效的空间阈值
    repeat(100) {
        val validThreshold = Random.nextInt(1, 101)
        val config = CrontabConfig(
            archiveMode = ArchiveMode.SPACE,
            cloudSpaceThreshold = validThreshold
        )
        
        val result = validateArchiveMode(config)
        assertTrue(result.isSuccess, "有效的空间阈值应该验证成功")
    }
}
```

### 8.3 集成测试

#### 8.3.1 完整同步流程测试（不同模式）

```kotlin
@Test
fun `ADD_ONLY 模式完整同步流程`() = runTest {
    // 1. 创建配置为 ADD_ONLY 的 Crontab
    val crontab = createTestCrontab(syncMode = SyncMode.ADD_ONLY)
    
    // 2. 初始同步
    syncService.executeSync(crontab.id)
    val initialFiles = listFilesInSync(crontab)
    
    // 3. 云端删除一些照片
    deleteCloudAssets(count = 5)
    
    // 4. 再次同步
    syncService.executeSync(crontab.id)
    val afterDeleteSync = listFilesInSync(crontab)
    
    // 5. 验证：本地文件应该保持不变
    assertEquals(initialFiles, afterDeleteSync)
}

@Test
fun `SYNC_ALL_CHANGES 模式完整同步流程`() = runTest {
    // 1. 创建配置为 SYNC_ALL_CHANGES 的 Crontab
    val crontab = createTestCrontab(syncMode = SyncMode.SYNC_ALL_CHANGES)
    
    // 2. 初始同步
    syncService.executeSync(crontab.id)
    val initialFiles = listFilesInSync(crontab)
    
    // 3. 云端删除一些照片
    val deletedCount = deleteCloudAssets(count = 5)
    
    // 4. 再次同步
    syncService.executeSync(crontab.id)
    val afterDeleteSync = listFilesInSync(crontab)
    
    // 5. 验证：本地文件应该减少
    assertEquals(initialFiles.size - deletedCount, afterDeleteSync.size)
}
```

#### 8.3.2 完整归档流程测试（不同模式）

```kotlin
@Test
fun `DISABLED 模式不执行归档`() = runTest {
    // 1. 创建配置为 DISABLED 的 Crontab
    val crontab = createTestCrontab(archiveMode = ArchiveMode.DISABLED)
    
    // 2. 准备测试数据
    setupTestAssets(crontab, count = 50)
    
    // 3. 尝试执行归档
    assertThrows<ArchiveDisabledException> {
        archiveService.executeArchive(crontab.id, confirmed = true)
    }
    
    // 4. 验证：没有文件被移动
    val backupFiles = listFilesInBackup(crontab)
    assertTrue(backupFiles.isEmpty())
}
```

### 8.4 前端测试

#### 8.4.1 配置界面测试

```typescript
describe('同步模式选择器', () => {
  it('应该显示两个同步模式选项', () => {
    const wrapper = mount(SyncConfigCard)
    const options = wrapper.findAll('.sync-mode-option')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toContain('仅新增')
    expect(options[1].text()).toContain('同步所有变化')
  })
  
  it('应该默认选择 ADD_ONLY 模式', () => {
    const wrapper = mount(SyncConfigCard)
    const selected = wrapper.find('.sync-mode-option.selected')
    expect(selected.text()).toContain('仅新增')
  })
})

describe('归档模式选择器', () => {
  it('应该显示三个归档模式选项', () => {
    const wrapper = mount(ArchiveConfigCard)
    const options = wrapper.findAll('.archive-mode-option')
    expect(options).toHaveLength(3)
    expect(options[0].text()).toContain('关闭归档')
    expect(options[1].text()).toContain('根据时间归档')
    expect(options[2].text()).toContain('根据空间归档')
  })
  
  it('选择 TIME 模式时应该显示天数输入框', async () => {
    const wrapper = mount(ArchiveConfigCard)
    await wrapper.find('[data-mode="TIME"]').trigger('click')
    expect(wrapper.find('.archive-days-input').isVisible()).toBe(true)
    expect(wrapper.find('.space-threshold-input').exists()).toBe(false)
  })
  
  it('选择 SPACE 模式时应该显示空间阈值输入框', async () => {
    const wrapper = mount(ArchiveConfigCard)
    
    await wrapper.find('[data-mode="SPACE"]').trigger('click')
    expect(wrapper.find('.space-threshold-input').isVisible()).toBe(true)
    expect(wrapper.find('.archive-days-input').exists()).toBe(false)
  })
  
  it('选择 DISABLED 模式时应该隐藏所有配置项', async () => {
    const wrapper = mount(ArchiveConfigCard)
    await wrapper.find('[data-mode="DISABLED"]').trigger('click')
    expect(wrapper.find('.archive-days-input').exists()).toBe(false)
    expect(wrapper.find('.space-threshold-input').exists()).toBe(false)
  })
})
```

### 8.5 测试配置

#### 8.5.1 属性测试配置

所有属性测试应该配置为：
- 最小迭代次数：100 次
- 随机种子：可配置（用于重现失败）
- 超时时间：每个测试 5 分钟

#### 8.5.2 测试标签

使用标签组织测试：

```kotlin
@Tag("unit")
@Tag("config")
class ConfigValidationTest { ... }

@Tag("property")
@Tag("sync-mode")
class SyncModePropertyTest { ... }

@Tag("integration")
@Tag("archive-mode")
class ArchiveModeIntegrationTest { ... }
```


## 9. 实现注意事项

### 9.1 向后兼容性

#### 9.1.1 配置迁移时机

- 在应用启动时自动执行配置迁移
- 使用 `@PostConstruct` 或类似机制触发迁移
- 迁移应该是幂等的，可以安全地多次执行

#### 9.1.2 enableArchive 字段保留

- 保留 `enableArchive` 字段以保持向后兼容
- 在保存配置时，自动同步 `enableArchive` 和 `archiveMode`：
  - 如果 `archiveMode == DISABLED`，设置 `enableArchive = false`
  - 如果 `archiveMode != DISABLED`，设置 `enableArchive = true`

#### 9.1.3 API 兼容性

- 现有的 API 端点不需要修改
- 前端发送的配置对象会自动包含新字段
- 旧版本的前端发送的配置会使用默认值

### 9.2 性能考虑

#### 9.2.1 同步性能

- `ADD_ONLY` 模式比 `SYNC_ALL_CHANGES` 模式更快（跳过删除和修改检测）
- 对于大量文件的场景，建议使用 `ADD_ONLY` 模式

#### 9.2.2 归档性能

- `DISABLED` 模式应该立即返回，不执行任何操作
- 避免在 `DISABLED` 模式下进行不必要的数据库查询

### 9.3 用户体验

#### 9.3.1 配置界面布局

建议的界面布局：

```
┌─────────────────────────────────────┐
│ 同步配置                             │
├─────────────────────────────────────┤
│ □ 启用同步                           │
│                                     │
│ 同步模式：                           │
│ ○ 仅新增（推荐）                     │
│   只下载云端新增的文件               │
│ ○ 同步所有变化                       │
│   同步新增、修改、删除               │
│                                     │
│ 同步文件夹：[sync        ]          │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ 归档配置                             │
├─────────────────────────────────────┤
│ 归档模式：                           │
│ ○ 关闭归档                           │
│   不执行任何归档操作                 │
│ ○ 根据时间归档                       │
│   保留天数：[30] 天                  │
│ ○ 根据空间归档                       │
│   空间阈值：[90] %                   │
│                                     │
│ 归档文件夹：[backup      ]          │
│ □ 归档后删除云端                     │
│ □ 归档前需要确认                     │
└─────────────────────────────────────┘
```

#### 9.3.2 帮助文本

为每个选项提供清晰的帮助文本：

- **仅新增**：适合希望保留本地所有文件的用户。即使云端删除了照片，本地也会保留。
- **同步所有变化**：适合希望本地与云端完全一致的用户。云端的删除和修改会同步到本地。
- **关闭归档**：不执行任何归档操作，所有照片保留在同步文件夹中。
- **根据时间归档**：自动归档超过指定天数的照片，释放同步文件夹空间。
- **根据空间归档**：当云端空间不足时，自动归档旧照片，直到空间使用率低于阈值。

### 9.4 日志记录

#### 9.4.1 关键操作日志

记录以下关键操作：

```kotlin
// 同步操作
logger.info("Starting sync for crontab ${crontabId}, mode: ${config.syncMode}")
logger.info("Sync completed: added=${addedCount}, deleted=${deletedCount}, updated=${updatedCount}")

// 归档操作
logger.info("Starting archive for crontab ${crontabId}, mode: ${config.archiveMode}")
logger.info("Archive completed: archived=${archivedCount}, freed=${freedSpace} bytes")

// 配置迁移
logger.info("Migrating config for crontab ${crontabId}: syncMode=${oldMode} -> ${newMode}")
```

#### 9.4.2 错误日志

记录详细的错误信息：

```kotlin
logger.error("Sync failed for crontab ${crontabId}", exception)
logger.error("Archive failed for crontab ${crontabId}: ${exception.message}")
logger.error("Config validation failed: ${validationResult.errorMessage}")
```

### 9.5 代码组织

#### 9.5.1 文件结构

```
server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/
├── model/
│   ├── CrontabConfig.kt (扩展)
│   ├── SyncMode.kt (新增)
│   └── ArchiveMode.kt (扩展)
├── service/
│   ├── SyncService.kt (增强)
│   ├── ArchiveService.kt (增强)
│   └── ConfigMigrationService.kt (新增)
└── validation/
    └── ConfigValidator.kt (新增)
```

#### 9.5.2 依赖注入

```kotlin
@Component
class SyncServiceImpl(
    private val crontabRepository: CrontabRepository,
    private val assetRepository: AssetRepository,
    private val fileService: FileService
) : SyncService {
    // ...
}

@Component
class ConfigMigrationService(
    private val crontabRepository: CrontabRepository
) {
    @PostConstruct
    fun init() {
        migrateConfigs()
    }
}
```

## 10. 部署和运维

### 10.1 部署步骤

1. **数据库备份**：升级前备份 SQLite 数据库
2. **应用升级**：部署新版本应用
3. **自动迁移**：应用启动时自动执行配置迁移
4. **验证迁移**：检查日志确认迁移成功
5. **功能测试**：测试同步和归档功能

### 10.2 回滚计划

如果升级出现问题：

1. **停止应用**
2. **恢复数据库备份**
3. **回滚到旧版本应用**
4. **验证功能正常**

### 10.3 监控指标

新增监控指标：

- 不同同步模式的使用率
- 不同归档模式的使用率
- 配置迁移成功率
- 配置验证失败率

## 11. 未来扩展

### 11.1 短期扩展（1-3 个月）

1. **自定义同步规则**：允许用户配置更细粒度的同步规则（如只同步特定类型的文件）
2. **归档策略组合**：支持同时使用时间和空间阈值（满足任一条件即归档）
3. **归档预览增强**：在归档前显示更详细的预览信息（文件列表、大小统计）

### 11.2 中期扩展（3-6 个月）

1. **增量同步优化**：对于 `SYNC_ALL_CHANGES` 模式，优化变化检测算法
2. **归档策略模板**：提供预设的归档策略模板（如"保守"、"平衡"、"激进"）
3. **同步冲突处理**：处理本地修改与云端修改的冲突

### 11.3 长期扩展（6+ 个月）

1. **智能归档**：基于照片重要性（浏览次数、标签等）的智能归档
2. **多级归档**：支持多级归档策略（如 sync → archive1 → archive2）
3. **归档到外部存储**：支持归档到其他云存储服务

## 12. 总结

本设计文档详细描述了灵活的同步与归档模式配置功能的技术实现方案。主要特点包括：

1. **灵活的同步模式**：支持"仅新增"和"同步所有变化"两种模式，满足不同用户需求
2. **扩展的归档模式**：新增"关闭归档"选项，与现有的时间和空间模式共存
3. **向后兼容**：通过自动配置迁移确保现有用户平滑升级
4. **配置遵循计划**：所有操作严格遵循 Crontab 中的配置
5. **完善的验证**：提供全面的配置验证，防止无效配置
6. **全面的测试**：包括单元测试、属性测试、集成测试和前端测试

通过遵循本设计文档，可以在 spec 2 的基础上实现一个更加灵活、用户友好的同步和归档配置系统。
