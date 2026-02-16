# Contributing Guide

感谢你为 Xiaomi Album Syncer 贡献代码。在提交 PR 之前，请阅读以下说明。

## 基本要求

- 你确认你有权提交该贡献（不存在雇主或第三方权利冲突）。
- 你同意本项目的许可证（当前为 GPL-3.0）并接受 CLA。详见 `CLA.md`。
- 如提交中包含第三方代码或资源，请在 PR 描述中注明来源与许可证，并确保与 GPL-3.0 兼容。

## 开发与测试

本仓库为前后端分离的 monorepo：

- 后端：`server`（Solon + Gradle + Kotlin）
- 前端：`web`（Vue + TypeScript + Vite）

提交 PR 前请确保以下命令通过：

```bash
cd server
./gradlew clean solonJar
```

```bash
cd web
yarn lint
yarn format
yarn build
```

## 贡献流程

1. 确保你已阅读并同意 `CLA.md`。
2. Fork 仓库并创建分支。
3. 完成修改后提交 PR，并在描述中说明变更内容。

## 许可证与 CLA

- 本项目代码对外发布遵循 GPL-3.0（以根目录 `LICENSE` 为准）。
- 所有贡献默认按 GPL-3.0 许可发布，同时你同意 `CLA.md` 中的授予条款。
