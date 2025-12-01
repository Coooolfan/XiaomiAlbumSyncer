package com.coooolfan.xiaomialbumsyncer.pipeline.stages

/**
 * EXIF处理阶段处理器
 * 
 * 职责：
 * 1. 从EXIF处理队列中消费已校验的AssetTask任务
 * 2. 根据Asset的拍摄时间修改文件的EXIF元数据
 * 3. 调用外部exiftool工具进行EXIF时间重写
 * 4. 处理时区转换和日期格式化
 * 5. 完成后将任务推送到文件时间处理队列
 * 
 * 特点：
 * - 依赖外部工具（exiftool），可能有较大开销
 * - 建议并发数：1-3个协程（避免过载外部工具）
 * - 仅处理图片文件，视频文件直接跳过
 */
class ExifProcessingStage {

}