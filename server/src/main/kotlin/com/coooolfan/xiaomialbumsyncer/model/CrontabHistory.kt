package com.coooolfan.xiaomialbumsyncer.model

import com.coooolfan.xiaomialbumsyncer.utils.CrontabHistoryDetailsCountResolver
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.*
import java.time.Instant

@Entity
interface CrontabHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val crontab: Crontab

    val startTime: Instant

    val endTime: Instant?

    // 时间线同步模式下各相册的快照，用于对比增量；null 表示本次运行未使用时间线模式
    @Serialized
    val timelineSnapshot: Map<Long, AlbumTimeline>?

    // 任务范围内的来源游标：小米相册 ID 或 iCloud 图库与相册范围；页级提交
    @Serialized
    @Column(name = "album_sync_cursors")
    val syncCursors: Map<String, String>?

    val fetchedAllAssets: Boolean

    @Formula(dependencies = ["endTime"])
    val isCompleted: Boolean
        get() = endTime != null

    @OneToMany(mappedBy = "crontabHistory")
    val details: List<CrontabHistoryDetail>

    @Transient(CrontabHistoryDetailsCountResolver::class)
    val detailsCount: Long

}
