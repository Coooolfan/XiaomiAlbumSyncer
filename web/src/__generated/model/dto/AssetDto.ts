import type {AssetType} from '../enums/';

export type AssetDto = {
    'AssetController/DEFAULT_ASSET': {
        readonly id: number;
        readonly fileName: string;
        readonly type: AssetType;
        readonly dateTaken: string;
        readonly sha1: string;
        readonly mimeType: string;
        readonly title: string;
        readonly size: number;
    }
}
