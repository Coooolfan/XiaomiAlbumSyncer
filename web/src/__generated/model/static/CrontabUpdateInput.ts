import type {CrontabConfig} from './';

export interface CrontabUpdateInput {
    readonly name?: string | undefined;
    readonly description?: string | undefined;
    readonly enabled?: boolean | undefined;
    readonly config?: CrontabConfig | undefined;
    readonly albumIds?: ReadonlyArray<string> | undefined;
}
