import type {ArchiveMode, ArchiveStatus} from '../enums/';
import type {Dynamic_ArchiveDetail, Dynamic_Crontab} from './';

/**
 * 归档记录实体
 * 记录每次归档操作的详细信息
 */
export interface Dynamic_ArchiveRecord {
    readonly id?: number;
    readonly crontab?: Dynamic_Crontab;
    readonly crontabId?: number;
    readonly archiveTime?: string;
    readonly archiveMode?: ArchiveMode;
    readonly archiveBeforeDate?: string;
    readonly archivedCount?: number;
    readonly freedSpaceBytes?: number;
    readonly status?: ArchiveStatus;
    readonly errorMessage?: string | undefined;
    readonly details?: ReadonlyArray<Dynamic_ArchiveDetail>;
}
