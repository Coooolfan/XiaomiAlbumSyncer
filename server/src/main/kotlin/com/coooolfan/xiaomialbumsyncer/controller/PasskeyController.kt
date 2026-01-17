package com.coooolfan.xiaomialbumsyncer.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.xiaomialbumsyncer.service.*
import org.babyfish.jimmer.client.meta.Api
import org.noear.solon.annotation.Body
import org.noear.solon.annotation.Controller
import org.noear.solon.annotation.Mapping
import org.noear.solon.annotation.Path
import org.noear.solon.core.handle.MethodType

/**
 * Passkey (WebAuthn) Controller
 *
 * Provides APIs for Passkey registration, authentication, and management.
 */
@Api
@Controller
@Mapping("/api/passkey")
class PasskeyController(private val service: PasskeyService) {

    // ==================== Registration Flow ====================

    /**
     * Start Passkey registration
     *
     * Requires login and password verification.
     * Returns WebAuthn options for the browser to create a new credential.
     */
    @Api
    @Mapping("/register/start", method = [MethodType.POST])
    @SaCheckLogin
    fun startRegistration(@Body request: PasskeyRegisterStartRequest): PasskeyRegisterStartResponse {
        return service.startRegistration(request.password, request.credentialName)
    }

    /**
     * Finish Passkey registration
     *
     * Validates the credential and saves it to the database.
     */
    @Api
    @Mapping("/register/finish", method = [MethodType.POST])
    @SaCheckLogin
    fun finishRegistration(@Body request: PasskeyRegisterFinishRequest): PasskeyCredentialInfo {
        return service.finishRegistration(request)
    }

    // ==================== Authentication Flow ====================

    /**
     * Start Passkey authentication
     *
     * Returns WebAuthn options for the browser to authenticate.
     * Public API, no login required.
     */
    @Api
    @Mapping("/authenticate/start", method = [MethodType.POST])
    fun startAuthentication(): PasskeyAuthStartResponse {
        return service.startAuthentication()
    }

    /**
     * Finish Passkey authentication
     *
     * Validates the assertion and logs in the user.
     * Public API, no login required.
     */
    @Api
    @Mapping("/authenticate/finish", method = [MethodType.POST])
    fun finishAuthentication(@Body request: PasskeyAuthFinishRequest) {
        service.finishAuthentication(request)
        StpUtil.login(0)  // Single user system, user ID is always 0
    }

    // ==================== Management ====================

    /**
     * Get registered Passkey list
     */
    @Api
    @Mapping("", method = [MethodType.GET])
    @SaCheckLogin
    fun listCredentials(): List<PasskeyCredentialInfo> {
        return service.listCredentials()
    }

    /**
     * Delete a Passkey
     */
    @Api
    @Mapping("/{credentialId}", method = [MethodType.DELETE])
    @SaCheckLogin
    fun deleteCredential(@Path credentialId: String) {
        service.deleteCredential(credentialId)
    }

    /**
     * Update Passkey name
     */
    @Api
    @Mapping("/{credentialId}/name", method = [MethodType.POST])
    @SaCheckLogin
    fun updateCredentialName(@Path credentialId: String, @Body request: PasskeyUpdateNameRequest) {
        service.updateCredentialName(credentialId, request.name)
    }

    /**
     * Check if any Passkeys are registered
     *
     * Public API for login page to determine available login methods.
     */
    @Api
    @Mapping("/available", method = [MethodType.GET])
    fun hasPasskeys(): HasPasskeysResponse {
        return HasPasskeysResponse(service.hasPasskeys())
    }
}

/**
 * Request DTO for starting Passkey registration
 */
data class PasskeyRegisterStartRequest(
    val password: String,
    val credentialName: String
)
