import type {SyncStatus} from '../enums/';
import type {Dynamic_Crontab, Dynamic_SyncRecordDetail} from './';

/**
 * 同步记录实体
 * 记录每次同步操作的详细信息
 */
export interface Dynamic_SyncRecord {
    readonly id?: number;
    readonly crontab?: Dynamic_Crontab;
    readonly crontabId?: number;
    readonly syncTime?: string;
    readonly addedCount?: number;
    readonly deletedCount?: number;
    readonly updatedCount?: number;
    readonly status?: SyncStatus;
    readonly errorMessage?: string | undefined;
    readonly details?: ReadonlyArray<Dynamic_SyncRecordDetail>;
}
