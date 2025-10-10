package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.jackson.JsonConverter
import org.babyfish.jimmer.jackson.LongToStringConverter
import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.OneToMany
import java.time.Instant

@Entity
interface Asset {
    @Id
    @JsonConverter(LongToStringConverter::class)
    val id: Long

    val fileName: String

    val type: AssetType

    val dateTaken: Instant

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val album: Album

    val sha1: String

    val mimeType: String

    val title: String

    // 文件大小，单位：字节
    val size: Long

    @OneToMany(mappedBy = "asset")
    val downloadHistories: List<CrontabHistoryDetail>
}