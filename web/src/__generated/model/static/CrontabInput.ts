export interface CrontabInput {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly expression: string;
    readonly timeZone: string;
    readonly albumIds: ReadonlyArray<number>;
}
