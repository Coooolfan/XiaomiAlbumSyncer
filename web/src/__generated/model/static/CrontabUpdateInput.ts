import type {CrontabSyncMode} from '../enums/';
import type {CrontabConfig} from './';

export interface CrontabUpdateInput {
    readonly name?: string | undefined;
    readonly description?: string | undefined;
    readonly enabled?: boolean | undefined;
    readonly syncMode?: CrontabSyncMode | undefined;
    readonly config?: CrontabConfig | undefined;
    readonly albumIds?: ReadonlyArray<number> | undefined;
}
