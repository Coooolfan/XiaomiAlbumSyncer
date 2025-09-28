import type {Executor} from '../';
import type {Dynamic_Album} from '../model/dynamic/';

export class AlbumsController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取所有相册列表
     *  
     * 此接口用于获取数据库中存储的所有相册信息
     * 需要用户登录认证才能访问
     *  
     * @return List<Album> 返回所有相册的列表，包含相册的基本信息
     *  
     */
    readonly listAlbums: () => Promise<
        ReadonlyArray<Dynamic_Album>
    > = async() => {
        let _uri = '/api/album';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<Dynamic_Album>>;
    }
    
    /**
     * 刷新相册列表
     *  
     * 此接口用于从远程服务获取最新的相册列表并更新到本地数据库
     * 需要用户登录认证才能访问
     *  
     * @return List<Album> 返回刷新后的相册列表，包含所有相册的基本信息
     *  
     */
    readonly refreshAlbumn: () => Promise<
        ReadonlyArray<Dynamic_Album>
    > = async() => {
        let _uri = '/api/album/lastest';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<Dynamic_Album>>;
    }
}

export type AlbumsControllerOptions = {
    'refreshAlbumn': {}, 
    'listAlbums': {}
}
