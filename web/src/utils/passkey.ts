import {
  startRegistration,
  startAuthentication,
  browserSupportsWebAuthn,
} from '@simplewebauthn/browser'
import type {
  PublicKeyCredentialCreationOptionsJSON,
  PublicKeyCredentialRequestOptionsJSON,
} from '@simplewebauthn/browser'
import { api } from '@/ApiInstance'
import type { PasskeyCredentialInfo } from '@/__generated/model/static'

export type { PasskeyCredentialInfo }

// Check if browser supports WebAuthn
export function isWebAuthnSupported(): boolean {
  return browserSupportsWebAuthn()
}

// Check if any Passkeys are available
export async function hasAvailablePasskeys(): Promise<boolean> {
  try {
    const resp = await api.passkeyController.hasPasskeys()
    return resp.available
  } catch {
    return false
  }
}

// Register a new Passkey
export async function registerPasskey(
  password: string,
  credentialName: string
): Promise<PasskeyCredentialInfo> {
  // 1. Get registration options from server
  const startResp = await api.passkeyController.startRegistration({
    body: { password, credentialName }
  })

  // 2. Convert to SimpleWebAuthn format
  const options: PublicKeyCredentialCreationOptionsJSON = {
    challenge: startResp.challenge,
    rp: {
      id: startResp.rpId,
      name: startResp.rpName,
    },
    user: {
      id: startResp.userId,
      name: startResp.userName,
      displayName: startResp.userDisplayName,
    },
    pubKeyCredParams: startResp.pubKeyCredParams.map(p => ({
      type: p.type as 'public-key',
      alg: p.alg,
    })),
    timeout: startResp.timeout,
    attestation: startResp.attestation as AttestationConveyancePreference,
    authenticatorSelection: {
      authenticatorAttachment: startResp.authenticatorSelection.authenticatorAttachment as AuthenticatorAttachment | undefined,
      residentKey: startResp.authenticatorSelection.residentKey as ResidentKeyRequirement,
      userVerification: startResp.authenticatorSelection.userVerification as UserVerificationRequirement,
    },
    excludeCredentials: startResp.excludeCredentials.map(c => ({
      id: c.id,
      type: c.type as 'public-key',
      transports: c.transports as AuthenticatorTransport[] | undefined,
    })),
  }

  // 3. Call browser WebAuthn API
  const credential = await startRegistration({ optionsJSON: options })

  // 4. Submit registration result to server
  const finishResp = await api.passkeyController.finishRegistration({
    body: {
      sessionId: startResp.sessionId,
      credentialName,
      id: credential.id,
      rawId: credential.rawId,
      type: credential.type,
      response: {
        clientDataJSON: credential.response.clientDataJSON,
        attestationObject: credential.response.attestationObject,
        transports: credential.response.transports as string[] | undefined,
      },
      authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
      clientExtensionResults: credential.clientExtensionResults as Record<string, unknown> | undefined,
    }
  })

  return finishResp
}

// Authenticate with Passkey
export async function authenticateWithPasskey(): Promise<void> {
  // 1. Get authentication options from server
  const startResp = await api.passkeyController.startAuthentication()

  // 2. Convert to SimpleWebAuthn format
  const options: PublicKeyCredentialRequestOptionsJSON = {
    challenge: startResp.challenge,
    rpId: startResp.rpId,
    timeout: startResp.timeout,
    userVerification: startResp.userVerification as UserVerificationRequirement,
    allowCredentials: startResp.allowCredentials.map(c => ({
      id: c.id,
      type: c.type as 'public-key',
      transports: c.transports as AuthenticatorTransport[] | undefined,
    })),
  }

  // 3. Call browser WebAuthn API
  const credential = await startAuthentication({ optionsJSON: options })

  // 4. Submit authentication result to server
  await api.passkeyController.finishAuthentication({
    body: {
      sessionId: startResp.sessionId,
      id: credential.id,
      rawId: credential.rawId,
      type: credential.type,
      response: {
        clientDataJSON: credential.response.clientDataJSON,
        authenticatorData: credential.response.authenticatorData,
        signature: credential.response.signature,
        userHandle: credential.response.userHandle ?? undefined,
      },
      authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
      clientExtensionResults: credential.clientExtensionResults as Record<string, unknown> | undefined,
    }
  })
}
