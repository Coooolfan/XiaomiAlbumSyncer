package com.coooolfan.xiaomialbumsyncer.service

import com.coooolfan.xiaomialbumsyncer.controller.LoginRequest
import com.coooolfan.xiaomialbumsyncer.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.lt
import org.noear.solon.annotation.Inject
import org.noear.solon.annotation.Managed
import org.noear.solon.core.handle.Context
import java.security.SecureRandom
import java.util.*

@Managed
class PasskeyService(
    private val sql: KSqlClient,
    private val systemConfigService: SystemConfigService
) {
    @Inject("\${solon.app.webauthn.rpId:localhost}")
    private lateinit var rpId: String

    @Inject("\${solon.app.webauthn.rpName:XiaomiAlbumSyncer}")
    private lateinit var rpName: String

    @Inject("\${solon.app.webauthn.origin:}")
    private lateinit var configuredOrigin: String

    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val objectMapper = jacksonObjectMapper()
    private val secureRandom = SecureRandom()
    private val objectConverter = ObjectConverter()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    companion object {
        private const val CHALLENGE_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
        private const val USER_ID = "xiaomi-album-syncer-user"
    }

    // ==================== Registration Flow ====================

    fun startRegistration(password: String, credentialName: String): PasskeyRegisterStartResponse {
        // 1. Verify password
        systemConfigService.login(LoginRequest(password))

        // 2. Clean expired challenges
        cleanExpiredChallenges()

        // 3. Generate challenge
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)

        // 4. Generate session ID
        val sessionId = UUID.randomUUID().toString()

        // 5. Save challenge
        val now = System.currentTimeMillis()
        sql.insert(WebAuthnChallenge {
            id = sessionId
            this.challenge = challenge
            type = "registration"
            createdAt = now
            expiresAt = now + CHALLENGE_TIMEOUT_MS
        })

        // 6. Get existing credential IDs (for exclusion)
        val existingCredentials = sql.executeQuery(PasskeyCredential::class) {
            select(table)
        }.map { cred ->
            CredentialDescriptor(
                type = "public-key",
                id = cred.id,
                transports = cred.transports?.let { objectMapper.readValue<List<String>>(it) }
            )
        }

        return PasskeyRegisterStartResponse(
            sessionId = sessionId,
            challenge = challenge,
            rpId = rpId,
            rpName = rpName,
            userId = Base64.getUrlEncoder().withoutPadding().encodeToString(USER_ID.toByteArray()),
            userName = "admin",
            userDisplayName = "System Admin",
            pubKeyCredParams = listOf(
                PubKeyCredParam("public-key", -7),   // ES256
                PubKeyCredParam("public-key", -257)  // RS256
            ),
            authenticatorSelection = AuthenticatorSelection(
                authenticatorAttachment = null,
                residentKey = "preferred",
                userVerification = "required"
            ),
            timeout = CHALLENGE_TIMEOUT_MS,
            attestation = "none",
            excludeCredentials = existingCredentials
        )
    }

    fun finishRegistration(request: PasskeyRegisterFinishRequest): PasskeyCredentialInfo {
        // 1. Get and validate challenge
        val challengeRecord = sql.findById(WebAuthnChallenge::class, request.sessionId)
            ?: throw IllegalStateException("Session expired or invalid")

        if (challengeRecord.expiresAt < System.currentTimeMillis()) {
            sql.deleteById(WebAuthnChallenge::class, request.sessionId)
            throw IllegalStateException("Session expired")
        }

        if (challengeRecord.type != "registration") {
            throw IllegalStateException("Session type mismatch")
        }

        // 2. Parse attestation response
        val clientDataJSON = Base64.getUrlDecoder().decode(request.response.clientDataJSON)
        val attestationObject = Base64.getUrlDecoder().decode(request.response.attestationObject)

        // 3. Validate using webauthn4j
        val registrationRequest = RegistrationRequest(
            attestationObject,
            clientDataJSON,
            request.clientExtensionResults?.keys?.toSet() ?: emptySet()
        )

        val origin = resolveOrigin()

        val serverProperty = ServerProperty(
            origin,
            rpId,
            DefaultChallenge(Base64.getUrlDecoder().decode(challengeRecord.challenge)),
            null
        )

        val registrationParameters = RegistrationParameters(serverProperty, true)

        val registrationData = webAuthnManager.parse(registrationRequest)
        webAuthnManager.validate(registrationData, registrationParameters)

        // 4. Save credential
        val attestedCredentialData = registrationData.attestationObject!!
            .authenticatorData.attestedCredentialData!!

        val credentialId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(attestedCredentialData.credentialId)

        val now = System.currentTimeMillis()

        sql.insert(PasskeyCredential {
            id = credentialId
            name = request.credentialName
            publicKeyCose = attestedCredentialDataConverter.convert(attestedCredentialData)
            signCount = registrationData.attestationObject!!.authenticatorData.signCount
            transports = request.response.transports?.let { objectMapper.writeValueAsString(it) }
            attestationFmt = registrationData.attestationObject!!.attestationStatement.format
            aaguid = attestedCredentialData.aaguid?.value?.toString()
            createdAt = now
            lastUsedAt = null
        })

        // 5. Delete used challenge
        sql.deleteById(WebAuthnChallenge::class, request.sessionId)

        return PasskeyCredentialInfo(
            id = credentialId,
            name = request.credentialName,
            createdAt = now,
            lastUsedAt = null,
            transports = request.response.transports
        )
    }

    // ==================== Authentication Flow ====================

    fun startAuthentication(): PasskeyAuthStartResponse {
        // 1. Clean expired challenges
        cleanExpiredChallenges()

        // 2. Get all registered credentials
        val credentials = sql.executeQuery(PasskeyCredential::class) {
            select(table)
        }.map { cred ->
            CredentialDescriptor(
                type = "public-key",
                id = cred.id,
                transports = cred.transports?.let { objectMapper.readValue<List<String>>(it) }
            )
        }

        if (credentials.isEmpty()) {
            throw IllegalStateException("No Passkey registered")
        }

        // 3. Generate challenge
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)

        // 4. Generate session ID and save
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        sql.insert(WebAuthnChallenge {
            id = sessionId
            this.challenge = challenge
            type = "authentication"
            createdAt = now
            expiresAt = now + CHALLENGE_TIMEOUT_MS
        })

        return PasskeyAuthStartResponse(
            sessionId = sessionId,
            challenge = challenge,
            rpId = rpId,
            timeout = CHALLENGE_TIMEOUT_MS,
            userVerification = "required",
            allowCredentials = credentials
        )
    }

    fun finishAuthentication(request: PasskeyAuthFinishRequest) {
        // 1. Get and validate challenge
        val challengeRecord = sql.findById(WebAuthnChallenge::class, request.sessionId)
            ?: throw IllegalStateException("Session expired or invalid")

        if (challengeRecord.expiresAt < System.currentTimeMillis()) {
            sql.deleteById(WebAuthnChallenge::class, request.sessionId)
            throw IllegalStateException("Session expired")
        }

        if (challengeRecord.type != "authentication") {
            throw IllegalStateException("Session type mismatch")
        }

        // 2. Find credential
        val credential = sql.findById(PasskeyCredential::class, request.id)
            ?: throw IllegalStateException("Credential not found")

        // 3. Parse authentication response
        val credentialId = Base64.getUrlDecoder().decode(request.id)
        val clientDataJSON = Base64.getUrlDecoder().decode(request.response.clientDataJSON)
        val authenticatorData = Base64.getUrlDecoder().decode(request.response.authenticatorData)
        val signature = Base64.getUrlDecoder().decode(request.response.signature)

        // 4. Validate using webauthn4j
        val authenticationRequest = AuthenticationRequest(
            credentialId,
            request.response.userHandle?.let { Base64.getUrlDecoder().decode(it) },
            authenticatorData,
            clientDataJSON,
            null,
            signature
        )

        val origin = resolveOrigin()

        val serverProperty = ServerProperty(
            origin,
            rpId,
            DefaultChallenge(Base64.getUrlDecoder().decode(challengeRecord.challenge)),
            null
        )

        // Rebuild authenticator from stored data
        val attestedCredentialData = attestedCredentialDataConverter.convert(credential.publicKeyCose)
        val authenticator = AuthenticatorImpl(
            attestedCredentialData,
            null,
            credential.signCount
        )

        val authenticationParameters = AuthenticationParameters(
            serverProperty,
            authenticator,
            listOf(credentialId),
            true
        )

        val authenticationData = webAuthnManager.parse(authenticationRequest)
        webAuthnManager.validate(authenticationData, authenticationParameters)

        // 5. Update sign count and last used time
        val now = System.currentTimeMillis()
        sql.createUpdate(PasskeyCredential::class) {
            set(table.signCount, authenticationData.authenticatorData!!.signCount)
            set(table.lastUsedAt, now)
            where(table.id eq request.id)
        }.execute()

        // 6. Delete used challenge
        sql.deleteById(WebAuthnChallenge::class, request.sessionId)
    }

    // ==================== Management ====================

    fun listCredentials(): List<PasskeyCredentialInfo> {
        return sql.createQuery(PasskeyCredential::class) {
            orderBy(table.createdAt.desc())
            select(table)
        }.execute().map { cred ->
            PasskeyCredentialInfo(
                id = cred.id,
                name = cred.name,
                createdAt = cred.createdAt,
                lastUsedAt = cred.lastUsedAt,
                transports = cred.transports?.let { objectMapper.readValue<List<String>>(it) }
            )
        }
    }

    fun deleteCredential(credentialId: String) {
        val count = sql.executeQuery(PasskeyCredential::class) { selectCount() }[0]
        if (count <= 1) {
            throw IllegalStateException("Cannot delete the last Passkey. Please ensure you have at least one login method.")
        }
        sql.deleteById(PasskeyCredential::class, credentialId)
    }

    fun updateCredentialName(credentialId: String, name: String) {
        sql.createUpdate(PasskeyCredential::class) {
            set(table.name, name)
            where(table.id eq credentialId)
        }.execute()
    }

    fun hasPasskeys(): Boolean {
        return sql.executeQuery(PasskeyCredential::class) { selectCount() }[0] > 0
    }

    private fun cleanExpiredChallenges() {
        val now = System.currentTimeMillis()
        sql.createDelete(WebAuthnChallenge::class) {
            where(table.expiresAt lt now)
        }.execute()
    }

    private fun resolveOrigin(): Origin {
        if (configuredOrigin.isNotBlank()) {
            return Origin(configuredOrigin)
        }
        val ctx = Context.current()
        val headerOrigin = ctx?.header("Origin")
        if (!headerOrigin.isNullOrBlank() && headerOrigin != "null") {
            return Origin(headerOrigin)
        }
        val scheme = ctx?.uri()?.scheme
            ?: if (ctx?.isSecure == true) "https" else if (ctx != null) "http" else "https"
        val host = ctx?.header("Host") ?: rpId
        return Origin("$scheme://$host")
    }
}

// ==================== DTOs ====================

data class PasskeyRegisterStartResponse(
    val sessionId: String,
    val challenge: String,
    val rpId: String,
    val rpName: String,
    val userId: String,
    val userName: String,
    val userDisplayName: String,
    val pubKeyCredParams: List<PubKeyCredParam>,
    val authenticatorSelection: AuthenticatorSelection,
    val timeout: Long,
    val attestation: String,
    val excludeCredentials: List<CredentialDescriptor>
)

data class PubKeyCredParam(
    val type: String,
    val alg: Int
)

data class AuthenticatorSelection(
    val authenticatorAttachment: String?,
    val residentKey: String,
    val userVerification: String
)

data class CredentialDescriptor(
    val type: String,
    val id: String,
    val transports: List<String>?
)

data class PasskeyRegisterFinishRequest(
    val sessionId: String,
    val credentialName: String,
    val id: String,
    val rawId: String,
    val type: String,
    val response: AuthenticatorAttestationResponse,
    val authenticatorAttachment: String?,
    val clientExtensionResults: Map<String, Any>?
)

data class AuthenticatorAttestationResponse(
    val clientDataJSON: String,
    val attestationObject: String,
    val transports: List<String>?
)

data class PasskeyAuthStartResponse(
    val sessionId: String,
    val challenge: String,
    val rpId: String,
    val timeout: Long,
    val userVerification: String,
    val allowCredentials: List<CredentialDescriptor>
)

data class PasskeyAuthFinishRequest(
    val sessionId: String,
    val id: String,
    val rawId: String,
    val type: String,
    val response: AuthenticatorAssertionResponse,
    val authenticatorAttachment: String?,
    val clientExtensionResults: Map<String, Any>?
)

data class AuthenticatorAssertionResponse(
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String?
)

data class PasskeyCredentialInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val transports: List<String>?
)

data class PasskeyUpdateNameRequest(
    val name: String
)

data class HasPasskeysResponse(
    val available: Boolean
)
