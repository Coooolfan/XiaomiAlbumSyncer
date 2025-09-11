export type CrontabDto = {
    'CrontabController/DEFAULT_CRONTAB': {
        readonly id: number;
        readonly name: string;
        readonly description: string;
        readonly enabled: boolean;
        readonly expression: string;
        readonly timeZone: string;
        readonly albumIds: ReadonlyArray<number>;
    }
}
