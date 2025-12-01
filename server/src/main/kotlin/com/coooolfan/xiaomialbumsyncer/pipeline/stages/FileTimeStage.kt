package com.coooolfan.xiaomialbumsyncer.pipeline.stages

/**
 * 文件时间处理阶段处理器
 * 
 * 职责：
 * 1. 从文件时间队列中消费已处理EXIF的AssetTask任务
 * 2. 修改文件的创建时间和修改时间为Asset的拍摄时间
 * 3. 将临时文件移动到最终目标位置
 * 4. 更新数据库中的CrontabHistoryDetail记录
 * 5. 标记任务为完成状态
 * 
 * 特点：
 * - 轻量级文件系统操作
 * - 建议并发数：1-2个协程
 * - 流水线的最后一个阶段，负责任务的最终收尾工作
 */
class FileTimeStage {

}