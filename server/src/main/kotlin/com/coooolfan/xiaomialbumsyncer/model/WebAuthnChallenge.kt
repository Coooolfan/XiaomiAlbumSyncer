package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id

@Entity
interface WebAuthnChallenge {
    @Id
    val id: String

    val challenge: String

    val type: String  // "registration" | "authentication"

    val createdAt: Long

    val expiresAt: Long
}
