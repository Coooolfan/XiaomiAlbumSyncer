export interface AuthenticatorSelection {
    readonly authenticatorAttachment?: string | undefined;
    readonly residentKey: string;
    readonly userVerification: string;
}
