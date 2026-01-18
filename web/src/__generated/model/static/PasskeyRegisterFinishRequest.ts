import type {AuthenticatorAttestationResponse} from './';

export interface PasskeyRegisterFinishRequest {
    readonly sessionId: string;
    readonly credentialName: string;
    readonly id: string;
    readonly rawId: string;
    readonly type: string;
    readonly response: AuthenticatorAttestationResponse;
    readonly authenticatorAttachment?: string | undefined;
}
