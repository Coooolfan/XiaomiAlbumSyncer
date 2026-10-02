# iCloud Photos API 字段参考

本文整理 XAS `feat/icloud` 分支使用的 iCloud Photos / CloudKit 接口，重点说明相册与资产的关系。字段以当前客户端读取行为为主；补充字段来自上游客户端或公开协议实测，不代表每个账号、每种资源都会返回。本文没有使用真实账号抓包核验全部字段，也不是 Apple 对 Photos 私有 schema 的兼容性承诺。

## 1. 图库、相册、照片与文件

| 对象 | 标识 | 含义与关系 |
| --- | --- | --- |
| 图库区域（zone） | `zoneID.zoneName` | 记录所在的命名空间；XAS 接受 `PrimarySync` 和 `SharedSync-*` |
| 相册 | `CPLAlbum.recordName` | 相册本身的元数据，例如名称 |
| 照片/视频逻辑资产 | `CPLAsset.recordName` | 用户在照片应用中看到的一项，包含拍摄时间、隐藏、收藏等状态 |
| 原件记录 | `CPLMaster.recordName` | 文件名与原始文件资源；通过 `CPLAsset.fields.masterRef.value.recordName` 关联 |
| 相册成员 | `CPLContainerRelation.recordName` | 将相册 `containerId` 与逻辑资产 `itemId` 关联 |
| 可下载文件 | `res*Res.value` | 文件大小、校验值和下载 URL；一项资产可能对应多个文件 |

关系示意：

```text
zone
 ├─ CPLAlbum A ← containerId ─ CPLContainerRelation ─ itemId → CPLAsset X
 ├─ CPLAlbum B ← containerId ─ CPLContainerRelation ─ itemId → CPLAsset X
 └─ CPLAsset X ─ masterRef → CPLMaster M
                              ├─ resOriginalRes（原件）
                              ├─ resOriginalAltRes（可选的另一原件）
                              └─ resOriginalVidComplRes（可选的 Live Photo 视频）
```

因此，相册成员数、逻辑资产数和下载文件数是不同的统计口径。相册名称不是唯一标识；标识需要保留 zone 和 recordName。`SharedSync-*` 的名字本身不足以判断全部共享能力；XAS 当前按共享图库区域处理，不支持产品中的「共享相册」服务。

## 2. 接口与返回结构

基础地址来自登录结果 `webservices.ckdatabasews.url`，XAS 拼接：

```text
{ckdatabasews}/database/1/com.apple.photos.cloud/production/private
```

下列请求均使用 POST。XAS 附带 `clientId`、`dsid`、`getCurrentSyncToken=true`、`remapEnums=true`。

| 接口 | 请求要点 | 返回与用途 |
| --- | --- | --- |
| `zones/list` | 空对象 | `zones[]`：区域列表；读取 `zoneID.zoneName`、`deleted` |
| `records/query` | `zoneID`、`query.recordType`、可选 `filterBy`、`resultsLimit`、`continuationMarker` | `records[]`：查询记录；可能有 `continuationMarker`、`syncToken` |
| `records/lookup` | `zoneID`、`records:[{recordName:...}]` | `records[]`：按标识补查资产、原件或成员关系；单项也可能返回错误 |
| `changes/zone` | `zones:[{zoneID,syncToken,resultsLimit}]` | `zones[]`；每区读取 `records`、`syncToken`、`moreComing`、错误信息 |

分页不能混用：相册列表使用 `continuationMarker`；资产枚举使用索引的 `startRank`；增量变更使用 `syncToken` 与 `moreComing`。`syncToken` 是整个 zone 的变更位点，不是某张照片或某个相册的版本。

### 通用记录外壳

```json
{
  "recordName": "asset-example",
  "recordType": "CPLAsset",
  "fields": {
    "masterRef": {"type": "REFERENCE", "value": {"recordName": "master-example"}},
    "assetDate": {"type": "TIMESTAMP", "value": 1700000000000},
    "isFavorite": {"type": "INT64", "value": 1}
  }
}
```

这是结构示例，不是真实响应。业务字段通常位于 `fields.<字段名>.value`；顶层 `recordName`、`recordType`、`deleted` 不在 `fields` 里。

| 字段 | 含义 |
| --- | --- |
| `recordName` | 区域内的记录标识；不要用名称代替 |
| `recordType` | 实际记录类型；查询索引名称可能与它不同 |
| `fields` | 业务字段字典；每项包含 `value`，可能包含 `type` |
| `deleted` | 顶层删除标志；XAS 增量处理时排除此类记录 |
| `serverErrorCode` / `reason` | 错误代码与说明；可能出现在单条 lookup 结果或某个 zone 中 |

其他通用字段可能包括 `recordChangeTag`（记录版本标记）、`created` / `modified`（创建与修改信息）、`zoneID`。字段的 `type` 表达服务端类型；照片协议的状态值也可能以 INT64 的 0/1 表示。时间戳按 Unix 毫秒处理。[Apple 通用字典说明](https://developer.apple.com/library/archive/documentation/DataManagement/Conceptual/CloudKitWebServicesReference/Types.html)

## 3. 相册相关字段与查询

### 3.1 相册列表：CPLAlbum

请求 `records/query`，设置 `query.recordType="CPLAlbumByPositionLive"` 和 `zoneID`。这是查询索引名称，不是返回记录类型 `CPLAlbum` 的同义字段。

| 字段/位置 | 含义 | XAS 当前行为 |
| --- | --- | --- |
| 顶层 `recordName` | 相册标识 | 保存到 `ICloudAlbumRef.recordName` |
| `albumNameEnc` | Base64 编码的 UTF-8 相册名称 | 解码；失败或为空时用 recordName |
| `isDeleted` | 相册删除状态 | 跳过 |
| `position` | 相册在容器中的位置 | 未读取 |
| `albumType` | 相册/容器类型枚举 | 未读取；没有确认具体数字与普通相册、文件夹的映射 |
| `sortAscending` | 排序方向标志 | 未读取 |
| `sortType` / `sortTypeExt` | 排序方式及扩展枚举 | 未读取；不假定数字含义 |
| `recordModificationDate` | 记录修改时间 | 未读取 |
| `userModificationDate` | 用户修改时间 | 未读取；不等同于相册内最新照片的时间 |
| `importedByBundleIdentifierEnc` | 导入来源应用标识的编码字段 | 未读取；具体编码需单独验证 |

补充字段及其语义范围参考 [kei 相册变更实测](https://github.com/rhoopr/kei/blob/main/docs/synctoken-reference.md#album-membership-add-photo-to-existing-album)。类型、排序字段的枚举映射尚未确认，不能仅凭字段名构造完整相册层级。

XAS 跳过 `----Root-Folder----` 和 `----Project-Root-Folder----` 两个根容器。当前不保存父文件夹关系，相册在本地作为平面列表展示。请求按 position 枚举相册，不表示相册内照片也按相同字段排序。

### 3.2 相册成员：CPLContainerRelation

| 字段/位置 | 含义 | XAS 当前行为 |
| --- | --- | --- |
| 顶层 `recordName` | 成员关系标识；当前约定为 `{assetRecordName}-IN-{albumRecordName}` | 增量时按该格式补查当前成员状态 |
| `containerId` | 相册标识 | 判断关系是否属于所选相册 |
| `itemId` | CPLAsset 标识 | 补查对应逻辑资产；不是 master 标识 |
| `isKeyAsset` | 是否为相册封面资产 | 未读取 |
| `position` | 资产在相册中的位置 | 未读取；区别于 CPLAlbum 的 position |
| `recordModificationDate` | 成员关系修改时间 | 未读取 |

同一资产可有多个成员关系。加入相册、移出相册不必修改 CPLAsset/CPLMaster；移出可体现为关系记录的顶层 `deleted=true`，资产仍在图库中。因此只监听资产变更会漏掉相册成员变化。[kei 成员变更实测](https://github.com/rhoopr/kei/blob/main/docs/synctoken-reference.md#album-membership-removal)

枚举某个用户相册时，使用索引 `CPLContainerRelationLiveByAssetDate`，条件为 `parentId=<相册 recordName>`。**这里 parentId 是请求过滤参数，返回关系上的相册字段是 containerId**。XAS 从该查询响应中处理 CPLAsset 和 CPLMaster；缺少原件时执行 lookup。不应要求每页的所有关联记录都齐全。

### 3.3 系统集合与智能相册

XAS 创建以下本地虚拟相册；`__all__` 等是 XAS 自己的标识，不是服务端 CPLAlbum.recordName。

| 显示名称 | XAS 标识 | 查询索引 | 附加条件 |
| --- | --- | --- | --- |
| 所有照片 | `__all__` | `CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted` | 无；排除隐藏和已删除 |
| 隐藏 | `__hidden__` | `CPLAssetAndMasterHiddenByAssetDate` | 无 |
| 个人收藏 | `__favorites__` | `CPLAssetAndMasterInSmartAlbumByAssetDate` | `smartAlbum=FAVORITE` |
| 用户相册 | 实际相册 recordName | `CPLContainerRelationLiveByAssetDate` | `parentId=<相册标识>` |

上游还使用 `VIDEO`、`LIVE`、`SCREENSHOT`、`PANORAMA`、`SLOMO`、`TIMELAPSE` 等 smartAlbum 条件；最近删除使用 `CPLAssetAndMasterDeletedByExpungedDate`，连拍使用专门索引。XAS 当前未暴露这些集合。照片总数可通过上游的 `internal/records/query/batch` + `HyperionIndexCountLookup` 查询 `itemCount`，XAS 当前未调用；本地相册构造时 `assetCount=0`，不代表远端空相册。[icloudpd 查询实现](https://github.com/icloud-photos-downloader/icloud_photos_downloader/blob/master/src/pyicloud_ipd/services/photos.py)

## 4. 逻辑资产：CPLAsset

| 字段 | 含义 | XAS 当前行为 |
| --- | --- | --- |
| `masterRef` | 指向 CPLMaster 的引用对象，内含 recordName | 用于连接原件记录 |
| `assetDate` | 资产的拍摄/内容日期，Unix 毫秒 | 映射为本地 dateTaken；不等同于上传时间 |
| `isFavorite` | 收藏标志 | 增量时判断个人收藏集合 |
| `isHidden` | 隐藏标志 | 增量时分配所有照片/隐藏集合 |
| `isDeleted` | 业务删除状态 | 增量时排除 |
| `isExpunged` | 清除状态 | 增量时排除 |

补充元数据可能包含 `addedDate`（加入图库日期）、`orientation`（方向）、`duration`（视频时长）、`captionEnc`（说明编码）、经纬度、`timeZoneOffset`、`assetSubtype` / `assetSubtypeV2`（子类型）、`assetHDRType`、连拍标记及 `burstId`、编辑类型和渲染类型。XAS 当前不解析它们；时长、时区与枚举的精确单位/取值需按响应和类型验证。`Enc` 后缀不保证内容均为可直接解码的 UTF-8；名称字段的处理不能直接套用于位置或编辑数据。[pyicloud 资产字段列表](https://github.com/picklepete/pyicloud/blob/master/pyicloud/services/photos.py)

## 5. 原件与文件资源：CPLMaster

| 字段 | 含义 | XAS 当前行为 |
| --- | --- | --- |
| `filenameEnc` | Base64 编码的 UTF-8 原始文件名 | 解码并构造本地文件名 |
| `itemType` | 媒体 UTI，例如 public.jpeg | 资源 FileType 缺失时作为后备 |
| `resOriginalRes` | 主要原件文件 | 下载 |
| `resOriginalAltRes` | 可选的另一原件，例如 RAW+JPEG 配对 | 存在时也下载；不能固定假定该字段一定是 RAW |
| `resOriginalVidComplRes` | Live Photo 原始配套视频 | 存在时也下载 |
| `resOriginalFileType` 等 `*FileType` | 对应资源的 UTI | 转成 MIME 与扩展名 |

上游还列出 `resJPEGFull*`、`resJPEGLarge*`、`resJPEGMed*`、`resJPEGThumb*`、`resVidFull*`、`resVidMed*`、`resVidSmall*` 和 `resSidecar*`。同组的 `Width/Height` 表示像素尺寸，`FileType` 表示 UTI，`Fingerprint` 表示指纹，`Res` 是资源对象。它们可能为渲染、缩略、转码或 sidecar 版本；不能全部当成原件，也不应把 Fingerprint 与 fileChecksum 当成可互换字段。XAS 当前仅枚举上述三种原始资源。[icloudpd 资源实现](https://github.com/icloud-photos-downloader/icloud_photos_downloader/blob/master/src/pyicloud_ipd/services/photos.py)

### 文件资源对象（例如 resOriginalRes.value）

| 字段 | 含义与使用 |
| --- | --- |
| `size` | 文件大小，字节；用于下载后长度校验 |
| `fileChecksum` | 文件校验值；XAS 支持 Base64 解码后 20 字节 SHA-1，或首字节 0x01 的 21 字节格式；不能直接视为十六进制 SHA-1 |
| `downloadURL` | 文件下载地址；XAS 下载前查询资产，并按需沿 masterRef 查询原件，不持久化 |
| `referenceChecksum` | 私有库文件包装密钥的校验值，区别于文件内容校验 |
| `wrappingKey` | 文件加密相关密钥材料 |
| `receipt` | 上传凭据；主要用于保存包含文件的记录 |

后三项来自 CloudKit 通用资源字典，并非所有读取响应都包含，XAS 不解析它们。[Apple Asset Dictionary](https://developer.apple.com/library/archive/documentation/DataManagement/Conceptual/CloudKitWebServicesReference/Types.html#//apple_ref/doc/uid/TP40015240-CH3-SW2)

资源可能在 CPLAsset 或 CPLMaster 上；当前 XAS 全量/增量枚举从 master 读取三种原件，下载刷新先查询 asset 上同名资源，缺失时沿实时 masterRef 查询 master。Live Photo 通常产生图片和视频两个本地 Asset，RAW+JPEG 也可产生两个；不意味着远端有两个 CPLAsset。

## 6. 索引状态与增量字段

`CheckIndexingState` 的响应读取 `records[0].fields.state.value`；只有 `FINISHED` 才继续枚举。

`changes/zone` 每个 zone 的主要返回字段：

| 字段 | 含义 |
| --- | --- |
| `records` | 此页变更记录；可混合资产、原件、相册和成员关系 |
| `syncToken` | 后续请求所需的位点；XAS 在该页资产持久化成功后提交 |
| `moreComing` | 是否还有下一页；即使 records 为空也以该字段决定是否继续 |
| `serverErrorCode` / `reason` | 区域级错误；XAS 对失效位点重建全量基线 |

当前 XAS 使用变更流中的 CPLAsset、CPLMaster 和新增成员关系，并 lookup 当前相册成员状态。不会依据删除事件清理已有备份，也没有将流中的 CPLAlbum 改名/排序变更直接应用到本地相册元数据。

## 7. 与 XAS 本地模型的区别

| 本地字段 | 来源/含义 |
| --- | --- |
| `Album.cloudAlbum` | zone、服务端相册标识或虚拟标识、查询索引、smartAlbum 条件 |
| `Album.remoteId` | XAS 对 zone/recordName 求哈希得到的 Long，不是 Apple 返回的数字 ID |
| `Album.lastUpdateTime` | 构造时为 Instant.EPOCH，未映射相册修改时间 |
| `Asset.remoteKey` | iCloud 为固定字段顺序的 JSON：assetId、resource；小米为远端数字 ID 的字符串 |
| `Asset.sha1` | 保存 `icloud:<fileChecksum>`，不是普通十六进制 SHA-1 |
| `Asset.fileName` | 原始文件名加稳定资源后缀，不是原样复制 filenameEnc |

资产唯一约束为 `(album_id, remote_key, sha1)`；账号与来源由相册关联查询，iCloud zone 由相册 cloudAlbum 提供，masterId 在下载时通过 masterRef 查询，校验值仅保存在 sha1。同一照片放入多个所选相册时，XAS 为每个相册建立独立下载记录。通过本地 assetCount 或下载文件数反推 Apple 照片应用的计数时，应先统一统计口径。

实现入口：[ICloudPhotos.kt](../server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/icloud/ICloudPhotos.kt)、[ICloudChanges.kt](../server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/icloud/ICloudChanges.kt)、[ICloudClient.kt](../server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/icloud/ICloudClient.kt)。
