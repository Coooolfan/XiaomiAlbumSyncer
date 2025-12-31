package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.jackson.JsonConverter
import org.babyfish.jimmer.jackson.LongToStringConverter
import org.babyfish.jimmer.sql.*
import java.time.Instant

@Entity
interface Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val remoteId: Long        // 小米云的原始 albumId

    val name: String

    val assetCount: Long

    // 最后更新时间：指的是相册最后被修改的时间，不是XiaomiAlbumSyncer获取此相册的时间
    val lastUpdateTime: Instant

    @ManyToOne
    val account: XiaomiAccount    // 关联的小米账号

    @IdView("account")
    val accountId: Long           // 账号ID视图

    @OneToMany(mappedBy = "album")
    val assets: List<Asset>
}