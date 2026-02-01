import type {Executor} from '../';

/**
 * 同步控制器
 * 
 * 提供云端同步相关的API接口，包括执行同步、获取同步记录、查询同步状态等功能
 * 所有接口均需要用户登录认证
 */
export class SyncController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 执行同步操作
     * 
     * 检测云端与本地的差异，并执行同步操作（新增、删除、更新）
     * 
     * @parameter {SyncControllerOptions['executeSync']} options
     * - crontabId 定时任务ID
     * @return 返回同步记录ID
     */
    readonly executeSync: (options: SyncControllerOptions['executeSync']) => Promise<
        { syncRecordId: number }
    > = async(options) => {
        let _uri = '/api/sync/execute/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<{ syncRecordId: number }>;
    }
    
    /**
     * 获取同步记录列表
     * 
     * 获取指定定时任务的所有同步记录
     * 
     * @parameter {SyncControllerOptions['getSyncRecords']} options
     * - crontabId 定时任务ID
     * @return 返回同步记录列表
     */
    readonly getSyncRecords: (options: SyncControllerOptions['getSyncRecords']) => Promise<
        ReadonlyArray<SyncRecord>
    > = async(options) => {
        let _uri = '/api/sync/records/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<SyncRecord>>;
    }
    
    /**
     * 获取同步状态
     * 
     * 获取指定定时任务的当前同步状态
     * 
     * @parameter {SyncControllerOptions['getSyncStatus']} options
     * - crontabId 定时任务ID
     * @return 返回同步状态信息
     */
    readonly getSyncStatus: (options: SyncControllerOptions['getSyncStatus']) => Promise<
        SyncStatusInfo
    > = async(options) => {
        let _uri = '/api/sync/status/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SyncStatusInfo>;
    }
    
    /**
     * 检测云端变化
     * 
     * 检测云端与本地的差异，但不执行同步操作
     * 
     * @parameter {SyncControllerOptions['detectChanges']} options
     * - crontabId 定时任务ID
     * @return 返回变化摘要
     */
    readonly detectChanges: (options: SyncControllerOptions['detectChanges']) => Promise<
        ChangeSummary
    > = async(options) => {
        let _uri = '/api/sync/detect-changes/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ChangeSummary>;
    }
}

export type SyncControllerOptions = {
    'executeSync': {
        readonly crontabId: number
    },
    'getSyncRecords': {
        readonly crontabId: number
    },
    'getSyncStatus': {
        readonly crontabId: number
    },
    'detectChanges': {
        readonly crontabId: number
    }
}

/**
 * 同步记录
 */
export interface SyncRecord {
    readonly id: number
    readonly crontabId: number
    readonly startTime: string
    readonly endTime?: string
    readonly status: 'RUNNING' | 'COMPLETED' | 'FAILED'
    readonly totalChanges: number
    readonly successCount: number
    readonly failedCount: number
    readonly errorMessage?: string
}

/**
 * 同步状态信息
 */
export interface SyncStatusInfo {
    readonly isRunning: boolean
    readonly lastSyncTime?: string
    readonly lastSyncResult?: 'COMPLETED' | 'FAILED'
}

/**
 * 变化摘要
 */
export interface ChangeSummary {
    readonly addedAssets: ReadonlyArray<AssetChange>
    readonly deletedAssets: ReadonlyArray<AssetChange>
    readonly updatedAssets: ReadonlyArray<AssetChange>
}

/**
 * 资源变化
 */
export interface AssetChange {
    readonly assetId: string
    readonly fileName: string
    readonly sha1?: string
}
