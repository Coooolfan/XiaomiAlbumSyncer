import type {Dynamic_Asset} from '../dynamic/';

/**
 * 待删除资产信息（包含实际下载路径和对应的 CrontabHistoryDetail ID）
 */
export interface SyncService_DeletedAssetInfo {
    readonly asset: Dynamic_Asset;
    readonly filePath: string;
    readonly historyDetailIds: ReadonlyArray<number>;
}
