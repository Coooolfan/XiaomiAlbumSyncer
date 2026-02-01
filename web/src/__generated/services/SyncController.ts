import type {Executor} from '../';
import type {SyncRecordDto} from '../model/dto/';
import type {ExecuteSyncResponse, SyncService_ChangeSummary, SyncService_SyncStatusInfo} from '../model/static/';

/**
 * 同步控制器
 * 提供云端到本地的同步功能 API
 */
export class SyncController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 检测云端变化
     * 
     * @parameter {SyncControllerOptions['detectChanges']} options
     * - crontabId 定时任务 ID
     * @return 变化摘要
     * 
     */
    readonly detectChanges: (options: SyncControllerOptions['detectChanges']) => Promise<
        SyncService_ChangeSummary
    > = async(options) => {
        let _uri = '/api/sync/detect-changes/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SyncService_ChangeSummary>;
    }
    
    /**
     * 执行同步任务
     * 
     * @parameter {SyncControllerOptions['executeSync']} options
     * - crontabId 定时任务 ID
     * @return 同步记录 ID
     * 
     */
    readonly executeSync: (options: SyncControllerOptions['executeSync']) => Promise<
        ExecuteSyncResponse
    > = async(options) => {
        let _uri = '/api/sync/execute/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<ExecuteSyncResponse>;
    }
    
    /**
     * 获取同步记录列表
     * 
     * @parameter {SyncControllerOptions['getSyncRecords']} options
     * - crontabId 定时任务 ID
     * @return 同步记录列表
     * 
     */
    readonly getSyncRecords: (options: SyncControllerOptions['getSyncRecords']) => Promise<
        ReadonlyArray<SyncRecordDto['SyncController/SYNC_RECORD_LIST']>
    > = async(options) => {
        let _uri = '/api/sync/records/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<SyncRecordDto['SyncController/SYNC_RECORD_LIST']>>;
    }
    
    /**
     * 获取同步状态
     * 
     * @parameter {SyncControllerOptions['getSyncStatus']} options
     * - crontabId 定时任务 ID
     * @return 同步状态信息
     * 
     */
    readonly getSyncStatus: (options: SyncControllerOptions['getSyncStatus']) => Promise<
        SyncService_SyncStatusInfo
    > = async(options) => {
        let _uri = '/api/sync/status/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SyncService_SyncStatusInfo>;
    }
}

export type SyncControllerOptions = {
    'executeSync': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }, 
    'getSyncRecords': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }, 
    'getSyncStatus': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }, 
    'detectChanges': {
        /**
         * 定时任务 ID
         */
        readonly crontabId: number
    }
}
