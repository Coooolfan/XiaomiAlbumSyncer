import type {Executor} from '../';
import type {CrontabDto} from '../model/dto/';
import type {CrontabCreateInput, CrontabCurrentStats, CrontabUpdateInput} from '../model/static/';

/**
 * 定时任务管理控制器
 * 
 * 提供定时任务相关的API接口，包括定时任务的创建、更新、删除、执行等功能
 * 所有接口均需要用户登录认证（通过类级别注解 @SaCheckLogin 控制）
 * 
 */
export class CrontabController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建新的定时任务
     * 
     * 此接口用于在系统中创建新的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['createCrontab']} options
     * - input 定时任务输入参数，包含任务的配置信息
     * @return Crontab 返回创建成功的定时任务对象
     * 
     */
    readonly createCrontab: (options: CrontabControllerOptions['createCrontab']) => Promise<
        CrontabDto['CrontabController/DEFAULT_CRONTAB']
    > = async(options) => {
        let _uri = '/api/crontab';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CrontabDto['CrontabController/DEFAULT_CRONTAB']>;
    }
    
    /**
     * 删除指定定时任务
     * 
     * 此接口用于从系统中删除指定的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['deleteCrontab']} options
     * - crontabId 定时任务ID，用于指定要删除的任务
     * 
     */
    readonly deleteCrontab: (options: CrontabControllerOptions['deleteCrontab']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 立即执行指定定时任务
     * 
     * 此接口用于立即执行指定的定时任务，不等待预定的执行时间
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['executeCrontab']} options
     * - crontabId 定时任务ID，用于指定要立即执行的任务
     * 
     */
    readonly executeCrontab: (options: CrontabControllerOptions['executeCrontab']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        _uri += '/executions';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
    }
    
    /**
     * 立即执行指定定时任务的EXIF填充操作
     * 
     * 此接口用于立即执行指定定时任务中的EXIF填充操作
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['executeCrontabExifTime']} options
     * - crontabId 定时任务ID，用于指定要执行EXIF填充操作的任务
     * 
     */
    readonly executeCrontabExifTime: (options: CrontabControllerOptions['executeCrontabExifTime']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        _uri += '/fill-exif/executions';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
    }
    
    /**
     * 立即执行指定定时任务的文件系统时间重写操作
     * 
     * 此接口用于立即执行指定定时任务中的文件系统时间重写操作
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['executeCrontabRewriteFileSystemTime']} options
     * - crontabId 定时任务ID，用于指定要执行文件系统时间重写操作的任务
     * 
     */
    readonly executeCrontabRewriteFileSystemTime: (options: CrontabControllerOptions['executeCrontabRewriteFileSystemTime']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        _uri += '/rewrite-fs-time/executions';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
    }
    
    /**
     * 获取指定定时任务的当前统计信息
     * 
     * 此接口用于获取指定定时任务的当前执行统计信息，包括资源数量、下载完成数等
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['getCrontabCurrentStats']} options
     * - crontabId 定时任务ID，用于指定要获取统计信息的任务
     * @return CrontabCurrentStats 返回定时任务的当前统计信息
     * 
     */
    readonly getCrontabCurrentStats: (options: CrontabControllerOptions['getCrontabCurrentStats']) => Promise<
        CrontabCurrentStats
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        _uri += '/current';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<CrontabCurrentStats>;
    }
    
    /**
     * 获取所有定时任务列表
     * 
     * 此接口用于获取系统中配置的所有定时任务信息
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @return List<Crontab> 返回所有定时任务的列表，包含任务的基本信息和执行历史
     * 
     */
    readonly listCrontabs: () => Promise<
        ReadonlyArray<CrontabDto['CrontabController/DEFAULT_CRONTAB']>
    > = async() => {
        let _uri = '/api/crontab';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<CrontabDto['CrontabController/DEFAULT_CRONTAB']>>;
    }
    
    /**
     * 更新指定定时任务
     * 
     * 此接口用于更新系统中已存在的定时任务配置
     * 需要用户登录认证才能访问（类级别注解）
     * 
     * @parameter {CrontabControllerOptions['updateCrontab']} options
     * - input 定时任务输入参数，包含要更新的配置信息
     * - crontabId 定时任务ID，用于指定要更新的任务
     * @return Crontab 返回更新后的定时任务对象
     * 
     */
    readonly updateCrontab: (options: CrontabControllerOptions['updateCrontab']) => Promise<
        CrontabDto['CrontabController/DEFAULT_CRONTAB']
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CrontabDto['CrontabController/DEFAULT_CRONTAB']>;
    }
}

export type CrontabControllerOptions = {
    'listCrontabs': {}, 
    'createCrontab': {
        /**
         * 定时任务输入参数，包含任务的配置信息
         */
        readonly body: CrontabCreateInput
    }, 
    'updateCrontab': {
        /**
         * 定时任务输入参数，包含要更新的配置信息
         */
        readonly body: CrontabUpdateInput, 
        /**
         * 定时任务ID，用于指定要更新的任务
         */
        readonly crontabId: number
    }, 
    'deleteCrontab': {
        /**
         * 定时任务ID，用于指定要删除的任务
         * 
         */
        readonly crontabId: number
    }, 
    'executeCrontab': {
        /**
         * 定时任务ID，用于指定要立即执行的任务
         * 
         */
        readonly crontabId: number
    }, 
    'getCrontabCurrentStats': {
        /**
         * 定时任务ID，用于指定要获取统计信息的任务
         */
        readonly crontabId: number
    }, 
    'executeCrontabExifTime': {
        /**
         * 定时任务ID，用于指定要执行EXIF填充操作的任务
         * 
         */
        readonly crontabId: number
    }, 
    'executeCrontabRewriteFileSystemTime': {
        /**
         * 定时任务ID，用于指定要执行文件系统时间重写操作的任务
         * 
         */
        readonly crontabId: number
    }
}
