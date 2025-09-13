package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*
import org.noear.solon.validation.annotation.Pattern

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
    val albumIds: List<Long>

}