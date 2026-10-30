package com.coooolfan.xiaomialbumsyncer.xiaomicloud.e2ee

import com.coooolfan.xiaomialbumsyncer.icloud.ICloudAccountService
import com.coooolfan.xiaomialbumsyncer.icloud.ICloudPhotos
import com.coooolfan.xiaomialbumsyncer.service.ProviderAccountService
import com.coooolfan.xiaomialbumsyncer.service.CloudMediaService
import com.coooolfan.xiaomialbumsyncer.xiaomicloud.XiaomiCloudApi
import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.model.dto.XiaomiAccountInput
import com.coooolfan.xiaomialbumsyncer.model.dto.xiaomiCredentialsMapper
import com.coooolfan.xiaomialbumsyncer.controller.ProviderAccountController.Companion.DEFAULT_PROVIDER_ACCOUNT
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class XiaomiE2eePersistenceTest {
    @Test fun restoresKeysAndPreservesThemAcrossCredentialUpdates(@TempDir directory: Path) {
        E2eeTestDatabase(directory.resolve("persistence.db")).use { db ->
            val account = db.sql.saveCommand(XiaomiAccountInput("test", "pass", "123456").toEntity(), SaveMode.INSERT_ONLY).execute().modifiedEntity
            val key = ByteArray(32) { it.toByte() }
            val credentials = xiaomiCredentialsMapper.writeValueAsString(mapOf("passToken" to "pass", "xiaomiE2ee" to mapOf("keys" to mapOf("10" to E2eeCrypto.encode(key)), "state" to "READY")))
            db.sql.saveCommand(ProviderAccount { id = account.id; this.credentials = credentials }, SaveMode.UPDATE_ONLY).execute()
            assertArrayEquals(key, db.service().appKey(account.id, 10))
            val accounts = db.accounts
            accounts.update(XiaomiAccountInput("renamed", "new-pass", "123456").toEntity(account.id), DEFAULT_PROVIDER_ACCOUNT)
            val stored = xiaomiCredentialsMapper.readTree(db.sql.findById(ProviderAccount::class, account.id)!!.credentials)
            assertEquals("new-pass", stored.path("passToken").asText())
            assertEquals(E2eeCrypto.encode(key), stored.at("/xiaomiE2ee/keys/10").asText())
            assertArrayEquals(key, db.service().appKey(account.id, 10))
            assertThrows(IllegalStateException::class.java) { db.service().appKey(account.id, 11) }
            assertEquals("VERIFICATION_REQUIRED", xiaomiCredentialsMapper.readTree(db.sql.findById(ProviderAccount::class, account.id)!!.credentials).at("/xiaomiE2ee/state").asText())
            assertArrayEquals(key, db.service().appKey(account.id, 10))
            accounts.update(XiaomiAccountInput("other", "other-pass", "999").toEntity(account.id), DEFAULT_PROVIDER_ACCOUNT)
            assertFalse(xiaomiCredentialsMapper.readTree(db.sql.findById(ProviderAccount::class, account.id)!!.credentials).has("xiaomiE2ee"))
            assertThrows(IllegalStateException::class.java) { db.service().appKey(account.id, 10) }
        }
    }

    @Test fun accountDeletionDispatchesBySourceAndRejectsMissingAccounts(@TempDir directory: Path) {
        E2eeTestDatabase(directory.resolve("account-sources.db")).use { db ->
            val xiaomi = db.accounts.create(XiaomiAccountInput("Xiaomi", "pass", "123456"), DEFAULT_PROVIDER_ACCOUNT)
            val apple = db.sql.saveCommand(ProviderAccount {
                provider = CloudProvider.ICLOUD
                nickname = "Apple"
                userId = "apple@example.com"
                credentials = "{}"
            }, SaveMode.INSERT_ONLY).execute().modifiedEntity
            val icloud = ICloudAccountService(db.sql, db.notifications)
            val accounts = ProviderAccountService(db.sql, db.accounts, icloud)
            val media = CloudMediaService(db.sql,
                XiaomiCloudApi(db.tokens),
                ICloudPhotos(icloud))
            assertEquals(CloudProvider.XIAOMI, media.provider(xiaomi.id))
            assertEquals(CloudProvider.ICLOUD, media.provider(apple.id))
            accounts.delete(apple.id)
            assertNull(db.sql.findById(ProviderAccount::class, apple.id))
            assertNotNull(db.sql.findById(ProviderAccount::class, xiaomi.id))
            assertThrows(IllegalArgumentException::class.java) { media.provider(apple.id) }
            accounts.delete(xiaomi.id)
            assertNull(db.sql.findById(ProviderAccount::class, xiaomi.id))
        }
    }

    @Test fun missingKeysReuseAccountAuthenticationTemplateAndNotifyOnlyOnce(@TempDir directory: Path) {
        val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/notify") { exchange ->
            bodies += exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            E2eeTestDatabase(directory.resolve("notifications.db")).use { db ->
                val account = db.sql.saveCommand(XiaomiAccountInput("Protected account", "pass", "123456").toEntity(), SaveMode.INSERT_ONLY).execute().modifiedEntity
                val config = NotifyConfig(
                    url = "http://127.0.0.1:${server.address.port}/notify",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = "",
                    dailySummaryBody = null,
                    dailySummaryCron = null,
                    dailySummaryTimeZone = null,
                    passTokenExpiredBody = """{"account":"${'$'}{account.nickname}","userId":"${'$'}{account.userId}","id":"${'$'}{account.id}"}""",
                )
                db.sql.saveCommand(SystemConfig {
                    id = com.coooolfan.xiaomialbumsyncer.service.SystemConfigService.CONFIG_ID
                    password = "test"
                    exifToolPath = "exiftool"
                    assetsDateMapTimeZone = "UTC"
                    notifyConfig = config
                    starNoticeDismissed = false
                }, SaveMode.INSERT_ONLY).execute()
                val service = db.service()
                repeat(2) {
                    assertThrows(IllegalStateException::class.java) { service.appKey(account.id, 10) }
                }
                assertEquals(1, bodies.size)
                val sent = xiaomiCredentialsMapper.readTree(bodies.single())
                assertEquals("Protected account", sent.path("account").asText())
                assertEquals("123456", sent.path("userId").asText())
                assertEquals(account.id.toString(), sent.path("id").asText())
                val legacy = xiaomiCredentialsMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(config)
                    .put("xiaomiE2eeRequiredBody", "unused legacy template")
                val restored = xiaomiCredentialsMapper.treeToValue(legacy, NotifyConfig::class.java)
                assertEquals(config.passTokenExpiredBody, restored.passTokenExpiredBody)
                assertFalse(xiaomiCredentialsMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(restored).has("xiaomiE2eeRequiredBody"))
            }
        } finally { server.stop(0) }
    }

}
