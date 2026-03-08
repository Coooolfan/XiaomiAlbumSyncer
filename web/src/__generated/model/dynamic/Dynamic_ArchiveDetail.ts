import type {Dynamic_ArchiveRecord, Dynamic_Asset} from './';

/**
 * 归档详情实体
 * 记录每个文件的归档操作详情
 */
export interface Dynamic_ArchiveDetail {
    readonly id?: number;
    readonly archiveRecord?: Dynamic_ArchiveRecord;
    readonly archiveRecordId?: number;
    readonly asset?: Dynamic_Asset;
    readonly assetId?: number;
    readonly sourcePath?: string;
    readonly targetPath?: string;
    readonly isMovedToBackup?: boolean;
    readonly isDeletedFromCloud?: boolean;
    readonly errorMessage?: string | undefined;
}
