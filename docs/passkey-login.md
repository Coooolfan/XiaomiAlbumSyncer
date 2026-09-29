# Passkey 登录

本项目支持使用 Passkey（WebAuthn）进行无密码登录，提供更安全、便捷的身份验证方式。

## 功能概述

Passkey 是基于 WebAuthn 标准的现代身份验证方式，支持：

- **生物识别登录**：Touch ID、Face ID、Windows Hello 等
- **硬件安全密钥**：YubiKey、Google Titan Key 等
- **多设备同步**：通过 iCloud 钥匙串、Google 密码管理器等同步
- **防钓鱼攻击**：基于公钥加密，无法被窃取或重放

## 前置要求

### 安全上下文

Passkey 功能**必须**在安全上下文下使用：

- **HTTPS 连接**（推荐）
- **localhost**（仅用于本地开发测试）

> 非安全上下文（如 HTTP）下，浏览器会阻止 WebAuthn API 调用。

### 浏览器支持

需要支持 WebAuthn 的现代浏览器：

- Chrome/Edge 67+
- Firefox 60+
- Safari 13+
- Opera 54+

## 配置方式

项目已在 `server/src/main/resources/app.yml` 预置了 WebAuthn 配置占位，可以通过环境变量传递配置：

```yaml
app:
  webauthn:
    rpId: ${WEBAUTHN_RP_ID:localhost}
    rpName: ${WEBAUTHN_RP_NAME:XiaomiAlbumSyncer}
```

### 配置参数说明

| 参数 | 说明 | 默认值 | 示例 |
| --- | --- | --- | --- |
| `WEBAUTHN_RP_ID` | Relying Party ID，必须是当前域名或其父域名 | `localhost` | `example.com` |
| `WEBAUTHN_RP_NAME` | 显示名称，用户在注册时会看到 | `XiaomiAlbumSyncer` | `我的相册同步` |

> **重要**：`rpId` 必须与访问域名匹配。例如，如果通过 `https://sync.example.com` 访问，`rpId` 应设置为 `sync.example.com` 或 `example.com`。

## 使用方式

### 1. 初始化系统

首次使用时，需要设置管理员密码：

1. 访问登录页面
2. 输入密码并确认
3. 点击"设置密码并进入"

### 2. 注册 Passkey

登录后，在设置页面注册 Passkey：

1. 进入"设置" → "Passkey 管理"
2. 点击"注册新 Passkey"
3. 输入当前密码
4. 输入 Passkey 名称（如"MacBook Touch ID"、"iPhone"）
5. 按照浏览器提示完成验证（指纹、面容、PIN 等）

### 3. 使用 Passkey 登录

注册 Passkey 后，登录页面会显示"使用 Passkey 登录"按钮：

1. 点击"使用 Passkey 登录"
2. 按照浏览器提示选择 Passkey
3. 完成生物识别或 PIN 验证
4. 自动登录成功

### 4. 管理 Passkey

在设置页面可以管理已注册的 Passkey：

- **查看列表**：显示所有已注册的 Passkey 及最后使用时间
- **重命名**：修改 Passkey 的显示名称
- **删除**：移除不再使用的 Passkey

## 运行方式示例

### JVM / 二进制

```bash
export WEBAUTHN_RP_ID=sync.example.com
export WEBAUTHN_RP_NAME="我的相册同步"

# JVM 版
java -jar app.jar

# 二进制版（示例）
./xiaomi-album-syncer-linux-x64-vX.X.X
```

### Docker

```bash
docker run -d \
  -p 8232:8080 \
  -e WEBAUTHN_RP_ID=sync.example.com \
  -e WEBAUTHN_RP_NAME="我的相册同步" \
  -v ~/xiaomi-album-syncer/download:/app/download \
  -v ~/xiaomi-album-syncer/db:/app/db \
  --name xiaomi-album-syncer \
  coooolfan/xiaomi-album-syncer:latest
```

### Docker Compose

```yaml
name: xiaomi-album-syncer

services:
  app:
    image: coolfan1024/xiaomi-album-syncer:latest
    container_name: xiaomi-album-syncer
    ports:
      - "8232:8080"
    environment:
      WEBAUTHN_RP_ID: sync.example.com
      WEBAUTHN_RP_NAME: "我的相册同步"
    volumes:
      - ~/xiaomi-album-syncer/download:/app/download
      - ~/xiaomi-album-syncer/db:/app/db
    restart: unless-stopped
```

## 注意事项

### 安全性

- **HTTPS 部署**：生产环境必须使用 HTTPS，参考 [SSL 支持文档](./ssl-suppot.md)
- **rpId 配置**：确保 `rpId` 与访问域名匹配，否则 Passkey 无法使用
- **备用登录**：建议保留密码登录方式，以防 Passkey 设备丢失

### 兼容性

- **跨域名不可用**：在 `example.com` 注册的 Passkey 无法在 `another.com` 使用
- **跨设备同步**：取决于操作系统和浏览器的支持（如 iCloud 钥匙串、Google 密码管理器）
- **私密浏览模式**：部分浏览器在隐私模式下可能限制 Passkey 功能

### 故障排查

| 问题 | 可能原因 | 解决方案 |
| --- | --- | --- |
| 无法注册 Passkey | 非安全上下文 | 使用 HTTPS 或 localhost |
| 登录时找不到 Passkey | rpId 配置错误 | 检查 `WEBAUTHN_RP_ID` 是否与域名匹配 |
| 浏览器不显示 Passkey 选项 | 浏览器不支持 WebAuthn | 更新浏览器或使用其他浏览器 |
| 注册时提示密码错误 | 输入的密码不正确 | 确认输入的是当前登录密码 |

## 相关资源

- [WebAuthn 标准](https://www.w3.org/TR/webauthn/)
- [Passkey 官方介绍](https://passkeys.dev/)
- [SimpleWebAuthn 库](https://simplewebauthn.dev/)
