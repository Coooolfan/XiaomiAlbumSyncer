import type {CrontabSyncMode} from '../enums/';
import type {CrontabConfig} from '../static/';

export type CrontabDto = {
    'CrontabController/DEFAULT_CRONTAB': {
        readonly id: number;
        readonly name: string;
        readonly description: string;
        readonly enabled: boolean;
        readonly syncMode: CrontabSyncMode;
        readonly config: CrontabConfig;
        readonly accountId: number;
        readonly account: {
            readonly id: number;
            readonly nickname: string;
        };
        readonly albumIds: ReadonlyArray<number>;
        readonly running: boolean;
        readonly histories: ReadonlyArray<{
            readonly id: number;
            readonly startTime: string;
            readonly endTime?: string | undefined;
            readonly syncCursors?: {readonly [key:string]: string} | undefined;
            readonly fetchedAllAssets: boolean;
            readonly isCompleted: boolean;
            readonly detailsCount: number;
        }>;
    }
}
