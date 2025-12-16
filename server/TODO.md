# XiaomiAlbumSyncer 并发与流水线改造指南（简要版）

## 1. 项目技术概览

XiaomiAlbumSyncer 是一个用于同步小米云相册到本地的工具，主要技术栈如下：

* **Kotlin**
* **Solon 框架**（Web/IoC/调度）
* **Jimmer ORM**
* **SQLite** 数据库
* 核心逻辑位于：
    * `TaskScheduler.kt`（任务调度）
    * `TaskActuators.kt`（具体业务工作流，如下载、刷新、Exif）

项目当前的任务执行流程结构是**单线程串行执行多个阶段**，在长时间运行的 I/O 操作场景下（小米云 API 下载等），性能受到明显限制。

---

## 2. 现有流程的运行方式

目前的执行过程如下：

1. 定时任务触发 → `TaskScheduler`
2. 调用 `actuators.doWork()` 开始同步
3. 同步逻辑采用如下**串行大循环结构**：
    * 刷新相册资源列表
    * 找出需要下载的文件
    * **逐个文件下载**
    * **全部下载完后再进行**
        * EXIF 更新
        * 文件系统时间更新

---

## 3. 当前架构的主要痛点

### A. 整体串行执行，无法并行化

下载、校验、修改时间等操作全部都是顺序完成，导致：

* 下载速度受网速影响严重，CPU 阶段会处于空闲状态
* 总时间被一个最慢步骤拖累

### B. 线程池缺乏分级调度能力

线程池只有一个（大小为 4），所有任务共享，导致：

* 下载阻塞会影响 EXIF/DB 等其他任务
* 无法根据任务性质（IO/CPU）进行合理调度

### C. 无法优雅处理“校验失败重新下载”

如果加入 SHA1 校验：

* 在串行结构中，校验失败立刻重试会破坏流程逻辑
* 等全量下载完再重试效率极低

---

## 4. 推荐的架构：基于 Channel 的多阶段流水线（SEDA 模型）

为解决下载慢、校验轻、Exif 重等不同任务的性能差异，建议引入**多阶段流水线架构**。

每个步骤拆解为独立“车间”，车间之间通过 `Channel` 来传递任务对象。

### 阶段（Stage）建议

1. **下载器（Downloader）**
    * 特点：IO 密集、最慢
    * 并发数：3–5（取决于带宽/小米云限制）

2. **校验器（Verifier，SHA1）**
    * 特点：CPU+磁盘 IO
    * 并发数：1–2（机械硬盘更低）

3. **Exif/时间处理器（ExifProcessor）**
    * 特点：可能调用第三方工具（开销大）
    * 并发数：1–3

每个阶段为一个协程消费者池，每个 stage 专注处理某一类任务。

---

## 5. 任务数据结构：AssetTask（上下文）

每个文件对应一个上下文（Context），在整个流水线中流转：

```
data class AssetTask(
    val asset: Asset,
    val targetPath: Path,
    var tempFile: Path? = null,
    var retry: Int = 0
)
```

> 极大降低模块之间的耦合，使得每个阶段只处理任务的一小方面。

---

## 6. 流水线调度思想（工作机制）

### A. Pipeline 关键特性

1. 各阶段**完全独立**
2. 并发量可分别配置
3. 通过 Channel 自动实现“背压”，避免过载
4. 校验失败可以毫无阻碍地“踢回下载通道”
5. 整体利用率大幅提升（网络、CPU、磁盘均可各自跑满）

---

## 7. 核心伪代码结构（概念示例）

```
downloadQueue -> verifyQueue -> exifQueue -> 完成
```

下载 → 校验 → exif → 完成，每个环节都可以有不同数量的 worker 协程。

---

## 8. SHA1 校验失败的处理逻辑

这种流水线架构天然适合实现“失败回退”：

* 校验失败 → `downloadQueue.send(task)` 重新入队
* 无需递归调用
* 无需破坏整体执行顺序
* 支持重试次数限制

---

## 9. 改造路线图（给第一次接触项目的人）

### Step 1：熟悉代码结构
重点阅读：

* `TaskScheduler.kt`
* `TaskActuators.kt`

### Step 2：拆分 `TaskActuators` 为多个子模块
例如：

* Downloader
* Sha1Verifier
* ExifProcessor
* FileTimeFixer

### Step 3：引入协程（Coroutine）与 Channel
为每个阶段建立独立 Channel 与 worker 线程数量。

### Step 4：构建 Pipeline 调度器
负责：

* 启动各个 stage
* 控制并发数
* 分发初始化任务（资产列表）
* 管理关闭机制

### Step 5：添加 SHA1 校验功能
将其作为专门 Stage 插入流水线第二步。

### Step 6：测试与调优
重点检测：

* 下载太快时，校验是否背压生效
* 高并发对磁盘的影响
* Exif 工具是否容易被过载
