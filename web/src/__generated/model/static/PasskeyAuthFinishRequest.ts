import type {AuthenticatorAssertionResponse} from './';

export interface PasskeyAuthFinishRequest {
    readonly sessionId: string;
    readonly id: string;
    readonly rawId: string;
    readonly type: string;
    readonly response: AuthenticatorAssertionResponse;
    readonly authenticatorAttachment?: string | undefined;
}
