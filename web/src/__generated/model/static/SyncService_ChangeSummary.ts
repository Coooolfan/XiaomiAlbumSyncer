import type {Dynamic_Asset} from '../dynamic/';

/**
 * 变化摘要
 */
export interface SyncService_ChangeSummary {
    readonly addedAssets: ReadonlyArray<Dynamic_Asset>;
    readonly deletedAssets: ReadonlyArray<Dynamic_Asset>;
    readonly updatedAssets: ReadonlyArray<Dynamic_Asset>;
}
