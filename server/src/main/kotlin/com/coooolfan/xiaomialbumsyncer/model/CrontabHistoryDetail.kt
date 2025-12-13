package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import java.time.Instant

@Entity
interface CrontabHistoryDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val crontabHistory: CrontabHistory

    // TODO)) 这个字段可以删掉了
    val downloadTime: Instant

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val asset: Asset

    val filePath: String

    // 预检
    val precheckCompleted: Boolean

    // 下载
    val downloadCompleted: Boolean

    // 校验
    val sha1Verified: Boolean

    // EXIF 填充
    val exifFilled: Boolean

    // 修改时间更新
    val fsTimeUpdated: Boolean
}