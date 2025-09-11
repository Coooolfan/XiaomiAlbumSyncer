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

    // cron 表达式
    // eg: "0 0 * * *" 每天午夜
    val expression: String

    val timeZone: String

    @ManyToMany
    val albums: List<Album>

    @IdView("albums")
    val albumIds: List<Long>

}