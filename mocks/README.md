# Mocks

`mocks` 是 XiaomiAlbumSyncer 的有状态远端模拟服务。它实现当前后端实际消费的小米云相册、云录音和 iCloud 照片接口，
并提供无需鉴权的控制 API，供下载链路、增量同步和内存测试矩阵使用。

## 启动

```bash
go run ./cmd/mocks \
  --listen 127.0.0.1:18080 \
  --scenario ./scenarios/default.json
```

也可以使用容器：

```bash
docker compose up --build
```

让 XiaomiAlbumSyncer 访问模拟服务：

```bash
XIAOMI_API_BASE_URL=http://127.0.0.1:18080
```

默认小米账号为 `mock-user` / `mock-pass-token`。生产环境仍使用项目原有的 `https://i.mi.com` 默认值。

iCloud 使用同一监听地址。Apple 登录在账号完成双重认证前返回 409，验证码是 `123456`。
认证通过后，`/setup/ws/1/accountLogin` 和 `/setup/ws/1/validate` 会把 CloudKit 根地址指回本服务。
图库包含「所有照片」和相册「旅行」；位点 `caught-up` 第一次访问返回 `CHANGE_TOKEN_EXPIRED`，之后恢复。
服务端测试通过 `ICLOUD_API_BASE_URL` 把 iCloud 请求切到这里。未配置时仍访问 Apple。

## 场景模型

场景 JSON 的顶层字段包括：

- `version`：当前固定为 `1`。
- `seed`：相同 seed 和场景会生成相同的 ID、元数据、服务令牌及下载字节。
- `logicalClock`：控制操作使用的逻辑时间，单位毫秒。
- `network`：下载响应延迟、分块和单连接限速。
- `accounts`：账号及其彼此独立的 `galleryAlbums`、`recordings`。

相册资产可以逐个填写，也可以使用 `generate` 批量生成。下载字节不会在启动时分配；服务只保存元数据，
下载时使用一个有上限的缓冲区重复输出确定性内容。`sha1Mode: "exact"` 会在加载场景时计算真实 SHA-1，
只适合 E2E 中的小文件；内存矩阵默认使用稳定但不匹配下载字节的声明 SHA-1，并关闭任务的 `checkSha1`。
图片资产或生成规则可以设置 `contentMode: "jpeg"`，模拟器会流式生成可被 ExifTool 读取和改写、长度精确且
内容稳定的 JPEG；该模式只接受 `type: "image"`。`memory-profile-exif.json` 和它的小场景版本用于 EXIF 内存基准。

远端录音资源没有相册 ID。`-1` 只由 XiaomiAlbumSyncer 在本地合成，因此：

- `galleryAlbums` 中禁止配置 `albumId: -1`。
- 录音只通过 `recordings` 和 `/sfs/ns/recorder/...` 接口访问。
- `/gallery/user/timeline?albumId=-1` 会返回 404，用于发现错误的调用方行为。

## 控制 API

所有控制接口都不鉴权。

重置到启动场景：

```bash
curl -X POST http://127.0.0.1:18080/_control/v1/reset \
  -H 'Content-Type: application/json' \
  -d '{}'
```

查看摘要或分页媒体：

```bash
curl 'http://127.0.0.1:18080/_control/v1/state'
curl 'http://127.0.0.1:18080/_control/v1/state?includeMedia=true&mediaPage=0&mediaPageSize=100'
```

向相册新增 100 个 16 MiB 图片：

```bash
curl -X POST http://127.0.0.1:18080/_control/v1/mutations \
  -H 'Content-Type: application/json' \
  -d '{
    "operations": [{
      "op": "addAssets",
      "userId": "mock-user",
      "albumId": 1,
      "count": 100,
      "template": {
        "type": "image",
        "fileName": "increment.jpg",
        "dateTaken": 1784131200000,
        "size": 16777216
      }
    }]
  }'
```

新增录音使用独立操作，不传 `albumId`：

```bash
curl -X POST http://127.0.0.1:18080/_control/v1/mutations \
  -H 'Content-Type: application/json' \
  -d '{
    "operations": [{
      "op": "addRecordings",
      "userId": "mock-user",
      "count": 10,
      "template": {
        "fileName": "call.m4a",
        "recordingType": 1,
        "createTime": 1784131200000,
        "size": 8388608
      }
    }]
  }'
```

删除最新资产时使用 `deleteAssets` 或 `deleteRecordings`，传 `count` 和 `selection: "newest"`；也可直接传
`ids`。一个请求中的全部 `operations` 原子生效。传入 `expectedRevision` 可以防止矩阵脚本基于旧状态修改，
revision 不匹配时返回 409。

设置每个下载连接为 4 MiB/s、32 KiB 分块、100 ms 首包延迟：

```bash
curl -X PUT http://127.0.0.1:18080/_control/v1/network \
  -H 'Content-Type: application/json' \
  -d '{
    "responseDelayMs": 100,
    "chunkSizeBytes": 32768,
    "bytesPerSecondPerDownload": 4194304
  }'
```

`mediaOverrides` 可使用媒体 ID 字符串覆盖某个媒体的网络参数。`GET /_control/v1/stats` 返回每条路径的
调用次数、相册时间线请求 ID、已发送字节数及当前/峰值并发下载数。

## 建议的内存测试顺序

1. 使用固定场景首次同步并下载，建立完整下载基线。
2. 不修改远端状态再次执行，采集空任务数据。
3. reset 后用 mutation 新增固定数量和大小的媒体，再执行增量同步。
4. 分别改变下载器并发、单下载限速、分块大小和响应延迟。
5. 每个矩阵项结束后读取 stats，确认实际走过的列表、时间线和下载接口与预期一致。

不要在应用进行多页读取的中间修改模拟状态；每个 HTTP 请求都会读取一致快照，但多个分页请求之间不提供
跨请求事务快照。

## 验证

```bash
go test ./...
go test -race ./...
go vet ./...
```
