package com.coooolfan.xiaomialbumsyncer.pipeline

import com.coooolfan.xiaomialbumsyncer.model.*
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.DownloadStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.ExifProcessingStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.FileTimeStage
import com.coooolfan.xiaomialbumsyncer.pipeline.stages.VerificationStage
import com.coooolfan.xiaomialbumsyncer.service.AssetService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.noear.solon.annotation.Managed
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * 计划任务流水线管理器
 */
@Managed
class CrontabPipeline(
    private val downloadStage: DownloadStage,
    private val verificationStage: VerificationStage,
    private val exifProcessingStage: ExifProcessingStage,
    private val fileTimeStage: FileTimeStage,
    private val assetService: AssetService,
    private val sql: KSqlClient,
) {

    private val log = LoggerFactory.getLogger(CrontabPipeline::class.java)

    // 从 Crontab 创建一个任务
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        request: PipelineRequest,
        concurrency: PipelineConcurrency = PipelineConcurrency(),
    ) {
        var total = 0
        var success = 0

        var pageIndex = 0
        val pageSize = 10

        // TODO)) 应该从 Crontab 开始
        listOf(request.crontabHistory).asFlow()
        .transform {
            var currentRows: Int

            do {
                // 1. 查询需要下载的资产(假设已经从远程同步好了本地数据库中的资产)
                // TODO)) 这里还需要考虑从终止的数据库中恢复的场景。
                val assets = assetService.getAssetsUndownloadByCrontab(it.crontab, pageIndex, pageSize)

                val details = mutableListOf<CrontabHistoryDetail>()
                val contexts = mutableListOf<AssetPipelineContext>()

                assets.forEach { asset ->
                    val detail = CrontabHistoryDetail.init(it, asset)
                    val context = AssetPipelineContext(asset, it.crontab.config, detail)
                    details.add(detail)
                    contexts.add(context)
                }

                // 2. 保存 CrontabHistoryDetail，落库
                sql.saveEntitiesCommand(details, SaveMode.INSERT_ONLY).execute()

                currentRows = assets.size
                pageIndex++

                emitAll(contexts.asFlow())
            } while (currentRows > 0)
        }.onEach {
            total++
        }.flatMapMerge(concurrency.downloaders) { context ->
            flow {
                emit(downloadStage.process(context))
            }.catch {
                log.error("资源 {} 的下载失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.verifiers) { context ->
            flow {
                emit(verificationStage.process(context))
            }.catch {
                log.error("资源 {} 的校验失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.exifProcessors) { context ->
            flow {
                emit(exifProcessingStage.process(context, request.systemConfig))
            }.catch {
                log.error("资源 {} 的 EXIF 处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.flatMapMerge(concurrency.fileTimeWorkers) { context ->
            flow {
                emit(fileTimeStage.process(context))
            }.catch {
                log.error("资源 {} 的文件时间阶段处理失败, 将跳过后续处理", context.asset.id, it)
            }
        }.onEach { context ->
            success++
            log.info("资源 {} 处理完成", context.asset.id)
        }.collect()

        sql.executeUpdate(CrontabHistory::class) {
            set(table.endTime, Instant.now())
            where(table.id eq request.crontabHistory.id)
        }

        log.info("Crontab {} 的流水线执行完毕, 成功 {}/{}", request.crontab.id, success, total)
    }
}

data class PipelineRequest(
    val crontab: Crontab,
    val systemConfig: SystemConfig,
    val crontabHistory: CrontabHistory,
)

data class PipelineConcurrency(
    val downloaders: Int = 4,
    val verifiers: Int = 2,
    val exifProcessors: Int = 1,
    val fileTimeWorkers: Int = 1,
)
