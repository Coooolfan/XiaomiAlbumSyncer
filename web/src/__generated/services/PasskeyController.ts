import type {Executor} from '../';
import type {
    HasPasskeysResponse, 
    PasskeyAuthFinishRequest, 
    PasskeyAuthStartResponse, 
    PasskeyCredentialInfo, 
    PasskeyRegisterFinishRequest, 
    PasskeyRegisterStartRequest, 
    PasskeyRegisterStartResponse, 
    PasskeyUpdateNameRequest
} from '../model/static/';

/**
 * Passkey (WebAuthn) Controller
 * 
 * Provides APIs for Passkey registration, authentication, and management.
 */
export class PasskeyController {
    
    constructor(private executor: Executor) {}
    
    /**
     * Delete a Passkey
     */
    readonly deleteCredential: (options: PasskeyControllerOptions['deleteCredential']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/';
        _uri += encodeURIComponent(options.credentialId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * Finish Passkey authentication
     * 
     * Validates the assertion and logs in the user.
     * Public API, no login required.
     */
    readonly finishAuthentication: (options: PasskeyControllerOptions['finishAuthentication']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/authenticate/finish';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * Finish Passkey registration
     * 
     * Validates the credential and saves it to the database.
     */
    readonly finishRegistration: (options: PasskeyControllerOptions['finishRegistration']) => Promise<
        PasskeyCredentialInfo
    > = async(options) => {
        let _uri = '/api/passkey/register/finish';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<PasskeyCredentialInfo>;
    }
    
    /**
     * Check if any Passkeys are registered
     * 
     * Public API for login page to determine available login methods.
     */
    readonly hasPasskeys: () => Promise<
        HasPasskeysResponse
    > = async() => {
        let _uri = '/api/passkey/available';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<HasPasskeysResponse>;
    }
    
    /**
     * Get registered Passkey list
     */
    readonly listCredentials: () => Promise<
        ReadonlyArray<PasskeyCredentialInfo>
    > = async() => {
        let _uri = '/api/passkey';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<PasskeyCredentialInfo>>;
    }
    
    /**
     * Start Passkey authentication
     * 
     * Returns WebAuthn options for the browser to authenticate.
     * Public API, no login required.
     */
    readonly startAuthentication: () => Promise<
        PasskeyAuthStartResponse
    > = async() => {
        let _uri = '/api/passkey/authenticate/start';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<PasskeyAuthStartResponse>;
    }
    
    /**
     * Start Passkey registration
     * 
     * Requires login and password verification.
     * Returns WebAuthn options for the browser to create a new credential.
     */
    readonly startRegistration: (options: PasskeyControllerOptions['startRegistration']) => Promise<
        PasskeyRegisterStartResponse
    > = async(options) => {
        let _uri = '/api/passkey/register/start';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<PasskeyRegisterStartResponse>;
    }
    
    /**
     * Update Passkey name
     */
    readonly updateCredentialName: (options: PasskeyControllerOptions['updateCredentialName']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/';
        _uri += encodeURIComponent(options.credentialId);
        _uri += '/name';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
}

export type PasskeyControllerOptions = {
    'startRegistration': {
        readonly body: PasskeyRegisterStartRequest
    }, 
    'finishRegistration': {
        readonly body: PasskeyRegisterFinishRequest
    }, 
    'startAuthentication': {}, 
    'finishAuthentication': {
        readonly body: PasskeyAuthFinishRequest
    }, 
    'listCredentials': {}, 
    'deleteCredential': {
        readonly credentialId: string
    }, 
    'updateCredentialName': {
        readonly credentialId: string, 
        readonly body: PasskeyUpdateNameRequest
    }, 
    'hasPasskeys': {}
}
