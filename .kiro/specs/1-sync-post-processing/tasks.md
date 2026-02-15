# 实现计划：同步后处理

## 概述

本实现计划将为 SyncService 添加完整的后处理支持，包括 SHA1 校验、EXIF 时间填充和文件系统时间更新。实现将复用现有的 Stage 组件，并通过新的 PostProcessingCoordinator 进行编排。

## 任务

- [ ] 1. 为现有 Stage 添加独立的处理方法
  - [x] 1.1 在 VerificationStage 中添加 verifySha1 方法
    - 提取 SHA1 校验的核心逻辑到独立方法
    - 方法签名：`fun verifySha1(asset: Asset, filePath: Path): Boolean`
    - 保持原有的 `process(context: CrontabHistoryDetail)` 方法不变
    - _需求：1.1, 1.2, 1.3, 4.5_
  
  - [ ]* 1.2 为 VerificationStage.verifySha1 编写属性测试
    - **属性 2：SHA1 校验正确性**
    - **验证：需求 1.1, 1.2, 1.3**
  
  - [x] 1.3 在 ExifProcessingStage 中添加 fillExifTime 方法
    - 提取 EXIF 时间填充的核心逻辑到独立方法
    - 方法签名：`fun fillExifTime(asset: Asset, filePath: Path, systemConfig: SystemConfig, timeZone: String?)`
    - 处理时区为 null 的情况
    - 捕获 "Not a valid JPG" 异常并记录警告
    - _需求：2.1, 2.2, 2.3, 2.6, 4.5_
  
  - [ ]* 1.4 为 ExifProcessingStage.fillExifTime 编写属性测试
    - **属性 4：EXIF 时间填充正确性**
    - **属性 5：EXIF 时间填充错误容忍**
    - **验证：需求 2.1, 2.2, 2.3**
  
  - [x] 1.5 在 FileTimeStage 中添加 updateFileSystemTime 方法
    - 提取文件系统时间更新的核心逻辑到独立方法
    - 方法签名：`fun updateFileSystemTime(asset: Asset, filePath: Path)`
    - _需求：3.1, 3.2, 4.5_
  
  - [ ]* 1.6 为 FileTimeStage.updateFileSystemTime 编写属性测试
    - **属性 6：文件系统时间更新正确性**
    - **验证：需求 3.1, 3.2**

- [ ] 2. 创建 PostProcessingCoordinator
  - [x] 2.1 创建 PostProcessingResult 数据类
    - 定义后处理结果的数据结构
    - 包含字段：success, errorMessage, sha1Verified, exifFilled, fsTimeUpdated
    - _需求：4.2, 4.4_
  
  - [x] 2.2 实现 PostProcessingCoordinator 类
    - 注入 VerificationStage、ExifProcessingStage、FileTimeStage 和 SystemConfigService
    - 实现 `process(asset: Asset, filePath: Path, config: CrontabConfig): PostProcessingResult` 方法
    - 按顺序调用各个 Stage 的处理方法
    - 根据配置决定是否执行每个步骤
    - 捕获每个步骤的异常并记录
    - _需求：1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 3.1, 3.2, 3.5, 4.1, 4.2, 4.4, 5.1, 5.2, 5.3, 5.4_
  
  - [ ]* 2.3 为 PostProcessingCoordinator 编写单元测试
    - 测试各种配置组合
    - 测试错误处理逻辑
    - 测试步骤执行顺序
    - _需求：4.1, 4.2, 5.1, 5.2, 5.3_
  
  - [ ]* 2.4 为 PostProcessingCoordinator 编写属性测试
    - **属性 1：配置驱动的后处理执行**
    - **属性 7：后处理步骤执行顺序**
    - **验证：需求 1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 3.1, 3.2, 3.5, 4.1, 5.1, 5.2, 5.3**

- [ ] 3. 集成后处理到 SyncService
  - [x] 3.1 在 SyncService 中注入 PostProcessingCoordinator
    - 添加 PostProcessingCoordinator 依赖
    - 使用 `@Inject` 注解进行依赖注入
    - _需求：4.5_
  
  - [x] 3.2 修改 executeSync 方法中的 ADD 操作处理
    - 在 `downloadToSync` 之后调用 `postProcessingCoordinator.process`
    - 根据 PostProcessingResult 更新 recordSyncDetail 的调用
    - 使用 try-catch 确保单个文件失败不影响其他文件
    - _需求：1.1, 2.1, 3.1, 4.2, 4.3, 4.4_
  
  - [x] 3.3 修改 executeSync 方法中的 UPDATE 操作处理
    - 在 `downloadToSync` 之后调用 `postProcessingCoordinator.process`
    - 根据 PostProcessingResult 更新 recordSyncDetail 的调用
    - 使用 try-catch 确保单个文件失败不影响其他文件
    - _需求：1.2, 2.2, 3.2, 4.2, 4.3, 4.4_
  
  - [x] 3.4 确认 DELETE 操作不执行后处理
    - 验证 DELETE 操作的代码路径不调用后处理
    - 添加注释说明 DELETE 操作跳过后处理的原因
    - _需求：7.1, 7.2, 7.3_
  
  - [ ]* 3.5 为 SyncService 的后处理集成编写属性测试
    - **属性 8：后处理失败隔离**
    - **属性 9：后处理成功标记**
    - **属性 10：后处理失败记录**
    - **属性 12：DELETE 操作跳过后处理**
    - **属性 13：DELETE 操作状态记录**
    - **验证：需求 4.2, 4.3, 4.4, 7.1, 7.2, 7.3**

- [ ] 4. 检查点 - 确保所有测试通过
  - 运行所有单元测试和属性测试
  - 确认编译无错误
  - 如有问题，请向用户询问

- [ ] 5. 添加日志记录
  - [x] 5.1 在 PostProcessingCoordinator 中添加日志
    - 在每个步骤开始时记录 INFO 日志
    - 在每个步骤成功时记录 INFO 日志
    - 在每个步骤失败时记录 ERROR 日志（包含异常堆栈）
    - 在 EXIF 填充遇到非 JPG 文件时记录 WARN 日志
    - _需求：6.1, 6.2, 6.3, 6.4_
  
  - [ ]* 5.2 为日志记录编写属性测试
    - **属性 14：日志记录完整性**
    - **验证：需求 6.1, 6.2, 6.3**

- [ ] 6. （可选）扩展 SyncRecordDetail 模型
  - [ ] 6.1 在 SyncRecordDetail 接口中添加后处理状态字段
    - 添加 `val sha1Verified: Boolean?` 字段
    - 添加 `val exifFilled: Boolean?` 字段
    - 添加 `val fsTimeUpdated: Boolean?` 字段
    - 这些字段为可选，用于更详细的状态追踪
    - _需求：4.2_
  
  - [ ] 6.2 创建数据库迁移脚本
    - 创建 `V0.14.0__sync_post_processing.sql`
    - 添加三个新字段到 sync_record_detail 表
    - 字段类型为 BOOLEAN，默认值为 NULL
    - _需求：4.2_
  
  - [ ] 6.3 更新 recordSyncDetail 方法
    - 如果扩展了模型，更新方法以记录详细的后处理状态
    - 从 PostProcessingResult 中提取状态信息
    - _需求：4.2_

- [ ] 7. 集成测试
  - [ ]* 7.1 编写端到端同步测试
    - 模拟完整的同步流程（ADD、UPDATE、DELETE）
    - 验证后处理步骤的正确执行
    - 验证 SyncRecord 和 SyncRecordDetail 的记录
    - _需求：1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 7.1, 7.2_
  
  - [ ]* 7.2 编写配置变更测试
    - 测试不同配置下的同步行为
    - 验证配置变更后的正确性
    - _需求：5.1, 5.2, 5.3, 5.4_
  
  - [ ]* 7.3 编写错误场景测试
    - 模拟 SHA1 校验失败
    - 模拟 EXIF 填充失败
    - 模拟文件时间更新失败
    - 验证错误处理和恢复机制
    - _需求：1.3, 2.3, 3.3, 4.2, 4.3_

- [ ] 8. 最终检查点 - 确保所有测试通过
  - 运行所有测试（单元测试、属性测试、集成测试）
  - 执行 `./gradlew clean solonJar` 确保编译通过
  - 如有问题，请向用户询问

## 注意事项

- 任务标记 `*` 的为可选的测试任务，可以根据时间和需求选择性实施
- 每个任务都引用了相关的需求编号，便于追溯
- 属性测试应当配置为至少运行 100 次迭代
- 所有代码注释应使用中文
- 确保向后兼容，不影响现有的全量下载功能
