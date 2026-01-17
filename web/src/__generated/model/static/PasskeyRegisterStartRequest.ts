/**
 * Request DTO for starting Passkey registration
 */
export interface PasskeyRegisterStartRequest {
    readonly password: string;
    readonly credentialName: string;
}
