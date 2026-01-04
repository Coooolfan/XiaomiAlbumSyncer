import type {Executor} from '../';
import type {XiaomiAccountDto} from '../model/dto/';
import type {XiaomiAccountCreate, XiaomiAccountUpdate} from '../model/static/';

/**
 * 小米账号管理控制器
 * 
 * 提供小米账号的增删改查功能
 * 所有接口均需要用户登录认证
 */
export class XiaomiAccountController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建新账号
     */
    readonly create: (options: XiaomiAccountControllerOptions['create']) => Promise<
        XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']
    > = async(options) => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>;
    }
    
    /**
     * 删除账号
     */
    readonly delete: (options: XiaomiAccountControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取所有账号列表
     */
    readonly listAll: () => Promise<
        ReadonlyArray<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>
    > = async() => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']>>;
    }
    
    /**
     * 更新账号信息
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
        readonly body: XiaomiAccountCreate
    }, 
    'update': {
        readonly id: number, 
        readonly body: XiaomiAccountUpdate
    }, 
    'delete': {
        readonly id: number
    }
}
