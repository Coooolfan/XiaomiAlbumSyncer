import type {Executor} from '../';
import type {XiaomiAccountDto} from '../model/dto/';
import type {XiaomiAccountCreate, XiaomiAccountUpdate} from '../model/static/';

/**
 * 小米账号管理控制器
 * 
 * 提供小米账号的增删改查功能
 * 所有接口均需要用户登录认证
 * 
 */
export class XiaomiAccountController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建新的小米账号
     * 
     * 此接口用于在系统中添加新的小米账号配置
     * 需要用户登录认证才能访问
     * 
     * @parameter {XiaomiAccountControllerOptions['create']} options
     * - create 账号创建参数，包含昵称、passToken、userId等信息
     * @return XiaomiAccount 返回创建成功的账号对象
     * 
     */
    readonly create: (options: XiaomiAccountControllerOptions['create']) => Promise<
        XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']
    > = async(options) => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>;
    }
    
    /**
     * 删除小米账号
     * 
     * 此接口用于从系统中删除指定的小米账号
     * 删除账号会同时删除关联的相册和定时任务（由数据库外键约束处理）
     * 需要用户登录认证才能访问
     * 
     * @parameter {XiaomiAccountControllerOptions['delete']} options
     * - id 账号ID，用于指定要删除的账号
     * 
     */
    readonly delete: (options: XiaomiAccountControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取所有小米账号列表
     * 
     * 此接口用于获取系统中配置的所有小米账号信息
     * 需要用户登录认证才能访问
     * 
     * @return List<XiaomiAccount> 返回所有小米账号的列表
     * 
     */
    readonly listAll: () => Promise<
        ReadonlyArray<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>
    > = async() => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>>;
    }
    
    /**
     * 更新小米账号信息
     * 
     * 此接口用于更新已存在的小米账号配置信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {XiaomiAccountControllerOptions['update']} options
     * - id 账号ID，用于指定要更新的账号
     * - update 账号更新参数，包含要更新的账号信息
     * @return XiaomiAccount 返回更新后的账号对象
     * 
     */
    readonly update: (options: XiaomiAccountControllerOptions['update']) => Promise<
        XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>;
    }
}

export type XiaomiAccountControllerOptions = {
    'listAll': {}, 
    'create': {
        /**
         * 账号创建参数，包含昵称、passToken、userId等信息
         */
        readonly body: XiaomiAccountCreate
    }, 
    'update': {
        /**
         * 账号ID，用于指定要更新的账号
         */
        readonly id: number, 
        /**
         * 账号更新参数，包含要更新的账号信息
         */
        readonly body: XiaomiAccountUpdate
    }, 
    'delete': {
        /**
         * 账号ID，用于指定要删除的账号
         * 
         */
        readonly id: number
    }
}
