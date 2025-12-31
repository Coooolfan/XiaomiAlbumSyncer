package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.OneToMany

@Entity
interface XiaomiAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val nickname: String      // 账号昵称，用于界面展示
    val passToken: String     // 小米账号 passToken
    val userId: String        // 小米账号 userId

    @OneToMany(mappedBy = "account")
    val albums: List<Album>

    @OneToMany(mappedBy = "account")
    val crontabs: List<Crontab>
}
