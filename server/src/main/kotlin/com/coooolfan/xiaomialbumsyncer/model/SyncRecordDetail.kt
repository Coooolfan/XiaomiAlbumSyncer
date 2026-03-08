package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*

/**
 * 同步记录详情实体
 * 记录每个文件的同步操作详情
 */
@Entity
interface SyncRecordDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /**
     * 关联的同步记录
     */
    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val syncRecord: SyncRecord

    @IdView("syncRecord")
    val syncRecordId: Long

    /**
     * 关联的资产（删除操作时可能为 null）
     */
    @ManyToOne
    val asset: Asset?

    @IdView("asset")
    val assetId: Long?

    /**
     * 操作类型
     */
    val operation: SyncOperation

    /**
     * 文件路径
     */
    val filePath: String

    /**
     * 是否完成
     */
    val isCompleted: Boolean

    /**
     * 错误信息（如果失败）
     */
    val errorMessage: String?
}

/**
 * 同步操作类型枚举
 */
enum class SyncOperation {
    /**
     * 新增
     */
    ADD,

    /**
     * 删除
     */
    DELETE,

    /**
     * 更新
     */
    UPDATE
}
