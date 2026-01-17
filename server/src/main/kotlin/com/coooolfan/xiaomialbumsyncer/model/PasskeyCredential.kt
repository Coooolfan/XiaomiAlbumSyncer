package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id

@Entity
interface PasskeyCredential {
    @Id
    val id: String  // credential ID (base64url)

    val name: String

    val publicKeyCose: ByteArray

    val signCount: Long

    val transports: String?  // JSON array

    val attestationFmt: String

    val aaguid: String?

    val createdAt: Long

    val lastUsedAt: Long?
}
