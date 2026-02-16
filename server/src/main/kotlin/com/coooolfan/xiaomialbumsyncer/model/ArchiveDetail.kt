package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*

/**
 * 归档详情实体
 * 记录每个文件的归档操作详情
 */
@Entity
interface ArchiveDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /**
     * 关联的归档记录
     */
    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val archiveRecord: ArchiveRecord

    @IdView("archiveRecord")
    val archiveRecordId: Long

    /**
     * 关联的资产
     */
    @ManyToOne
    val asset: Asset

    @IdView("asset")
    val assetId: Long

    /**
     * 原路径（sync）
     */
    val sourcePath: String

    /**
     * 目标路径（backup）
     */
    val targetPath: String

    /**
     * 是否已移动到 backup
     */
    val isMovedToBackup: Boolean

    /**
     * 是否已从云端删除
     */
    val isDeletedFromCloud: Boolean

    /**
     * 错误信息（如果失败）
     */
    val errorMessage: String?
}
