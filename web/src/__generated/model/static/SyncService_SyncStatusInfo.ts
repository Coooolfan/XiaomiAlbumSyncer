import type {SyncStatus} from '../enums/';

export interface SyncService_SyncStatusInfo {
    readonly isRunning: boolean;
    readonly lastSyncTime?: string | undefined;
    readonly lastSyncResult?: SyncStatus | undefined;
}
