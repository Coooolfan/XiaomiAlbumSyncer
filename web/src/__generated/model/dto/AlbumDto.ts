export type AlbumDto = {
    'AlbumsController/DEFAULT_ALBUM': {
        readonly id: number;
        /**
         * 远端相册定位；来源由所属账号决定。
         */
        readonly remoteKey: string;
        readonly name: string;
        readonly assetCount?: number | undefined;
        readonly lastUpdateTime?: string | undefined;
        readonly shadow: boolean;
        readonly account: {
            readonly id: number;
            readonly nickname: string;
        };
    }
}
