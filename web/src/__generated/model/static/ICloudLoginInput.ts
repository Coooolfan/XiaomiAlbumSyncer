export interface ICloudLoginInput {
    readonly appleId: string;
    readonly password: string;
    readonly domain: string;
    readonly nickname: string;
    readonly accountId?: number | undefined;
}
