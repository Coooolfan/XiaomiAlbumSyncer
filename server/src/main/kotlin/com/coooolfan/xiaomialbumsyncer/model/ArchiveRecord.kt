package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*
import java.time.Instant
import java.time.LocalDate

/**
 * 归档记录实体
 * 记录每次归档操作的详细信息
 */
@Entity
interface ArchiveRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /**
     * 关联的定时任务
     */
    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val crontab: Crontab

    @IdView("crontab")
    val crontabId: Long

    /**
     * 归档时间
     */
    val archiveTime: Instant

    /**
     * 归档模式（TIME / SPACE）
     */
    val archiveMode: ArchiveMode

    /**
     * 归档此日期之前的照片
     */
    val archiveBeforeDate: LocalDate

    /**
     * 归档文件数
     */
    val archivedCount: Int

    /**
     * 释放的云空间（字节）
     */
    val freedSpaceBytes: Long

    /**
     * 归档状态
     */
    val status: ArchiveStatus

    /**
     * 错误信息（如果失败）
     */
    val errorMessage: String?

    /**
     * 归档详情列表
     */
    @OneToMany(mappedBy = "archiveRecord")
    val details: List<ArchiveDetail>
}

/**
 * 归档模式枚举
 */
enum class ArchiveMode {
    /**
     * 基于时间
     */
    TIME,

    /**
     * 基于空间阈值
     */
    SPACE
}

/**
 * 归档状态枚举
 */
enum class ArchiveStatus {
    /**
     * 计划中
     */
    PLANNING,

    /**
     * 移动文件中
     */
    MOVING_FILES,

    /**
     * 删除云端中
     */
    DELETING_CLOUD,

    /**
     * 已完成
     */
    COMPLETED,

    /**
     * 失败
     */
    FAILED
}
