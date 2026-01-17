export interface AuthenticatorAssertionResponse {
    readonly clientDataJSON: string;
    readonly authenticatorData: string;
    readonly signature: string;
    readonly userHandle?: string | undefined;
}
