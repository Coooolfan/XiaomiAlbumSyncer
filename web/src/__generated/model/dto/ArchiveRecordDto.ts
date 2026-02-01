import type {ArchiveMode, ArchiveStatus} from '../enums/';

export type ArchiveRecordDto = {
    /**
     * 归档记录实体
     * 记录每次归档操作的详细信息
     */
    'ArchiveController/ARCHIVE_RECORD_LIST': {
        readonly id: number;
        /**
         * 归档时间
         */
        readonly archiveTime: string;
        /**
         * 归档模式（TIME / SPACE）
         */
        readonly archiveMode: ArchiveMode;
        /**
         * 归档此日期之前的照片
         */
        readonly archiveBeforeDate: string;
        /**
         * 归档文件数
         */
        readonly archivedCount: number;
        /**
         * 释放的云空间（字节）
         */
        readonly freedSpaceBytes: number;
        /**
         * 归档状态
         */
        readonly status: ArchiveStatus;
        /**
         * 错误信息（如果失败）
         */
        readonly errorMessage?: string | undefined;
        readonly crontabId: number;
    }
}
