/**
 * 定时任务当前统计信息数据类
 * 
 */
export interface CrontabCurrentStats {
    /**
     * 统计时间戳
     */
    readonly ts?: string | undefined;
    /**
     * 资源总数
     */
    readonly assetCount?: number | undefined;
    /**
     * 下载完成数
     */
    readonly downloadCompletedCount?: number | undefined;
    /**
     * SHA1验证完成数
     */
    readonly sha1VerifiedCount?: number | undefined;
    /**
     * EXIF填充完成数
     */
    readonly exifFilledCount?: number | undefined;
    /**
     * 文件系统时间更新完成数
     */
    readonly fsTimeUpdatedCount?: number | undefined;
}
