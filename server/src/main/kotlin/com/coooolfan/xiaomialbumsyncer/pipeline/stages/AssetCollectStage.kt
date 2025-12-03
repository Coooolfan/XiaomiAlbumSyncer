package com.coooolfan.xiaomialbumsyncer.pipeline.stages

import com.coooolfan.xiaomialbumsyncer.pipeline.AssetPipelineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory

/**
 * 资源收集阶段处理器
 */
@Managed
class AssetCollectStage {

    private val log = LoggerFactory.getLogger(AssetCollectStage::class.java)

    fun start(
        scope: CoroutineScope,
        tasks: List<AssetPipelineContext>,
        downloadChannel: Channel<AssetPipelineContext>,
    ): Job = scope.launch {
        log.info("AssetCollectStage pushing {} tasks", tasks.size)
        for (task in tasks) {
            downloadChannel.send(task)
        }
        log.info("AssetCollectStage completed pushing tasks")
    }
}
