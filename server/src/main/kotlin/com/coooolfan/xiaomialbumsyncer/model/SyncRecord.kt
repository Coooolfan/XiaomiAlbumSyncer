package com.coooolfan.xiaomialbumsyncer.model

import org.babyfish.jimmer.sql.*
import java.time.Instant

/**
 * 同步记录实体
 * 记录每次同步操作的详细信息
 */
@Entity
interface SyncRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    // 关联的定时任务
    @OnDissociate(DissociateAction.DELETE)
    @ManyToOne
    val crontab: Crontab

    @IdView("crontab")
    val crontabId: Long
    val syncTime: Instant  // 同步时间
    val addedCount: Int    // 新增文件数
    val deletedCount: Int    // 删除文件数
    val updatedCount: Int   //更新文件数
    val status: SyncStatus // 同步状态
    val errorMessage: String?  // 错误信息

    // 同步详情列表
    @OneToMany(mappedBy = "syncRecord")
    val details: List<SyncRecordDetail> 
}

// 同步状态枚举
enum class SyncStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
