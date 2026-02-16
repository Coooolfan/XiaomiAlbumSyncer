import type {ArchiveMode, SyncMode} from '../enums/';

export interface CrontabConfig {
    readonly expression: string;
    readonly timeZone: string;
    readonly targetPath: string;
    readonly downloadImages: boolean;
    readonly downloadVideos: boolean;
    readonly rewriteExifTime: boolean;
    readonly diffByTimeline: boolean;
    readonly rewriteExifTimeZone?: string | undefined;
    readonly skipExistingFile: boolean;
    readonly rewriteFileSystemTime: boolean;
    readonly checkSha1: boolean;
    readonly fetchFromDbSize: number;
    readonly downloaders: number;
    readonly verifiers: number;
    readonly exifProcessors: number;
    readonly fileTimeWorkers: number;
    readonly downloadAudios: boolean;
    readonly expressionTargetPath: string;
    /**
     * 同步模式
     * ADD_ONLY: 仅新增模式，只下载云端新增的文件到本地
     * SYNC_ALL_CHANGES: 同步所有变化模式，同步云端的新增、修改、删除到本地
     * 默认为 ADD_ONLY 以保持向后兼容
     */
    readonly syncMode: SyncMode;
    /**
     * 同步文件夹名称（相对于 targetPath）
     */
    readonly syncFolder: string;
    /**
     * 归档模式
     * DISABLED: 关闭归档，不执行任何归档操作
     * TIME: 基于时间归档，归档超过指定天数的照片
     * SPACE: 基于空间阈值归档，当云端空间不足时自动归档旧照片
     * 默认为 DISABLED
     */
    readonly archiveMode: ArchiveMode;
    /**
     * 保留天数（时间模式）
     */
    readonly archiveDays: number;
    /**
     * 云空间阈值百分比（空间模式）
     */
    readonly cloudSpaceThreshold: number;
    /**
     * 归档文件夹名称（相对于 targetPath）
     */
    readonly backupFolder: string;
    /**
     * 归档后是否删除云端
     */
    readonly deleteCloudAfterArchive: boolean;
}
