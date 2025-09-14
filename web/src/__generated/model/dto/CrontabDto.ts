import type {CrontabConfig} from '../static/';

export type CrontabDto = {
    'CrontabController/DEFAULT_CRONTAB': {
        readonly id: number;
        readonly name: string;
        readonly description: string;
        readonly enabled: boolean;
        readonly config: CrontabConfig;
        readonly albumIds: ReadonlyArray<number>;
        readonly histories: ReadonlyArray<{
            readonly id: number;
            readonly startTime: string;
            readonly endTime?: string | undefined;
            readonly isCompleted: boolean;
        }>;
    }
}
