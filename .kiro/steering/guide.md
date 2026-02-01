---
inclusion: always
---

# XiaomiAlbumSyncer 项目开发规范

## 项目架构

### 技术栈
- **后端**: Kotlin + Solon 框架 + Jimmer ORM + SQLite
- **前端**: Vue 3 + TypeScript + Vite + PrimeVue + Tailwind CSS
- **API 交互**: OkHttp 5
- **任务调度**: Cron 表达式

### 目录结构
```
server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/
├── config/         # 全局配置（数据库、Web、调度器、线程池）
├── controller/     # API 接口层（处理 HTTP 请求）
├── model/          # Jimmer 实体定义（数据库表结构）
├── service/        # 业务逻辑层
├── pipeline/       # 流水线处理（下载、校验、EXIF、文件时间）
├── utils/          # 工具类
└── xiaomicloud/    # 小米云 API 交互层
```

## 开发规范

### 代码风格
1. **Kotlin 代码**
   - 使用 Kotlin 惯用语法
   - 优先使用不可变变量（val）
   - 使用数据类（data class）表示数据模型
   - 合理使用协程处理异步操作

2. **命名规范**
   - 类名：大驼峰（PascalCase）
   - 函数/变量：小驼峰（camelCase）
   - 常量：全大写下划线分隔（UPPER_SNAKE_CASE）
   - 数据库表/字段：小写下划线分隔（snake_case）

3. **注释规范**
   - 使用中文注释
   - 类和公共方法必须有 KDoc 注释
   - 复杂逻辑必须添加行内注释说明

### 数据库设计
1. **使用 Jimmer ORM**
   - 实体类放在 `model/` 目录
   - 使用 `@Entity` 注解定义实体
   - 使用 `@OnDissociate` 处理级联删除
   - 使用 `SaveMode.UPSERT` 处理插入或更新

2. **数据库迁移**
   - 使用 Flyway 管理数据库版本
   - 迁移脚本放在 `resources/db/migration/`
   - 命名格式：`V{版本号}__{描述}.sql`

### API 设计
1. **RESTful 风格**
   - GET：查询数据
   - POST：创建数据
   - PUT：更新数据
   - DELETE：删除数据

2. **接口规范**
   - 使用 `@Api` 注解标记接口
   - 使用 `@SaCheckLogin` 进行鉴权
   - 返回统一的响应格式
   - 异常使用 `GlobalHandler` 统一处理

### 业务逻辑
1. **分层架构**
   - Controller 层：只负责参数校验和调用 Service
   - Service 层：实现核心业务逻辑
   - Repository 层：通过 Jimmer 操作数据库

2. **流水线模式**
   - 使用 Kotlin Flow 处理数据流
   - 每个阶段独立处理（下载、校验、EXIF、文件时间）
   - 使用 `flatMapMerge` 实现并发处理
   - 异常捕获避免影响其他任务

### 小米云 API 交互
1. **认证管理**
   - 使用 `TokenManager` 管理 token
   - 自动刷新过期的 token
   - 支持多账号管理

2. **API 调用**
   - 所有 API 调用封装在 `XiaoMiApi` 类中
   - 使用 OkHttp 处理 HTTP 请求
   - 实现重试机制处理网络错误
   - 记录详细的日志便于调试

### 前端开发
1. **Vue 3 组合式 API**
   - 使用 `<script setup>` 语法
   - 使用 Pinia 管理状态
   - 使用 Vue Router 管理路由

2. **UI 组件**
   - 优先使用 PrimeVue 组件
   - 使用 Tailwind CSS 自定义样式
   - 保持响应式设计

### 测试规范
1. **单元测试**
   - 核心业务逻辑必须有单元测试
   - 测试文件命名：`{类名}Test.kt`

2. **集成测试**
   - API 接口需要集成测试
   - 测试数据库操作的正确性

### 日志规范
1. **日志级别**
   - ERROR：错误信息（需要立即处理）
   - WARN：警告信息（需要关注）
   - INFO：重要信息（关键流程节点）
   - DEBUG：调试信息（开发阶段使用）

2. **日志内容**
   - 包含足够的上下文信息
   - 敏感信息需要脱敏
   - 使用结构化日志格式

## 性能优化

### 并发控制
- 使用 Semaphore 控制并发数量
- 下载任务：根据配置动态调整
- API 请求：避免触发限流

### 数据库优化
- 使用分页查询避免一次加载大量数据
- 合理使用索引提升查询性能
- 使用批量操作减少数据库交互

### 文件操作
- 使用流式下载避免内存溢出
- 合理使用缓冲区大小
- 及时关闭文件句柄

## 安全规范

### 数据安全
- 密码使用加密存储
- Token 不记录到日志
- 文件路径校验避免路径遍历

### 接口安全
- 所有接口需要登录认证
- 使用 Sa-Token 管理会话
- 支持 Passkey 无密码登录

## 部署规范

### Docker 部署
- 提供 JVM 和 Native 两种镜像
- 使用多阶段构建减小镜像体积
- 合理设置资源限制

### 配置管理
- 使用环境变量配置敏感信息
- 提供默认配置文件
- 支持配置热更新

## Git 提交规范

### Commit Message 格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型
- feat: 新功能
- fix: Bug 修复
- refactor: 重构
- docs: 文档更新å
- style: 代码格式调整
- test: 测试相关
- chore: 构建、配置等

### 示例
```
feat(sync): 添加云端到本地的同步功能

- 实现文件新增、删除、修改的同步
- 支持增量同步提升性能
- 添加同步状态记录

Closes #123
```

## 文档规范

### 代码文档
- 每个模块提供 README.md
- 复杂算法提供流程图
- API 接口提供使用示例

### 用户文档
- 功能说明清晰易懂
- 提供配置示例
- 常见问题解答

## 注意事项

1. **向后兼容**
   - 数据库变更需要提供迁移脚本
   - API 变更需要保持向后兼容
   - 配置变更需要提供默认值

2. **错误处理**
   - 所有异常必须妥善处理
   - 提供友好的错误提示
   - 记录详细的错误日志

3. **资源管理**
   - 及时释放资源（文件、连接等）
   - 避免内存泄漏
   - 合理使用线程池

4. **国际化**
   - 代码注释使用中文
   - 用户界面支持多语言
   - 日志信息使用中文
