package com.coooolfan.xiaomialbumsyncer.pipeline

/**
 * 流水线配置类
 *
 * 作用：
 * 1. 定义各个处理阶段的并发工作协程数量
 * 2. 配置各阶段之间Channel的缓冲区大小
 * 3. 设置重试次数限制和超时时间
 * 4. 提供性能调优相关的参数配置
 * 5. 支持根据系统资源动态调整配置
 *
 * 可调参数：
 * - 下载并发数（网络IO密集）
 * - 校验并发数（CPU+磁盘IO密集）
 * - EXIF处理并发数（外部工具调用）
 * - 文件时间处理并发数（文件系统操作）
 */
data class PipelineConfig(
    // TODO: 定义流水线配置参数
    val todo: Any
)