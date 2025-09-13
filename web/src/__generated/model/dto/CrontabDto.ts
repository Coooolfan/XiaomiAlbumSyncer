import type {CrontabConfig} from '../static/';

export type CrontabDto = {
    'CrontabController/DEFAULT_CRONTAB': {
        readonly id: number;
        readonly name: string;
        readonly description: string;
        readonly enabled: boolean;
        readonly config: CrontabConfig;
        readonly albumIds: ReadonlyArray<number>;
    }
}
