import type {Executor} from '../';
import type {ProviderAccountDto} from '../model/dto/';
import type {XiaomiAccountInput} from '../model/static/';

/**
 * 云服务账号查询与删除，以及小米账号凭据写入。
 */
export class ProviderAccountController {

    constructor(private executor: Executor) {}

    /**
     * 创建小米账号；iCloud 账号通过 ICloudController 登录接口创建。
     */
    readonly create: (options: ProviderAccountControllerOptions['create']) => Promise<
        ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']
    > = async(options) => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']>;
    }

    /**
     * 删除云服务账号及关联相册、定时任务。
     */
    readonly delete: (options: ProviderAccountControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }

    /**
     * 查询云服务账号的公开信息，不返回凭据。
     */
    readonly listAll: () => Promise<
        ReadonlyArray<ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']>
    > = async() => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']>>;
    }

    /**
     * 更新小米账号信息及凭据。
     */
    readonly update: (options: ProviderAccountControllerOptions['update']) => Promise<
        ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']>;
    }
}

export type ProviderAccountControllerOptions = {
    'listAll': {},
    'create': {
        readonly body: XiaomiAccountInput
    },
    'update': {
        readonly id: number,
        readonly body: XiaomiAccountInput
    },
    'delete': {
        readonly id: number
    }
}
