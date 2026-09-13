# MCP 服务

Xiaomi Album Syncer 内置 MCP（Model Context Protocol）服务端点，AI 助手或任意 MCP 客户端可通过它查询相册、同步任务与运行历史，并按权限触发任务立即执行。

## 功能概述

- **端点**：`/mcp`，Streamable HTTP 通道，与服务端同一端口（默认 `8080`）
- **单工具**：仅暴露 `xas_query`，以 `domain` + `action` 信封覆盖全部查询与操作
- **独立鉴权**：`Authorization: Bearer <token>`，Token 在 Web UI 中按需创建，权限粒度到单个 Token
- **脱敏输出**：不暴露密码、passToken、通知配置等敏感信息

## MCP Token 管理

在 Web UI「设置」页的「MCP Token 管理」卡片中创建、查看与撤销 Token。

- Token 由服务端生成，原文只在创建成功弹窗中显示一次，请立即保存
- 服务端仅保存 Token 的 SHA-256 哈希，丢失后无法找回，需撤销并重新创建
- 权限在创建时固定；如需变更，请撤销后重新创建

| 权限 | list / get | trigger |
| --- | --- | --- |
| 只读 | ✅ | ❌ |
| 允许触发任务 | ✅ | ✅ |

## 客户端接入

端点地址（`SERVER_PORT` 默认为 `8080`）：

```text
http://<host>:<SERVER_PORT>/mcp
```

所有请求需携带：

```text
Authorization: Bearer xas_mcp_xxxxxxxx
```

通用 MCP 客户端配置示例（Streamable HTTP）：

```json
{
  "mcpServers": {
    "xiaomi-album-syncer": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer xas_mcp_xxxxxxxx"
      }
    }
  }
}
```

Claude Code CLI：

```bash
claude mcp add --transport http xiaomi-album-syncer http://localhost:8080/mcp \
  --header "Authorization: Bearer xas_mcp_xxxxxxxx"
```

> 前端开发服务器（Vite）仅代理 `/api`，不代理 `/mcp`，请直连服务端口。

## xas_query 工具

所有调用共用一个工具，通过 `domain`（资源域）与 `action`（操作）组合选择能力，可选 `id` 与分页参数。

### domain × action 支持矩阵

| domain | 说明 | list | get | trigger | `id` 语义 |
| --- | --- | --- | --- | --- | --- |
| `album` | 相册 | ✅ | — | — | 不支持 |
| `crontab` | 同步任务 | ✅ | ✅ | ✅¹ | 任务 id（get / trigger 必填） |
| `crontab_history` | 运行历史 | ✅ | — | — | 任务 id（可选，按任务过滤） |
| `crontab_history_detail` | 运行明细 | ✅ | — | — | 运行历史 id（必填） |
| `system` | 账号与系统信息 | ✅ | — | — | 不支持 |

¹ 需要 Token 权限为「允许触发任务」；任务正在运行时重复触发会返回 `triggered=false`，不会产生第二个执行实例。

### 分页参数

| 参数 | 说明 |
| --- | --- |
| `pageIndex` | 页码，从 0 开始，默认 0 |
| `pageSize` | 每页条数，默认 50，上限 200 |

仅 `crontab_history` 与 `crontab_history_detail` 分页生效，响应中包含 `totalCount`、`pageIndex`、`pageSize`。

### 调用示例

```json
{ "domain": "crontab", "action": "list" }
```

```json
{ "domain": "crontab", "action": "trigger", "id": "3" }
```

```json
{ "domain": "crontab_history_detail", "action": "list", "id": "12", "pageIndex": 0, "pageSize": 100 }
```

### 输出约定

- 每次响应附带 `hint` 字段，提示下一步可用的 domain/action 组合
- 时间字段输出 ISO-8601 字符串；`currentStats.ts` 为毫秒时间戳
- 各 domain 输出概要：
  - `album`：id、remoteId、名称、资产数、最后更新时间、`shadow`（远程已不存在的本地相册）、所属账号昵称
  - `crontab` list：id、名称、启用状态、运行状态、最近运行时间；get 额外返回描述、相册 id 列表、完整 `config` 与实时统计 `currentStats`
  - `crontab_history`：id、任务 id 与名称、起止时间、是否完成、明细条数
  - `crontab_history_detail`：运行概况 + 每条明细的文件名/类型/所属相册、保存路径，以及下载完成、SHA1 校验、EXIF 填充、文件系统时间更新各阶段状态与附加消息
  - `system`：账号昵称与 userId 列表、是否已初始化、资产日期映射时区、应用版本

## 注意事项

### 安全性

- Token 等同于访问凭据，明文经网络传输；生产环境务必启用 HTTPS，参考 [SSL 支持文档](./ssl-suppot.md)
- 未携带 Token 或 Token 无效时，端点返回 401
- 撤销立即生效，使用该 Token 的客户端即刻失去访问能力

### 行为

- `trigger` 为异步触发：`triggered=true` 仅表示任务已开始执行，不代表已完成；通过 `crontab` get 的 `currentStats` 或 `crontab_history` 跟踪进度
- `album` 与 `system` 返回本地数据库快照，不会向小米云发起请求
- 业务参数错误（如 domain/action 组合不支持、`id` 缺失或非法）以工具错误结果返回，不会产生副作用

## 相关资源

- [Model Context Protocol 官方文档](https://modelcontextprotocol.io/)
