export interface CrontabCurrentStats {
    readonly ts: string;
    readonly downloadCompletedFinished?: number | undefined;
    readonly sha1VerifiedFinished?: number | undefined;
    readonly exifFilledFinished?: number | undefined;
    readonly fsTimeUpdatedFinished?: number | undefined;
}
