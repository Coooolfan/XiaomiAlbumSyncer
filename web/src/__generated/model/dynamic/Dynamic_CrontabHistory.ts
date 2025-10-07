import type {Dynamic_Crontab, Dynamic_CrontabHistoryDetail} from './';

export interface Dynamic_CrontabHistory {
    readonly id?: number;
    readonly crontab?: Dynamic_Crontab;
    readonly startTime?: string;
    readonly endTime?: string | undefined;
    readonly isCompleted?: boolean;
    readonly details?: ReadonlyArray<Dynamic_CrontabHistoryDetail>;
}
