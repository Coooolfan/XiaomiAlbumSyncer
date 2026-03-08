import type {SyncStatus} from '../enums/';

export type SyncRecordDto = {
    /**
     * 同步记录实体
     * 记录每次同步操作的详细信息
     */
    'SyncController/SYNC_RECORD_LIST': {
        readonly id: number;
        readonly syncTime: string;
        readonly addedCount: number;
        readonly deletedCount: number;
        readonly updatedCount: number;
        readonly status: SyncStatus;
        readonly errorMessage?: string | undefined;
        readonly crontabId: number;
    }
}
