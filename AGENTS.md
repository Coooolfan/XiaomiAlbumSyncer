Xiaomi Album Syncer : 全量/增量/定时 下载小米云服务中的 相册/录音 到本地

# 项目结构

此文件夹为项目根目录。使用 monorepo 管理多个工程，前后端分离

# 项目概述

- **后端**: 于 `./server` 文件夹. Solon, Gradle, Kotlin, JVM 25.
  - 除非用户要求，每次更改后都应当执行`./gralew clean solonJar`以确保编译通过，如果因为JVM版本不兼容导致编译失败，请用户自行解决
- **前端**: 于 `./web` 文件夹. Vue, TypeScript, Vite, Pinia, Tailwind CSS.
  - 除非用户要求，每次更改后都应当执行`yarn lint`, `yarn format`, `yarn build`以确保编译通过
