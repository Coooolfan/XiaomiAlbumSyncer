import type {Dynamic_Asset, Dynamic_CrontabHistory} from './';

export interface Dynamic_CrontabHistoryDetail {
    readonly id?: number;
    readonly crontabHistory?: Dynamic_CrontabHistory;
    readonly downloadTime?: string;
    readonly asset?: Dynamic_Asset;
    readonly filePath?: string;
}
