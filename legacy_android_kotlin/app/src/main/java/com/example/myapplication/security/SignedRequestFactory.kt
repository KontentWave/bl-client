package com.example.myapplication.security

import com.example.myapplication.phone.PhoneNumberNormalizer

interface SignedRequestFactory {
    fun createVerifyRequest(challengeId: String): SignedRequestPayload

    fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload

    fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload
}

class SecuritySignedRequestFactory(
    private val securityManager: SecurityManager = SecurityManager(),
) : SignedRequestFactory {
    override fun createVerifyRequest(challengeId: String): SignedRequestPayload {
        val normalizedPublicKey = normalizedPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createVerify(challengeId, normalizedPublicKey)
        return securityManager.createSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload {
        require(PhoneNumberNormalizer.normalize(clientPhoneNumber) == clientPhoneNumber) {
            "Normalize the report number before signing and sending it."
        }
        val normalizedPublicKey = existingPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createReport(
            clientPhoneNumber = clientPhoneNumber,
            feature = feature,
            normalizedPublicKey = normalizedPublicKey,
        )
        return securityManager.createExistingSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload {
        val normalizedPublicKey = existingPublicKey()
        val canonicalPayload = CanonicalPayloadFactory.createBlacklistCheck(
            targetHash = targetHash,
            normalizedPublicKey = normalizedPublicKey,
        )
        return securityManager.createExistingSignedRequestPayload(canonicalPayload, normalizedPublicKey)
    }

    private fun normalizedPublicKey(): String =
        PublicKeyPemEncoder.normalize(securityManager.getPublicKeyPem())

    private fun existingPublicKey(): String =
        PublicKeyPemEncoder.normalize(PublicKeyPemEncoder.toPem(securityManager.requireExistingPublicKey()))
}
