# Server 依赖库清单

本文档列出了 Xiaomi Album Syncer Server 子项目所使用的直接依赖库及其开源协议。

## 运行时依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Solon Web | 4.0.6 | Apache-2.0 | Solon Web 框架 |
| Solon AOT | 4.0.6 | Apache-2.0 | Solon AOT 支持 |
| Solon Logging Logback | 4.0.6 | Apache-2.0 | Logback 日志适配 |
| Logback Classic | 1.6.3 | EPL-1.0 / LGPL-2.1 | 日志实现及安全版本覆盖 |
| Sa-Token Solon Plugin | 1.46.0 | Apache-2.0 | Sa-Token 权限认证组件 |
| Solon Serialization Jackson | 4.0.6 | Apache-2.0 | Jackson 序列化支持 |
| OkHttp | 5.5.0 | Apache-2.0 | HTTP 客户端 |
| Jackson BOM / Module Kotlin | 2.22.2 | Apache-2.0 | Jackson 组件版本对齐及 Kotlin 支持 |
| Solon Scheduling Simple | 4.0.6 | Apache-2.0 | 轻量级定时任务支持 |
| Flyway Core | 13.5.0 | Apache-2.0 | 数据库迁移工具 |
| Kotlinx Coroutines Core | 1.11.0 | Apache-2.0 | Kotlin 协程支持 |
| Jimmer Client | 0.12.0 | Apache-2.0 | Jimmer 客户端模型支持 |
| Jimmer SQL Kotlin | 0.12.0 | Apache-2.0 | Jimmer ORM 框架（Kotlin） |
| WebAuthn4J Core | 0.31.10.RELEASE | Apache-2.0 | WebAuthn 认证支持 |
| HikariCP | 7.1.0 | Apache-2.0 | JDBC 连接池 |
| SQLite JDBC | 3.53.4.0 | Apache-2.0 | SQLite 数据库驱动 |

## 构建工具与插件

| 插件/工具 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Gradle Wrapper | 9.7.1 | Apache-2.0 | Gradle 构建工具 |
| Kotlin JVM | 2.4.10 | Apache-2.0 | Kotlin 编译插件 |
| KSP | 2.3.11 | Apache-2.0 | Kotlin 符号处理器 |
| Ben Manes Versions | 0.61.0 | Apache-2.0 | 依赖版本检查插件 |
| GraalVM Native Build Tools | 1.1.11 | UPL-1.0 | GraalVM 原生镜像构建工具 |
| Solon Native Gradle Plugin | - | Apache-2.0 | 项目内置的 Solon Native 构建插件（buildSrc） |
| ASM | 9.10.1 | BSD-3-Clause | 字节码操作库（buildSrc 依赖） |

## 测试依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Solon Test | 4.0.6 | Apache-2.0 | Solon 测试支持 |
| JUnit Jupiter API | 6.1.3 | EPL-2.0 | JUnit 测试 API |
| JUnit Jupiter Engine | 6.1.3 | EPL-2.0 | JUnit 运行引擎 |
| JUnit Platform Launcher | 6.1.3 | EPL-2.0 | JUnit 平台启动器 |

## 协议说明

- **Apache-2.0**: Apache License 2.0 - 允许商业使用、修改、分发，需保留版权和许可声明
- **BSD-3-Clause**: BSD 3-Clause License - 允许商业使用、修改、分发，需保留版权声明
- **UPL-1.0**: Universal Permissive License 1.0 - Oracle 通用许可协议，与 MIT 兼容
- **EPL-1.0 / LGPL-2.1**: Logback 双许可证
- **EPL-2.0**: Eclipse Public License 2.0 - 允许商业使用，修改需按许可证要求公开
