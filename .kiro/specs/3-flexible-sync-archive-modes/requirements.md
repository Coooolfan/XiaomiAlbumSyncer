# 需求文档：灵活的同步与归档模式配置

## 1. 引言

本文档描述了对 XiaomiAlbumSyncer 云端同步与智能归档功能的扩展需求。该扩展旨在为用户提供更灵活的同步模式和归档模式选项，以满足不同的使用场景。

## 2. 术语表

- **System**：XiaomiAlbumSyncer 系统
- **Crontab**：定时任务配置
- **CrontabConfig**：定时任务的配置对象
- **SyncMode**：同步模式枚举（ADD_ONLY 或 SYNC_ALL_CHANGES）
- **ArchiveMode**：归档模式枚举（DISABLED、TIME 或 SPACE）
- **CloudAsset**：云端照片或视频资产
- **LocalAsset**：本地照片或视频资产
- **SyncFolder**：与云端保持同步的本地文件夹
- **BackupFolder**：长期归档的本地文件夹

## 3. 需求

### 需求 1：同步模式配置

**用户故事**：作为用户，我希望能够选择不同的同步模式，以便根据我的需求控制本地文件夹与云端的同步行为。

#### 验收标准

1. WHEN 用户配置 Crontab 时，THE System SHALL 提供同步模式选择选项
2. THE System SHALL 支持"仅新增"同步模式（ADD_ONLY）
3. THE System SHALL 支持"同步所有变化"同步模式（SYNC_ALL_CHANGES）
4. WHEN 同步模式设置为 ADD_ONLY 时，THE System SHALL 仅下载云端新增的文件到本地
5. WHEN 同步模式设置为 SYNC_ALL_CHANGES 时，THE System SHALL 同步云端的新增、修改和删除操作到本地

### 需求 2：归档模式扩展

**用户故事**：作为用户，我希望能够选择不同的归档模式，包括关闭归档功能，以便更灵活地管理我的照片归档策略。

#### 验收标准

1. WHEN 用户配置 Crontab 时，THE System SHALL 提供归档模式选择选项
2. THE System SHALL 支持"关闭归档"模式（DISABLED）
3. THE System SHALL 支持"根据时间归档"模式（TIME）
4. THE System SHALL 支持"根据空间归档"模式（SPACE）
5. WHEN 归档模式设置为 DISABLED 时，THE System SHALL 不执行任何归档操作

### 需求 3：时间归档配置

**用户故事**：作为用户，当我选择根据时间归档时，我希望能够配置保留天数，以便控制哪些照片会被归档。

#### 验收标准

1. WHEN 归档模式设置为 TIME 时，THE System SHALL 显示天数配置选项
2. THE System SHALL 允许用户输入保留天数（正整数）
3. WHEN 执行归档时，THE System SHALL 归档早于（当前日期 - 保留天数）的照片
4. WHEN 归档模式不是 TIME 时，THE System SHALL 隐藏天数配置选项

### 需求 4：空间归档配置

**用户故事**：作为用户，当我选择根据空间归档时，我希望能够配置云端空间阈值，以便自动释放云端空间。

#### 验收标准

1. WHEN 归档模式设置为 SPACE 时，THE System SHALL 显示空间阈值配置选项
2. THE System SHALL 允许用户输入空间阈值百分比（1-100 之间的整数）
3. WHEN 执行归档时，THE System SHALL 按时间从旧到新归档照片，直到云端空间使用率低于阈值
4. WHEN 归档模式不是 SPACE 时，THE System SHALL 隐藏空间阈值配置选项

### 需求 5：配置遵循计划约束

**用户故事**：作为用户，我希望同步和归档操作严格遵循我在计划中配置的账号、相册和路径设置，以确保操作的准确性和安全性。

#### 验收标准

1. WHEN 执行同步操作时，THE System SHALL 仅同步 Crontab 中指定的账号
2. WHEN 执行同步操作时，THE System SHALL 仅同步 Crontab 中选择的相册
3. WHEN 执行同步操作时，THE System SHALL 将文件下载到 Crontab 中配置的路径
4. WHEN 执行归档操作时，THE System SHALL 仅归档 Crontab 中指定账号的照片
5. WHEN 执行归档操作时，THE System SHALL 仅归档 Crontab 中选择相册的照片
6. WHEN 执行归档操作时，THE System SHALL 从 Crontab 配置的 syncFolder 移动文件到 backupFolder

### 需求 6：配置持久化

**用户故事**：作为用户，我希望我的同步模式和归档模式配置能够被保存，以便下次使用时不需要重新配置。

#### 验收标准

1. WHEN 用户保存 Crontab 配置时，THE System SHALL 持久化同步模式设置
2. WHEN 用户保存 Crontab 配置时，THE System SHALL 持久化归档模式设置
3. WHEN 用户保存 Crontab 配置时，THE System SHALL 持久化相关的配置参数（天数或空间阈值）
4. WHEN 用户重新打开 Crontab 配置时，THE System SHALL 显示之前保存的配置

### 需求 7：向后兼容性

**用户故事**：作为现有用户，我希望系统升级后我的现有配置仍然有效，不需要重新配置。

#### 验收标准

1. WHEN 系统升级时，THE System SHALL 为现有 Crontab 设置默认的同步模式为 ADD_ONLY
2. WHEN 系统升级时，THE System SHALL 保留现有 Crontab 的归档模式设置
3. WHEN 现有 Crontab 的 enableArchive 为 false 时，THE System SHALL 将归档模式设置为 DISABLED
4. WHEN 现有 Crontab 的 enableArchive 为 true 时，THE System SHALL 保留原有的归档模式（TIME 或 SPACE）

### 需求 8：用户界面清晰性

**用户故事**：作为用户，我希望配置界面清晰易懂，能够快速理解每个选项的含义和影响。

#### 验收标准

1. WHEN 用户查看同步模式选项时，THE System SHALL 显示每个模式的简短描述
2. WHEN 用户查看归档模式选项时，THE System SHALL 显示每个模式的简短描述
3. WHEN 用户选择不同的归档模式时，THE System SHALL 动态显示或隐藏相关的配置项
4. WHEN 用户输入无效的配置值时，THE System SHALL 显示清晰的错误提示

### 需求 9：配置验证

**用户故事**：作为用户，我希望系统能够验证我的配置输入，防止我输入无效的配置导致功能异常。

#### 验收标准

1. WHEN 用户输入保留天数时，THE System SHALL 验证输入为正整数
2. WHEN 用户输入空间阈值时，THE System SHALL 验证输入为 1-100 之间的整数
3. WHEN 用户输入无效值时，THE System SHALL 阻止保存配置
4. WHEN 用户输入无效值时，THE System SHALL 显示具体的错误信息

### 需求 10：同步模式行为差异

**用户故事**：作为用户，我希望清楚了解两种同步模式的具体行为差异，以便选择适合我的模式。

#### 验收标准

1. WHEN 同步模式为 ADD_ONLY 且云端删除了照片时，THE System SHALL 保留本地 syncFolder 中的照片
2. WHEN 同步模式为 ADD_ONLY 且云端修改了照片时，THE System SHALL 保留本地 syncFolder 中的原照片
3. WHEN 同步模式为 SYNC_ALL_CHANGES 且云端删除了照片时，THE System SHALL 从本地 syncFolder 中删除该照片
4. WHEN 同步模式为 SYNC_ALL_CHANGES 且云端修改了照片时，THE System SHALL 更新本地 syncFolder 中的照片

## 4. 非功能性需求

### 需求 11：性能要求

**用户故事**：作为用户，我希望配置更改能够快速生效，不影响系统的正常使用。

#### 验收标准

1. WHEN 用户保存配置时，THE System SHALL 在 1 秒内完成保存操作
2. WHEN 用户切换归档模式时，THE System SHALL 立即更新界面显示
3. WHEN 执行同步或归档操作时，THE System SHALL 使用最新的配置

### 需求 12：数据安全

**用户故事**：作为用户，我希望配置更改不会导致数据丢失或损坏。

#### 验收标准

1. WHEN 用户更改同步模式时，THE System SHALL 不删除或修改已存在的本地文件
2. WHEN 用户更改归档模式时，THE System SHALL 不影响已归档的文件
3. WHEN 配置保存失败时，THE System SHALL 保留原有配置
4. WHEN 数据库更新失败时，THE System SHALL 回滚事务

## 5. 约束条件

1. 新增的配置字段必须添加到现有的 CrontabConfig 数据模型中
2. 必须提供数据库迁移脚本以支持现有数据的平滑升级
3. 前端界面必须在现有的计划配置页面中集成，不创建新的独立页面
4. 所有配置更改必须通过现有的 API 端点进行，或扩展现有端点
5. 必须保持与 spec 2 中实现的同步和归档功能的兼容性

## 6. 验收测试场景

### 场景 1：配置仅新增同步模式

1. 用户创建新的 Crontab
2. 用户选择同步模式为"仅新增"
3. 用户保存配置
4. 云端新增照片 A
5. 执行同步操作
6. 验证：本地 syncFolder 中存在照片 A
7. 云端删除照片 B
8. 执行同步操作
9. 验证：本地 syncFolder 中仍然存在照片 B

### 场景 2：配置同步所有变化模式

1. 用户创建新的 Crontab
2. 用户选择同步模式为"同步所有变化"
3. 用户保存配置
4. 云端新增照片 A
5. 执行同步操作
6. 验证：本地 syncFolder 中存在照片 A
7. 云端删除照片 B
8. 执行同步操作
9. 验证：本地 syncFolder 中不存在照片 B

### 场景 3：配置关闭归档模式

1. 用户创建新的 Crontab
2. 用户选择归档模式为"关闭归档"
3. 用户保存配置
4. 执行归档操作
5. 验证：没有文件被移动到 backupFolder
6. 验证：没有云端照片被删除

### 场景 4：配置根据时间归档

1. 用户创建新的 Crontab
2. 用户选择归档模式为"根据时间归档"
3. 用户输入保留天数为 30
4. 用户保存配置
5. syncFolder 中存在 40 天前的照片 A 和 20 天前的照片 B
6. 执行归档操作
7. 验证：照片 A 被移动到 backupFolder
8. 验证：照片 B 仍在 syncFolder 中

### 场景 5：配置根据空间归档

1. 用户创建新的 Crontab
2. 用户选择归档模式为"根据空间归档"
3. 用户输入空间阈值为 80%
4. 用户保存配置
5. 云端空间使用率为 95%
6. 执行归档操作
7. 验证：旧照片被归档直到云端空间使用率低于 80%

### 场景 6：向后兼容性验证

1. 系统升级前存在一个 Crontab，enableSync = true，enableArchive = true，archiveMode = TIME
2. 执行系统升级
3. 验证：该 Crontab 的 syncMode 被设置为 ADD_ONLY
4. 验证：该 Crontab 的 archiveMode 仍为 TIME
5. 验证：该 Crontab 的其他配置保持不变
6. 执行同步和归档操作
7. 验证：功能正常工作

## 7. 依赖关系

本需求依赖于以下已实现的功能：

- spec 2：云端同步与智能归档功能的基础实现
- CrontabConfig 数据模型
- SyncService 和 ArchiveService
- 前端的 Crontab 配置界面

## 8. 优先级

- P0（必须实现）：需求 1, 2, 3, 4, 5, 6, 7, 9
- P1（应该实现）：需求 8, 10, 11, 12
