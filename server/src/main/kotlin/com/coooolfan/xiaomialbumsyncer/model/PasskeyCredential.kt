package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id

@Entity
interface PasskeyCredential {
    @Id
    val id: String  // 凭据 ID（base64url）

    val name: String

    val publicKeyCose: ByteArray

    val signCount: Long

    val transports: String?  // JSON 数组

    val attestationFmt: String

    val aaguid: String?

    val createdAt: Long

    val lastUsedAt: Long?
}
