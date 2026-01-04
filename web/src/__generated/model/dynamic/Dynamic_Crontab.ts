import type {CrontabConfig} from '../static/';
import type {Dynamic_Album, Dynamic_CrontabHistory, Dynamic_XiaomiAccount} from './';

export interface Dynamic_Crontab {
    readonly id?: number;
    readonly name?: string;
    readonly description?: string;
    readonly enabled?: boolean;
    readonly config?: CrontabConfig;
    readonly account?: Dynamic_XiaomiAccount;
    readonly accountId?: number;
    readonly albums?: ReadonlyArray<Dynamic_Album>;
    readonly running?: boolean;
    readonly albumIds?: ReadonlyArray<string>;
    readonly histories?: ReadonlyArray<Dynamic_CrontabHistory>;
}
