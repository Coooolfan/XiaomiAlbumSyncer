---
name: jvm-live-debug
description: 在无 GUI 调试器的 CLI 环境中，对运行中的 XAS 服务端（Solon，`./gradlew run` 启动）做交互式断点调试。通过 JDWP + jdb + tmux 组合：run 任务暴露 5005 端口，jdb 跑在持久 tmux session 里，由 send-keys/capture-pane 跨调用读写 REPL 状态。适用于：(1) 设断点观察请求现场；(2) 在断点处求值/调用方法；(3) 排查只能在运行时复现的问题（如同步流水线、小米云接口交互）。
---

## 适用场景

当需要在运行中的 server 进程上设断点、看变量、调方法，而没有 IDEA 等 GUI 可用时使用本流程。不适合长时间多线程 step 调试——那种场景效率远不如让用户开 IDEA。

## 完整流程

### 1. 启动后端（带 JDWP）

服务端使用 Gradle `application` 插件，主类 `com.coooolfan.xiaomialbumsyncer.App`。

```bash
mkdir -p /tmp/xas-debug
cd server && ./gradlew run --debug-jvm > /tmp/xas-debug/server.log 2>&1
```

工具调用上放到后台执行。然后等待日志出现 `Listening for transport dt_socket at address: 5005`，再做下一步。

`--debug-jvm` 默认 `suspend=y`，JVM 会等调试器附加后才真正启动 Solon。不想 suspend 时改用：

```bash
./gradlew run -PjvmArgs='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'
```

但 suspend=y 更好用——延迟断点能在类加载前就挂上。

### 2. 用 tmux 启动 jdb

```bash
tmux kill-session -t jdb 2>/dev/null
tmux new-session -d -s jdb -x 200 -y 50 'jdb -attach localhost:5005'
sleep 3
tmux capture-pane -t jdb -p -S -50
```

attach 成功后看到 `VM 已启动` 和 `main[1]` 提示符，说明 JVM 已经从 suspend 状态接管。

### 3. 设断点 → cont

```bash
tmux send-keys -t jdb 'stop at com.coooolfan.xiaomialbumsyncer.service.AssetService:120' Enter
sleep 1
tmux send-keys -t jdb 'cont' Enter
```

类还没加载时 jdb 会显示 `正在延迟断点 ... 将在加载类后设置`，正常现象。

### 4. 等待服务启动完成

等服务端日志出现 Solon 的启动完成行（`Started:` / `started ... ms`）或端口监听信息即可。

### 5. 命中后取现场

请求把断点打中后，tmux pane 会出现 `Breakpoint hit:` 和当前栈，提示符变成 `<thread-name>[1]`。

```bash
tmux send-keys -t jdb 'locals' Enter         # 局部变量
tmux send-keys -t jdb 'print someVar' Enter  # 求值表达式
tmux send-keys -t jdb 'where' Enter          # 调用栈
sleep 2
tmux capture-pane -t jdb -p | grep -v '^$' | tail -30
```

### 6. 放行 + 清理

```bash
tmux send-keys -t jdb 'cont' Enter           # 放当前断点
tmux kill-session -t jdb                     # 退出 jdb（不杀 JVM）
# 再停掉后台的 gradlew run / yarn dev 任务
```

## jdb 常用命令速查

| 命令 | 作用 |
|---|---|
| `stop at FQCN:LINE` | 在某文件某行设断点 |
| `stop in FQCN.method` | 在方法入口设断点 |
| `clear` | 列出所有断点 |
| `clear FQCN:LINE` | 删除断点 |
| `cont` | 继续执行 |
| `step` / `next` | 单步进入 / 跳过 |
| `step up` | 执行到当前方法返回 |
| `locals` | 当前栈帧的局部变量和方法参数 |
| `print EXPR` / `eval EXPR` | 求值表达式（可调用方法，见下） |
| `where` | 当前线程调用栈 |
| `up` / `down` | 切换栈帧 |
| `threads` / `thread ID` | 列线程 / 切线程 |
| `exit` | 退出 jdb（不杀被调试 JVM） |

## 关键技巧

### Kotlin 顶层 / 扩展函数的类名

文件 `XiaomiCloudApi.kt` 中的顶层函数或扩展函数会被编译成 `<FileName>Kt` 类的静态方法：

- `package com.coooolfan.xiaomialbumsyncer.xiaomicloud` 下 `XiaomiCloudApi.kt` 的扩展函数
- 设断点用 `com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiCloudApiKt:行号`，**不是** `XiaomiCloudApi`

### 在断点处调用方法

`print` / `eval` 可以求值任意表达式，包括调用方法：

```
print this.sql.entities.findById(Album::class.java, 1L)
```

`this.field` 必须显式带 `this.`——jdb 不会自动解析隐式 receiver。返回值会原样打印。

### 协程栈不好看

服务端大量使用 kotlinx.coroutines（下载流水线）。挂起函数在 jdb 里的栈是状态机形态，`where` 看到的是 `invokeSuspend`；跨挂起点的 step 基本无意义，优先在挂起点之间的同步片段设断点。

### 延迟断点

JVM 是 lazy class loading：服务刚启动时大量类还没加载。`stop at` 一个未加载类的位置时 jdb 会 defer，等类加载时再激活。所以**任何时候都可以下断点**，不必担心时序。

### tmux capture-pane 看不到东西时

`capture-pane -p` 默认只看可见区域。pane 越大空白行越多，输出可能被 tail 掉。两个办法：

- `tmux capture-pane -t jdb -p -S -50 | grep -v '^$' | tail -30`（带 scrollback、过滤空行）
- 创建 session 时给小一点的尺寸：`tmux new-session -d -s jdb -x 200 -y 50 ...`

## 踩坑记录

### `run --debug-jvm` 默认 suspend=y

JVM 启动后会卡在 `Listening for transport ...`，**不附加 jdb 永远不会进入 Solon 启动流程**。所以必须：(1) 启动 run → (2) 等 JDWP listener → (3) attach jdb → (4) JVM 才开始真正启动。

### zsh glob 空目录会让整条命令失败

```bash
mkdir -p /tmp/x && rm -f /tmp/x/*.log    # 目录刚建好没文件时整条命令 exit 1
```

zsh 默认 `nomatch` 开启。要么先创建占位文件，要么把 `rm` 分开写，要么避免用 glob。

### `monitor where` 副作用很吵

`monitor where` 会让**每次断点命中**都打印整个栈。一旦开了就用 `unmonitor 1` 关掉。命中后真要看栈用一次性的 `where`。

### jdb 中文版 `where N` 不工作

`where 5` 在中文版 jdb 里会被解释成「线程 ID 5」并报错。直接用 `where` 看全栈，或者用 `up`/`down` 切帧。

### 命中后请求会挂住

JVM 被 jdb 暂停了，发起这次请求的 HTTP 连接也卡在那里，前端可能报超时。处理完务必 `cont` 放行。若命中点在定时任务或下载流水线中，还可能触发上游超时重试，注意甄别。

## 完整模板

```bash
# === 1. 启动后端 (后台) ===
mkdir -p /tmp/xas-debug && cd /Users/yang/Documents/code/XiaomiAlbumSyncer/server && \
  ./gradlew run --debug-jvm > /tmp/xas-debug/server.log 2>&1

# === 2. 等 JDWP listener ===
until grep -qE "Listening for transport|BUILD FAILED|FAILURE:" /tmp/xas-debug/server.log; do sleep 1; done

# === 3. tmux + jdb ===
tmux kill-session -t jdb 2>/dev/null
tmux new-session -d -s jdb -x 200 -y 50 'jdb -attach localhost:5005'
sleep 3

# === 4. 设断点 + cont ===
tmux send-keys -t jdb 'stop at com.coooolfan.xiaomialbumsyncer.service.AlbumsService:30' Enter
sleep 1
tmux send-keys -t jdb 'cont' Enter

# === 5. 启动前端 (后台) ===
cd /Users/yang/Documents/code/XiaomiAlbumSyncer/web && yarn dev > /tmp/xas-debug/web.log 2>&1

# === 6. 等服务端 + Vite 就绪 ===
until grep -qiE "started|Solon" /tmp/xas-debug/server.log && \
      grep -q "Local:.*http" /tmp/xas-debug/web.log; do sleep 2; done

# === 7. 等用户触发，命中后取现场 ===
tmux send-keys -t jdb 'locals' Enter
sleep 1
tmux capture-pane -t jdb -p | grep -v '^$' | tail -30

# === 8. 放行 + 清理 ===
tmux send-keys -t jdb 'cont' Enter
tmux kill-session -t jdb
# 然后停掉后台的 gradlew run / yarn dev
```
