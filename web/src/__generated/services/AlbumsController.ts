import type {Executor} from '../';
import type {Dynamic_Album} from '../model/dynamic/';

export class AlbumsController {
    
    constructor(private executor: Executor) {}
    
    readonly listAlbums: () => Promise<
        ReadonlyArray<Dynamic_Album>
    > = async() => {
        let _uri = '/api/album';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<Dynamic_Album>>;
    }
    
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
