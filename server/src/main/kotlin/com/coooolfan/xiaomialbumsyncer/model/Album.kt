package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.OneToMany
import java.time.Instant

@Entity
interface Album {
    @Id
    val id: Long

    val name: String

    val assetCount: Int

    // 最后更新时间：指的是相册最后被修改的时间，不是XiaomiAlbumSyncer获取此相册的时间
    val lastUpdateTime: Instant

    @OneToMany(mappedBy = "album")
    val assets: List<Asset>
}