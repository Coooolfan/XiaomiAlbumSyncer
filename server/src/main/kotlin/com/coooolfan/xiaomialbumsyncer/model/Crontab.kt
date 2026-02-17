package com.coooolfan.xiaomialbumsyncer.model

import com.coooolfan.xiaomialbumsyncer.utils.CrontabRunningResolver
import org.babyfish.jimmer.sql.*

@Entity
interface Crontab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    val description: String

    val enabled: Boolean

    val config: CrontabConfig

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val account: XiaomiAccount    // 绑定的小米账号

    @IdView("account")
    val accountId: Long           // 账号ID视图

    @ManyToMany
    val albums: List<Album>

    @Transient(CrontabRunningResolver::class)
    val running: Boolean

    @IdView("albums")
    val albumIds: List<Long>

    @OneToMany(mappedBy = "crontab")
    val histories: List<CrontabHistory>

    @OneToMany(mappedBy = "crontab")
    val syncRecords: List<SyncRecord>

    @OneToMany(mappedBy = "crontab")
    val archiveRecords: List<ArchiveRecord>

}