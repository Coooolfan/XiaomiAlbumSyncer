# 小米云 Web API 增量同步接口调研

调研日期：2026-09-21

小米云 Web API（`i.mi.com`）提供记录级增量拉取能力，可替代现有的时间线 diff 方案实现真增量同步。接口鉴权复用现有 Web session（cookie），无需额外认证机制。

## 结论

- `/gallery/allitems` 提供相册级增量拉取流：水位游标 + 分页 + 追平标记，记录粒度到单条资产；
- `/gallery/album/full` 一次返回全部相册及其当前水位（`incrementalTag`），充当库级变更预检；
- `/gallery/deleted_v2` 提供库级删除记录流：被删资产以完整记录 + `deleteTime` 形式持久保留在回收站，按 `maxTag` 游标倒序翻页；
- `allitems` 流中删除墓碑仅短暂可见（删除后窗口期内出现 `status:"deleted"` 记录，随后被压实移除），可靠的删除数据源是 `deleted_v2`；
- 本项目定位为归档，云端删除不向本地传导，`deleted_v2` 仅作能力备查，不纳入同步流程；
- 录音不在相册体系中，不参与本增量流，继续走现有接口。

## 接口定义

### GET /gallery/album/full —— 相册枚举与水位预检

每次返回全量相册快照，无分页、无游标参数。覆盖系统相册、用户相册与隐藏相册。

`data.albums[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `albumId` | number/string | 相册服务端 ID，即 `allitems` 的 `groupId` |
| `name` | string | 相册名（系统相册为英文标识） |
| `incrementalTag` | string | 相册内容水位头，任何新增/修改/删除都会推进 |
| `fullBuildTag` | string | 相册建立时的基线水位 |
| `totalImageCount` | number | 当前资产总数 |
| `totalSize` | number | 总字节数 |
| `status` | number | 相册状态（正常为 1） |
| `lastUpdateTime` | number | 毫秒时间戳 |
| `createTime` / `userId` / `description` / `thumbnailUrls` | — | 元数据 |

用途：每轮同步执行一次，与本地持久化水位比对，筛出发生变化的相册；快照 diff 检测相册新建与删除。

### GET /gallery/allitems —— 相册增量拉取流

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `groupId` | 是 | 相册 ID（`albumId`），按相册隔离 |
| `tag` | 是 | 水位下界（独占）。`0` = 从头部全量回放 |
| `limit` | 否 | 每页条数，服务端实际上限约 60 |
| `simpleResult` | 否 | `true` 时每条仅返回 `fileName`/`id`/`tag`，用于轻量探测 |
| `ts` | 否 | 毫秒时间戳（防缓存） |

响应 `data`：

| 字段 | 说明 |
|---|---|
| `content` | 资产记录数组，按 `tag` 升序 |
| `syncTag` | 续拉位点，作为下一页的 `tag` 参数回传 |
| `lastPage` | `true` 表示已追平水位 |
| `syncIgnoreTag` | 服务端水位头（语义未完全确认，疑似本轮读取的一致快照上界） |
| `group` | 相册信息（通常为空数组） |

`content[]` 条目字段（`simpleResult=false`）：

`id`（服务端资产 ID）、`tag`（记录水位）、`status`（观测值为 `"custom"`）、`type`（`image`/`video`）、`fileName`、`title`、`mimeType`、`sha1`、`size`、`groupId`、`sortTime`、`dateTaken`、`dateModified`、`createTime`（毫秒）、`isFavorite`、`isFrontCamera`、`isUbiImage`、`description`（JSON 字符串，含 `isFavorite`/`specialTypeFlags`）、`exifInfo`（`imageWidth`/`imageLength`/`orientation` 等）、`thumbnailInfo`/`bigThumbnailInfo`（`{data: 签名URL, isUrl}`）。

字段与现有 `Asset` 模型一致，可直接映射。

### 水位语义

- `tag` 为全局单调递增序列，跨相册交错（同一底层变更序列按相册过滤投影）；
- `tag=N` 返回 `tag > N` 的记录；`tag=0` 返回相册全量；
- 每次响应的 `syncTag` 为下一页起点；当 `tag` 已超过当前头部时，返回空 `content`、`lastPage=true`，`syncTag` 为头部水位；
- 幂等：每条记录携带 `tag`，本地可存为 per-asset 水位，重复拉取时 `本地tag >= 流入tag` 跳过；
- 分页过程中每页可持久化水位，进程中断后从已提交位点继续。

### GET /gallery/deleted_v2 —— 删除记录流（回收站）

回收站（软删除）资产的独立枚举接口，删除的资产在此持久可见。

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `maxTag` | 是 | 倒序翻页游标。`0` = 从最新开始；后续页传上一响应返回的游标值 |
| `limit` | 否 | 每页条数（前端使用 50） |
| `_dc` / `ts` | 否 | 毫秒时间戳（防缓存） |

响应 `data`：`{content[], lastPage, syncTag}`。

`content[]` 条目与 `allitems` 同 schema，额外包含 `deleteTime`（删除时间，毫秒），`status` 固定为 `"deleted"`，每条含 `groupId` 标识来源相册。

### 已知限制

- **墓碑双轨**：删除事件在 `allitems` 中仅短暂可见，可靠数据源是 `deleted_v2`；`deleted_v2` 覆盖回收站（软删除），永久清除（purged）是否产生记录未验证；
- **无全库单流**：资产增量必须按相册遍历；`deleted_v2` 为库级单流，删除拉取无需按相册拆分；
- 次级游标参数（`syncInfo`/`syncExtraInfo` 等）不被 `allitems` 接受；
- 相册 `status` 的枚举含义未完全确认（正常=1），删除中的相册表现为从列表消失还是状态置位未验证。

## 删除语义

归档定位下云端删除不向本地传导——云端被删的资产在本地归档中保留，`deleted_v2` 仅作能力备查，不纳入同步流程。

`allitems` 流中偶现的 `status:"deleted"` 瞬态记录在应用侧跳过（该记录资产已不在云端，不应进入下载队列；若此前已归档则本地文件保留）。

如未来需要镜像模式（云端删除同步删除本地），`deleted_v2` 可直接提供被删资产 `id` 列表。

## 与现有 timeline-diff 方案对比

| 维度 | 现有实现（`refreshAssetsByDiffTimeline`） | Web 增量流 |
|---|---|---|
| 预检成本 | 每个相册一次 timeline 请求 | 一次 `album/full` 覆盖全部相册 |
| 增量粒度 | 天级（count 变化的天整体重拉） | 记录级（仅变更条目） |
| 删除感知 | 无（只处理 count 增加的天） | 归档场景不需要；`deleted_v2` 可提供删除记录备查 |
| 盲区 | 同日删一加一、纯元数据修改会漏 | 水位推进覆盖全部变更类型 |
| 相册列表变化 | 整体降级为全量刷新 | 仅新相册 `tag=0`，其余相册不受影响 |
| 中断恢复 | 任务级重跑 | 页级水位持久化，断点续拉 |
| 全量/增量关系 | 两条独立路径 + 可用性判断 | 同一流路径，全量 = 位点为 0 的增量 |

## 接入方案

全量与增量为同一条路径：位点为空即全量，位点存在即增量。

### 切换方式

定时任务通过 `syncMode` 字段选择元数据刷新模式，三态共存：

- `FULL`：全量刷新（原有路径）；
- `TIMELINE`：时间线比对（原有路径，失败时降级全量）；
- `CURSOR`：位点流（本方案）。

存量任务由迁移脚本按旧 `config.diffByTimeline` 值回填；`timelineSnapshot` 字段保留兼容旧数据，CURSOR 模式的运行不再写入。

### 位点存储

位点存于 `CrontabHistory` 的序列化字段 `albumSyncCursors`（`Map<albumId, syncTag>`）。每个值都是 `allitems` 返回的续拉位点，用于预检和断点续拉。

行为约定：

- 运行在创建 `CrontabHistory` 后即开始页级提交；每页先 UPSERT 资产，再更新位点，重试时重复资产按 ID 覆盖；
- 基线查询取本任务最近一次含位点的历史（不限 `endTime`，过滤 `albumSyncCursors is not null`）——崩溃任务的位点即恢复点，实现断点续拉；
- 时间线模式的基线查询维持 `endTime ne null` 过滤，按同步模式条件添加；
- 「清理下载历史」连带清空位点，下次运行自然回退全量回放，与现有按钮语义一致；
- 每条历史自带「本轮结束位点」快照，审计内置。

### 每轮同步流程

1. 请求 `album/full`，逐个比较相册的 `incrementalTag` 与本地 `syncTag`；相等时跳过该相册；
2. 对每个需要更新的相册，以本地 `syncTag` 为起点循环调用 `allitems`（`simpleResult=false`），对 `content` 中 `status` 为 `"custom"` 的记录按 `id` UPSERT（不记录 per-asset 位点），每页提交后更新 `albumSyncCursors`，直至 `lastPage`；
   - 新相册 / 位点缺失：起点为 `0`，即全量回放；
   - 位点被服务端拒绝（失效/参数错误）：清空该相册位点，本轮内以 `tag=0` 自动重置重放，不中断运行；
3. 各相册流顺序拉取，不并发；
4. 全部相册追到 `lastPage` 后置位完成标记，进入下游流水线；下载、校验、EXIF、文件时间等阶段不变。

### 边界

- 录音相册（`remoteId=-1`）不在相册体系中，保持现有全量路径；
- 共享相册在 `album/full` 中的覆盖情况未验证（测试账号无共享相册），共享相册维持现有接口路径；
- `Asset` 不新增 `remoteTag` 字段，重复记录按 `id` UPSERT 覆盖。
