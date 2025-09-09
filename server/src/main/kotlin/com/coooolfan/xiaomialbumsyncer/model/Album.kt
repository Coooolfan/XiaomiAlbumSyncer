package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Key
import java.time.Instant

@Entity
interface Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    @Key
    val cloudId: String

    val assetCount: Int

    // 最后更新时间：指的是相册最后被修改的时间，不是XiaomiAlbumSyncer获取此相册的时间
    val lastUpdateTime: Instant
}