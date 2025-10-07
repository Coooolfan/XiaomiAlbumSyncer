# Xiaomi Album Syncer - Web API 文档

## 概述

本项目使用小米云服务的多个API接口来实现相册同步功能。所有API请求都需要有效的Cookie认证。

## API 端点列表

### 1. 获取相册列表
- **URL**: `https://i.mi.com/gallery/user/album/list`
- **方法**: GET
- **参数**:
  - `ts`: 时间戳（毫秒）
  - `pageNum`: 页码（从0开始）
  - `pageSize`: 每页数量（默认100）
  - `isShared`: 是否共享相册（"false"）
  - `numOfThumbnails`: 缩略图数量（1）
- **功能**: 获取用户的所有相册列表
- **位置**: `src/api.py:22`

### 2. 获取媒体文件列表
- **URL**: `https://i.mi.com/gallery/user/galleries`
- **方法**: GET
- **参数**:
  - `ts`: 时间戳（毫秒）
  - `albumId`: 相册ID
  - `startDate`: 开始日期
  - `endDate`: 结束日期
  - `pageNum`: 页码
  - `pageSize`: 每页数量
- **功能**: 获取指定相册中的媒体文件列表
- **位置**: `src/api.py:75`

### 3. 获取文件下载信息
- **URL**: `https://i.mi.com/gallery/storage`
- **方法**: GET
- **参数**:
  - `id`: 媒体文件ID
  - `ts`: 时间戳（毫秒）
- **功能**: 获取媒体文件的下载URL和元数据信息
- **位置**: `src/api.py:114`

### 4. Cookie刷新接口
- **URL**: `https://i.mi.com/status/lite/setting?type=AutoRenewal&inactiveTime=10`
- **方法**: GET
- **功能**: 刷新认证Cookie，保持会话活跃
- **位置**: `src/api.py:164`

## 认证机制

所有API请求都需要有效的Cookie认证。Cookie通过以下方式获取：
1. 用户手动登录小米云服务（https://i.mi.com/）
2. 访问相册页面（https://i.mi.com/gallery/h5#/）
3. 从浏览器开发者工具中复制Cookie字符串
4. 通过CLI命令设置到应用中

Cookie每3分钟自动刷新一次以保持会话有效。

## 数据格式

### 相册列表响应示例
```json
{
  "data": {
    "albums": [
      {
        "albumId": 123,
        "name": "相册名称",
        "mediaCount": 100,
        "coverUrl": "..."
      }
    ]
  }
}
```

### 媒体文件列表响应示例
```json
{
  "data": {
    "galleries": [
      {
        "id": "file_id",
        "fileName": "filename.jpg",
        "mimeType": "image/jpeg",
        "type": "image",
        "sha1": "file_hash",
        "dateModified": "2023-01-01T00:00:00Z"
      }
    ]
  }
}
```

### 文件下载信息响应示例
```json
{
  "data": {
    "url": "https://download.url"
  }
}
```

## 错误处理

- **HTTP状态码**: 非200时抛出异常，包含响应内容
- **Cookie失效**: 需要用户重新设置Cookie (`src/api.py:168`)
- **下载失败**: 记录错误信息并继续处理其他文件
- **自动重试**: 媒体列表获取失败时自动重试500ms (`src/task.py:46-47`)
- **超时处理**: Cookie每3分钟自动刷新 (`src/api.py:151-159`)

## 限制和注意事项

1. **私密相册**: 相册ID为1000的私密相册无法访问 (`src/api.py:38-39`)
2. **分页查询**: 所有列表查询都支持分页，默认pageSize=200 (`src/configer.py:14`)
3. **频率限制**: 避免过于频繁的请求，建议合理设置请求间隔
4. **会话超时**: Cookie需要定期刷新，每3分钟自动刷新一次
5. **文件大小**: 大文件下载可能需要较长时间，支持断点续传
6. **EXIF依赖**: 视频EXIF填充需要安装exiftool并配置正确路径
7. **日期范围**: 支持按日期范围筛选媒体文件 (`src/configer.py:10-11`)