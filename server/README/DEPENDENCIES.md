# Server 依赖库清单

本文档列出了 Xiaomi Album Syncer Server 子项目所使用的所有依赖库及其开源协议。

## 运行时依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Solon Web | 3.8.3 | Apache-2.0 | Solon Web 框架 |
| Solon AOT | 3.8.3 | Apache-2.0 | Solon AOT 支持 |
| Solon Logging Logback | 3.8.3 | Apache-2.0 | Logback 日志适配 |
| Sa-Token Solon Plugin | 1.44.0 | Apache-2.0 | Sa-Token 权限认证组件 |
| Solon Serialization Jackson | 3.8.3 | Apache-2.0 | Jackson 序列化支持 |
| OkHttp | 5.3.2 | Apache-2.0 | HTTP 客户端 |
| Jackson Module Kotlin | 2.19.2 | Apache-2.0 | Jackson 的 Kotlin 支持模块 |
| Solon Scheduling Simple | 3.8.3 | Apache-2.0 | 轻量级定时任务支持 |
| Flyway Core | 11.20.1 | Apache-2.0 | 数据库迁移工具 |
| Kotlinx Coroutines Core | 1.10.2 | Apache-2.0 | Kotlin 协程支持 |
| Jimmer Client | 0.9.120 | Apache-2.0 | Jimmer 客户端模型支持 |
| Jimmer SQL Kotlin | 0.9.120 | Apache-2.0 | Jimmer ORM 框架（Kotlin） |
| WebAuthn4J Core | 0.30.2.RELEASE | Apache-2.0 | WebAuthn 认证支持 |
| HikariCP | 7.0.2 | Apache-2.0 | JDBC 连接池 |
| SQLite JDBC | 3.51.1.0 | Apache-2.0 | SQLite 数据库驱动 |

## 构建工具与插件

| 插件/工具 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Kotlin JVM | 2.3.0 | Apache-2.0 | Kotlin 编译插件 |
| KSP | 2.3.4 | Apache-2.0 | Kotlin 符号处理器 |
| Ben Manes Versions | 0.53.0 | Apache-2.0 | 依赖版本检查插件 |
| GraalVM Native Build Tools | 0.11.3 | UPL-1.0 | GraalVM 原生镜像构建工具 |
| Solon Native Gradle Plugin | - | Apache-2.0 | 项目内置的 Solon Native 构建插件（buildSrc） |
| ASM | 9.5 | BSD-3-Clause | 字节码操作库（buildSrc 依赖） |

## 测试依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|---|---|---|---|
| Solon Test | 3.8.3 | Apache-2.0 | Solon 测试支持 |
| JUnit Jupiter API | 5.11.4 | EPL-2.0 | JUnit 5 测试 API |
| JUnit Jupiter Engine | 5.11.4 | EPL-2.0 | JUnit 5 运行引擎 |
| JUnit Platform Launcher | 1.11.4 | EPL-2.0 | JUnit 平台启动器 |

## 协议说明

- **Apache-2.0**: Apache License 2.0 - 允许商业使用、修改、分发，需保留版权和许可声明
- **BSD-3-Clause**: BSD 3-Clause License - 允许商业使用、修改、分发，需保留版权声明
- **UPL-1.0**: Universal Permissive License 1.0 - Oracle 通用许可协议，与 MIT 兼容
- **EPL-2.0**: Eclipse Public License 2.0 - 允许商业使用，修改需开源
