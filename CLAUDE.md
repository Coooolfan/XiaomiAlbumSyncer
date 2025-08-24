# CLAUDE.md

本文档为Claude Code (claude.ai/code) 提供在此代码库中工作的指导。

## 项目概述

Xiaomi Album Syncer 是一个Python工具，用于从小米云相册下载照片和视频到本地存储。它提供交互式CLI和非交互式命令行两种界面。

## 架构

- **主入口**: `main.py` - 使用Click和InquirerPy的CLI界面
- **核心模块**:
  - `src/task.py` - 主要任务编排
  - `src/api.py` - 小米云API交互
  - `src/model/` - 使用Peewee ORM的数据库模型（Album, Media）
  - `src/configer.py` - 配置管理
  - `src/PersistentCookies.py` - Cookie持久化
  - `src/exiftool.py` - EXIF元数据处理

## 开发命令

**运行应用程序:**
```bash
uv run main.py  # 交互模式
uv run main.py --help  # 显示CLI帮助
```

**常用CLI选项:**
```bash
uv run main.py -sc "cookie_string"  # 设置Cookie
uv run main.py -uc  # 更新Cookie
uv run main.py -ua  # 更新相册列表
uv run main.py -da  # 下载选中的相册
uv run main.py -cd  # 清除下载记录
```

**构建可执行文件:**
```bash
# Windows
pyinstaller --onefile --add-data "requirements.txt;." main.py

# Linux
pyinstaller --onefile --add-data "requirements.txt:." main.py
```

## 数据库结构

### Album表 (`src/model/album.py:6-14`)
- `id`: 相册ID (Integer, 主键)
- `media_count`: 媒体文件数量 (Integer, 默认0)
- `name`: 相册名称 (CharField)
- `selected`: 是否选中下载 (Boolean, 默认False)

### Media表 (`src/model/media.py:6-14`)
- `id`: 媒体ID (Integer, 主键)
- `album_id`: 所属相册ID (Integer)
- `filename`: 文件名 (CharField)
- `mime_type`: MIME类型 (CharField)
- `media_type`: 媒体类型 (CharField, image/video)
- `sha1`: 文件SHA1哈希 (CharField)
- `downloaded`: 是否已下载 (Boolean, 默认False)
- `date_modified`: 修改时间戳 (Integer, 毫秒, 默认0)

## 配置

存储在 `config.json` 中，完整配置选项 (`src/configer.py:8-19`):
- `userAgent`: 用户代理字符串
- `startDate`: 开始日期 (YYYYMMDD)
- `endDate`: 结束日期 (YYYYMMDD)
- `downloadPath`: 下载路径 (默认"download")
- `dirName`: 相册文件夹命名方式 ("name"或"id", 默认"name")
- `pageSize`: 分页大小 (默认"200")
- `fillExif`: 是否填充照片EXIF ("true"/"false", 默认"false")
- `downloadVideo`: 是否下载视频 ("true"/"false", 默认"false")
- `fillVideoExif`: 是否填充视频EXIF ("true"/"false", 默认"false")
- `exiftoolPath`: exiftool路径 (默认"exiftool")

## 关键文件

- `config.json` - 应用程序配置
- `cookies.json` - 认证Cookie
- `sql.db` - SQLite数据库，用于跟踪下载状态
- `download/` - 默认下载目录
- `tampermonkey.js` - 油猴脚本，用于浏览器获取Cookie
- `static/` - 静态资源目录（截图和说明图片）

## 错误处理和重试机制

- **API请求重试**: 媒体列表获取失败时自动重试 (`src/task.py:46-47`)
- **Cookie自动刷新**: 每3分钟自动刷新Cookie保持会话 (`src/api.py:151-159`)
- **数据库事务**: 使用原子操作保证数据一致性 (`src/model/database.py:11,44`)
- **EXIF错误处理**: 填充EXIF时的异常捕获和友好提示 (`src/exiftool.py:68-71,116-117`)

## 依赖项

通过 `pyproject.toml` 和 `uv.lock` 使用UV包管理器管理。主要依赖包括:
- httpx: HTTP客户端
- InquirerPy: 交互式CLI
- Peewee: SQLite ORM
- Click: CLI框架
- Piexif: EXIF操作
- peewee-migrate: 数据库迁移
- tqdm: 进度条显示
- nest-asyncio: 异步事件循环嵌套支持
- rookiepy: 浏览器Cookie获取（已禁用）

## 构建和部署

**PyInstaller构建** (`compiler.md`):
```bash
pyinstaller --onefile --add-data "requirements.txt:." main.py
```

**Docker支持**:
- `Dockerfile` - 容器化构建配置
- `docker-compose.yaml` - Docker Compose配置