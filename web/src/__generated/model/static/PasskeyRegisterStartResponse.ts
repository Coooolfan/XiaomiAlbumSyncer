import type {AuthenticatorSelection, CredentialDescriptor, PubKeyCredParam} from './';

export interface PasskeyRegisterStartResponse {
    readonly sessionId: string;
    readonly challenge: string;
    readonly rpId: string;
    readonly rpName: string;
    readonly userId: string;
    readonly userName: string;
    readonly userDisplayName: string;
    readonly pubKeyCredParams: ReadonlyArray<PubKeyCredParam>;
    readonly authenticatorSelection: AuthenticatorSelection;
    readonly timeout: number;
    readonly attestation: string;
    readonly excludeCredentials: ReadonlyArray<CredentialDescriptor>;
}
