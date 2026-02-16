/**
 * 云端空间使用详情项
 */
export interface SpaceUsageItem {
    /**
     * 使用空间（字节）
     */
    readonly size: number;
    /**
     * 显示文本
     */
    readonly text: string;
}
