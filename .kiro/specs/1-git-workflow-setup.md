# Spec 1: Git 分支开发工作流设置

## 目标
在 fork 的 XiaomiAlbumSyncer 项目上建立规范的 Git 分支开发流程，支持独立功能开发和版本管理。

## 当前状态
- 已从 https://github.com/Acckion/XiaomiAlbumSyncer fork 并 clone 到本地
- 当前在 main 分支
- origin 指向自己的 fork 仓库

## 需要完成的任务

### 1. 添加上游仓库
将原始仓库设置为 upstream，方便后续同步更新

### 2. 创建开发分支
- 保持 main 分支干净，用于同步上游
- 创建 develop 分支作为主开发分支
- 为每个新功能创建独立的 feature 分支

### 3. 建立分支管理规范
```
main (同步上游)
  └── develop (主开发分支)
       ├── feature/功能名称-1
       ├── feature/功能名称-2
       └── feature/功能名称-3
```

## 工作流程

### 日常开发
1. 从 develop 创建新的 feature 分支
2. 在 feature 分支上开发和提交
3. 完成后合并回 develop
4. 定期将 develop 推送到自己的 fork

### 同步上游更新
1. 从 upstream/main 拉取最新代码到本地 main
2. 将 main 的更新合并到 develop
3. 解决可能的冲突

## 分支命名规范
- `feature/功能描述` - 新功能开发
- `fix/问题描述` - Bug 修复
- `refactor/重构描述` - 代码重构
- `docs/文档描述` - 文档更新
