# SSL 支持

本项目基于 Solon 的 `server.ssl` 配置启用 HTTPS。

## 配置方式

项目已在 `server/src/main/resources/app.yml` 预置了 SSL 配置占位，可以通过环境变量传递配置：

```yaml
server:
  ssl:
    keyStore: ${SSL_KEYSTORE:}
    keyStorePassword: ${SSL_KEYSTORE_PASSWORD:}
```

环境变量说明：

- `SSL_KEYSTORE`: 证书库路径
  
    受依赖库限制，当前仅支持 `.jks` / `.pfx` / `.p12` 格式的证书
- `SSL_KEYSTORE_PASSWORD`: 证书库密码

**不配置这两个参数（或保持为空）时，服务仍以 HTTP 方式运行。**

## 运行方式示例

### JVM / 二进制

```bash
export SSL_KEYSTORE=/data/_ca/demo.jks
export SSL_KEYSTORE_PASSWORD=demo

# JVM 版
java -jar app.jar

# 二进制版（示例）
./xiaomi-album-syncer-linux-x64-vX.X.X
```

### Docker

```bash
docker run -d \
  -p 8232:8080 \
  -e SSL_KEYSTORE=/certs/demo.pfx \
  -e SSL_KEYSTORE_PASSWORD=demo \
  -v ~/xiaomi-album-syncer/download:/app/download \
  -v ~/xiaomi-album-syncer/db:/app/db \
  -v /local/certs:/certs:ro \
  --name xiaomi-album-syncer \
  coooolfan/xiaomi-album-syncer:latest
```

### Docker Compose

以下示例在 `docker/docker-compose.yml` 基础上补充 SSL 参数与证书挂载路径：

```yaml
name: xiaomi-album-syncer

services:
  app:
    image: coolfan1024/xiaomi-album-syncer:latest
    container_name: xiaomi-album-syncer
    ports:
      - "8232:8080"
    environment:
      SSL_KEYSTORE: /certs/demo.pfx
      SSL_KEYSTORE_PASSWORD: demo
    volumes:
      - ~/xiaomi-album-syncer/download:/app/download
      - ~/xiaomi-album-syncer/db:/app/db
      - ~/xiaomi-album-syncer/certs:/certs:ro
    restart: unless-stopped
```

## Solon 文档参考

- [如何增加 https 监听支持（ssl 证书）](https://solon.noear.org/article/343)
- [solon-server-smarthttp 说明](https://solon.noear.org/article/1135)
