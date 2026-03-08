import type {Dynamic_Album, Dynamic_Crontab} from './';

export interface Dynamic_XiaomiAccount {
    readonly id?: number;
    readonly nickname?: string;
    readonly passToken?: string;
    readonly userId?: string;
    readonly albums?: ReadonlyArray<Dynamic_Album>;
    readonly crontabs?: ReadonlyArray<Dynamic_Crontab>;
}
