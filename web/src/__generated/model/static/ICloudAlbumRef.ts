/**
 * iCloud 图库中的相册或智能相册查询。
 */
export interface ICloudAlbumRef {
    readonly zone: string;
    readonly recordName: string;
    readonly queryType: string;
    readonly smartAlbum?: string | undefined;
}
