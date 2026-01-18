export interface PasskeyCredentialInfo {
    readonly id: string;
    readonly name: string;
    readonly createdAt: number;
    readonly lastUsedAt?: number | undefined;
    readonly transports?: ReadonlyArray<string> | undefined;
}
