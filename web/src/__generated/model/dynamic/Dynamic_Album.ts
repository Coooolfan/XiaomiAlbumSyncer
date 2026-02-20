import type {Dynamic_Asset, Dynamic_XiaomiAccount} from './';

export interface Dynamic_Album {
    readonly id?: number;
    readonly remoteId?: string;
    readonly name?: string;
    readonly assetCount?: number;
    readonly lastUpdateTime?: string;
    readonly account?: Dynamic_XiaomiAccount;
    readonly accountId?: number;
    readonly assets?: ReadonlyArray<Dynamic_Asset>;
}
