import type {Executor} from '../';
import type {AssetDto} from '../model/dto/';

export class AssetController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取指定相册的媒体资源列表
     *  
     * 此接口用于获取数据库中存储的指定相册的所有媒体资源信息
     * 需要用户登录认证才能访问（类级别注解）
     *  
     * @parameter {AssetControllerOptions['listAssets']} options
     * - albumId 相册ID，用于指定要获取哪个相册的媒体资源
     * @return List<Asset> 返回指定相册的所有媒体资源列表
     *  
     */
    readonly listAssets: (options: AssetControllerOptions['listAssets']) => Promise<
        ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>
    > = async(options) => {
        let _uri = '/api/asset/';
        _uri += encodeURIComponent(options.albumId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>>;
    }
    
    /**
     * 刷新指定相册的媒体资源列表
     *  
     * 此接口用于从远程服务获取指定相册的最新媒体资源列表并更新到本地数据库
     * 需要用户登录认证才能访问（类级别注解）
     *  
     * @parameter {AssetControllerOptions['refreshAssets']} options
     * - albumId 相册ID，用于指定要刷新哪个相册的媒体资源
     * @return List<Asset> 返回刷新后的媒体资源列表，包含所有媒体资源的基本信息
     *  
     */
    readonly refreshAssets: (options: AssetControllerOptions['refreshAssets']) => Promise<
        ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>
    > = async(options) => {
        let _uri = '/api/asset/';
        _uri += encodeURIComponent(options.albumId);
        _uri += '/lastest';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>>;
    }
}

export type AssetControllerOptions = {
    'refreshAssets': {
        /**
         * 相册ID，用于指定要刷新哪个相册的媒体资源
         */
        readonly albumId: number
    }, 
    'listAssets': {
        /**
         * 相册ID，用于指定要获取哪个相册的媒体资源
         */
        readonly albumId: number
    }
}
