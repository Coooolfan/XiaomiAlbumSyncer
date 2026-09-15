import type {AssetType, RecordingType} from '../enums/';

export type AssetDto = {
    'AssetController/DEFAULT_ASSET': {
        readonly id: string;
        readonly fileName: string;
        readonly type: AssetType;
        readonly recordingType?: RecordingType | undefined;
        readonly dateTaken: string;
        readonly sha1: string;
        readonly mimeType: string;
        readonly title: string;
        readonly size: number;
    }
}
