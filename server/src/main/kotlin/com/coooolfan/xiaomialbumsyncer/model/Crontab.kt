package com.coooolfan.xiaomialbumsyncer.model

import com.coooolfan.xiaomialbumsyncer.utils.CrontabRunningResolver
import org.babyfish.jimmer.jackson.JsonConverter
import org.babyfish.jimmer.jackson.LongListToStringListConverter
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
    val account: XiaomiAccount    // 绑定的小米账号

    @IdView("account")
    val accountId: Long           // 账号ID视图

    @ManyToMany
    val albums: List<Album>

    @Transient(CrontabRunningResolver::class)
    val running: Boolean

    @IdView("albums")
    @JsonConverter(LongListToStringListConverter::class)
    val albumIds: List<Long>

    @OneToMany(mappedBy = "crontab")
    val histories: List<CrontabHistory>

}