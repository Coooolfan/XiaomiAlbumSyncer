import type {CrontabConfig} from './';

export interface CrontabInput {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly config: CrontabConfig;
    readonly albumIds: ReadonlyArray<number>;
}
