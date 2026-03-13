import type {AssetType} from '../enums/';
import type {Dynamic_Album, Dynamic_CrontabHistoryDetail} from './';

export interface Dynamic_Asset {
    readonly id?: string;
    readonly fileName?: string;
    readonly type?: AssetType;
    readonly dateTaken?: string;
    readonly album?: Dynamic_Album;
    readonly sha1?: string;
    readonly mimeType?: string;
    readonly title?: string;
    readonly size?: number;
    readonly downloadHistories?: ReadonlyArray<Dynamic_CrontabHistoryDetail>;
}
