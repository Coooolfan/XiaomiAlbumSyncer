# <img src="./static/xiaomi-album-syncer-logo.png" alt="logo" width="20rem"> Xiaomi Album Syncer

发了 **issues** 记得常回来看看有没有回复，或者 >>>>> **[QQ 群聊：1059332701](https://qm.qq.com/q/H2trW6JWM4)**

![banner](./static/banner.avif)

发了 **issues** 记得常回来看看有没有回复，或者 >>>>> **[QQ 群聊：1059332701](https://qm.qq.com/q/H2trW6JWM4)**

## Features/功能

- [x] 📸 下载指定相册中的所有照片
- [x] ⏭️ 自动跳过已下载的照片
- [x] 🔄 自动刷新 Cookie
- [x] ⏰ 支持定时任务
- [x] 📥 支持增量下载（超快）
- [x] 🗂️ 支持按相册分类存储照片
- [x] 🌍 友好的 Web UI
- [x] 📅 填充照片和视频的 Exif 时间信息
- [x] 📅 填充照片和视频的文件系统时间

> [!CAUTION] 
> 此项目已于 `0.3.0` 完成重构。新版目前仅提供 Web UI，部署方式提供 Docker、JVM、原生二进制版本。旧版 CLI 工具仍然可用，可前往 [0.2.1 releases](https://github.com/Coooolfan/XiaomiAlbumSyncer/releases/tag/0.2.1) 下载。
>
> 如需从旧版迁移数据，请参考 [从 v2 迁移](#从v2迁移) 一节。

## 部署

自 0.3.0 版本起，程序会尝试兼容旧版本的数据库。在**跨多个版本升级**时，建议先浏览 Releases 页面，查看每个版本的更新内容，以及可能的数据库变更和破坏性更新。

**不建议使用各种自动升级工具。**

> 原生版本和 jvm 版本的逻辑实现相同，数据库可以通用

### 二进制部署 (Binary)

此部署方式无需安装 Java 环境，直接下载对应平台的可执行文件即可运行。

1.  **下载**: 前往 [Releases](https://github.com/Coooolfan/XiaomiAlbumSyncer/releases) 页面下载对应系统的二进制文件压缩包（Linux/macOS/Windows）。
    *   **Linux x64**: `xiaomi-album-syncer-linux-x64-vX.X.X.zip`
    *   **Linux arm64**: `xiaomi-album-syncer-linux-arm64-vX.X.X.zip`
    *   **macOS x64**: `xiaomi-album-syncer-macos-x64-vX.X.X.zip`
    *   **macOS arm64**: `xiaomi-album-syncer-macos-arm64-vX.X.X.zip`
    *   **Windows x64**: `xiaomi-album-syncer-windows-x64-vX.X.X.exe.zip`
    *   **Windows arm64**: GraalVM 尚不支持 windows on arm
    
2.  **解压**: 解压下载的压缩包，得到可执行文件。

3.  **运行**:
    *   **Linux / macOS**:
        ```bash
        # 赋予执行权限 (文件名后的版本号视具体情况而异)
        chmod +x xiaomi-album-syncer-linux-x64-v0.3.0
        # 运行
        ./xiaomi-album-syncer-linux-x64-v0.3.0
        ```
    *   **Windows**: 双击运行解压后的 `xiaomi-album-syncer-windows-x64-v0.3.0.exe`。

4.  **访问**: 打开浏览器访问 `http://localhost:8080`。

    > [!NOTE]
    > 如果在 Windows 下运行，且用户名为中文，可能会遇到类似于 `java.lang.UnsatisfiedLinkError: Can't load library` 或者 `Failed to load native library: ... sqlite-3.50.3.0-...-sqlitejdbc.dll`
    >
    > 这是由于系统的临时路径存在非 ASCII 字符导致的。可以使用 `-Dorg.sqlite.tmpdir=C:\sqlite_tmp` 参数手动指定临时路径。
    >
    > 例如可以打开控制后使用命令 `.\xiaomi-album-syncer-windows-x64-v0.3.0.exe -Dorg.sqlite.tmpdir=C:\sqlite_tmp` 执行


### Docker

1. 拉起 docker 容器

    ```bash
    # 使用默认版本 (JVM)
    docker run -d \
      -p 8232:8080 \
      --name xiaomi-album-syncer \
      -v ~/xiaomi-album-syncer/download:/app/download \
      -v ~/xiaomi-album-syncer/db:/app/db \
      coolfan1024/xiaomi-album-syncer:latest
    ```
    
    或者使用 Native 版本：

    ```bash
    # 使用 Native 版本
    docker run -d \
      -p 8232:8080 \
      --name xiaomi-album-syncer \
      -v ~/xiaomi-album-syncer/download:/app/download \
      -v ~/xiaomi-album-syncer/db:/app/db \
      coolfan1024/xiaomi-album-syncer:latest-native
    ```
    
2. 访问 Web UI

   打开浏览器，访问 `http://localhost:8232`（如果你在本地运行 Docker），或者替换为你的服务器 IP 地址和端口。

### Docker Compose

1. 下载`docker-compsoe.yml`文件
    ```bash
    mkdir -p ~/xiaomi-album-syncer
    cd ~/xiaomi-album-syncer
    curl -O https://raw.githubusercontent.com/Coooolfan/XiaomiAlbumSyncer/main/docker/docker-compose.yml
    ```
2. 按需编辑`docker-compose.yml`文件
   - JVM 版本(默认)：版本号或者 latest。eg. `0.8.0` `latest`
   - Native 版本：在版本号或者 latest 后添加 **-native** 后缀。eg. `0.8.0-native` `latest-native`

3. 启动服务
    ```bash
    docker compose up -d
    ```
4. 访问 Web UI

  打开浏览器，访问 `http://localhost:8232`（如果你在本地运行 Docker），或者替换为你的服务器 IP 地址和端口。

## 反向代理（可选）

**强烈建议在任何情况下访问此服务都启用 HTTPS 。**

此项目的 Web UI 与 API 仅使用了简单的 Restful API 构建，无复杂有状态长连接。

## 使用

### 初始化

访问 `http://localhost:8232` ，输入一个强密码（此密码仅用于访问此项目，与任何外部服务或者小米无关），完成此项目的初始化。


### 获取 PassToken 与 UserId

1. 登录[小米云服务](https://i.mi.com/)
2. **[访问一次相册页面](https://i.mi.com/gallery/h5#/)**
3. 如果出现手机验证，勾选 `信任此设备`
4. 点击右上角头像，点击 `我的小米账号`，进入 `我的小米账号` 页面
5. 打开浏览器 开发者工具
6. 选中 应用程序/Application 一栏。复制对应的两个 `passToken` 与 `userId` 字段值
![getpassanduserid](static/copybydevtool.avif)
即图中的 4️⃣ 和 5️⃣ 对应的黄底内容。

### 设置 PassToken 与 UserId

访问 `http://localhost:8232/dashboard/setting` ，设置 PassToken 与 UserId。

### 获取所有相册

访问 `http://localhost:8080/#/dashboard/schedule` 选择 `从远程更新整个相册列表`

![fetchlastestalbums](static/fetchlastestalbums.avif)

此接口为同步接口，具体时长取决于相册数量和网络情况。请耐心等待。

> [!WARNING] 
> 如果此获取数据失败，大概率是 passToken 和 userId 没有正确设置的导致的。如果已确保两个配置已经正确配置，请前往 Issues 页面发布 issue 并附上相关日志。

### 创建计划任务

访问 `http://localhost:8080/#/dashboard/schedule` 单击 任务计划 卡片右上角的绿色 ➕ 号。按需要填写各项配置。

![democrontab](./static/democrontab.avif)

> [!NOTE]
> 图中的 Cron 表达式为 `0 0 23 * * ?`，即北京时间每天 23 点执行一次。
> 
> 更多 Cron 表达式请参考 [Cron 表达式](https://cron.qqe2.com/)。

程序会在设置的时间执行任务。任务执行时，会自动刷新选中的相册列表，并**增量地**下载相册中的所有照片和视频。

### 手动触发计划任务

不论任务计划是否启用。您都可以在控制台手动触发任务执行

![manualtriggercrontab](static/manualtriggercrontab.avif)

<div id="从v2迁移"></div>

## 从 v2 迁移

xiaomi-album-syncer 是一个有状态的应用，不论是 v2 还是 v3+ 版本，都使用 SQLite 作为数据库。为避免迁移后导致重复下载，v3+ 版本提供了从 v2 版本迁移数据的功能。在使用此功能前，确保：

- 你已经正确备份了 v2 版本的数据库文件
- v3 版本的实例已经正确运行过一次，并完成密码初始化
- v3 版本的实例没有任何相册和照片数据（确保只进行过密码初始化，passToken 和 userId 提交过不影响）

### 创建 v3 实例并挂载旧数据库

```bash
docker run \
  --name xiaomi-album-syncer \
  -p 8232:8080 \
  -v ~/xiaomi-album-syncer/download:/app/download \
  -v ~/xiaomi-album-syncer/db:/app/db \
  -v ./old.db:/app/old.db \
  coolfan1024/xiaomi-album-syncer:latest
```

观察此 docker 命令，不难发现主要区别在于多了一个 `-v ./old.db:/app/old.db` 的挂载。`./old.db` 是你 v2 版本的数据库文件路径。

### 触发迁移

访问 `http://localhost:8283/#/dashboard/setting` ，在页面底部找到 `从 V2 导入数据` 栏，单击 `导入` 按钮，等待导入完成。

**如果遇到错误，请前往 Issues 页面发布 issue 并附上相关 docker 日志。**

返回 schedule 页面，可以看到已经导入了所有相册和下载记录。

### 卸载旧数据库（可选）

迁移完成后，你可以选择卸载旧数据库文件。

```bash
docker run \
  --name xiaomi-album-syncer \
  -p 8232:8080 \
  -v ~/xiaomi-album-syncer/download:/app/download \
  -v ~/xiaomi-album-syncer/db:/app/db \
  coolfan1024/xiaomi-album-syncer:latest
```

容器本身不存储任何状态和数据，删除重启容器不影响任何数据。

## Star History

<picture>
  <source
    media="(prefers-color-scheme: dark)"
    srcset="
      https://api.star-history.com/svg?repos=Coooolfan/XiaomiAlbumSyncer&type=Date&theme=dark
    "
  />
  <source
    media="(prefers-color-scheme: light)"
    srcset="
      https://api.star-history.com/svg?repos=Coooolfan/XiaomiAlbumSyncer&type=Date
    "
  />
  <img
    alt="Star History Chart"
    src="https://api.star-history.com/svg?repos=Coooolfan/XiaomiAlbumSyncer&type=Date"
  />
</picture>
