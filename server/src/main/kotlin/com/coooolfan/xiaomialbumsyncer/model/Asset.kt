package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import java.time.Instant

@Entity
interface Asset {
    @Id
    val id: Long

    val fileName: String

    val type: AssetType

    val dateTaken: Instant

    @ManyToOne
    val album: Album

    val sha1: String

    val mimeType: String

    val title: String
}