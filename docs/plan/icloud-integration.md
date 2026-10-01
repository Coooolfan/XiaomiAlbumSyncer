# iCloud 接入调研

调研日期：2026-09-20

使用方式与支持范围见 [iCloud 相册同步](../icloud.md)。

评估为项目新增 iCloud 相册同步能力的可行性：同类项目、许可证兼容性、鉴权与续期机制。

## 同类项目

| 项目 | 技术栈 | License | 特点 |
|---|---|---|---|
| [icloudpd](https://github.com/icloud-photos-downloader/icloud_photos_downloader) | Python CLI | MIT | 事实标准（~12k stars）。Copy / Sync(`--auto-delete`) / Move(`--keep-icloud-recent-days`) 三种模式；`--watch-with-interval` 定时；`--until-found`/`--recent` 增量；`--album` 按相册 |
| [kei](https://github.com/rhoopr/kei)（原 `icloudpd-rs`） | Rust CLI | MIT | 并行下载、断点续传与定时运行；支持按日期、相册、智能分类、共享图库和未归入相册的照片组织目录；支持 Live Photo、RAW 和编辑版本；实现 CloudKit `syncToken` 图库增量，按 zone 保存位点，支持相册成员筛选与失效回退 |
| [docker-icloudpd](https://github.com/boredazfcuk/docker-icloudpd) | Docker 封装 icloudpd | — | NAS 常驻场景：多账号、HEIC→JPG、Telegram/DingTalk/WeCom 等通知 |
| [icloud-photos-sync](https://github.com/steilerDev/icloud-photos-sync) | TypeScript | GPL-3.0 | 架构与本项目最像：单向同步 + WebUI + REST API + cron daemon |
| [darwin-photos](https://github.com/cleanexit0/darwin-photos) | macOS 专用 | — | 直读本地 `Photos.sqlite`，本地文件直拷 + 云端并行下载 |
| [osxphotos](https://github.com/RhetTbull/osxphotos) | Python | — | 操作本地 Photos 图库，`--download-missing` 拉云端原件 |
| [pyicloud](https://github.com/picklepete/pyicloud) | Python 库 | MIT | iCloud 私有 API 客户端；icloudpd 使用仓库内维护的 `pyicloud_ipd` 分支 |
| Parachute Backup | macOS 商业应用 | — | 买断制，全量/增量/镜像备份 |

无 JVM 端 iCloud Photos 客户端库可依赖（dav4jvm/iCalDAV 走 CalDAV/WebDAV，hfhbd/CloudKitClient 仅支持公共库 serverKey 认证），Kotlin 客户端需自行实现。[Notes-Of-Fruit](https://github.com/ericmigo/Notes-Of-Fruit)（Android/Kotlin，cookie 认证打 `ckdatabasews` 私有库）是 JVM 上最近的同协议族参考。

## 许可证结论

本项目为 GPL-3.0，且 `CLA.md` 约定贡献可在项目未来选择的许可证下发布。

- **pyicloud / icloudpd（MIT）**：与 GPL 兼容，可参考甚至近似移植，保留 MIT 出处声明（文件头注释或 NOTICE 文件）；
- **icloud-photos-sync（GPL-3.0）**：GPL→GPL 直接复制合法，但会引入无法随项目换 license 的外来版权代码，与 CLA 承诺冲突——建议仅作架构/产品形态参考；
- 跨语言逐行翻译在法律上仍属衍生作品，分界不在语言而在「学逻辑 vs 抄表达」。近似移植或翻译仍需遵守来源许可证，保留版权及完整许可声明。

## iCloud 鉴权模型

三层时效：

1. **Web session**（cookie + `dsWebAuthToken`）：按服务端响应判断有效性；
2. **Trust token**（2FA 通过后 `GET idmsa/appleauth/auth/2sv/trust` 换取）：有效期由 Apple 决定；
3. **MFA 挑战**：trust token 到期后 Apple 强制重新验证，服务端政策，任何工具无法绕过。

### 登录凭据

- 当前 icloudpd 使用 `/signin/init` → `/signin/complete` 的 SRP 流程（`s2k/s2k_fo`），完成请求提交证明值和 `trustTokens[]`；**用户仍需提供 Apple 账号与密码**；
- trust token 是压制 MFA 的附件，不能替代密码，属于内部持久化状态，不应暴露为用户输入项；
- icloud-photos-sync 额外暴露 `-T/--trust-token` 用于无头部署（预先在别处用 `icloud-photos-sync token` 换好后注入），密码仍必填；
- **无扫码登录**：Apple 认证体系没有扫码入口。Passkey/Touch ID 登录 icloud.com 属 WebAuthn 浏览器仪式，session 无法移交服务端；Sign in with Apple 仅给 identity token，无数据访问权。

### 续期机制（以 icloudpd 的 pyicloud_ipd 实现为参考）

- 持久化：cookie jar + session JSON（`session_token`/`trust_token`/`scnt`/`session_id`），每个响应自动捕获 `X-Apple-Session-Token`、`X-Apple-TwoSV-Trust-Token` 等头落盘；
- `authenticate()` 降级链：
  1. `POST setup.icloud.com/setup/ws/1/validate` 验 session_token → 有效则零交互；
  2. session 失效 → 密码登录携带 `trustTokens:[...]` → `hsaTrustedBrowser=true`，不触发 MFA；
  3. trust token 也失效 → 完整 2FA → `trust_session()` 换新 trust token；
- 请求中 421/450/500 → `authenticate(force_refresh=True)` 自动重试一次；
- icloudpd 补充：keyring 存密码使静默重登完全非交互；docker-icloudpd 在需 MFA 时推送通知；
- icloud-photos-sync 把鉴权做成一等状态机（`MFA_REQUIRED`/`SESSION_EXPIRED`/`DEVICE_TRUSTED`…），WebUI 提供 Renew authentication 流程 + `POST /api/reauthenticate`，值得照抄其产品形态。

### 产品化含义

Apple 可以随时要求重新 MFA，应将其设计为正常用户操作：鉴权状态机 + re-auth API + 前端引导输码 + 通知渠道提醒。另需处理 `CheckIndexingState`（新账号需等待索引完成）与 网页数据访问权限与当前后台协议不支持 ADP（高级数据保护）的限制。

## 需实现的协议模块（→ Kotlin）

| 模块 | 参考位置 |
|---|---|
| Apple ID 认证：idmsa 登录 → 2FA/2SA → trust → cookie 会话 | `icloudpd/src/pyicloud_ipd/base.py`；icps 公开 [Postman 集合与流程文档](https://icps.steiler.dev/dev/api/) |
| webservices 端点解析（`webservices.ckdatabasews.url`） | `icloudpd/src/pyicloud_ipd/base.py` |
| Photos 查询：`{ckdatabasews}/database/1/com.apple.photos.cloud/production/private`，zone `PrimarySync`，`CheckIndexingState` | `icloudpd/src/pyicloud_ipd/services/photos.py` |
| 相册结构：SMART_FOLDERS + `CPLContainerRelationNotDeletedByAssetDate` | 同上 |
| 增量同步：`getCurrentSyncToken` + `/changes/zone` syncToken 机制 | [kei/docs/synctoken-reference.md](https://github.com/rhoopr/kei/blob/main/docs/synctoken-reference.md) |
| 资产下载 URL（`CPLAsset` → derivative） | `icloudpd/src/pyicloud_ipd/services/photos.py` + icloudpd 下载管线 |

## iCloud 资产下载请求时序（当前实现）

以下 HTML 内嵌 SVG 时序图按当前 Kotlin 客户端与前端调用路径绘制。实线为请求或本地调用，虚线为响应，黄色条为条件分支、循环或本地处理。四幅图依次描述登录、相册与资产查询、文件下载、公共认证与重试。SVG 需要支持内嵌 HTML/SVG 的 Markdown 预览器。

| 图中前缀 | 请求地址 |
|---|---|
| `AUTH` | 国际：`https://idmsa.apple.com/appleauth/auth`；大陆：`https://idmsa.apple.com.cn/appleauth/auth` |
| `SETUP` | 国际：`https://setup.icloud.com/setup/ws/1`；大陆：`https://setup.icloud.com.cn/setup/ws/1` |
| `CK` | `{webservices.ckdatabasews.url}/database/1/com.apple.photos.cloud/production/private` |
| `downloadURL` | `records/lookup` 返回的资源签名地址，按需获取，不持久化 |

所有 `CK` 请求均使用 POST，正文是 JSON，`Content-Type: text/plain`，查询参数为 `clientId`、`dsid`、`getCurrentSyncToken=true`、`remapEnums=true`。位点模式消费 `syncToken` 并调用 `/changes/zone`；全量模式仍使用列表枚举。认证与 CloudKit 使用按账号隔离的 cookie；认证请求额外携带 Apple OAuth 头及已保存的 `scnt`、`X-Apple-ID-Session-Id`。各响应自动更新 cookie 与相关 session/trust 响应头。

XAS 数据库是本地 SQLite。`provider_account` 承载来源通用的账号信息，`credentials` JSON 列保存来源凭据与认证状态；`album`、`asset` 保存云端引用；`crontab_history`、`crontab_history_detail` 保存任务与各处理阶段状态。凭据直接存为 JSON，无需密钥文件；下载文件保存在本地文件系统。数据库箭头表示本地 ORM 读写，不是 HTTP 请求。

### 登录与设备双重认证

<div>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1800 3312" width="100%" role="img" aria-labelledby="icloud-auth-title">
<title id="icloud-auth-title">登录与设备双重认证</title>
<defs><marker id="icloud-auth-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#334155" /></marker></defs>
<rect width="1800" height="3312" rx="12" fill="#f8fafc" />
<g font-family="system-ui, sans-serif" font-size="15" fill="#0f172a">
<rect x="20" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="150" y="43" text-anchor="middle" font-weight="600">用户 / 前端</text>
<line x1="150" y1="60" x2="150" y2="3292" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="360" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="490" y="43" text-anchor="middle" font-weight="600">XAS 后端</text>
<line x1="490" y1="60" x2="490" y2="3292" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="720" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="850" y="43" text-anchor="middle" font-weight="600">Apple Auth</text>
<line x1="850" y1="60" x2="850" y2="3292" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1080" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1210" y="43" text-anchor="middle" font-weight="600">iCloud Setup</text>
<line x1="1210" y1="60" x2="1210" y2="3292" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1520" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1650" y="43" text-anchor="middle" font-weight="600">XAS 数据库</text>
<line x1="1650" y1="60" x2="1650" y2="3292" stroke="#cbd5e1" stroke-dasharray="5 5" />
<text x="320.0" y="86" text-anchor="middle">POST /api/icloud/login</text>
<text x="320.0" y="105" text-anchor="middle" font-size="13" fill="#475569">appleId · password · domain · nickname · accountId?</text>
<line x1="150" y1="119" x2="490" y2="119" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="162" text-anchor="middle">读取 provider_account</text>
<text x="1070.0" y="181" text-anchor="middle" font-size="13" fill="#475569">按 accountId 或 provider=ICLOUD + Apple ID 查询</text>
<line x1="490" y1="195" x2="1650" y2="195" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="238" text-anchor="middle">已有账号 / 未创建账号</text>
<text x="1070.0" y="257" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="271" x2="490" y2="271" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="302" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="321" font-weight="600">已有账号：从 credentials JSON 恢复同区域 cookie、session / trust token</text>
<text x="40" y="341" font-size="13"></text>
<rect x="20" y="378" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="397" font-weight="600">本地：恢复同账号、同区域的 trust token；生成 SRP 公钥 A</text>
<text x="40" y="417" font-size="13">密码用于本地密码派生，Apple 登录请求提交 SRP 证明。</text>
<text x="670.0" y="466" text-anchor="middle">POST AUTH/signin/init</text>
<text x="670.0" y="485" text-anchor="middle" font-size="13" fill="#475569">accountName · a · protocols=[s2k, s2k_fo]</text>
<line x1="490" y1="499" x2="850" y2="499" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="542" text-anchor="middle">salt · b · iteration · protocol · c</text>
<text x="670.0" y="561" text-anchor="middle" font-size="13" fill="#475569">同时捕获 cookie、scnt、X-Apple-ID-Session-Id 等响应头</text>
<line x1="850" y1="575" x2="490" y2="575" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="606" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="625" font-weight="600">本地：根据挑战计算 m1、m2</text>
<text x="40" y="645" font-size="13"></text>
<text x="670.0" y="694" text-anchor="middle">POST AUTH/signin/complete</text>
<text x="670.0" y="713" text-anchor="middle" font-size="13" fill="#475569">?isRememberMeEnabled=true；m1 · m2 · c · trustTokens[]</text>
<line x1="490" y1="727" x2="850" y2="727" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="770" text-anchor="middle">HTTP 200 或 409</text>
<text x="670.0" y="789" text-anchor="middle" font-size="13" fill="#475569">响应头中的 session token / trust token 更新到账号会话</text>
<line x1="850" y1="803" x2="490" y2="803" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="834" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="853" font-weight="600">分支 A：complete 返回 409 → 直接触发设备验证码，不先调用 accountLogin</text>
<text x="40" y="873" font-size="13"></text>
<text x="670.0" y="922" text-anchor="middle">PUT AUTH/verify/trusteddevice/securitycode</text>
<text x="670.0" y="941" text-anchor="middle" font-size="13" fill="#475569">请求 Apple 向受信任设备发出验证码</text>
<line x1="490" y1="955" x2="850" y2="955" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="998" text-anchor="middle">新增 / 更新 provider_account</text>
<text x="1070.0" y="1017" text-anchor="middle" font-size="13" fill="#475569">provider=ICLOUD · user_id · nickname · credentials JSON / state</text>
<line x1="490" y1="1031" x2="1650" y2="1031" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="1074" text-anchor="middle">accountId</text>
<text x="1070.0" y="1093" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="1107" x2="490" y2="1107" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="1150" text-anchor="middle">读取账号与会话，构造 status</text>
<text x="1070.0" y="1169" text-anchor="middle" font-size="13" fill="#475569">响应仅含 accountId · state · domain</text>
<line x1="490" y1="1183" x2="1650" y2="1183" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="320.0" y="1226" text-anchor="middle">accountId · MFA_REQUIRED · domain</text>
<text x="320.0" y="1245" text-anchor="middle" font-size="13" fill="#475569">密码、cookie、token JSON 持久化；前端等待用户输码</text>
<line x1="490" y1="1259" x2="150" y2="1259" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="1290" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1309" font-weight="600">分支 B：complete 成功 → 查询实际信任状态；若仍需 MFA，执行上面的 PUT 并返回 MFA_REQUIRED</text>
<text x="40" y="1329" font-size="13"></text>
<text x="850.0" y="1378" text-anchor="middle">POST SETUP/accountLogin</text>
<text x="850.0" y="1397" text-anchor="middle" font-size="13" fill="#475569">dsWebAuthToken · trustToken · accountCountryCode · extended_login=true</text>
<line x1="490" y1="1411" x2="1210" y2="1411" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="850.0" y="1454" text-anchor="middle">dsInfo · hsaTrustedBrowser · hsaChallengeRequired</text>
<text x="850.0" y="1473" text-anchor="middle" font-size="13" fill="#475569">包含 webservices.ckdatabasews.url；受信任则直接 READY</text>
<line x1="1210" y1="1487" x2="490" y2="1487" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="1518" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1537" font-weight="600">分支 B：accountLogin 后按实际信任状态持久化；需要 MFA 时先触发设备验证码</text>
<text x="40" y="1557" font-size="13"></text>
<text x="1070.0" y="1606" text-anchor="middle">新增 / 更新 provider_account</text>
<text x="1070.0" y="1625" text-anchor="middle" font-size="13" fill="#475569">provider=ICLOUD · user_id · nickname · credentials JSON / state</text>
<line x1="490" y1="1639" x2="1650" y2="1639" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="1682" text-anchor="middle">accountId</text>
<text x="1070.0" y="1701" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="1715" x2="490" y2="1715" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="1758" text-anchor="middle">读取账号与会话，构造 status</text>
<text x="1070.0" y="1777" text-anchor="middle" font-size="13" fill="#475569">响应仅含 accountId · state · domain</text>
<line x1="490" y1="1791" x2="1650" y2="1791" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="320.0" y="1834" text-anchor="middle">accountId · READY 或 MFA_REQUIRED</text>
<text x="320.0" y="1853" text-anchor="middle" font-size="13" fill="#475569">READY 直接进入相册刷新；MFA_REQUIRED 继续下面的验证码流程</text>
<line x1="490" y1="1867" x2="150" y2="1867" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="1898" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1917" font-weight="600">需要双重认证时：用户输入六位验证码；已受信任的登录跳过以下验证步骤</text>
<text x="40" y="1937" font-size="13"></text>
<text x="320.0" y="1986" text-anchor="middle">POST /api/icloud/{id}/verify</text>
<text x="320.0" y="2005" text-anchor="middle" font-size="13" fill="#475569">code</text>
<line x1="150" y1="2019" x2="490" y2="2019" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="2062" text-anchor="middle">读取账号；客户端缓存未命中时读取会话</text>
<text x="1070.0" y="2081" text-anchor="middle" font-size="13" fill="#475569">校验 provider；解析 JSON 并恢复 cookie / token</text>
<line x1="490" y1="2095" x2="1650" y2="2095" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="2138" text-anchor="middle">账号与持久化会话</text>
<text x="1070.0" y="2157" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="2171" x2="490" y2="2171" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="2214" text-anchor="middle">POST AUTH/verify/trusteddevice/securitycode</text>
<text x="670.0" y="2233" text-anchor="middle" font-size="13" fill="#475569">securityCode.code</text>
<line x1="490" y1="2247" x2="850" y2="2247" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="2290" text-anchor="middle">验证码验证结果</text>
<text x="670.0" y="2309" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="2323" x2="490" y2="2323" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="2366" text-anchor="middle">GET AUTH/2sv/trust</text>
<text x="670.0" y="2385" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="490" y1="2399" x2="850" y2="2399" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="670.0" y="2442" text-anchor="middle">新的信任令牌 / cookie</text>
<text x="670.0" y="2461" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="2475" x2="490" y2="2475" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="850.0" y="2518" text-anchor="middle">POST SETUP/accountLogin</text>
<text x="850.0" y="2537" text-anchor="middle" font-size="13" fill="#475569">携带最新 session token 与 trust token</text>
<line x1="490" y1="2551" x2="1210" y2="2551" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="850.0" y="2594" text-anchor="middle">账号状态与动态 CloudKit 服务地址</text>
<text x="850.0" y="2613" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1210" y1="2627" x2="490" y2="2627" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="2670" text-anchor="middle">UPDATE provider_account.credentials</text>
<text x="1070.0" y="2689" text-anchor="middle" font-size="13" fill="#475569">保存最新凭据 JSON与 state=READY</text>
<line x1="490" y1="2703" x2="1650" y2="2703" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="2746" text-anchor="middle">读取账号与会话，构造 status</text>
<text x="1070.0" y="2765" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="490" y1="2779" x2="1650" y2="2779" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="320.0" y="2822" text-anchor="middle">accountId · READY · domain</text>
<text x="320.0" y="2841" text-anchor="middle" font-size="13" fill="#475569">持久化会话；错误验证码保留 MFA_REQUIRED</text>
<line x1="490" y1="2855" x2="150" y2="2855" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<rect x="20" y="2886" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="2905" font-weight="600">编辑 / 恢复认证弹窗：读取持久化状态，不请求 Apple</text>
<text x="40" y="2925" font-size="13"></text>
<text x="320.0" y="2974" text-anchor="middle">GET /api/icloud/{id}/status</text>
<text x="320.0" y="2993" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="150" y1="3007" x2="490" y2="3007" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="3050" text-anchor="middle">读取 provider_account</text>
<text x="1070.0" y="3069" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="490" y1="3083" x2="1650" y2="3083" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-auth-arrow)" />
<text x="1070.0" y="3126" text-anchor="middle">账号 · state · credentials JSON</text>
<text x="1070.0" y="3145" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="3159" x2="490" y2="3159" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
<text x="320.0" y="3202" text-anchor="middle">accountId · state · domain</text>
<text x="320.0" y="3221" text-anchor="middle" font-size="13" fill="#475569">后端解析 JSON 获取区域；不返回密码、cookie 或 token</text>
<line x1="490" y1="3235" x2="150" y2="3235" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-auth-arrow)" />
</g>
</svg>
</div>

### 相册发现与资产分页

<div>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1460 2856" width="100%" role="img" aria-labelledby="icloud-albums-title">
<title id="icloud-albums-title">相册发现与资产分页</title>
<defs><marker id="icloud-albums-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#334155" /></marker></defs>
<rect width="1460" height="2856" rx="12" fill="#f8fafc" />
<g font-family="system-ui, sans-serif" font-size="15" fill="#0f172a">
<rect x="20" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="150" y="43" text-anchor="middle" font-weight="600">前端 / 调度器</text>
<line x1="150" y1="60" x2="150" y2="2836" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="360" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="490" y="43" text-anchor="middle" font-weight="600">XAS 后端</text>
<line x1="490" y1="60" x2="490" y2="2836" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="720" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="850" y="43" text-anchor="middle" font-weight="600">CloudKit 私有库</text>
<line x1="850" y1="60" x2="850" y2="2836" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1160" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1290" y="43" text-anchor="middle" font-weight="600">XAS 数据库</text>
<line x1="1290" y1="60" x2="1290" y2="2836" stroke="#cbd5e1" stroke-dasharray="5 5" />
<text x="320.0" y="86" text-anchor="middle">GET /api/account；GET /api/icloud/{id}/status</text>
<text x="320.0" y="105" text-anchor="middle" font-size="13" fill="#475569">前端刷新账号与认证状态；READY 时继续</text>
<line x1="150" y1="119" x2="490" y2="119" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="162" text-anchor="middle">读取账号列表与对应认证状态</text>
<text x="890.0" y="181" text-anchor="middle" font-size="13" fill="#475569">provider_account · credentials</text>
<line x1="490" y1="195" x2="1290" y2="195" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="238" text-anchor="middle">账号及会话状态</text>
<text x="890.0" y="257" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1290" y1="271" x2="490" y2="271" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<text x="320.0" y="314" text-anchor="middle">GET /api/crontab</text>
<text x="320.0" y="333" text-anchor="middle" font-size="13" fill="#475569">更新任务列表；与相册刷新并行，只读本地数据库</text>
<line x1="150" y1="347" x2="490" y2="347" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="390" text-anchor="middle">读取 crontab 与相册关联</text>
<text x="890.0" y="409" text-anchor="middle" font-size="13" fill="#475569">任务列表只查询本地数据</text>
<line x1="490" y1="423" x2="1290" y2="423" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="466" text-anchor="middle">任务配置与已选相册</text>
<text x="890.0" y="485" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1290" y1="499" x2="490" y2="499" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<text x="320.0" y="542" text-anchor="middle">GET /api/album/latest/{accountId}</text>
<text x="320.0" y="561" text-anchor="middle" font-size="13" fill="#475569">连接成功后自动刷新；也可手动刷新</text>
<line x1="150" y1="575" x2="490" y2="575" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="606" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="625" font-weight="600">每次 CloudKit 请求前执行认证检查；续期与重试见第四幅图</text>
<text x="40" y="645" font-size="13"></text>
<text x="670.0" y="694" text-anchor="middle">POST CK/zones/list</text>
<text x="670.0" y="713" text-anchor="middle" font-size="13" fill="#475569">{}</text>
<line x1="490" y1="727" x2="850" y2="727" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="670.0" y="770" text-anchor="middle">图库 zones</text>
<text x="670.0" y="789" text-anchor="middle" font-size="13" fill="#475569">只保留未删除的 PrimarySync 与 SharedSync-*</text>
<line x1="850" y1="803" x2="490" y2="803" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="834" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="853" font-weight="600">循环：每个图库区域 zone</text>
<text x="40" y="873" font-size="13"></text>
<text x="670.0" y="922" text-anchor="middle">POST CK/records/query</text>
<text x="670.0" y="941" text-anchor="middle" font-size="13" fill="#475569">query.recordType=CheckIndexingState · zoneID</text>
<line x1="490" y1="955" x2="850" y2="955" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="670.0" y="998" text-anchor="middle">state=FINISHED</text>
<text x="670.0" y="1017" text-anchor="middle" font-size="13" fill="#475569">其他状态结束查询并提示照片正在建立索引</text>
<line x1="850" y1="1031" x2="490" y2="1031" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="1062" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1081" font-weight="600">本地生成“所有照片 / 隐藏 / 个人收藏”入口：此处不单独查询这三个入口的资产</text>
<text x="40" y="1101" font-size="13"></text>
<text x="670.0" y="1150" text-anchor="middle">POST CK/records/query</text>
<text x="670.0" y="1169" text-anchor="middle" font-size="13" fill="#475569">CPLAlbumByPositionLive · zoneID · continuationMarker?</text>
<line x1="490" y1="1183" x2="850" y2="1183" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="670.0" y="1226" text-anchor="middle">用户相册 records · continuationMarker?</text>
<text x="670.0" y="1245" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="1259" x2="490" y2="1259" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="1290" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1309" font-weight="600">循环：带返回的 continuationMarker 查询下一页，直到无 marker</text>
<text x="40" y="1329" font-size="13">忽略根文件夹及已删除相册；Base64 解码 albumNameEnc。</text>
<text x="890.0" y="1378" text-anchor="middle">保存相册引用及账号关联</text>
<text x="890.0" y="1397" text-anchor="middle" font-size="13" fill="#475569">zone · recordName · queryType · smartAlbum?</text>
<line x1="490" y1="1411" x2="1290" y2="1411" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="320.0" y="1454" text-anchor="middle">相册列表</text>
<text x="320.0" y="1473" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="490" y1="1487" x2="150" y2="1487" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="1518" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1537" font-weight="600">创建任务 POST /api/crontab；手动启动 POST /api/crontab/{id}/executions，或由定时器触发</text>
<text x="40" y="1557" font-size="13">创建任务只写本地配置；执行时按 FULL / CURSOR 选择全量枚举或图库变更流，按本地历史跳过已完成下载。</text>
<text x="890.0" y="1606" text-anchor="middle">创建 crontab / crontab_album_mapping</text>
<text x="890.0" y="1625" text-anchor="middle" font-size="13" fill="#475569">POST /api/crontab 时保存任务配置</text>
<line x1="490" y1="1639" x2="1290" y2="1639" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="1682" text-anchor="middle">执行开始：创建 crontab_history</text>
<text x="890.0" y="1701" text-anchor="middle" font-size="13" fill="#475569">记录任务 ID 与 start_time</text>
<line x1="490" y1="1715" x2="1290" y2="1715" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="1758" text-anchor="middle">任务历史 ID</text>
<text x="890.0" y="1777" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1290" y1="1791" x2="490" y2="1791" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="1822" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1841" font-weight="600">循环：任务选中的每个相册；先查询 CheckIndexingState（同上），再从 startRank=0 开始分页</text>
<text x="40" y="1861" font-size="13"></text>
<text x="670.0" y="1910" text-anchor="middle">POST CK/records/query</text>
<text x="670.0" y="1929" text-anchor="middle" font-size="13" fill="#475569">ref.queryType · zoneID · resultsLimit=200</text>
<line x1="490" y1="1943" x2="850" y2="1943" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="670.0" y="1986" text-anchor="middle">CPLAsset + CPLMaster records</text>
<text x="670.0" y="2005" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="2019" x2="490" y2="2019" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="2050" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="2069" font-weight="600">分页参数：startRank · direction=ASCENDING；收藏附 smartAlbum=FAVORITE，自建相册附 parentId</text>
<text x="40" y="2089" font-size="13">queryType：所有照片=...WithoutHiddenOrDeleted；隐藏=...HiddenByAssetDate；收藏=...InSmartAlbumByAssetDate；自建=CPLContainerRelationLiveByAssetDate。</text>
<rect x="20" y="2126" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="2145" font-weight="600">条件分支：本页缺少 CPLAsset.masterRef 对应的 CPLMaster 时补查</text>
<text x="40" y="2165" font-size="13"></text>
<text x="670.0" y="2214" text-anchor="middle">POST CK/records/lookup</text>
<text x="670.0" y="2233" text-anchor="middle" font-size="13" fill="#475569">zoneID · records=[缺失 master 的 recordName]</text>
<line x1="490" y1="2247" x2="850" y2="2247" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="670.0" y="2290" text-anchor="middle">补齐 CPLMaster</text>
<text x="670.0" y="2309" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="2323" x2="490" y2="2323" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="2366" text-anchor="middle">按 remote_key 查询已保存资源</text>
<text x="890.0" y="2385" text-anchor="middle" font-size="13" fill="#475569">新小米资源同时检查本地 ID 冲突</text>
<line x1="490" y1="2399" x2="1290" y2="2399" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="2442" text-anchor="middle">已有本地资产 ID</text>
<text x="890.0" y="2461" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1290" y1="2475" x2="490" y2="2475" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="2518" text-anchor="middle">保存各原件资源与 remoteKey</text>
<text x="890.0" y="2537" text-anchor="middle" font-size="13" fill="#475569">resOriginal · resOriginalAlt · resOriginalVidCompl；只保存存在的资源</text>
<line x1="490" y1="2551" x2="1290" y2="2551" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<rect x="20" y="2582" width="1420" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="2601" font-weight="600">循环：startRank += 本页 CPLAsset 数量，继续 records/query，直到本页没有 CPLAsset</text>
<text x="40" y="2621" font-size="13">资产分页使用 rank，不使用 continuationMarker；完成枚举后读取本地待下载资源并进入第三幅图。</text>
<text x="890.0" y="2670" text-anchor="middle">更新 album.asset_count</text>
<text x="890.0" y="2689" text-anchor="middle" font-size="13" fill="#475569">每个相册枚举结束后写入统计</text>
<line x1="490" y1="2703" x2="1290" y2="2703" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
<text x="890.0" y="2746" text-anchor="middle">标记 crontab_history.fetched_all_assets</text>
<text x="890.0" y="2765" text-anchor="middle" font-size="13" fill="#475569">完成全部选中相册的枚举</text>
<line x1="490" y1="2779" x2="1290" y2="2779" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-albums-arrow)" />
</g>
</svg>
</div>

### 位点增量的图库变更流

`CURSOR` 使用 CloudKit `/changes/zone`。游标保存在任务历史的 `album_sync_cursors` JSON 列中；小米键为相册 ID，iCloud 键包含账号、zone 及所选相册 ID。不同任务独立推进，所选范围改变后建立新基线。

首次同步先取图库当前 `syncToken`，再全量枚举所选相册，最后从此前水位回放变化，覆盖枚举期间发生的上传。已有位点时跳过全量查询，每页请求和响应如下：

```text
XAS → XAS 数据库：读取本任务最近一次游标（包括中断任务已提交的页）
XAS → CloudKit：POST /changes/zone {zones:[{zoneID,syncToken,resultsLimit:200}]}
CloudKit → XAS：{zones:[{records,syncToken,moreComing}]}
XAS → CloudKit：按需 POST /records/lookup，补齐 CPLAsset、CPLMaster 与相册成员关系
XAS → XAS 数据库：保存所选相册的原件资源
XAS → XAS 数据库：保存该页 syncToken
moreComing=true：继续请求下一页；空 records 不代表结束
moreComing=false：结束元数据刷新，进入下载流水线
```

变化资产按隐藏、收藏标记路由；自建相册依据 `CPLContainerRelation`，并通过其 `{assetUUID}-IN-{albumUUID}` 记录名查询当前成员状态。新增成员关系即使没有对应的资产变化记录，也会补查资产。删除记录不移除本地备份。位点失效自动重新建立基线；限流、网络错误、记录查询或落库失败保留已提交位点，等待下次重试。

协议行为参考 [kei syncToken 调研](https://github.com/rhoopr/kei/blob/main/docs/synctoken-reference.md)。

### 逐资源下载与文件处理

<div>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1800 1716" width="100%" role="img" aria-labelledby="icloud-download-title">
<title id="icloud-download-title">逐资源下载与文件处理</title>
<defs><marker id="icloud-download-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#334155" /></marker></defs>
<rect width="1800" height="1716" rx="12" fill="#f8fafc" />
<g font-family="system-ui, sans-serif" font-size="15" fill="#0f172a">
<rect x="20" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="150" y="43" text-anchor="middle" font-weight="600">XAS 下载器</text>
<line x1="150" y1="60" x2="150" y2="1696" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="360" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="490" y="43" text-anchor="middle" font-weight="600">CloudKit 私有库</text>
<line x1="490" y1="60" x2="490" y2="1696" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="720" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="850" y="43" text-anchor="middle" font-weight="600">下载 CDN</text>
<line x1="850" y1="60" x2="850" y2="1696" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1080" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1210" y="43" text-anchor="middle" font-weight="600">本地文件系统</text>
<line x1="1210" y1="60" x2="1210" y2="1696" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1520" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1650" y="43" text-anchor="middle" font-weight="600">XAS 数据库</text>
<line x1="1650" y1="60" x2="1650" y2="1696" stroke="#cbd5e1" stroke-dasharray="5 5" />
<text x="900.0" y="86" text-anchor="middle">分页读取待处理资产与已有下载历史</text>
<text x="900.0" y="105" text-anchor="middle" font-size="13" fill="#475569">asset · crontab_history_detail；筛选任务选中的资源</text>
<line x1="150" y1="119" x2="1650" y2="119" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<text x="900.0" y="162" text-anchor="middle">待处理资产与阶段完成状态</text>
<text x="900.0" y="181" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="195" x2="150" y2="195" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-download-arrow)" />
<text x="900.0" y="238" text-anchor="middle">创建 crontab_history_detail</text>
<text x="900.0" y="257" text-anchor="middle" font-size="13" fill="#475569">记录目标路径；关闭的处理阶段预先标记完成</text>
<line x1="150" y1="271" x2="1650" y2="271" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<text x="900.0" y="314" text-anchor="middle">detailId</text>
<text x="900.0" y="333" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="347" x2="150" y2="347" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="378" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="397" font-weight="600">循环：每个待处理资源；多个下载器可并行；已完成历史直接跳过下载</text>
<text x="40" y="417" font-size="13"></text>
<rect x="20" y="454" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="473" font-weight="600">分支：skipExistingFile=true 且目标文件存在 → 不发 lookup / CDN 请求，直接进入后续本地阶段</text>
<text x="40" y="493" font-size="13"></text>
<text x="320.0" y="542" text-anchor="middle">POST CK/records/lookup</text>
<text x="320.0" y="561" text-anchor="middle" font-size="13" fill="#475569">zoneID · records=[CPLAsset recordName, CPLMaster masterName]</text>
<line x1="150" y1="575" x2="490" y2="575" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<text x="320.0" y="618" text-anchor="middle">指定原件资源的 downloadURL</text>
<text x="320.0" y="637" text-anchor="middle" font-size="13" fill="#475569">优先 CPLAsset 上的资源，缺失时从 CPLMaster 读取</text>
<line x1="490" y1="651" x2="150" y2="651" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="682" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="701" font-weight="600">lookup 前同样执行认证检查；同账号认证 / CloudKit 操作串行，CDN 传输不占用认证锁</text>
<text x="40" y="721" font-size="13"></text>
<text x="500.0" y="770" text-anchor="middle">GET downloadURL</text>
<text x="500.0" y="789" text-anchor="middle" font-size="13" fill="#475569">独立 HTTP 客户端：User-Agent；无 Apple 认证头和 cookie</text>
<line x1="150" y1="803" x2="850" y2="803" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="834" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="853" font-weight="600">条件分支：CDN 返回重定向 → HTTP 客户端跟随 Location，再发 GET</text>
<text x="40" y="873" font-size="13">初始 downloadURL 必须为 HTTPS。</text>
<text x="500.0" y="922" text-anchor="middle">成功响应：文件字节流</text>
<text x="500.0" y="941" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="955" x2="150" y2="955" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-download-arrow)" />
<text x="680.0" y="998" text-anchor="middle">流式写入 {fileName}.{detailId}.tmp</text>
<text x="680.0" y="1017" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="150" y1="1031" x2="1210" y2="1031" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="1062" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1081" font-weight="600">条件分支：第一次 CDN 请求返回 401 / 403 / 410 → 从 lookup 重新获取 URL，再 GET 一次</text>
<text x="40" y="1101" font-size="13">第二次失败或其他错误直接结束此资源；不对文件大小或 SHA-1 错误执行此重试。</text>
<rect x="20" y="1138" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1157" font-weight="600">下载成功：检查文件大小；通过后原子移动临时文件到目标路径</text>
<text x="40" y="1177" font-size="13">不支持原子移动的文件系统改用 REPLACE_EXISTING；下载异常清理临时文件。</text>
<text x="900.0" y="1226" text-anchor="middle">更新 download_completed=true</text>
<text x="900.0" y="1245" text-anchor="middle" font-size="13" fill="#475569">下载成功或复用已有文件后保存</text>
<line x1="150" y1="1259" x2="1650" y2="1259" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="1290" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1309" font-weight="600">checkSha1=true：校验阶段执行 CloudKit 文件校验；false：跳过；下载阶段不执行 SHA-1</text>
<text x="40" y="1329" font-size="13"></text>
<rect x="20" y="1366" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1385" font-weight="600">本地可选阶段：rewriteExifTime → EXIF；rewriteFileSystemTime → 文件时间</text>
<text x="40" y="1405" font-size="13">各阶段更新完成标记；失败记录错误并跳过该资源后续阶段。</text>
<text x="900.0" y="1454" text-anchor="middle">更新各阶段完成标记 / 失败 message</text>
<text x="900.0" y="1473" text-anchor="middle" font-size="13" fill="#475569">sha1_verified · exif_filled · fs_time_updated</text>
<line x1="150" y1="1487" x2="1650" y2="1487" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<text x="900.0" y="1530" text-anchor="middle">任务结束：更新 crontab_history.end_time</text>
<text x="900.0" y="1549" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="150" y1="1563" x2="1650" y2="1563" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-download-arrow)" />
<rect x="20" y="1594" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1613" font-weight="600">任务结束：写入任务历史；notify=true 且已配置通知模板时，由通知服务发送任务结果</text>
<text x="40" y="1633" font-size="13">通知目标由用户配置，与 iCloud 请求无关；不删除已下载的云端已删除资源。</text>
</g>
</svg>
</div>

### 会话检查、自动续期与 CloudKit 重试

<div>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1800 1564" width="100%" role="img" aria-labelledby="icloud-renew-title">
<title id="icloud-renew-title">会话检查、自动续期与 CloudKit 重试</title>
<defs><marker id="icloud-renew-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#334155" /></marker></defs>
<rect width="1800" height="1564" rx="12" fill="#f8fafc" />
<g font-family="system-ui, sans-serif" font-size="15" fill="#0f172a">
<rect x="20" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="150" y="43" text-anchor="middle" font-weight="600">XAS 后端</text>
<line x1="150" y1="60" x2="150" y2="1544" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="360" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="490" y="43" text-anchor="middle" font-weight="600">Apple Auth</text>
<line x1="490" y1="60" x2="490" y2="1544" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="720" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="850" y="43" text-anchor="middle" font-weight="600">iCloud Setup</text>
<line x1="850" y1="60" x2="850" y2="1544" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1080" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1210" y="43" text-anchor="middle" font-weight="600">CloudKit 私有库</text>
<line x1="1210" y1="60" x2="1210" y2="1544" stroke="#cbd5e1" stroke-dasharray="5 5" />
<rect x="1520" y="16" width="260" height="44" rx="8" fill="#dbeafe" stroke="#93c5fd" />
<text x="1650" y="43" text-anchor="middle" font-weight="600">XAS 数据库</text>
<line x1="1650" y1="60" x2="1650" y2="1544" stroke="#cbd5e1" stroke-dasharray="5 5" />
<text x="900.0" y="86" text-anchor="middle">withClient：读取 provider_account</text>
<text x="900.0" y="105" text-anchor="middle" font-size="13" fill="#475569">每次校验账号仍然存在且 provider=ICLOUD</text>
<line x1="150" y1="119" x2="1650" y2="119" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<text x="900.0" y="162" text-anchor="middle">账号 · credentials JSON / state</text>
<text x="900.0" y="181" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1650" y1="195" x2="150" y2="195" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-renew-arrow)" />
<rect x="20" y="226" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="245" font-weight="600">本地：解析 credentials JSON，恢复按账号隔离的 cookie、session / trust token</text>
<text x="40" y="265" font-size="13"></text>
<rect x="20" y="302" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="321" font-weight="600">入口：每次 cloud() 调用的 ensureAuthenticated()</text>
<text x="40" y="341" font-size="13"></text>
<rect x="20" y="378" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="397" font-weight="600">分支 A：内存 accountData 存在且验证时间在 10 分钟内 → 无 HTTP 请求</text>
<text x="40" y="417" font-size="13">mfaRequired=true 时直接中断并要求验证码；10 分钟是本地缓存窗口，不是 Apple 会话有效期。</text>
<rect x="20" y="454" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="473" font-weight="600">分支 B：无新鲜内存状态，但保存了 X-Apple-Session-Token</text>
<text x="40" y="493" font-size="13"></text>
<text x="500.0" y="542" text-anchor="middle">POST SETUP/validate</text>
<text x="500.0" y="561" text-anchor="middle" font-size="13" fill="#475569">账号 cookie；正文为 JSON null</text>
<line x1="150" y1="575" x2="850" y2="575" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<text x="500.0" y="618" text-anchor="middle">账号状态 · webservices · MFA 状态</text>
<text x="500.0" y="637" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="850" y1="651" x2="150" y2="651" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-renew-arrow)" />
<rect x="20" y="682" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="701" font-weight="600">validate 成功且已受信任 → 继续 CloudKit；提示 MFA → 中断，返回 MFA_REQUIRED</text>
<text x="40" y="721" font-size="13"></text>
<rect x="20" y="758" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="777" font-weight="600">分支 C：没有 session token，或 validate 返回 401 / 421 / 450 → 完整 SRP 登录</text>
<text x="40" y="797" font-size="13">执行第一幅图 init → complete（携带保存的 trustTokens）→ accountLogin；需要时进入设备双重认证。</text>
<text x="680.0" y="846" text-anchor="middle">POST CK/{zones/list | records/query | records/lookup}</text>
<text x="680.0" y="865" text-anchor="middle" font-size="13" fill="#475569">原业务请求</text>
<line x1="150" y1="879" x2="1210" y2="879" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<text x="680.0" y="922" text-anchor="middle">成功响应，或 HTTP 421 / 450</text>
<text x="680.0" y="941" text-anchor="middle" font-size="13" fill="#475569"></text>
<line x1="1210" y1="955" x2="150" y2="955" stroke="#334155" stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#icloud-renew-arrow)" />
<rect x="20" y="986" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1005" font-weight="600">仅 421 / 450：强制重新 SRP 登录，跳过内存缓存和 validate，再重发原 CloudKit 请求一次</text>
<text x="40" y="1025" font-size="13"></text>
<text x="320.0" y="1074" text-anchor="middle">POST AUTH/signin/init → POST AUTH/signin/complete</text>
<text x="320.0" y="1093" text-anchor="middle" font-size="13" fill="#475569">恢复 trust token；若要求验证码，业务请求停止等待重新认证</text>
<line x1="150" y1="1107" x2="490" y2="1107" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<text x="500.0" y="1150" text-anchor="middle">POST SETUP/accountLogin</text>
<text x="500.0" y="1169" text-anchor="middle" font-size="13" fill="#475569">登录成功并受信任后继续</text>
<line x1="150" y1="1183" x2="850" y2="1183" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<text x="680.0" y="1226" text-anchor="middle">POST 原 CK 路径及原正文</text>
<text x="680.0" y="1245" text-anchor="middle" font-size="13" fill="#475569">重试一次；再失败则向调用方抛出错误</text>
<line x1="150" y1="1259" x2="1210" y2="1259" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<rect x="20" y="1290" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1309" font-weight="600">本地：会话变更保存至 credentials JSON；409 → MFA_REQUIRED，非验证码提交场景的 401 → SESSION_EXPIRED</text>
<text x="40" y="1329" font-size="13">错误验证码提交保留 MFA_REQUIRED；开启认证失效通知模板时，同一失效状态通知一次。</text>
<text x="900.0" y="1378" text-anchor="middle">withClient 成功或认证异常：UPDATE provider_account.credentials</text>
<text x="900.0" y="1397" text-anchor="middle" font-size="13" fill="#475569">成功保存 READY；认证异常保存 MFA_REQUIRED / SESSION_EXPIRED</text>
<line x1="150" y1="1411" x2="1650" y2="1411" stroke="#334155" stroke-width="1.6" marker-end="url(#icloud-renew-arrow)" />
<rect x="20" y="1442" width="1760" height="48" rx="6" fill="#fef3c7" stroke="#fcd34d" />
<text x="40" y="1461" font-weight="600">状态与凭据在 withClient 操作结束时落库，不是每个 Apple 响应单独写数据库</text>
<text x="40" y="1481" font-size="13">相册 / 资产枚举在一次 withClient 中执行；下载 URL lookup 在每次尝试的 withClient 中执行。</text>
</g>
</svg>
</div>

实现入口：`ICloudController` → `ICloudAccountService` → `ICloudClient` / `ICloudPhotos`；任务流水线通过 `CloudMediaService` 分派来源，下载与校验分别由 `DownloadStage`、`VerificationStage` 处理。

## 结论

- 许可证无障碍：以 pyicloud/icloudpd（MIT）为代码参考，icloud-photos-sync 作架构参考，移植处保留 MIT 声明；
- 登录交互 = Apple ID + 密码 + 条件性 2FA 码，trust token 内部持久化；
- 鉴权生命周期照 icps 模式做成状态机，MFA 重新验证是设计内事件；
- 录音类内容 iCloud 侧无等价物，仅覆盖照片/视频。
