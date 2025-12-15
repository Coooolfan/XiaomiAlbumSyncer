package com.coooolfan.xiaomialbumsyncer.model

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

    @Serialized
    val timelineSnapshot: Map<Long, AlbumTimeline>

    val fetchedAllAssets: Boolean

    @Formula(dependencies = ["endTime"])
    val isCompleted: Boolean
        get() = endTime != null

    @OneToMany(mappedBy = "crontabHistory")
    val details: List<CrontabHistoryDetail>

}