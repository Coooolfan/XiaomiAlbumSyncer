export interface NotifyConfig {
    readonly url: string;
    readonly headers: {readonly [key:string]: string};
    readonly body: string;
    readonly dailySummaryBody?: string | undefined;
    readonly dailySummaryCron?: string | undefined;
    readonly dailySummaryTimeZone?: string | undefined;
}
