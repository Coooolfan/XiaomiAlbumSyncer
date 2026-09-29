import type {CrontabSyncMode} from '../enums/';
import type {CrontabConfig} from './';

export interface CrontabCreateInput {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly syncMode: CrontabSyncMode;
    readonly config: CrontabConfig;
    readonly accountId: number;
    readonly albumIds: ReadonlyArray<number>;
}
