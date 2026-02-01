# 编译、调试和运行指南

本文档介绍如何在本地编译、调试和运行 Xiaomi Album Syncer 项目。

## 环境要求

### 必需工具
- **Java 25**：推荐使用 GraalVM JDK 25
- **Gradle**：项目自带 Gradle Wrapper，无需单独安装
- **Node.js 24+**：用于构建前端（如需完整构建）

### macOS 环境配置

#### 1. 安装 GraalVM JDK 25
```bash
# 使用 Homebrew 安装
brew install --cask graalvm-jdk@25
```

#### 2. 配置环境变量
```bash
# 临时配置（当前终端会话）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

# 永久配置（添加到 ~/.zshrc）
echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### 3. 验证安装
```bash
java -version
# 应显示：java version "25.0.2" ... Oracle GraalVM 25.0.2

native-image --version
# 应显示：native-image 25.0.2 ... GraalVM Runtime Environment
```

## 开发调试模式

### 方式 1：一键启动脚本（最简单）⭐

#### 仅启动后端（API 模式）
```bash
# 在项目根目录运行
./debug.sh
```
**适用场景**：只需要测试 API 接口，不需要前端界面

#### 启动后端 + 前端开发服务器（推荐）⭐⭐⭐
```bash
./debug.sh -d
# 或
./debug.sh --frontend-dev
```
**适用场景**：前端开发，支持热重载
- 前端地址：http://localhost:5173
- 后端地址：http://localhost:8080

#### 构建前端并启动完整应用
```bash
./debug.sh -f
# 或
./debug.sh --frontend
```
**适用场景**：测试完整的生产环境体验
- 应用地址：http://localhost:8080

**功能特点**：
- ✅ 自动检查 Java 和 Node.js 环境
- ✅ 自动清理端口占用
- ✅ 彩色日志输出（ERROR/WARN/INFO）
- ✅ 启用远程调试（端口 5005）
- ✅ 优雅退出（Ctrl+C）
- ✅ 支持前端开发服务器

**日志颜色说明**：
- 🔴 红色：ERROR 级别
- 🟡 黄色：WARN 级别
- 🟢 绿色：INFO 级别

### 方式 2：使用 Gradle 运行

```bash
cd XiaomiAlbumSyncer/server

# 运行应用（JVM 模式，启动快，支持热重载）
./gradlew run

# 或者使用 bootRun（如果配置了 Spring Boot）
./gradlew bootRun
```

**优点**：
- 启动速度快（几秒钟）
- 支持代码热重载
- 便于调试和开发

**访问应用**：
- 前端界面：http://localhost:8080
- API 接口：http://localhost:8080/api
- 远程调试：localhost:5005 (JDWP)

### 方式 3：使用 IDE 调试

#### IntelliJ IDEA
1. 打开项目：`File` → `Open` → 选择 `XiaomiAlbumSyncer/server`
2. 等待 Gradle 同步完成
3. 找到主类：`com.coooolfan.xiaomialbumsyncer.App`
4. 右键点击 → `Debug 'App'`

**调试功能**：
- 设置断点
- 单步执行
- 查看变量值
- 热重载代码

#### 连接远程调试
如果使用 `debug.sh` 启动，可以在 IDE 中连接远程调试：
1. `Run` → `Edit Configurations`
2. 添加 `Remote JVM Debug`
3. 设置 Host: `localhost`, Port: `5005`
4. 点击 Debug 按钮连接

### 方式 4：构建 JAR 包运行

```bash
cd XiaomiAlbumSyncer/server

# 构建 JAR 包
./gradlew solonJar

# 运行 JAR 包
java -jar build/libs/XiaomiAlbumSyncer-dev.jar
```

**适用场景**：
- 测试打包后的应用
- 模拟生产环境

## 生产构建模式

### 构建 Native Image（原生二进制）

```bash
cd XiaomiAlbumSyncer/server

# 清理之前的构建
./gradlew clean

# 构建原生二进制（需要 10-20 分钟）
./gradlew nativeCompile
```

**构建产物位置**：
```
server/build/native/nativeCompile/XiaomiAlbumSyncer
```

**运行原生二进制**：
```bash
cd build/native/nativeCompile
./XiaomiAlbumSyncer
```

**优点**：
- 启动速度极快（毫秒级）
- 内存占用小
- 无需 JVM 环境

**缺点**：
- 构建时间长
- 不支持热重载
- 调试困难

## 前端开发

### 开发模式（带热重载）

```bash
cd XiaomiAlbumSyncer/web

# 安装依赖
yarn install

# 启动开发服务器
yarn dev
```

访问：http://localhost:5173

**特点**：
- 代码修改后自动刷新
- 支持 HMR（热模块替换）
- 开发工具集成

### 构建生产版本

```bash
cd XiaomiAlbumSyncer/web

# 构建前端资源
yarn build
```

构建产物会输出到 `web/dist` 目录，后端会自动加载这些静态资源。

## 常见问题

### 1. 访问 http://localhost:8080 显示 404

**原因**：后端没有前端静态资源

**解决方案**：

**方案 A：使用前端开发服务器（推荐）**
```bash
# 终端 1：启动后端
./debug.sh

# 终端 2：启动前端
./start-frontend.sh
```
然后访问：http://localhost:5173

**方案 B：使用集成模式**
```bash
./debug.sh -d
```
脚本会自动启动前端开发服务器

**方案 C：构建前端到后端**
```bash
./debug.sh -f
```
然后访问：http://localhost:8080

### 2. Yarn 相关错误

**问题**：`未找到 yarn` 或 `yarn 版本不对`

**解决**：
```bash
# 卸载旧的 yarn
npm uninstall -g yarn

# 安装 corepack（Node.js 包管理器）
npm install -g corepack

# 启用 corepack
corepack enable

# 验证（在 web 目录下应该显示 4.9.2）
cd web
yarn --version
```

### 3. 数据库迁移失败

**问题**：启动时报数据库验证错误

**解决**：删除旧数据库，让应用重新创建
```bash
cd XiaomiAlbumSyncer/server
rm -rf ./db
```

### 3. 数据库迁移失败

**问题**：启动时报数据库验证错误

**解决**：删除旧数据库，让应用重新创建
```bash
cd XiaomiAlbumSyncer/server
rm -rf ./db
```

### 4. Native Image 构建失败

**问题**：`predefined-classes-config.json` 格式错误

**解决**：该文件在 GraalVM 25 中已不需要，可以删除
```bash
rm server/src/main/resources/META-INF/native-image/predefined-classes-config.json
```

### 5. 端口被占用

**问题**：`Address already in use: bind`

**解决**：
```bash
# 查找占用端口的进程
lsof -i :8080

# 杀死进程
kill -9 <PID>
```

### 6. Gradle 构建缓存问题

**解决**：清理 Gradle 缓存
```bash
./gradlew clean
./gradlew --stop
rm -rf ~/.gradle/caches
```

## 推荐开发流程

### 日常开发
1. 使用 `./gradlew run` 或 IDE 运行后端
2. 使用 `yarn dev` 运行前端开发服务器
3. 修改代码后自动重载

### 功能测试
1. 构建 JAR 包：`./gradlew solonJar`
2. 运行 JAR 测试完整功能
3. 确认无误后提交代码

### 发布前验证
1. 构建 Native Image：`./gradlew nativeCompile`
2. 运行原生二进制测试性能
3. 创建 Release 触发 CI/CD

## 性能对比

| 模式 | 启动时间 | 内存占用 | 构建时间 | 适用场景 |
|------|---------|---------|---------|---------|
| JVM 开发模式 | ~5秒 | ~200MB | ~30秒 | 日常开发 |
| JAR 包 | ~3秒 | ~150MB | ~1分钟 | 功能测试 |
| Native Image | <1秒 | ~50MB | ~15分钟 | 生产部署 |

## 相关文档

- [开发者指南](DEVELOPER_GUIDE.md)
- [Git 工作流](GIT_WORKFLOW.md)
- [GitHub Actions 配置](.github/workflows/builder.yml)
