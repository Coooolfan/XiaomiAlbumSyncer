import type {
  PublicKeyCredentialCreationOptionsJSON,
  PublicKeyCredentialRequestOptionsJSON,
} from '@simplewebauthn/browser'
import {
  browserSupportsWebAuthn,
  startAuthentication,
  startRegistration,
} from '@simplewebauthn/browser'
import { api } from '@/ApiInstance'
import type { PasskeyCredentialInfo } from '@/__generated/model/static'

export type { PasskeyCredentialInfo }

export function isWebAuthnSupported(): boolean {
  return browserSupportsWebAuthn()
}

export async function hasAvailablePasskeys(): Promise<boolean> {
  try {
    const resp = await api.passkeyController.hasPasskeys()
    return resp.available
  } catch {
    return false
  }
}

// 注册新的 Passkey
export async function registerPasskey(
  password: string,
  credentialName: string,
): Promise<PasskeyCredentialInfo> {
  // 1. 从服务端获取注册参数
  const startResp = await api.passkeyController.startRegistration({
    body: { password, credentialName },
  })

  // 2. 转换为 SimpleWebAuthn 格式
  const authenticatorSelection: PublicKeyCredentialCreationOptionsJSON['authenticatorSelection'] = {
    residentKey: startResp.authenticatorSelection.residentKey as ResidentKeyRequirement,
    userVerification: startResp.authenticatorSelection
      .userVerification as UserVerificationRequirement,
  }
  const authenticatorAttachment = startResp.authenticatorSelection.authenticatorAttachment
  if (authenticatorAttachment != null) {
    authenticatorSelection.authenticatorAttachment =
      authenticatorAttachment as AuthenticatorAttachment
  }

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
    pubKeyCredParams: startResp.pubKeyCredParams.map((p) => ({
      type: p.type as 'public-key',
      alg: p.alg,
    })),
    timeout: startResp.timeout,
    attestation: startResp.attestation as AttestationConveyancePreference,
    authenticatorSelection,
    excludeCredentials: startResp.excludeCredentials.map((c) => ({
      id: c.id,
      type: c.type as 'public-key',
      transports: c.transports?.map((t) => t as AuthenticatorTransport),
    })),
  }

  // 3. 调用浏览器 WebAuthn API
  const credential = await startRegistration({ optionsJSON: options })

  // 4. 提交注册结果到服务端

  return await api.passkeyController.finishRegistration({
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
    },
  })
}

// 使用 Passkey 进行认证
export async function authenticateWithPasskey(): Promise<void> {
  // 1. 从服务端获取认证参数
  const startResp = await api.passkeyController.startAuthentication()

  // 2. 转换为 SimpleWebAuthn 格式
  const options: PublicKeyCredentialRequestOptionsJSON = {
    challenge: startResp.challenge,
    rpId: startResp.rpId,
    timeout: startResp.timeout,
    userVerification: startResp.userVerification as UserVerificationRequirement,
    allowCredentials: startResp.allowCredentials.map((c) => ({
      id: c.id,
      type: c.type as 'public-key',
      transports: c.transports?.map((t) => t as AuthenticatorTransport),
    })),
  }

  // 3. 调用浏览器 WebAuthn API
  const credential = await startAuthentication({ optionsJSON: options })

  // 4. 提交认证结果到服务端
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
    },
  })
}
