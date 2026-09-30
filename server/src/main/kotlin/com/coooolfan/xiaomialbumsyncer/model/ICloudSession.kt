package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*

/** 账号的加密登录凭据与会话，不通过账号查询接口返回。 */
@Entity
interface ICloudSession {
    @Id
    val id: Long
    val encryptedData: String
    val state: String
}
