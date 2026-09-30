/**
 * 一个相册成员的一个文件资源；不持久化有时效的下载 URL。
 */
export interface ICloudAssetRef {
    readonly zone: string;
    readonly recordName: string;
    readonly masterName: string;
    readonly resource: string;
    readonly checksum: string;
}
