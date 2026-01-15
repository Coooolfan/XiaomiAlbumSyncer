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
}
