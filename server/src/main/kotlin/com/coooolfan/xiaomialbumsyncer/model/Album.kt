package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*
import java.time.Instant

@Entity
interface Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /** 远端相册定位；来源由所属账号决定。 */
    val remoteKey: String

    val name: String

    val assetCount: Long?

    // 最后更新时间：指的是相册最后被修改的时间，不是XiaomiAlbumSyncer获取此相册的时间
    val lastUpdateTime: Instant?

    // 本地存在，但是远程不存在的相册。比如相册在小米云上被删除
    val shadow: Boolean

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val account: ProviderAccount    // 所属云服务账号

    @IdView("account")
    val accountId: Long           // 账号ID视图

    @OneToMany(mappedBy = "album")
    val assets: List<Asset>
}