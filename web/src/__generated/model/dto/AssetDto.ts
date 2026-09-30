import type {AssetType, RecordingType} from '../enums/';
import type {ICloudAssetRef} from '../static/';

export type AssetDto = {
    'AssetController/DEFAULT_ASSET': {
        readonly id: string;
        readonly xiaomiId: string;
        readonly remoteKey?: string | undefined;
        readonly cloudAsset?: ICloudAssetRef | undefined;
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
