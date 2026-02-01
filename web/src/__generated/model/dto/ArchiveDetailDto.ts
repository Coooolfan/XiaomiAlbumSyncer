export type ArchiveDetailDto = {
    /**
     * 归档详情实体
     * 记录每个文件的归档操作详情
     */
    'ArchiveController/ARCHIVE_DETAIL_LIST': {
        readonly id: number;
        /**
         * 原路径（sync）
         */
        readonly sourcePath: string;
        /**
         * 目标路径（backup）
         */
        readonly targetPath: string;
        /**
         * 是否已移动到 backup
         */
        readonly isMovedToBackup: boolean;
        /**
         * 是否已从云端删除
         */
        readonly isDeletedFromCloud: boolean;
        /**
         * 错误信息（如果失败）
         */
        readonly errorMessage?: string | undefined;
        readonly archiveRecordId: number;
        readonly assetId: number;
    }
}
