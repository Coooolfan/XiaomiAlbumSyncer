import type {SyncStatus} from '../enums/';

/**
 * 同步状态信息
 */
export interface SyncService_SyncStatusInfo {
    readonly isRunning: boolean;
    readonly lastSyncTime?: string | undefined;
    readonly lastSyncResult?: SyncStatus | undefined;
}
