import type {Executor} from '../';

/**
 * 归档控制器
 * 
 * 提供智能归档相关的API接口，包括预览归档、执行归档、获取归档记录等功能
 * 所有接口均需要用户登录认证
 */
export class ArchiveController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 预览归档计划
     * 
     * 根据配置计算归档计划，但不执行实际归档操作
     * 
     * @parameter {ArchiveControllerOptions['previewArchive']} options
     * - crontabId 定时任务ID
     * @return 返回归档计划
     */
    readonly previewArchive: (options: ArchiveControllerOptions['previewArchive']) => Promise<
        ArchivePlan
    > = async(options) => {
        let _uri = '/api/archive/preview/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<ArchivePlan>;
    }
    
    /**
     * 执行归档操作
     * 
     * 执行归档操作，将照片移动到backup文件夹，并可选择删除云端照片
     * 
     * @parameter {ArchiveControllerOptions['executeArchive']} options
     * - crontabId 定时任务ID
     * - body 归档执行请求
     * @return 返回归档记录ID
     */
    readonly executeArchive: (options: ArchiveControllerOptions['executeArchive']) => Promise<
        { archiveRecordId: number }
    > = async(options) => {
        let _uri = '/api/archive/execute/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<{ archiveRecordId: number }>;
    }
    
    /**
     * 获取归档记录列表
     * 
     * 获取指定定时任务的所有归档记录
     * 
     * @parameter {ArchiveControllerOptions['getArchiveRecords']} options
     * - crontabId 定时任务ID
     * @return 返回归档记录列表
     */
    readonly getArchiveRecords: (options: ArchiveControllerOptions['getArchiveRecords']) => Promise<
        ReadonlyArray<ArchiveRecord>
    > = async(options) => {
        let _uri = '/api/archive/records/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArchiveRecord>>;
    }
    
    /**
     * 获取归档详情
     * 
     * 获取指定归档记录的详细信息
     * 
     * @parameter {ArchiveControllerOptions['getArchiveDetails']} options
     * - recordId 归档记录ID
     * @return 返回归档详情列表
     */
    readonly getArchiveDetails: (options: ArchiveControllerOptions['getArchiveDetails']) => Promise<
        ReadonlyArray<ArchiveDetail>
    > = async(options) => {
        let _uri = '/api/archive/details/';
        _uri += encodeURIComponent(options.recordId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArchiveDetail>>;
    }
}

export type ArchiveControllerOptions = {
    'previewArchive': {
        readonly crontabId: number
    },
    'executeArchive': {
        readonly crontabId: number
        readonly body: ArchiveExecuteRequest
    },
    'getArchiveRecords': {
        readonly crontabId: number
    },
    'getArchiveDetails': {
        readonly recordId: number
    }
}

/**
 * 归档计划
 */
export interface ArchivePlan {
    readonly archiveBeforeDate?: string
    readonly assetsToArchive: ReadonlyArray<AssetToArchive>
    readonly estimatedFreedSpace: number
}

/**
 * 待归档资源
 */
export interface AssetToArchive {
    readonly assetId: string
    readonly fileName: string
    readonly fileSize: number
    readonly createTime: string
}

/**
 * 归档执行请求
 */
export interface ArchiveExecuteRequest {
    readonly confirmed: boolean
}

/**
 * 归档记录
 */
export interface ArchiveRecord {
    readonly id: number
    readonly crontabId: number
    readonly startTime: string
    readonly endTime?: string
    readonly status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'ROLLED_BACK'
    readonly mode: 'TIME' | 'SPACE'
    readonly archiveBeforeDate?: string
    readonly totalAssets: number
    readonly archivedCount: number
    readonly deletedFromCloudCount: number
    readonly freedSpace: number
    readonly errorMessage?: string
}

/**
 * 归档详情
 */
export interface ArchiveDetail {
    readonly id: number
    readonly recordId: number
    readonly assetId: string
    readonly fileName: string
    readonly fileSize: number
    readonly status: 'PENDING' | 'MOVED' | 'VERIFIED' | 'CLOUD_DELETED' | 'COMPLETED' | 'FAILED'
    readonly errorMessage?: string
}
