import type {SpaceUsageItem} from './';

/**
 * 云端空间使用情况信息
 */
export interface CloudSpaceInfo {
    /**
     * 总空间（字节）
     */
    readonly totalQuota: number;
    /**
     * 已用空间（字节）
     */
    readonly used: number;
    /**
     * 相册使用空间（字节）
     */
    readonly galleryUsed: number;
    /**
     * 使用百分比
     */
    readonly usagePercent: number;
    /**
     * 详细使用信息
     */
    readonly usedDetail: {readonly [key:string]: SpaceUsageItem};
}
