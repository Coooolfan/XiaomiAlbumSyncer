import type {SpaceUsageItem} from './';

/**
 * 云端空间使用情况信息
 */
export interface CloudSpaceInfo {
    readonly totalQuota: number;
    readonly used: number;
    readonly galleryUsed: number;
    readonly usagePercent: number;
    readonly usedDetail: {readonly [key:string]: SpaceUsageItem};
}
