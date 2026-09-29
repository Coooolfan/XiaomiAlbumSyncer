# iCloud 接入调研

调研日期：2026-09-20

评估为项目新增 iCloud 相册同步能力的可行性：同类项目、许可证兼容性、鉴权与续期机制。

## 同类项目

| 项目 | 技术栈 | License | 特点 |
|---|---|---|---|
| [icloudpd](https://github.com/icloud-photos-downloader/icloud_photos_downloader) | Python CLI | MIT | 事实标准（~12k stars）。Copy / Sync(`--auto-delete`) / Move(`--keep-icloud-recent-days`) 三种模式；`--watch-with-interval` 定时；`--until-found`/`--recent` 增量；`--album` 按相册 |
| [docker-icloudpd](https://github.com/boredazfcuk/docker-icloudpd) | Docker 封装 icloudpd | — | NAS 常驻场景：多账号、HEIC→JPG、Telegram/DingTalk/WeCom 等通知 |
| [icloud-photos-sync](https://github.com/steilerDev/icloud-photos-sync) | TypeScript | GPL-3.0 | 架构与本项目最像：单向同步 + WebUI + REST API + cron daemon |
| [darwin-photos](https://github.com/cleanexit0/darwin-photos) | macOS 专用 | — | 直读本地 `Photos.sqlite`，本地文件直拷 + 云端并行下载 |
| [osxphotos](https://github.com/RhetTbull/osxphotos) | Python | — | 操作本地 Photos 图库，`--download-missing` 拉云端原件 |
| [pyicloud](https://github.com/picklepete/pyicloud) | Python 库 | MIT | icloudpd 底层依赖，iCloud 私有 API 客户端 |
| Parachute Backup | macOS 商业应用 | — | 买断制，全量/增量/镜像备份 |

无 JVM 端 iCloud Photos 客户端库可依赖（dav4jvm/iCalDAV 走 CalDAV/WebDAV，hfhbd/CloudKitClient 仅支持公共库 serverKey 认证），Kotlin 客户端需自行实现。[Notes-Of-Fruit](https://github.com/ericmigo/Notes-Of-Fruit)（Android/Kotlin，cookie 认证打 `ckdatabasews` 私有库）是 JVM 上最近的同协议族参考。

## 许可证结论

本项目为 GPL-3.0，且 `CLA.md` 约定贡献可在项目未来选择的许可证下发布。

- **pyicloud / icloudpd（MIT）**：与 GPL 兼容，可参考甚至近似移植，保留 MIT 出处声明（文件头注释或 NOTICE 文件）；
- **icloud-photos-sync（GPL-3.0）**：GPL→GPL 直接复制合法，但会引入无法随项目换 license 的外来版权代码，与 CLA 承诺冲突——建议仅作架构/产品形态参考；
- 跨语言逐行翻译在法律上仍属衍生作品，分界不在语言而在「学逻辑 vs 抄表达」。Python/TS → Kotlin 重写协议交互属于参考实现，无许可证障碍。

## iCloud 鉴权模型

三层时效：

1. **Web session**（cookie + `dsWebAuthToken`）：约 8 小时；
2. **Trust token**（2FA 通过后 `GET idmsa/appleauth/auth/2sv/trust` 换取）：约 30 天；
3. **MFA 挑战**：trust token 到期后 Apple 强制重新验证，服务端政策，任何工具无法绕过。

### 登录凭据

- 登录请求体始终为 `accountName + password + trustTokens[]`——**Apple ID + 密码是必需的**；
- trust token 是压制 MFA 的附件，不能替代密码，属于内部持久化状态，不应暴露为用户输入项；
- icloud-photos-sync 额外暴露 `-T/--trust-token` 用于无头部署（预先在别处用 `icloud-photos-sync token` 换好后注入），密码仍必填；
- **无扫码登录**：Apple 认证体系没有扫码入口。Passkey/Touch ID 登录 icloud.com 属 WebAuthn 浏览器仪式，session 无法移交服务端；Sign in with Apple 仅给 identity token，无数据访问权。

### 续期机制（以 pyicloud 实现为准）

- 持久化：cookie jar + session JSON（`session_token`/`trust_token`/`scnt`/`session_id`），每个响应自动捕获 `X-Apple-Session-Token`、`X-Apple-TwoSV-Trust-Token` 等头落盘；
- `authenticate()` 降级链：
  1. `POST setup.icloud.com/setup/ws/1/validate` 验 session_token → 有效则零交互；
  2. session 失效 → 密码登录携带 `trustTokens:[...]` → `hsaTrustedBrowser=true`，不触发 MFA；
  3. trust token 也失效 → 完整 2FA → `trust_session()` 换新 trust token；
- 请求中 421/450/500 → `authenticate(force_refresh=True)` 自动重试一次；
- icloudpd 补充：keyring 存密码使静默重登完全非交互；docker-icloudpd 在需 MFA 时推送通知；
- icloud-photos-sync 把鉴权做成一等状态机（`MFA_REQUIRED`/`SESSION_EXPIRED`/`DEVICE_TRUSTED`…），WebUI 提供 Renew authentication 流程 + `POST /api/reauthenticate`，值得照抄其产品形态。

### 产品化含义

「~30 天一次 MFA」是 Apple 硬约束，应设计为可预期的周期性用户操作而非异常：鉴权状态机 + re-auth API + 前端引导输码 + 通知渠道提醒。另需处理 `CheckIndexingState`（新账号需等待索引完成）与 ADP（高级数据保护）账号不可用的硬限制。

## 需实现的协议模块（→ Kotlin）

| 模块 | 参考位置 |
|---|---|
| Apple ID 认证：idmsa 登录 → 2FA/2SA → trust → cookie 会话 | `pyicloud/base.py`；icps 公开 [Postman 集合与流程文档](https://icps.steiler.dev/dev/api/) |
| webservices 端点解析（`webservices.ckdatabasews.url`） | `pyicloud/base.py` |
| Photos 查询：`{ckdatabasews}/database/1/com.apple.photos.cloud/production/private`，zone `PrimarySync`，`CheckIndexingState` | `pyicloud/services/photos.py` |
| 相册结构：SMART_FOLDERS + `CPLContainerRelationNotDeletedByAssetDate` | 同上 |
| 增量同步：`getCurrentSyncToken` + `/changes/zone` syncToken 机制 | [kei/docs/synctoken-reference.md](https://github.com/rhoopr/kei/blob/main/docs/synctoken-reference.md) |
| 资产下载 URL（`CPLAsset` → derivative） | `pyicloud/services/photos.py` + icloudpd 下载管线 |

## 结论

- 许可证无障碍：以 pyicloud/icloudpd（MIT）为代码参考，icloud-photos-sync 作架构参考，移植处保留 MIT 声明；
- 登录交互 = Apple ID + 密码 + 条件性 2FA 码，trust token 内部持久化；
- 鉴权生命周期照 icps 模式做成状态机，30 天 MFA 是设计内事件；
- 录音类内容 iCloud 侧无等价物，仅覆盖照片/视频。
