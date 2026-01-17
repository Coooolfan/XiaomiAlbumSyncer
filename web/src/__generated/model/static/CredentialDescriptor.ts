export interface CredentialDescriptor {
    readonly type: string;
    readonly id: string;
    readonly transports?: ReadonlyArray<string> | undefined;
}
