import type {ArchiveMode, ArchiveStatus} from '../enums/';

export type ArchiveRecordDto = {
    /**
     * 归档记录实体
     * 记录每次归档操作的详细信息
     */
    'ArchiveController/ARCHIVE_RECORD_LIST': {
        readonly id: number;
        readonly archiveTime: string;
        readonly archiveMode: ArchiveMode;
        readonly archiveBeforeDate: string;
        readonly archivedCount: number;
        readonly freedSpaceBytes: number;
        readonly status: ArchiveStatus;
        readonly errorMessage?: string | undefined;
        readonly crontabId: number;
    }
}
