export interface AuthenticatorAttestationResponse {
    readonly clientDataJSON: string;
    readonly attestationObject: string;
    readonly transports?: ReadonlyArray<string> | undefined;
}
