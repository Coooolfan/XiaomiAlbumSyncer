package com.coooolfan.xiaomialbumsyncer.model

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

    @ManyToMany
    val albums: List<Album>

    @IdView("albums")
    @JsonConverter(LongListToStringListConverter::class)
    val albumIds: List<Long>

    @OneToMany(mappedBy = "crontab")
    val histories: List<CrontabHistory>

}