import type {CrontabConfig} from './';

export interface CrontabCreateInput {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly config: CrontabConfig;
    readonly albumIds: ReadonlyArray<string>;
}
