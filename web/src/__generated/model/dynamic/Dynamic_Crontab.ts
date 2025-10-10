import type {CrontabConfig} from '../static/';
import type {Dynamic_Album, Dynamic_CrontabHistory} from './';

export interface Dynamic_Crontab {
    readonly id?: number;
    readonly name?: string;
    readonly description?: string;
    readonly enabled?: boolean;
    readonly config?: CrontabConfig;
    readonly albums?: ReadonlyArray<Dynamic_Album>;
    readonly albumIds?: ReadonlyArray<string>;
    readonly histories?: ReadonlyArray<Dynamic_CrontabHistory>;
}
