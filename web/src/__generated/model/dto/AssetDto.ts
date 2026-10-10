import type {AssetType, RecordingType} from '../enums/';

export type AssetDto = {
    'AssetController/DEFAULT_ASSET': {
        readonly id: string;
        /**
         * 远端文件定位；来源由相册所属账号决定。
         */
        readonly remoteKey: string;
        readonly fileName: string;
        readonly type: AssetType;
        readonly recordingType?: RecordingType | undefined;
        readonly dateTaken: string;
        readonly checksum: string;
        readonly mimeType: string;
        readonly title: string;
        readonly size: number;
    }
}
