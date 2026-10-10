package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.jackson.JsonConverter
import org.babyfish.jimmer.jackson.LongToStringConverter
import org.babyfish.jimmer.sql.*
import java.time.Instant

@Entity
interface Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonConverter(LongToStringConverter::class)
    val id: Long

    /** 远端文件定位；来源由相册所属账号决定。 */
    @Key
    val remoteKey: String

    val fileName: String

    val type: AssetType

    val recordingType: RecordingType?

    val dateTaken: Instant

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    @Key
    val album: Album

    /** 来源返回的文件校验值；解码格式由账号来源决定。 */
    @Key
    val checksum: String

    val mimeType: String

    val title: String

    // 文件大小，单位：字节
    val size: Long

    @OneToMany(mappedBy = "asset")
    val downloadHistories: List<CrontabHistoryDetail>
}
