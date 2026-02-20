import type {Executor} from '../';
import type {ArchiveDetailDto, ArchiveRecordDto} from '../model/dto/';
import type {ArchiveService_ArchivePlan, ExecuteArchiveRequest, ExecuteArchiveResponse} from '../model/static/';

/**
 * 归档控制器
 */
export class ArchiveController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 执行归档任务
     * 
     * @parameter {ArchiveControllerOptions['executeArchive']} options
     * - crontabId 定时任务 ID
     * - request 归档执行请求
     * @return 归档记录 ID
     * 
     */
    readonly executeArchive: (options: ArchiveControllerOptions['executeArchive']) => Promise<
        ExecuteArchiveResponse
    > = async(options) => {
        let _uri = '/api/archive/execute/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ExecuteArchiveResponse>;
    }
    
    /**
     * 获取归档详情
     * 
     * @parameter {ArchiveControllerOptions['getArchiveDetails']} options
     * - recordId 归档记录 ID
     * @return 归档详情列表
     * 
     */
    readonly getArchiveDetails: (options: ArchiveControllerOptions['getArchiveDetails']) => Promise<
        ReadonlyArray<ArchiveDetailDto['ArchiveController/ARCHIVE_DETAIL_LIST']>
    > = async(options) => {
        let _uri = '/api/archive/details/';
        _uri += encodeURIComponent(options.recordId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArchiveDetailDto['ArchiveController/ARCHIVE_DETAIL_LIST']>>;
    }
    
    /**
     * 获取归档记录列表
     * 
     * @parameter {ArchiveControllerOptions['getArchiveRecords']} options
     * - crontabId 定时任务 ID
     * @return 归档记录列表
     * 
     */
    readonly getArchiveRecords: (options: ArchiveControllerOptions['getArchiveRecords']) => Promise<
        ReadonlyArray<ArchiveRecordDto['ArchiveController/ARCHIVE_RECORD_LIST']>
    > = async(options) => {
        let _uri = '/api/archive/records/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArchiveRecordDto['ArchiveController/ARCHIVE_RECORD_LIST']>>;
    }
    
    /**
     * 预览归档计划
     * 
     * @parameter {ArchiveControllerOptions['previewArchive']} options
     * - crontabId 定时任务 ID
     * @return 归档计划
     * 
     */
    readonly previewArchive: (options: ArchiveControllerOptions['previewArchive']) => Promise<
        ArchiveService_ArchivePlan
    > = async(options) => {
        let _uri = '/api/archive/preview/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<ArchiveService_ArchivePlan>;
    }
}

export type ArchiveControllerOptions = {
    'previewArchive': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }, 
    'executeArchive': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number, 
        /**
         * 归档执行请求
         */
        readonly body: ExecuteArchiveRequest
    }, 
    'getArchiveRecords': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }, 
    'getArchiveDetails': {
        /**
         * 归档记录 ID
         */
        readonly recordId: number
    }
}
