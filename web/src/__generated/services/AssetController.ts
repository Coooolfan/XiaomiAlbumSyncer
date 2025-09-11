import type {Executor} from '../';
import type {AssetDto} from '../model/dto/';

export class AssetController {
    
    constructor(private executor: Executor) {}
    
    readonly listAssets: (options: AssetControllerOptions['listAssets']) => Promise<
        ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>
    > = async(options) => {
        let _uri = '/api/asset/';
        _uri += encodeURIComponent(options.albumId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AssetDto['AssetController/DEFAULT_ASSET']>>;
    }
    
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
        readonly albumId: number
    }, 
    'listAssets': {
        readonly albumId: number
    }
}
