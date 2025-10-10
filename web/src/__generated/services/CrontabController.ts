import type {Executor} from '../';
import type {CrontabDto} from '../model/dto/';
import type {CrontabCreateInput, CrontabUpdateInput} from '../model/static/';

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
     * @return Result<Int> 返回执行结果，状态码201表示执行成功
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
         */
        readonly crontabId: number
    }
}
