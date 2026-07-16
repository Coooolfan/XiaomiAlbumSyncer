# For Developers

## development

直接使用 `./gradlew run` 启动即可。

SQLite 默认参数与此前保持一致，可以按部署环境通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `SQLITE_URL` | 空 | 完整 SQLite JDBC URL；非空时优先于以下单项参数 |
| `SQLITE_JOURNAL_MODE` | `WAL` | SQLite journal mode |
| `SQLITE_SYNCHRONOUS` | `NORMAL` | 落盘同步级别 |
| `SQLITE_CACHE_SIZE` | `10000` | page cache；正数单位为 page，负数单位为 KiB |
| `SQLITE_TEMP_STORE` | `memory` | 临时表和索引存储位置 |
| `SQLITE_MMAP_SIZE` | `0` | mmap 上限，单位 byte；默认关闭 mmap |
| `SQLITE_BUSY_TIMEOUT` | `30000` | 等待其他 SQLite 写事务释放锁的最长时间，单位 ms |
| `SQLITE_MAXIMUM_POOL_SIZE` | `4` | Hikari 最大连接数，合法范围 1–64 |

`SQLITE_URL` 未设置或只包含空白时，应用继续使用 `APP_DB_PATH` 和六个单项参数拼接 URL。非空时必须是完整的 `jdbc:sqlite:` URL，主数据库连接将忽略 `APP_DB_PATH` 和单项参数；格式非法会在启动阶段失败。例如：

```shell
SQLITE_URL='jdbc:sqlite:/app/db/xiaomialbumsyncer.db?journal_mode=WAL&synchronous=NORMAL&cache_size=10000&temp_store=memory&mmap_size=0&busy_timeout=30000'
```

URL 可用参数由 sqlite-jdbc 支持范围决定。回退方案中的枚举参数使用 SQLite 支持的名称，不区分大小写；数值参数格式错误，或 `SQLITE_MMAP_SIZE` / `SQLITE_BUSY_TIMEOUT` 为负数时，应用会在启动阶段失败。`SQLITE_MAXIMUM_POOL_SIZE` 是独立的 Hikari 配置，不属于 JDBC URL；即使设置完整 `SQLITE_URL`，它仍然生效。

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

API E2E 会在独立进程中启动真实 Solon 应用，并构建、启动 `../xiaomi-cloud-mock` 有状态模拟服务。测试使用
临时 SQLite 数据库和下载目录，不会访问真实小米云，也不会污染开发数据库。模拟服务把云相册与云录音作为
独立远端资源，`-1` 只保留为应用内部的录音相册归一化标识。

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
资源、录音专用及混合任务、定时任务、文件下载、通知、Passkey 启动流程以及 OpenAPI 资源。运行 E2E 还需
本机安装 Go 1.25 或更高版本，用于构建独立模拟服务。

生产环境仍默认访问 `https://i.mi.com`。只有测试或明确配置时才需要设置：

```shell
XIAOMI_API_BASE_URL=http://127.0.0.1:18080
APP_DB_PATH=/tmp/xiaomi-album-syncer.db
```

需要手工执行真实新增资产、下载限速或内存测试矩阵时，参见 `../xiaomi-cloud-mock/README.md`。模拟服务支持
固定 seed 场景、原子 mutation、reset、下载网络参数和调用统计。

仓库还提供可重复的 JVM 下载内存基准。默认场景会先下载 1000 个 16 MiB 文件，执行一次无变化空任务，
再新增并下载 100 个 16 MiB 文件；并发参数使用生产默认值 `fetch=2 / download=8 / verify=2 / EXIF=2 / fileTime=2`，
完整运行需要约 18 GiB 磁盘空间：

```shell
./gradlew apiMemoryBenchmarkJvm -Pxiaomi.benchmark.keepWorkDir=true
```

报告写入 `build/reports/xiaomi-memory-benchmark`，包括各阶段耗时、100 ms 采样的 JVM RSS 峰值、
JVM 与 ExifTool 等子进程的进程树 RSS 峰值、NMT 摘要、Mock 实际发送字节数和并发下载数。
进程树 RSS 是各进程 RSS 之和，可能重复计算共享页，主要用于和容器总体内存趋势对照。常用矩阵参数包括：

```shell
# 保留 512 MiB 硬上限，只比较较低的 G1 软上限
./gradlew apiMemoryBenchmarkJvm \
  -Pxiaomi.benchmark.maxHeap=512m \
  -Pxiaomi.benchmark.softMaxHeap=192m \
  -Pxiaomi.benchmark.keepWorkDir=true

# 使用 200 个初始文件和 20 个增量文件采集 JFR
./gradlew apiMemoryBenchmarkJvm \
  -Pxiaomi.benchmark.jfr=true \
  -Pxiaomi.benchmark.incrementalCount=20 \
  -Pxiaomi.benchmark.keepWorkDir=true

# 复现默认并发下开启“填充 EXIF 时间”的真实图片处理链路
./gradlew apiMemoryBenchmarkJvm \
  -Pxiaomi.benchmark.scenario=../xiaomi-cloud-mock/scenarios/memory-profile-exif.json \
  -Pxiaomi.benchmark.rewriteExifTime=true \
  -Pxiaomi.benchmark.contentMode=jpeg \
  -Pxiaomi.benchmark.keepWorkDir=true
```

还可以使用 `xiaomi.benchmark.downloaders`、`xiaomi.benchmark.fetchFromDbSize`、
`xiaomi.benchmark.verifiers`、`xiaomi.benchmark.exifProcessors`、`xiaomi.benchmark.fileTimeWorkers`、
`xiaomi.benchmark.incrementalCount`、`xiaomi.benchmark.periodicGcInterval`、`xiaomi.benchmark.rewriteExifTime`、
`xiaomi.benchmark.contentMode` 和 `xiaomi.benchmark.scenario`
覆盖矩阵参数。基准下载目录位于报告目录下，
成功后仅在 `keepWorkDir=true` 时保留。
