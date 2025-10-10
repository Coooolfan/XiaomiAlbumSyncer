import type {Dynamic_Asset} from './';

export interface Dynamic_Album {
    readonly id?: string;
    readonly name?: string;
    readonly assetCount?: number;
    readonly lastUpdateTime?: string;
    readonly assets?: ReadonlyArray<Dynamic_Asset>;
}
