import type {SyncOperation} from '../enums/';
import type {Dynamic_Asset, Dynamic_SyncRecord} from './';

/**
 * 同步记录详情实体
 * 记录每个文件的同步操作详情
 */
export interface Dynamic_SyncRecordDetail {
    readonly id?: number;
    /**
     * 关联的同步记录
     */
    readonly syncRecord?: Dynamic_SyncRecord;
    readonly syncRecordId?: number;
    /**
     * 关联的资产（删除操作时可能为 null）
     */
    readonly asset?: Dynamic_Asset | undefined;
    readonly assetId?: number | undefined;
    /**
     * 操作类型
     */
    readonly operation?: SyncOperation;
    /**
     * 文件路径
     */
    readonly filePath?: string;
    /**
     * 是否完成
     */
    readonly isCompleted?: boolean;
    /**
     * 错误信息（如果失败）
     */
    readonly errorMessage?: string | undefined;
}
