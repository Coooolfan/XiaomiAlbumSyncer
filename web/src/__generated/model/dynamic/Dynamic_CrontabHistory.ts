import type {AlbumTimeline} from '../static/';
import type {Dynamic_Crontab, Dynamic_CrontabHistoryDetail} from './';

export interface Dynamic_CrontabHistory {
    readonly id?: number;
    readonly crontab?: Dynamic_Crontab;
    readonly startTime?: string;
    readonly endTime?: string | undefined;
    readonly timelineSnapshot?: {readonly [key:string]: AlbumTimeline};
    readonly fetchedAllAssets?: boolean;
    readonly isCompleted?: boolean;
    readonly details?: ReadonlyArray<Dynamic_CrontabHistoryDetail>;
    readonly detailsCount?: number;
}
