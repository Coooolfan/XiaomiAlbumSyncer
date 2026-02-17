import type {Dynamic_Asset} from '../dynamic/';
import type {SyncService_DeletedAssetInfo} from './';

export interface SyncService_ChangeSummary {
    readonly addedAssets: ReadonlyArray<Dynamic_Asset>;
    readonly deletedAssets: ReadonlyArray<SyncService_DeletedAssetInfo>;
    readonly updatedAssets: ReadonlyArray<Dynamic_Asset>;
}
