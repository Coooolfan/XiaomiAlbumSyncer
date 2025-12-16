export interface CrontabCurrentStats {
    readonly ts: string;
    readonly assetCount?: number | undefined;
    readonly downloadCompletedCount?: number | undefined;
    readonly sha1VerifiedCount?: number | undefined;
    readonly exifFilledCount?: number | undefined;
    readonly fsTimeUpdatedCount?: number | undefined;
}
