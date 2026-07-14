# For Developers

## development

直接使用 `./gradlew run` 启动即可。

SQLite 默认参数与此前保持一致，可以按部署环境通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `SQLITE_JOURNAL_MODE` | `WAL` | SQLite journal mode |
| `SQLITE_SYNCHRONOUS` | `NORMAL` | 落盘同步级别 |
| `SQLITE_CACHE_SIZE` | `10000` | page cache；正数单位为 page，负数单位为 KiB |
| `SQLITE_TEMP_STORE` | `memory` | 临时表和索引存储位置 |
| `SQLITE_MMAP_SIZE` | `268435456` | mmap 上限，单位 byte |

枚举参数使用 SQLite 支持的名称，不区分大小写；数值参数格式错误或 `SQLITE_MMAP_SIZE` 为负数时，应用会在启动阶段失败。

## publishing

### jvm

```bash
./gradlew clean solonJar
```

### native

> 需要安装 GraalVM 并配置环境变量 `JAVA_HOME` 指向 GraalVM 的安装路径

```shell
# 使用 agent 生成配置文件（可选）
# 记得改成你的 jar 路径
java -agentlib:native-image-agent=config-merge-dir=./src/main/resources/META-INF/native-image -jar build/libs/XiaomiAlbumSyncer.jar 
```

```shell
./gradlew clean nativeCompile
```

## API E2E 与 Native Image 元数据

API E2E 会在独立进程中启动真实 Solon 应用，并在测试进程内启动 mock Xiaomi API。测试使用临时 SQLite
数据库和下载目录，不会访问真实小米云，也不会污染开发数据库。

```shell
# 普通 JVM 黑盒 API 测试
./gradlew apiE2eJvm

# 使用 GraalVM tracing agent 执行同一套测试，结果写入 build/native-agent-merged
./gradlew collectNativeMetadata

# 检查提交到源码中的 Native Image 元数据是否遗漏了本次收集结果
./gradlew checkNativeMetadata

# 审核结果后，显式更新 src/main/resources/META-INF/native-image
./gradlew updateNativeMetadata

# 构建 Native Image，并在原生可执行文件上复用同一套 API 测试
./gradlew apiE2eNative
```

`collectNativeMetadata` 与 `apiE2eNative` 需要 GraalVM 25。测试覆盖初始化、登录、系统配置、账号、相册、
资源、定时任务、文件下载、通知、Passkey 启动流程以及 OpenAPI 资源。

生产环境仍默认访问 `https://i.mi.com`。只有测试或明确配置时才需要设置：

```shell
XIAOMI_API_BASE_URL=http://127.0.0.1:18080
APP_DB_PATH=/tmp/xiaomi-album-syncer.db
```
