export type ArchiveDetailDto = {
    /**
     * 归档详情实体
     * 记录每个文件的归档操作详情
     */
    'ArchiveController/ARCHIVE_DETAIL_LIST': {
        readonly id: number;
        readonly sourcePath: string;
        readonly targetPath: string;
        readonly isMovedToBackup: boolean;
        readonly isDeletedFromCloud: boolean;
        readonly errorMessage?: string | undefined;
        readonly archiveRecordId: number;
        readonly assetId: number;
    }
}
