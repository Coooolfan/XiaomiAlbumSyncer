package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.OneToMany
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

    @Formula(dependencies = ["endTime"])
    val isCompleted: Boolean
        get() = endTime != null

    @OneToMany(mappedBy = "crontabHistory")
    val details: List<CrontabHistoryDetail>

}