import type {SyncStatus} from '../enums/';

export type SyncRecordDto = {
    /**
     * 同步记录实体
     * 记录每次同步操作的详细信息
     */
    'SyncController/SYNC_RECORD_LIST': {
        readonly id: number;
        /**
         * 同步时间
         */
        readonly syncTime: string;
        /**
         * 新增文件数
         */
        readonly addedCount: number;
        /**
         * 删除文件数
         */
        readonly deletedCount: number;
        /**
         * 更新文件数
         */
        readonly updatedCount: number;
        /**
         * 同步状态
         */
        readonly status: SyncStatus;
        /**
         * 错误信息（如果失败）
         */
        readonly errorMessage?: string | undefined;
        readonly crontabId: number;
    }
}
