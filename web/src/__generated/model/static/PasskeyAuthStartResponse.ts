import type {CredentialDescriptor} from './';

export interface PasskeyAuthStartResponse {
    readonly sessionId: string;
    readonly challenge: string;
    readonly rpId: string;
    readonly timeout: number;
    readonly userVerification: string;
    readonly allowCredentials: ReadonlyArray<CredentialDescriptor>;
}
