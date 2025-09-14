export interface CrontabConfig {
    readonly expression: string;
    readonly timeZone: string;
    readonly targetPath: string;
    readonly downloadImages: boolean;
    readonly downloadVideos: boolean;
    readonly rewriteExifTime: boolean;
    readonly rewriteExifTimeZone?: string | undefined;
}
