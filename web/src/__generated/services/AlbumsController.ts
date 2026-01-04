import type {Executor} from '../';
import type {Dynamic_Album} from '../model/dynamic/';

/**
 * 相册管理控制器
 * 
 * 提供相册相关的API接口，包括刷新相册列表、获取相册信息、查询相册日期映射等功能
 * 所有接口均需要用户登录认证（通过类级别注解 @SaCheckLogin 控制）
 * 
 */
export class AlbumsController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取指定相册的日期映射
     * 
     * 此接口用于获取指定相册中照片的日期分布情况
     * 需要用户登录认证才能访问
     * 
     * @parameter {AlbumsControllerOptions['fetchDateMap']} options
     * - albumIds List<Long> 需要查询的相册ID列表，可选参数，默认为空列表
     * - start Instant 查询的开始时间戳，可选参数，默认为纪元时间
     * - end Instant 查询的结束时间戳，可选参数，默认为当前时间
     * @return Map<LocalDate, Long> 返回一个映射，键为日期，值为该日期下的照片数量
     * 
     */
    readonly fetchDateMap: (options: AlbumsControllerOptions['fetchDateMap']) => Promise<
        {readonly [key:string]: number}
    > = async(options) => {
        let _uri = '/api/album/date-map';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.albumIds;
        if (_value !== undefined && _value !== null) {
            for (const _item of _value) {
                _uri += _separator
                _uri += 'albumIds='
                _uri += encodeURIComponent(_item);
                _separator = '&';
            }
        }
        _value = options.start;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'start='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.end;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'end='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<{readonly [key:string]: number}>;
    }
    
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
        const _uri = '/api/album';
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
    readonly refreshAlbums: () => Promise<
        ReadonlyArray<Dynamic_Album>
    > = async() => {
        const _uri = '/api/album/lastest';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<Dynamic_Album>>;
    }
}

export type AlbumsControllerOptions = {
    'refreshAlbums': {}, 
    'listAlbums': {}, 
    'fetchDateMap': {
        /**
         * List<Long> 需要查询的相册ID列表，可选参数，默认为空列表
         */
        readonly albumIds?: ReadonlyArray<number> | undefined, 
        /**
         * Instant 查询的开始时间戳，可选参数，默认为纪元时间
         */
        readonly start?: string | undefined, 
        /**
         * Instant 查询的结束时间戳，可选参数，默认为当前时间
         */
        readonly end?: string | undefined
    }
}
