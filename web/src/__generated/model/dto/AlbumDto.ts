export type AlbumDto = {
    'AlbumsController/DEFAULT_ALBUM': {
        readonly id: number;
        readonly remoteId: string;
        readonly name: string;
        readonly assetCount: number;
        readonly lastUpdateTime: string;
        readonly shadow: boolean;
        readonly account: {
            readonly id: number;
            readonly nickname: string;
        };
    }
}
