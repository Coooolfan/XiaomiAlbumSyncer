# CLAUDE.md

本文档为Claude Code (claude.ai/code) 提供在此代码库中工作的指导。

## 项目概述

Xiaomi Album Syncer Web 是一个基于 Vue 3 的前端应用程序，提供用于管理小米云相册同步的 Web 界面。具有认证、仪表板导航和背景动画组件等功能。

## 架构

- **框架**: Vue 3 with Composition API and TypeScript
- **构建工具**: Vite with Rolldown
- **UI 框架**: PrimeVue with Aura theme
- **状态管理**: Pinia
- **路由**: Vue Router
- **样式**: Tailwind CSS v4
- **背景动画**: 自定义 WebGL 组件 (Silk 和 LightRays)

## 关键组件

- **App.vue**: 根组件，提供背景动画
- **AuthPage.vue**: 认证和初始化页面
- **DashboardLayout.vue**: 主仪表板布局，包含导航
- **SettingPage.vue**: 应用程序设置界面
- **背景组件**: Silk.vue 和 LightRays.vue，用于动画背景
- **ApiInstance.ts**: API 客户端，处理认证

## 开发命令

**运行开发服务器:**
```bash
npm run dev
```

**构建生产版本:**
```bash
npm run build
```

**类型检查:**
```bash
npm run type-check
```

**代码检查:**
```bash
npm run lint
```

**代码格式化:**
```bash
npm run format
```

**API 代码生成:**
```bash
npm run api
```

## API 集成

前端通过 `src/__generated/` 中的自动生成 API 客户端与后端服务器通信。API 实例处理：
- 认证令牌管理
- 错误处理 (401 重定向，500 警报)
- 请求/响应序列化

## 背景动画系统

提供两种背景模式：
- **LightRays**: 动态光线动画，支持鼠标交互
- **Silk**: 丝绸般流畅动画

背景偏好设置保存在 localStorage 中，并通过 Vue 的 provide/inject 系统管理。

## 路由结构

- `/` - 认证页面 (仅限访客)
- `/dashboard` - 主仪表板布局
  - `/dashboard/setting` - 设置页面
  - `/dashboard/schedule` - 计划管理页面

## 样式方法

- Tailwind CSS 用于实用优先的样式
- PrimeVue 组件确保一致的 UI
- 自定义 CSS 用于背景动画和过渡效果
- 响应式设计，移动优先方法

## 开发服务器配置

Vite 配置包含：
- Vue 3 插件与开发工具
- Tailwind CSS 集成
- API 代理到后端服务器 (localhost:8080)
- 路径别名 (@ -> src/)

## TypeScript 配置

- 严格的 TypeScript 配置
- Vue 特定的 TypeScript 支持
- 导入路径映射
- 现代 ES 模块解析