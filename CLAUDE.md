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

- `Album` 表: id (主键), media_count, name, selected
- `Media` 表: id (主键), album_id, filename, mime_type, media_type, sha1, downloaded, date_modified

## 配置

存储在 `config.json` 中，默认值:
- downloadPath: "download"
- fillExif: "false"
- downloadVideo: "false"
- fillVideoExif: "false"
- exiftoolPath: "exiftool"

## 关键文件

- `config.json` - 应用程序配置
- `cookies.json` - 认证Cookie
- `sql.db` - SQLite数据库，用于跟踪下载状态
- `download/` - 默认下载目录

## 依赖项

通过 `pyproject.toml` 和 `uv.lock` 使用UV包管理器管理。主要依赖包括:
- httpx: HTTP客户端
- InquirerPy: 交互式CLI
- Peewee: SQLite ORM
- Click: CLI框架
- Piexif: EXIF操作