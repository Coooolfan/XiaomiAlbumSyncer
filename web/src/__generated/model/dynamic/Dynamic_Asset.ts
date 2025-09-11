import type {AssetType} from '../enums/';
import type {Dynamic_Album} from './';

export interface Dynamic_Asset {
    readonly id?: number;
    readonly fileName?: string;
    readonly type?: AssetType;
    readonly dateTaken?: string;
    readonly album?: Dynamic_Album;
    readonly sha1?: string;
    readonly mimeType?: string;
    readonly title?: string;
    readonly size?: number;
}
