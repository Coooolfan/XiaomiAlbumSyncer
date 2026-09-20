# 小米账号登录方式调研

调研日期：2026-09-20

## 背景

当前账号接入要求用户手动从浏览器 Cookie 中提取 `passToken` 与 `userId`（README「获取 PassToken 与 UserId」一节），步骤涉及开发者工具，用户门槛高。本文调研账号密码登录与扫码登录两种替代方案，以及与两者相关的二次验证（2FA）流程。

## 现状：凭证模型

服务端持有 `userId` + `passToken` 两个长效凭证，`TokenManager.genServiceToken()` 通过三步换取短效的 `serviceToken`（约 10 分钟刷新一次）：

1. `GET {api}/user/login?ts=...&followUp=...&_locale=zh_CN`（携带 `userId`/`deviceId`/`passToken` Cookie）→ `data.loginUrl`
2. `GET {loginUrl}`（同上 Cookie）→ 302 `Location`
3. `GET {location}`（同上 Cookie）→ `Set-Cookie: serviceToken`

`passToken` 失效特征：第三步响应不再下发 `serviceToken`。

## 方案一：扫码登录（推荐主路径）

小米账号体系原生支持扫码登录（微信网页版同款模型）。以下端点已于调研当日实测可用（`sid=i.mi.com`）：

### 流程

```
① GET https://account.xiaomi.com/pass/serviceLogin?sid=i.mi.com&_json=true
   Cookie: deviceId=<16位随机>; sdkVersion=3.4.1
   → _sign, qs, callback("https://i.mi.com/sts"), serviceParam
   （未登录时 code=70016 是正常应答，签名参数照常返回）

② GET https://account.xiaomi.com/longPolling/loginUrl
   ?qs&callback&_sign&sid&serviceParam&_qrsize=480&_locale=zh_CN&_dc=<ts>
     &needTheme=false&showActiveX=false&bizDeviceType=
   → {
       qr:  "https://account.xiaomi.com/pass/qr/login?ticket=lp_xxx&..."  // PNG 图片
       lp:  "https://{dc}.lp.account.xiaomi.com/lp/s?k=lp_xxx"            // 长轮询端点
       timeout: 300,                                                    // 二维码有效期（秒）
       qrTips: "可使用小米手机或平板，点击设置 > 小米账号后扫码登录，或使用其他扫码工具"
     }

③ 后端持有 lp 长轮询（挂起式 GET，扫码确认后才返回）
   → 成功载荷与 serviceLoginAuth2 相同结构：
     userId, passToken, ssecurity, location, nonce ...

④ userId + passToken 写入 XiaomiAccount → 现有 serviceToken 管线接管
```

实测确认：`qr` URL 直接返回 `image/png`，前端可 `<img>` 热链，无需自行渲染二维码；`lp` 为挂起式长连接。

### 说明

- 扫码动作发生在用户已登录小米账号的设备上（小米手机「设置 > 小米账号 > 扫码」，或其他扫码工具打开登录链接后授权），不经过服务端传输密码；
- 唯一未实测环节：③ 的成功载荷（需真实小米设备扫码确认 `passToken` 字段，多个开源实现的结构推断一致，首次联调时验证）。

## 方案二：账号密码登录（备选）

### 流程

```
① GET /pass/serviceLogin?sid=i.mi.com&_json=true  → _sign, qs, callback
② POST /pass/serviceLoginAuth2   (application/x-www-form-urlencoded)
   user=<手机号/邮箱/小米ID>
   hash=MD5(password).hexdigest().upper()
   qs, _sign, callback, sid=i.mi.com, _json=true
   → userId, cUserId, passToken, ssecurity, location, nonce
③ GET location → serviceToken（本项目不需要，TokenManager 已有等价实现）
```

### 已知失败模式（各开源项目 issue 记录）

| 场景 | 响应特征 | 处理 |
|---|---|---|
| 图形验证码 | `captchaUrl` 非空 | 下载验证码图（获取 `ick` cookie），用户输码后带 `captCode` + `ick` 重新提交 Auth2 |
| 短信/邮箱二次验证 | `notificationUrl` 非空 + `securityStatus:16` | 走下方 identity 流程；异地/机房 IP 登录触发率高 |
| 密码含特殊字符 | 历史兼容性 bug | 注意编码处理 |

需要存储用户小米密码，安全负担重于 passToken。

## 二次验证（identity）流程

密码登录返回 `notificationUrl`，或网页端访问相册页触发的手机验证，均为同一套 `identity` 机制。完整流程（参考 XiaomiGateway3 `core/xiaomi_cloud.py` 实现）：

```
notificationUrl（/fe/service/identity/authStart?context=...）
  → 改写为 /identity/list 后 GET
  → 返回 {"code":2, "flag": 4|8}，Set-Cookie 下发 identity_session
      flag: 4=手机号验证, 8=邮箱验证

GET /identity/auth/verify{Phone|Email}?_flag={flag}&_json=true
  → maskedPhone / maskedEmail（脱敏的发码目标）

POST /identity/auth/send{Phone|Email}Ticket
  body: {retry:0, icode:"", _json:true}
  → 下发短信/邮件验证码
  ⚠️ 此步也可能返回 captchaUrl → 取图（ick cookie）→ 带 icode=图形码 重发

POST /identity/auth/verify{Phone|Email}
  params: {_flag, ticket=<用户输入的码>, trust:"false", _json:true}
  → 成功后走 location 重定向链
  → passToken + userId 在重定向历史的 Set-Cookie 中
  → ssecurity 在 extension-pragma 响应头中
```

要点：

- `trust` 参数置 `true` 等价于网页勾选「信任此设备」，配合固定 `deviceId` 有望让后续不再触发 2FA（待验证）；
- 验证码存在「嵌套」场景：Auth2 阶段一次 captchaUrl（`captCode`+`ick`），sendTicket 阶段可能再一次（`icode`+`ick`），前端状态机需支持；
- 2FA 完成后的 `passToken` 与手动抠取的凭证完全等价，下游零改动。

## 待验证问题

- **相册权限激活**：release notes 0.1.7 记录过「访问相册页面以避免无权限 cookie」。扫码/密码登录拿到的 passToken 是否天然具备相册 API 权限，还是同样需要在相册上下文完成一次 identity 验证激活，需真机联调验证。若需要激活，identity 流程须实现为登录后可复用的独立步骤。
- `lp` 长轮询成功载荷中 `passToken` 的确切位置（JSON 字段 vs Set-Cookie）。

## 建议方案

**主路径：扫码登录；保留手动 passToken 输入作为兜底；密码登录按需后补。**

理由：扫码一步拿到的凭证与现状完全相同（`userId`+`passToken`），对下游零改动；密码登录的失败模式（验证码、2FA、风控）在服务器部署场景触发率更高。

### 服务端

- `POST /api/account/login/qr`：执行步骤①②，创建登录会话（保留 `deviceId` cookie 上下文），返回二维码 URL + 会话 ID + 有效期；
- `GET /api/account/login/qr/{id}/status`：后端持有 `lp` 长轮询，前端轮询返回 `等待扫码 / 已确认 / 已过期 / 已完成` 状态；
- 成功后复用现有 `XiaomiAccountService.create()` 落库；
- `deviceId` 持久化到账号（现为每次随机 `wb_`+UUID，固定后更接近真实设备、降低风控概率，且与 `trust=true` 的信任设备机制配套）。

### 前端

登录弹窗：二维码 `<img>` + 状态轮询 + 300 秒过期重发；账号表单保留手动输入 tab。

## 参考实现

| 项目 | 语言 | 覆盖内容 |
|---|---|---|
| [AlexxIT/XiaomiGateway3](https://github.com/AlexxIT/XiaomiGateway3) `core/xiaomi_cloud.py` | Python | 密码登录、验证码、identity 2FA 全流程（最完整） |
| [al-one/hass-xiaomi-miot](https://github.com/al-one/hass-xiaomi-miot) | Python | 新版 `fe/service/identity/authStart` URL 适配 |
| [Mi-Bee-Studio/MiBeeNvr](https://github.com/Mi-Bee-Studio/MiBeeNvr) `internal/xiaomi/cloud.go` | Go | 登录 + captcha + 2FA 结构清晰 |
| [offici5l/MiUnlockTool](https://github.com/offici5l/MiUnlockTool) | Python | identity 端点文档化 |
| [PiotrMachowski/Xiaomi-cloud-tokens-extractor](https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor) | Python | 密码登录 + captcha |
| [AC-Nexus](https://github.com/oywq00008-cell/AC-Nexus) `xiaomi_cloud.py` | Python | 扫码登录（sid=xiaomiio） |
| [mik-laj gist](https://gist.github.com/mik-laj/97994a82cad049cd4b843edd9acb990b) | Python | 扫码登录最小实现 |

> 注：`ssecurity` + `nonce` + SHA1/RC4 签名仅米家 `api.io.mi.com` 设备接口需要，本项目相册链路（`i.mi.com`）不涉及。
