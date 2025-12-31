# 多小米云账号支持重构计划

## 概述

将当前单账号设计重构为支持多个小米云账号，使用单一系统密码管理多个账号。

### 用户需求确认
- **数据展示**: 合并展示 - 所有账号的相册/资产统一展示，但标记来源账号
- **任务绑定**: 一对一绑定 - 每个 Crontab 任务绑定一个特定账号
- **账号标识**: 支持自定义昵称

---

## 实施步骤

### 1. 创建新的 XiaomiAccount 实体

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/model/XiaomiAccount.kt`

```kotlin
@Entity
interface XiaomiAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val nickname: String      // 账号昵称，用于界面展示
    val passToken: String     // 小米账号 passToken
    val userId: String        // 小米账号 userId

    @OneToMany(mappedBy = "account")
    val albums: List<Album>

    @OneToMany(mappedBy = "account")
    val crontabs: List<Crontab>
}
```

### 2. 修改 SystemConfig 实体

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/model/SystemConfig.kt`

移除 `passToken` 和 `userId` 字段，保留系统级配置：

```kotlin
@Entity
interface SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val password: String              // 系统登录密码
    val exifToolPath: String          // exiftool 路径
    val assetsDateMapTimeZone: String // 时区配置
}
```

### 3. 修改 Album 实体

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/model/Album.kt`

添加账号关联：

```kotlin
@Entity
interface Album {
    @Id
    @JsonConverter(LongToStringConverter::class)
    val id: Long

    val name: String
    val assetCount: Long
    val lastUpdateTime: Instant

    @ManyToOne
    val account: XiaomiAccount    // 新增：关联的小米账号

    @IdView("account")
    val accountId: Long           // 新增：账号ID视图

    @OneToMany(mappedBy = "album")
    val assets: List<Asset>
}
```

**注意**: Album 的主键需要改为复合主键或使用自增ID，因为不同账号可能有相同的 albumId。建议改为：
- 使用自增 `id` 作为主键
- 添加 `remoteId` 字段存储小米云的原始 albumId

### 4. 修改 Crontab 实体

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/model/Crontab.kt`

添加账号绑定：

```kotlin
@Entity
interface Crontab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String
    val description: String
    val enabled: Boolean
    val config: CrontabConfig

    @ManyToOne
    val account: XiaomiAccount    // 新增：绑定的小米账号

    @IdView("account")
    val accountId: Long           // 新增：账号ID视图

    @ManyToMany
    val albums: List<Album>

    @Transient(CrontabRunningResolver::class)
    val running: Boolean

    @IdView("albums")
    @JsonConverter(LongListToStringListConverter::class)
    val albumIds: List<Long>

    @OneToMany(mappedBy = "crontab")
    val histories: List<CrontabHistory>
}
```

### 5. 数据库迁移脚本

**文件**: `server/src/main/resources/db/migration/V0.9.0__multi_account.sql`

```sql
-- 1. 创建小米账号表
CREATE TABLE xiaomi_account (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    nickname TEXT NOT NULL,
    pass_token TEXT NOT NULL,
    user_id TEXT NOT NULL
);

-- 2. 迁移现有账号数据（如果存在）
INSERT INTO xiaomi_account (nickname, pass_token, user_id)
SELECT '默认账号', pass_token, user_id FROM system_config WHERE id = 0 AND pass_token != '-';

-- 3. 为 album 表添加账号关联
-- 先添加列（允许NULL用于迁移）
ALTER TABLE album ADD COLUMN account_id INTEGER REFERENCES xiaomi_account(id);
-- 关联现有数据到第一个账号
UPDATE album SET account_id = (SELECT id FROM xiaomi_account LIMIT 1) WHERE account_id IS NULL;

-- 4. 为 crontab 表添加账号关联
ALTER TABLE crontab ADD COLUMN account_id INTEGER REFERENCES xiaomi_account(id);
-- 关联现有数据到第一个账号
UPDATE crontab SET account_id = (SELECT id FROM xiaomi_account LIMIT 1) WHERE account_id IS NULL;

-- 5. 从 system_config 移除账号相关字段
-- SQLite 不支持 DROP COLUMN，需要重建表
CREATE TABLE system_config_new (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password TEXT NOT NULL,
    exif_tool_path TEXT NOT NULL,
    assets_date_map_time_zone TEXT NOT NULL
);

INSERT INTO system_config_new (id, password, exif_tool_path, assets_date_map_time_zone)
SELECT id, password, exif_tool_path, assets_date_map_time_zone FROM system_config;

DROP TABLE system_config;
ALTER TABLE system_config_new RENAME TO system_config;
```

### 6. 重构 TokenManager

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/xiaomicloud/TokenManager.kt`

改为多账号支持，使用账号ID索引的缓存：

```kotlin
@Managed
class TokenManager(private val sql: KSqlClient) {
    private val log = LoggerFactory.getLogger(TokenManager::class.java)

    // 改为 Map 缓存，key 是账号 ID
    private val tokenCache = ConcurrentHashMap<Long, CachedToken>()

    data class CachedToken(
        val serviceToken: String,
        val userId: String,
        val lastFreshenTime: Instant
    )

    fun getAuthPair(accountId: Long): Pair<String, String> {
        val cached = tokenCache[accountId]
        if (cached != null && !needRefresh(cached.lastFreshenTime)) {
            return cached.userId to cached.serviceToken
        }

        synchronized(this) {
            // 双重检查
            val cachedAgain = tokenCache[accountId]
            if (cachedAgain != null && !needRefresh(cachedAgain.lastFreshenTime)) {
                return cachedAgain.userId to cachedAgain.serviceToken
            }

            val account = sql.findById(XiaomiAccount::class, accountId)
                ?: throw IllegalStateException("Account not found: $accountId")

            val serviceToken = genServiceToken(account.passToken, account.userId)
            tokenCache[accountId] = CachedToken(serviceToken, account.userId, Instant.now())

            return account.userId to serviceToken
        }
    }

    fun invalidateToken(accountId: Long) {
        tokenCache.remove(accountId)
    }

    private fun needRefresh(lastFreshenTime: Instant): Boolean {
        return Instant.now().isAfter(lastFreshenTime.plusSeconds(60 * 10))
    }

    // genServiceToken 方法保持不变...
}
```

### 7. 修改 XiaoMiApi

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/xiaomicloud/XiaoMiApi.kt`

所有方法添加 `accountId` 参数：

```kotlin
@Managed
class XiaoMiApi(private val tokenManager: TokenManager) {

    fun fetchAllAlbums(accountId: Long): List<Album> {
        // 使用 tokenManager.getAuthPair(accountId)
        // ...
    }

    fun fetchAssetsByAlbumId(accountId: Long, album: Album, day: LocalDate? = null, handler: (List<Asset>) -> Unit): Long {
        // 使用 tokenManager.getAuthPair(accountId)
        // ...
    }

    fun downloadAsset(accountId: Long, asset: Asset, targetPath: Path): Path {
        // 使用 tokenManager.getAuthPair(accountId)
        // ...
    }
    // ...
}
```

### 8. 创建 XiaomiAccountService

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/service/XiaomiAccountService.kt`

```kotlin
@Managed
class XiaomiAccountService(private val sql: KSqlClient, private val tokenManager: TokenManager) {

    fun listAccounts(): List<XiaomiAccount> {
        return sql.executeQuery(XiaomiAccount::class) {
            select(table)
        }
    }

    fun getAccount(id: Long): XiaomiAccount? {
        return sql.findById(XiaomiAccount::class, id)
    }

    fun createAccount(nickname: String, passToken: String, userId: String): XiaomiAccount {
        return sql.insert(XiaomiAccount {
            this.nickname = nickname
            this.passToken = passToken
            this.userId = userId
        })
    }

    fun updateAccount(id: Long, nickname: String?, passToken: String?, userId: String?) {
        sql.update(XiaomiAccount::class) {
            where(table.id eq id)
            nickname?.let { set(table.nickname, it) }
            passToken?.let { set(table.passToken, it) }
            userId?.let { set(table.userId, it) }
        }
        // 更新凭证后清除缓存
        if (passToken != null || userId != null) {
            tokenManager.invalidateToken(id)
        }
    }

    fun deleteAccount(id: Long) {
        // 检查是否有关联的 crontab 或 album
        sql.delete(XiaomiAccount::class) {
            where(table.id eq id)
        }
    }
}
```

### 9. 创建 XiaomiAccountController

**文件**: `server/src/main/kotlin/com/coooolfan/xiaomialbumsyncer/controller/XiaomiAccountController.kt`

```kotlin
@Controller
@Mapping("/api/xiaomi-accounts")
class XiaomiAccountController(private val service: XiaomiAccountService) {

    @Get
    fun list(): List<XiaomiAccountView> { ... }

    @Get("/{id}")
    fun get(@Path id: Long): XiaomiAccountView { ... }

    @Post
    fun create(@Body request: XiaomiAccountCreate): XiaomiAccountView { ... }

    @Put("/{id}")
    fun update(@Path id: Long, @Body request: XiaomiAccountUpdate): XiaomiAccountView { ... }

    @Delete("/{id}")
    fun delete(@Path id: Long) { ... }
}
```

### 10. 创建 DTO 文件

**文件**: `server/src/main/dto/XiaomiAccount.dto`

```
export com.coooolfan.xiaomialbumsyncer.model.XiaomiAccount
    -> package com.coooolfan.xiaomialbumsyncer.model.dto

XiaomiAccountView {
    id
    nickname
    userId
}

input XiaomiAccountCreate {
    nickname
    passToken
    userId
}

input XiaomiAccountUpdate {
    nickname?
    passToken?
    userId?
}
```

### 11. 修改相关 Service 层

需要修改的服务：
- `AlbumService` - 查询时可按账号过滤，创建时关联账号
- `CrontabService` - 创建/更新任务时绑定账号
- `SyncService` - 同步时使用任务绑定的账号

---

## 修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `model/XiaomiAccount.kt` | 新建 | 小米账号实体 |
| `model/SystemConfig.kt` | 修改 | 移除 passToken/userId |
| `model/Album.kt` | 修改 | 添加 account 关联 |
| `model/Crontab.kt` | 修改 | 添加 account 关联 |
| `dto/XiaomiAccount.dto` | 新建 | 账号 DTO |
| `dto/SystemConfig.dto` | 修改 | 移除 passToken 相关 DTO |
| `xiaomicloud/TokenManager.kt` | 重构 | 多账号 token 缓存 |
| `xiaomicloud/XiaoMiApi.kt` | 修改 | 方法添加 accountId 参数 |
| `service/XiaomiAccountService.kt` | 新建 | 账号管理服务 |
| `service/AlbumService.kt` | 修改 | 支持多账号 |
| `service/CrontabService.kt` | 修改 | 支持账号绑定 |
| `service/SyncService.kt` | 修改 | 使用任务绑定的账号 |
| `controller/XiaomiAccountController.kt` | 新建 | 账号管理 API |
| `db/migration/V0.9.0__multi_account.sql` | 新建 | 数据库迁移 |

---

## 关键注意事项

1. **Album ID 冲突**: 不同账号可能有相同的小米云 albumId，需要修改主键策略或添加 remoteId 字段
2. **数据迁移**: 确保现有数据正确关联到默认账号
3. **API 兼容性**: 前端需要同步更新以支持多账号
4. **Token 缓存**: 每个账号独立缓存，避免串号
5. **错误处理**: 账号凭证失效时给出明确提示
