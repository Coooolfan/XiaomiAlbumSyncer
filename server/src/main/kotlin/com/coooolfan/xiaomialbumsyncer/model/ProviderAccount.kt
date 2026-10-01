package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*

@Entity
interface ProviderAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Default("XIAOMI")
    val provider: CloudProvider

    val nickname: String      // 账号昵称，用于界面展示
    val credentials: String  // 来源特定的 JSON 凭据
    val userId: String        // 来源账号标识

    @OneToMany(mappedBy = "account")
    val albums: List<Album>

    @OneToMany(mappedBy = "account")
    val crontabs: List<Crontab>
}
